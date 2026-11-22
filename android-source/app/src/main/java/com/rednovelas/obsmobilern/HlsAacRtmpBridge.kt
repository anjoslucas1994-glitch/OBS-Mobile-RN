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
    private val muted = AtomicBoolean(false)
    private val volumePercent = AtomicInteger(100)

    /** Silencia só a saída AAC RTMP, não o som local da prévia. */
    fun setMuted(value: Boolean) { muted.set(value) }
    fun isMuted(): Boolean = muted.get()

    /** 0–100%; evita amplificação e clipping do áudio do canal de testes. */
    fun setVolumePercent(value: Int) { volumePercent.set(value.coerceIn(0, 100)) }
    fun getVolumePercent(): Int = volumePercent.get()

    private fun applyPcmVolume(bytes: ByteArray, level: Int) {
        if (level == 100) return
        if (level == 0) {
            bytes.fill(0)
            return
        }
        // PCM16 little-endian intercalado. Converte cada amostra sem alterar
        // frequência, canais ou duração, preservando a sincronização do AAC.
        var index = 0
        while (index + 1 < bytes.size) {
            val low = bytes[index].toInt() and 0xff
            val high = bytes[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            val scaled = sample * level / 100
            bytes[index] = (scaled and 0xff).toByte()
            bytes[index + 1] = (scaled shr 8).toByte()
            index += 2
        }
    }
    private val queuedPcm = ArrayBlockingQueue<ByteArray>(32)
    private val sourceCutLock = Any()
    private var waitForAudioFlushAfterCut = false
    private var audioFlushCountAtCut = 0L
    private val audioPackets = AtomicInteger(0)
    private var thread: Thread? = null

    /** Remove PCM ainda na fila antes de trocar a fonte HLS ao vivo.
     *  Não reinicia o codificador nem altera o relógio AAC.
     *  Um buffer já consumido pelo encoder não pode ser recuperado aqui.
     */
    fun discardQueuedPcmBeforeCut() {
        synchronized(sourceCutLock) {
            audioFlushCountAtCut = tap.snapshot().flushes
            waitForAudioFlushAfterCut = true
            queuedPcm.clear()
        }
    }

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
                    // O PCM é uma cópia independente: mutar não altera o som da prévia.
                    applyPcmVolume(bytes, if (muted.get()) 0 else volumePercent.get())
                    synchronized(sourceCutLock) {
                        if (waitForAudioFlushAfterCut) {
                            if (tap.snapshot().flushes <= audioFlushCountAtCut) {
                                // Não aceitar novos buffers da fonte anterior.
                                return@synchronized
                            }
                            waitForAudioFlushAfterCut = false
                        }
                        if (!queuedPcm.offer(bytes)) {
                            queuedPcm.poll()
                            queuedPcm.offer(bytes)
                        }
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
                            if (consumed > 0) {
                                clock.advancePcmBytes(consumed)
                            } else {
                                // Codificador temporariamente sem buffer disponível.
                                // Evita laço ocupado que consome CPU e aquece o celular.
                                Thread.sleep(4L)
                            }
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
