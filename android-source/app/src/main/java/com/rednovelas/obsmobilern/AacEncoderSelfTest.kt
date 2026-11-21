package com.rednovelas.obsmobilern

import android.media.MediaCodec
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * Teste isolado do codificador AAC-LC usando PCM sintético.
 * Não grava microfone, não extrai áudio HLS e não envia RTMP.
 */
internal object AacEncoderSelfTest {
    data class Result(val packets: Int, val totalBytes: Long)

    fun run(): Result {
        val sampleRate = 44100
        val channels = 2
        val seconds = 2
        val encoder = AacPcmEncoder(sampleRate, channels, 128000)
        val clock = PcmTimestampClock(sampleRate, channels)
        var packets = 0
        var bytes = 0L
        var formatConfirmed = false
        fun drain() {
            encoder.drain(
                timeoutUs = 1000,
                onFormat = { formatConfirmed = true },
                onSample = { _: ByteBuffer, info: MediaCodec.BufferInfo ->
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        packets++
                        bytes += info.size
                    }
                }
            )
        }
        try {
            encoder.start()
            val framesPerBatch = 1024
            var framesWritten = 0
            val targetFrames = sampleRate * seconds
            while (framesWritten < targetFrames) {
                val count = minOf(framesPerBatch, targetFrames - framesWritten)
                val pcm = ByteBuffer.allocate(count * channels * 2)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (n in 0 until count) {
                    val value = (sin(2.0 * PI * 440.0 * (framesWritten + n) / sampleRate) *
                        12000.0).toInt().toShort()
                    pcm.putShort(value)
                    pcm.putShort(value)
                }
                pcm.flip()
                var retries = 0
                while (pcm.hasRemaining()) {
                    val countBytes = encoder.queuePcm(pcm, clock.timestampUs(), 2000)
                    if (countBytes > 0) {
                        clock.advancePcmBytes(countBytes)
                        retries = 0
                    } else {
                        if (++retries > 1000) error("Codificador AAC não aceitou PCM")
                    }
                    drain()
                }
                framesWritten += count
            }
            encoder.signalEndOfStream(clock.timestampUs())
            repeat(100) { drain() }
            check(formatConfirmed) { "O codificador AAC não retornou formato" }
            check(packets > 0 && bytes > 0) { "Nenhum quadro AAC gerado" }
            return Result(packets, bytes)
        } finally {
            encoder.close()
        }
    }
}
