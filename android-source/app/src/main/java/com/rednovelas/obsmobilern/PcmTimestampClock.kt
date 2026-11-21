package com.rednovelas.obsmobilern

/**
 * Calcula timestamps de áudio PCM sem depender do relógio da interface.
 * Um frame de áudio contém uma amostra por canal.
 * Não captura nem transmite áudio.
 */
internal class PcmTimestampClock(
    private val sampleRate: Int,
    private val channels: Int,
    private val bytesPerSample: Int = 2
) {
    init {
        require(sampleRate > 0)
        require(channels in 1..2)
        require(bytesPerSample > 0)
    }

    private var framesSubmitted: Long = 0
    private var baseTimeUs: Long = 0

    fun reset(startTimeUs: Long = 0) {
        require(startTimeUs >= 0)
        baseTimeUs = startTimeUs
        framesSubmitted = 0
    }

    fun timestampUs(): Long = baseTimeUs + framesSubmitted * 1_000_000L / sampleRate

    fun advancePcmBytes(bytes: Int) {
        require(bytes >= 0)
        val frameBytes = channels * bytesPerSample
        require(bytes % frameBytes == 0) { "PCM deve conter frames completos" }
        framesSubmitted += bytes / frameBytes
    }
}
