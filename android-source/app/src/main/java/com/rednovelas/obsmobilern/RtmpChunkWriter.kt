package com.rednovelas.obsmobilern

import java.io.ByteArrayOutputStream

/**
 * Serializa uma mensagem RTMP usando chunks fmt=0 e fmt=3.
 * O payload pode ser uma tag de áudio/vídeo FLV SEM o cabeçalho de 11 bytes
 * e SEM PreviousTagSize. Ainda não há conexão ou comando publish.
 */
internal object RtmpChunkWriter {
    fun encode(
        chunkStreamId: Int,
        messageStreamId: Int,
        messageTypeId: Int,
        timestampMs: Long,
        payload: ByteArray,
        chunkSize: Int = 128
    ): ByteArray {
        require(chunkStreamId in 2..63)
        require(messageStreamId >= 0)
        require(messageTypeId in 0..255)
        require(timestampMs in 0..0xffffffffL)
        require(payload.size <= 0xffffff)
        require(chunkSize in 1..0x7fffffff)
        val extended = timestampMs >= 0xffffff
        val out = ByteArrayOutputStream()
        out.write(chunkStreamId)
        write24(out, if (extended) 0xffffff else timestampMs.toInt())
        write24(out, payload.size)
        out.write(messageTypeId)
        write32LE(out, messageStreamId)
        if (extended) write32BE(out, timestampMs.toInt())
        var offset = 0
        while (offset < payload.size) {
            if (offset > 0) {
                out.write(0xc0 or chunkStreamId)
                if (extended) write32BE(out, timestampMs.toInt())
            }
            val length = minOf(chunkSize, payload.size - offset)
            out.write(payload, offset, length)
            offset += length
        }
        return out.toByteArray()
    }

    private fun write24(out: ByteArrayOutputStream, v: Int) {
        out.write(v ushr 16 and 255)
        out.write(v ushr 8 and 255)
        out.write(v and 255)
    }
    private fun write32BE(out: ByteArrayOutputStream, v: Int) {
        out.write(v ushr 24 and 255)
        out.write(v ushr 16 and 255)
        out.write(v ushr 8 and 255)
        out.write(v and 255)
    }
    private fun write32LE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 255)
        out.write(v ushr 8 and 255)
        out.write(v ushr 16 and 255)
        out.write(v ushr 24 and 255)
    }
}
