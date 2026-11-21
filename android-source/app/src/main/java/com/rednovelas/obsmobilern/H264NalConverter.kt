package com.rednovelas.obsmobilern

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Converte amostras H.264 Annex-B (start codes 00 00 01 / 00 00 00 01)
 * para AVCC (NAL precedida de tamanho de 4 bytes), formato exigido
 * pelo empacotador FLV. Não altera o buffer de entrada.
 */
internal object H264NalConverter {
    fun annexBToAvcc(sample: ByteBuffer): ByteBuffer {
        val data = ByteArray(sample.remaining())
        sample.slice().get(data)
        fun startCodeLength(at: Int): Int {
            if (at + 3 < data.size && data[at] == 0.toByte() &&
                data[at + 1] == 0.toByte() && data[at + 2] == 0.toByte() &&
                data[at + 3] == 1.toByte()) return 4
            if (at + 2 < data.size && data[at] == 0.toByte() &&
                data[at + 1] == 0.toByte() && data[at + 2] == 1.toByte()) return 3
            return 0
        }
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < data.size) {
            val prefix = startCodeLength(pos)
            require(prefix > 0) { "Amostra H.264 não está no formato Annex-B" }
            val begin = pos + prefix
            var end = begin
            while (end < data.size && startCodeLength(end) == 0) end++
            // Annex-B permite zeros de preenchimento após a última NAL.
            val lastNal = end == data.size
            if (lastNal) {
                while (end > begin && data[end - 1] == 0.toByte()) end--
            }
            require(end > begin) { "NAL H.264 vazia" }
            val length = end - begin
            out.write(length ushr 24 and 255)
            out.write(length ushr 16 and 255)
            out.write(length ushr 8 and 255)
            out.write(length and 255)
            out.write(data, begin, length)
            pos = if (lastNal) data.size else end
        }
        require(out.size() > 0) { "Amostra H.264 vazia" }
        return ByteBuffer.wrap(out.toByteArray())
    }
}
