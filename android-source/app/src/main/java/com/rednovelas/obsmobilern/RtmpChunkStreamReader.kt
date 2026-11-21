package com.rednovelas.obsmobilern

import java.io.EOFException
import java.io.InputStream

/**
 * Leitor RTMP com estado por chunk stream: suporta fmt 0/1/2/3,
 * chunks intercalados e mensagens fragmentadas.
 * A alteração do tamanho de chunk de entrada é aplicada pelo chamador.
 */
internal class RtmpChunkStreamReader(
    private val input: InputStream,
    var chunkSize: Int = 128,
    private val maxMessageSize: Int = 1024 * 1024
) {
    private data class State(
        var timestamp: Long = 0,
        var delta: Long = 0,
        var length: Int = 0,
        var type: Int = 0,
        var streamId: Int = 0,
        var extended: Boolean = false,
        var absoluteHeader: Boolean = true,
        var received: Int = 0,
        var payload: ByteArray = byteArrayOf(),
        var initialized: Boolean = false
    )
    private val states = mutableMapOf<Int, State>()

    fun read(): RtmpSimpleChunkReader.Message {
        require(chunkSize in 1..(1024 * 1024))
        while (true) {
            val basic = byte()
            val fmt = basic ushr 6
            val rawId = basic and 63
            val csid = when (rawId) {
                0 -> 64 + byte()
                1 -> 64 + byte() + (byte() shl 8)
                else -> rawId
            }
            val state = states.getOrPut(csid) { State() }
            require(fmt == 0 || state.initialized) { "Cabeçalho RTMP sem estado anterior" }
            val continuing = state.initialized && state.received < state.length
            require(!continuing || fmt == 3) { "Continuação RTMP esperava fmt=3" }

            when (fmt) {
                0 -> {
                    val ts = u24()
                    state.length = u24()
                    state.type = byte()
                    state.streamId = byte() or (byte() shl 8) or
                        (byte() shl 16) or (byte() shl 24)
                    state.extended = ts == 0xffffff
                    state.timestamp = if (state.extended) u32() else ts.toLong()
                    state.delta = 0
                    state.absoluteHeader = true
                }
                1 -> {
                    val delta = u24()
                    state.length = u24()
                    state.type = byte()
                    state.extended = delta == 0xffffff
                    state.delta = if (state.extended) u32() else delta.toLong()
                    state.timestamp += state.delta
                    state.absoluteHeader = false
                }
                2 -> {
                    val delta = u24()
                    state.extended = delta == 0xffffff
                    state.delta = if (state.extended) u32() else delta.toLong()
                    state.timestamp += state.delta
                    state.absoluteHeader = false
                }
                3 -> {
                    if (state.extended) {
                        val extended = u32()
                        if (!continuing) {
                            state.timestamp = if (state.absoluteHeader) extended
                                else state.timestamp + extended
                            state.delta = if (state.absoluteHeader) 0 else extended
                        }
                    } else if (!continuing) {
                        state.timestamp += state.delta
                    }
                }
            }
            if (!continuing) {
                require(state.length in 0..maxMessageSize) { "Mensagem RTMP grande demais" }
                state.payload = ByteArray(state.length)
                state.received = 0
                state.initialized = true
            }
            val count = minOf(chunkSize, state.length - state.received)
            var done = 0
            while (done < count) {
                val n = input.read(state.payload, state.received + done, count - done)
                if (n < 0) throw EOFException("Payload RTMP incompleto")
                if (n > 0) done += n
            }
            state.received += count
            if (state.received == state.length) {
                return RtmpSimpleChunkReader.Message(
                    csid, state.streamId, state.type, state.timestamp, state.payload
                )
            }
        }
    }

    private fun byte(): Int = input.read().also {
        if (it < 0) throw EOFException("Chunk RTMP incompleto")
    }
    private fun u24(): Int = (byte() shl 16) or (byte() shl 8) or byte()
    private fun u32(): Long = (byte().toLong() shl 24) or
        (byte().toLong() shl 16) or (byte().toLong() shl 8) or byte().toLong()
}
