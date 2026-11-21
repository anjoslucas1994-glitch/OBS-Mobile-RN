package com.rednovelas.obsmobilern

/**
 * Separa o endereço RTMP em servidor, aplicação e nome do stream.
 * A chave não deve ser exibida em logs ou mensagens.
 * Aceita somente caminhos no formato /aplicacao/stream.
 */
internal data class RtmpDestination(
    val serverUrl: String,
    val app: String,
    val streamName: String,
    val tcUrl: String
) {
    companion object {
        fun parse(server: String, key: String): RtmpDestination {
            val normalized = server.trim().trimEnd('/')
            val match = Regex("^(rtmps?)://([^/?#]+)(/[^?#]*)?$", RegexOption.IGNORE_CASE)
                .matchEntire(normalized)
                ?: throw IllegalArgumentException("Endereço RTMP inválido")
            val scheme = match.groupValues[1].lowercase()
            val authority = match.groupValues[2]
            val path = match.groupValues[3].trim('/')
            val segments = path.split('/').filter { it.isNotBlank() }
            require(segments.isNotEmpty()) { "Informe o nome da aplicação RTMP no servidor" }
            require(segments.size == 1) { "Informe o endereço do servidor sem a chave" }
            require(key.isNotBlank()) { "Chave de transmissão não informada" }
            require(!key.contains('/') && !key.contains('?') && !key.contains('#')) {
                "A chave deve ser informada separadamente, sem barras ou parâmetros"
            }
            val app = segments[0]
            val streamName = key.trim()
            val base = "$scheme://$authority/$app"
            return RtmpDestination(base, app, streamName, base)
        }
    }
}
