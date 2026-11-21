package com.rednovelas.obsmobilern

import java.io.ByteArrayOutputStream

/**
 * Constrói tags FLV completas (cabeçalho, timestamp e PreviousTagSize).
 * Os payloads AVC/AAC vêm de FlvPacketizer.
 * Não publica RTMP nem faz sincronização entre trilhas.
 */
internal object FlvTagWriter {
    fun header(hasAudio: Boolean = true, hasVideo: Boolean = true): ByteArray {
        val flags = (if (hasAudio) 4 else 0) or (if (hasVideo) 1 else 0)
        return byteArrayOf(
            0x46, 0x4c, 0x56, 0x01, flags.toByte(),
            0, 0, 0, 9, 0, 0, 0, 0
        )
    }

    fun tag(type: Int, timestampMs: Long, payload: ByteArray): ByteArray {
        require(type == 8 || type == 9) { "Somente tags FLV de áudio/vídeo" }
        require(timestampMs in 0..0xffffffffL)
        require(payload.size <= 0xffffff)
        val out = ByteArrayOutputStream(payload.size + 15)
        out.write(type)
        write24(out, payload.size)
        write24(out, (timestampMs and 0xffffff).toInt())
        out.write((timestampMs ushr 24).toInt() and 255)
        write24(out, 0) // StreamID
        out.write(payload)
        val tagSize = 11 + payload.size
        out.write(tagSize ushr 24 and 255)
        out.write(tagSize ushr 16 and 255)
        out.write(tagSize ushr 8 and 255)
        out.write(tagSize and 255)
        return out.toByteArray()
    }

    private fun write24(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 16 and 255)
        out.write(value ushr 8 and 255)
        out.write(value and 255)
    }
}
