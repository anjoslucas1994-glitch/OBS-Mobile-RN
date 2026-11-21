package com.rednovelas.obsmobilern

import android.net.Uri

/**
 * Verificação local preventiva. Não abre conexão, não autentica
 * e não garante que o destino esteja seguro ou disponível.
 */
internal object StreamDestinationGuard {
    sealed class Result {
        object AllowedForTesting : Result()
        data class Blocked(val reason: String) : Result()
    }

    fun inspect(sourceHls: String, rtmpServer: String, streamKey: String): Result {
        val source = runCatching { Uri.parse(sourceHls.trim()) }.getOrNull()
            ?: return Result.Blocked("Endereço de origem inválido")
        val output = runCatching { Uri.parse(rtmpServer.trim()) }.getOrNull()
            ?: return Result.Blocked("Endereço de destino inválido")
        if (source.scheme !in listOf("http", "https") || source.host.isNullOrBlank()) {
            return Result.Blocked("A origem precisa ser HTTP ou HTTPS")
        }
        if (output.scheme !in listOf("rtmp", "rtmps") || output.host.isNullOrBlank()) {
            return Result.Blocked("O destino precisa ser RTMP ou RTMPS")
        }
        if (streamKey.isBlank()) return Result.Blocked("Chave de transmissão não informada")

        val originHost = source.host.orEmpty().lowercase()
        val targetHost = output.host.orEmpty().lowercase()
        val originPath = source.path.orEmpty().trim('/')
        val targetPath = output.path.orEmpty().trim('/')
        val originChannel = originPath.substringBefore('/').lowercase()
        val targetChannel = targetPath.substringBefore('/').lowercase()
        if (originHost == targetHost && originChannel.isNotBlank() && originChannel == targetChannel) {
            return Result.Blocked(
                "Possível loop: entrada e saída usam o mesmo host e canal. Utilize um destino de teste independente."
            )
        }
        return Result.AllowedForTesting
    }
}
