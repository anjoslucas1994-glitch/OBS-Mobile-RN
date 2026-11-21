package com.rednovelas.obsmobilern

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Empacotamento FLV de amostras H.264 (AVCC) e AAC para futura saída RTMP.
 *
 * Recebe H.264 com NALs prefixados por tamanho de 4 bytes e AAC sem cabeçalho ADTS.
 * SPS/PPS e AudioSpecificConfig devem ser obtidos do MediaFormat do codificador.
 * Não abre conexão de rede nem transmite sozinho.
 */
internal object FlvPacketizer {
    fun avcConfiguration(sps: ByteArray, pps: ByteArray): ByteArray {
        require(sps.size >= 4 && sps.size <= 65535)
        require(pps.isNotEmpty() && pps.size <= 65535)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x17, 0x00, 0x00, 0x00, 0x00))
        out.write(1)
        out.write(sps[1].toInt() and 255)
        out.write(sps[2].toInt() and 255)
        out.write(sps[3].toInt() and 255)
        out.write(0xff)
        out.write(0xe1)
        write16(out, sps.size)
        out.write(sps)
        out.write(1)
        write16(out, pps.size)
        out.write(pps)
        return out.toByteArray()
    }

    fun avcFrame(avccSample: ByteBuffer, keyframe: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(if (keyframe) 0x17 else 0x27)
        out.write(byteArrayOf(0x01, 0x00, 0x00, 0x00))
        val sample = avccSample.slice()
        require(sample.remaining() > 0)
        while (sample.hasRemaining()) {
            require(sample.remaining() >= 4) { "Cabeçalho NAL incompleto" }
            val size = sample.int
            require(size > 0 && size <= sample.remaining()) { "Tamanho NAL inválido" }
            write32(out, size)
            val nal = ByteArray(size)
            sample.get(nal)
            out.write(nal)
        }
        return out.toByteArray()
    }

    fun aacConfiguration(audioSpecificConfig: ByteArray): ByteArray {
        require(audioSpecificConfig.isNotEmpty())
        return byteArrayOf(0xaf.toByte(), 0x00) + audioSpecificConfig
    }

    fun aacFrame(rawAac: ByteBuffer): ByteArray {
        val sample = rawAac.slice()
        require(sample.hasRemaining())
        val payload = ByteArray(sample.remaining())
        sample.get(payload)
        return byteArrayOf(0xaf.toByte(), 0x01) + payload
    }

    private fun write16(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 8 and 255)
        out.write(value and 255)
    }

    private fun write32(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 24 and 255)
        out.write(value ushr 16 and 255)
        out.write(value ushr 8 and 255)
        out.write(value and 255)
    }
}
