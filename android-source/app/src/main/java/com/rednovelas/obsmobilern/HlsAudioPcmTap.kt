package com.rednovelas.obsmobilern

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Cópia passiva dos quadros PCM que o ExoPlayer envia ao alto-falante.
 * Não captura microfone, não transmite à rede e não armazena áudio.
 * O limite protege contra excesso de CPU/memória durante a prévia.
 */
internal class HlsAudioPcmTap {
    @Volatile var sampleRate = 0
        private set
    @Volatile var channels = 0
        private set
    @Volatile var encoding = 0
        private set

    private val bytesSeen = AtomicLong(0L)
    private val buffersSeen = AtomicLong(0L)

    fun resetCounters() {
        bytesSeen.set(0L)
        buffersSeen.set(0L)
    }

    fun snapshot(): Snapshot = Snapshot(
        sampleRate, channels, encoding,
        bytesSeen.get(), buffersSeen.get()
    )

    data class Snapshot(
        val sampleRate: Int,
        val channels: Int,
        val encoding: Int,
        val bytes: Long,
        val buffers: Long
    )

    val sink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            sampleRate = sampleRateHz
            channels = channelCount
            this@HlsAudioPcmTap.encoding = encoding
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            // TeeAudioProcessor fornece uma cópia de leitura dos dados PCM;
            // não alterar position/limit do áudio em reprodução.
            bytesSeen.addAndGet(buffer.remaining().toLong())
            buffersSeen.incrementAndGet()
        }
    }
}

/** Instala o TeeAudioProcessor na saída do ExoPlayer para testar PCM HLS real. */
internal class HlsAudioRenderersFactory(
    context: Context,
    private val tap: HlsAudioPcmTap
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(tap.sink)))
            .build()
    }
}
