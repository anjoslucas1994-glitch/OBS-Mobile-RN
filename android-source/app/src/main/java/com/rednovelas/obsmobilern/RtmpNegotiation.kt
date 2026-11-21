package com.rednovelas.obsmobilern

/**
 * Controla a ordem de respostas do servidor durante a negociação RTMP.
 * Não abre socket, não envia comandos e não publica mídia.
 */
internal class RtmpNegotiation {
    enum class Stage { WAIT_CONNECT, WAIT_STREAM, READY, FAILED }
    var stage = Stage.WAIT_CONNECT
        private set
    var streamId: Int? = null
        private set

    fun onCommand(response: RtmpCommandResponse.Response): Stage {
        if (stage == Stage.FAILED || stage == Stage.READY) return stage
        if (response.command == "_error") {
            stage = Stage.FAILED
            return stage
        }
        if (response.command != "_result") return stage
        when (stage) {
            Stage.WAIT_CONNECT -> {
                if (response.transactionId == 1.0) stage = Stage.WAIT_STREAM
            }
            Stage.WAIT_STREAM -> {
                if (response.transactionId == 4.0) {
                    val id = response.streamId
                    if (id != null && id.isFinite() && id > 0 &&
                        id <= Int.MAX_VALUE && id == id.toInt().toDouble()) {
                        streamId = id.toInt()
                        stage = Stage.READY
                    } else stage = Stage.FAILED
                }
            }
            else -> Unit
        }
        return stage
    }
}
