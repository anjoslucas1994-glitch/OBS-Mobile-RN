package com.rednovelas.obsmobilern

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lê o campo "code" de um evento onStatus AMF0 do servidor RTMP.
 * Não transforma uma resposta recebida em transmissão ativa por conta própria.
 */
internal object RtmpPublishStatus {
    fun code(payload: ByteArray): String? {
        val input = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        if (readString(input) != "onStatus") return null
        if (input.remaining() < 9 || (input.get().toInt() and 255) != 0) return null
        input.double // transaction ID
        if (!input.hasRemaining()) return null
        val commandObjectType = input.get().toInt() and 255
        if (commandObjectType != 5 && commandObjectType != 6) return null
        if (!input.hasRemaining() || (input.get().toInt() and 255) != 3) return null
        while (input.remaining() >= 3) {
            val keyLength = input.short.toInt() and 65535
            if (keyLength == 0) {
                if (!input.hasRemaining()) return null
                if ((input.get().toInt() and 255) == 9) return null
                return null
            }
            if (input.remaining() < keyLength + 1) return null
            val keyBytes = ByteArray(keyLength)
            input.get(keyBytes)
            val key = keyBytes.toString(Charsets.UTF_8)
            val type = input.get().toInt() and 255
            when (type) {
                2 -> {
                    if (input.remaining() < 2) return null
                    val length = input.short.toInt() and 65535
                    if (input.remaining() < length) return null
                    val value = ByteArray(length)
                    input.get(value)
                    if (key == "code") return value.toString(Charsets.UTF_8)
                }
                0 -> {
                    if (input.remaining() < 8) return null
                    input.position(input.position() + 8)
                }
                1 -> {
                    if (!input.hasRemaining()) return null
                    input.get()
                }
                5, 6 -> Unit
                else -> return null // Formato ainda não suportado
            }
        }
        return null
    }

    private fun readString(input: ByteBuffer): String? {
        if (input.remaining() < 3 || (input.get().toInt() and 255) != 2) return null
        val length = input.short.toInt() and 65535
        if (input.remaining() < length) return null
        val bytes = ByteArray(length)
        input.get(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    fun isPublishStarted(payload: ByteArray): Boolean =
        code(payload) == "NetStream.Publish.Start"
}
