package com.rednovelas.obsmobilern

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lê apenas os valores AMF0 necessários para identificar respostas de comandos RTMP.
 * Recebe um payload AMF0 já remontado (não processa chunks nem acessa a rede).
 */
internal object RtmpCommandResponse {
    data class Response(val command: String, val transactionId: Double, val streamId: Double?)

    fun parse(payload: ByteArray): Response {
        val input = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val command = readString(input)
        require(input.remaining() >= 9 && input.get().toInt() == 0) {
            "Transaction ID AMF0 ausente"
        }
        val transactionId = input.double
        var streamId: Double? = null
        // Resposta de createStream: _result, transactionId, null, streamId.
        if (command == "_result" && input.hasRemaining()) {
            val type = input.get().toInt() and 255
            if (type == 5 || type == 6) {
                if (input.remaining() >= 9) {
                    val nextType = input.get().toInt() and 255
                    if (nextType == 0) streamId = input.double
                }
            }
        }
        return Response(command, transactionId, streamId)
    }

    private fun readString(input: ByteBuffer): String {
        require(input.remaining() >= 3 && (input.get().toInt() and 255) == 2) {
            "Nome do comando AMF0 ausente"
        }
        val length = input.short.toInt() and 65535
        require(input.remaining() >= length) { "String AMF0 incompleta" }
        val bytes = ByteArray(length)
        input.get(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}
