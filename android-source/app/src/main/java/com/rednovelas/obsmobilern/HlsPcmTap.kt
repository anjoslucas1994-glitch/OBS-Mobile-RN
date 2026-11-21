package com.rednovelas.obsmobilern

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Observa PCM que o próprio ExoPlayer decodifica do HLS.
 * Apenas conta bytes para diagnóstico: não grava áudio e não transmite.
 * O TeeAudioProcessor continua entregando áudio normal ao alto-falante.
 */
internal class HlsPcmTap : TeeAudioProcessor.AudioBufferSink {
    private val byteCounter = AtomicLong(0)
    @Volatile var sampleRateHz: Int = 0
        private set
    @Volatile var channels: Int = 0
        private set
    @Volatile var encoding: Int = 0
        private set

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRateHz = sampleRateHz
        channels = channelCount
        this.encoding = encoding
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        byteCounter.addAndGet(buffer.remaining().toLong())
    }

    fun totalPcmBytes(): Long = byteCounter.get()

    fun isPcm16Bit(): Boolean = encoding == C.ENCODING_PCM_16BIT
}
