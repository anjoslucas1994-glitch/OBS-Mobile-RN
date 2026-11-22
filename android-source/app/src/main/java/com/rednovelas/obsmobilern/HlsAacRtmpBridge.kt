package com.rednovelas.obsmobilern

import android.media.MediaCodec
import android.media.AudioFormat
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ponte experimental de áudio real HLS PCM16 -> AAC -> RTMP.
 * Envia somente no canal de testes após Publish.Start confirmado.
 * Não captura microfone. A callback de áudio nunca bloqueia o Media3.
 */
internal class HlsAacRtmpBridge(
    private val tap: HlsAudioPcmTap,
    private val output: EncodedRtmpOutput,
    private val onError: (Throwable) -> Unit
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val queuedPcm = ArrayBlockingQueue<ByteArray>(32)
    private val audioPackets = AtomicInteger(0)
    private var thread: Thread? = null

    val packetCount: Int get() = audioPackets.get()

    fun start() {
        check(running.compareAndSet(false, true)) { "Áudio já iniciado" }
        val config = tap.snapshot()
        require(config.encoding == AudioFormat.ENCODING_PCM_16BIT &&
            config.sampleRate in listOf(32000, 44100, 48000) &&
            config.channels in 1..2
        ) {
            running.set(false)
            "Formato PCM do M3U8 ainda indisponível ou incompatível"
        }
        val frameBytes = config.channels * 2
        val consumer: (ByteArray) -> Unit = { bytes ->
            if (running.get()) {
                // Sem bloqueio no thread do ExoPlayer: descarta buffers
                // quando houver atraso de rede para evitar travar o vídeo.
                if (bytes.size % frameBytes == 0) {
                    if (!queuedPcm.offer(bytes)) {
                        queuedPcm.poll()
                        queuedPcm.offer(bytes)
                    }
                }
            }
        }
        tap.setPcmConsumer(consumer)
        thread = Thread({
            val encoder = AacPcmEncoder(config.sampleRate, config.channels, 128_000)
            val clock = PcmTimestampClock(config.sampleRate, config.channels)
            try {
                encoder.start()
                while (running.get() || queuedPcm.isNotEmpty()) {
                    val pcm = queuedPcm.poll(100, TimeUnit.MILLISECONDS)
                    if (pcm != null) {
                        val buffer = ByteBuffer.wrap(pcm)
                        while (buffer.hasRemaining() && running.get()) {
                            val consumed = encoder.queuePcm(buffer, clock.timestampUs(), 1000)
                            if (consumed > 0) clock.advancePcmBytes(consumed)
                            encoder.drain(
                                timeoutUs = 0,
                                onFormat = output::onAudioFormat,
                                onSample = { sample: ByteBuffer, info: MediaCodec.BufferInfo ->
                                    output.onAudioSample(sample, info)
                                    if (info.size > 0 &&
                                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                                    ) audioPackets.incrementAndGet()
                                }
                            )
                        }
                    } else {
                        encoder.drain(
                            timeoutUs = 0,
                            onFormat = output::onAudioFormat,
                            onSample = { sample, info ->
                                output.onAudioSample(sample, info)
                                if (info.size > 0 &&
                                    info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                                ) audioPackets.incrementAndGet()
                            }
                        )
                    }
                }
            } catch (error: Throwable) {
                if (running.get()) {
                    Handler(Looper.getMainLooper()).post { onError(error) }
                }
            } finally {
                tap.setPcmConsumer(null)
                running.set(false)
                encoder.close()
                queuedPcm.clear()
            }
        }, "RN-HLS-AAC-RTMP").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        running.set(false)
        tap.setPcmConsumer(null)
        queuedPcm.clear()
    }
}
