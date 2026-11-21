package com.rednovelas.obsmobilern

import java.io.ByteArrayOutputStream

/**
 * Codificador AMF0 mínimo para comandos RTMP connect/createStream/publish.
 * Apenas serializa valores; não abre sessão nem publica uma transmissão.
 */
internal object Amf0Writer {
    fun string(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65535) { "String AMF0 excede 65535 bytes" }
        val out = ByteArrayOutputStream()
        out.write(2)
        write16(out, bytes.size)
        out.write(bytes)
        return out.toByteArray()
    }

    fun number(value: Double): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0)
        val bits = java.lang.Double.doubleToRawLongBits(value)
        for (shift in 56 downTo 0 step 8) out.write((bits ushr shift).toInt() and 255)
        return out.toByteArray()
    }

    fun boolean(value: Boolean): ByteArray =
        byteArrayOf(1, if (value) 1 else 0)

    fun nullValue(): ByteArray = byteArrayOf(5)

    fun objectValue(properties: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(3)
        for ((name, value) in properties) {
            val key = name.toByteArray(Charsets.UTF_8)
            require(key.isNotEmpty() && key.size <= 65535)
            write16(out, key.size)
            out.write(key)
            out.write(value)
        }
        out.write(byteArrayOf(0, 0, 9))
        return out.toByteArray()
    }

    fun command(name: String, transactionId: Double, vararg args: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(string(name))
        out.write(number(transactionId))
        for (arg in args) out.write(arg)
        return out.toByteArray()
    }

    private fun write16(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 8 and 255)
        out.write(value and 255)
    }
}
