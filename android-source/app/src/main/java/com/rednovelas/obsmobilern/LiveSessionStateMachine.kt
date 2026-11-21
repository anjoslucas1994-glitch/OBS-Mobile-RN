package com.rednovelas.obsmobilern

/**
 * Estados internos planejados para a futura transmissão.
 * Este controlador não abre conexão nem envia vídeo.
 */
internal enum class LiveState {
    STOPPED, CONNECTING, LIVE, STOPPING, ERROR
}

internal class LiveSessionStateMachine {
    var state: LiveState = LiveState.STOPPED
        private set

    fun requestStart(): Boolean {
        if (state != LiveState.STOPPED && state != LiveState.ERROR) return false
        state = LiveState.CONNECTING
        return true
    }

    fun connected(): Boolean {
        if (state != LiveState.CONNECTING) return false
        state = LiveState.LIVE
        return true
    }

    fun requestStop(): Boolean {
        if (state != LiveState.CONNECTING && state != LiveState.LIVE) return false
        state = LiveState.STOPPING
        return true
    }

    fun stopped(): Boolean {
        if (state != LiveState.STOPPING) return false
        state = LiveState.STOPPED
        return true
    }

    fun failed() {
        state = LiveState.ERROR
    }

    fun reset() {
        state = LiveState.STOPPED
    }
}
