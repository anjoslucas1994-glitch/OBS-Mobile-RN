package com.rednovelas.obsmobilern

import java.io.EOFException
import java.io.InputStream

/**
 * Lê uma mensagem RTMP completa de um único chunk stream (fmt=0 seguido de fmt=3).
 * Leitor inicial para respostas de servidor; ainda não suporta interleaving de
 * múltiplos chunk streams nem fmt=1/2. Não usar em produção sem ampliar suporte.
 */
internal object RtmpSimpleChunkReader {
    data class Message(
        val chunkStreamId: Int,
        val messageStreamId: Int,
        val typeId: Int,
        val timestampMs: Long,
        val payload: ByteArray
    )

    fun read(input: InputStream, chunkSize: Int = 128, maxMessageSize: Int = 1024 * 1024): Message {
        require(chunkSize > 0)
        val first = readByte(input)
        require(first ushr 6 == 0) { "Esperado chunk RTMP fmt=0" }
        val csid = first and 63
        require(csid in 2..63) { "Chunk stream ID estendido não suportado" }
        val ts24 = read24(input)
        val length = read24(input)
        require(length <= maxMessageSize) { "Mensagem RTMP grande demais" }
        val type = readByte(input)
        val streamId = readByte(input) or (readByte(input) shl 8) or
            (readByte(input) shl 16) or (readByte(input) shl 24)
        val extended = ts24 == 0xffffff
        val timestamp = if (extended) read32(input) else ts24.toLong()
        val payload = ByteArray(length)
        var offset = 0
        while (offset < length) {
            if (offset > 0) {
                val continuation = readByte(input)
                require(continuation == (0xc0 or csid)) { "Continuação RTMP inválida" }
                if (extended) require(read32(input) == timestamp) { "Timestamp divergente" }
            }
            val count = minOf(chunkSize, length - offset)
            var done = 0
            while (done < count) {
                val n = input.read(payload, offset + done, count - done)
                if (n < 0) throw EOFException("Payload RTMP incompleto")
                if (n == 0) continue
                done += n
            }
            offset += count
        }
        return Message(csid, streamId, type, timestamp, payload)
    }

    private fun readByte(input: InputStream): Int {
        val value = input.read()
        if (value < 0) throw EOFException("Mensagem RTMP incompleta")
        return value
    }

    private fun read24(input: InputStream): Int =
        (readByte(input) shl 16) or (readByte(input) shl 8) or readByte(input)

    private fun read32(input: InputStream): Long =
        ((readByte(input).toLong() shl 24) or
         (readByte(input).toLong() shl 16) or
         (readByte(input).toLong() shl 8) or readByte(input).toLong())
}
