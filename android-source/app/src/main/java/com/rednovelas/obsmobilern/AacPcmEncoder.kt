package com.rednovelas.obsmobilern

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Codificador AAC-LC experimental para PCM 16-bit intercalado.
 * Não extrai áudio do ExoPlayer e não publica RTMP.
 *
 * O produtor deve enviar PCM com timestamps monotônicos em microssegundos.
 */
internal class AacPcmEncoder(
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 2,
    private val bitrate: Int = 128_000
) : AutoCloseable {
    init {
        require(sampleRate in listOf(32000, 44100, 48000))
        require(channelCount in 1..2)
        require(bitrate in 32000..320000)
    }

    private var codec: MediaCodec? = null

    fun start() {
        check(codec == null) { "Codificador AAC já iniciado" }
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            codec = encoder
        } catch (error: Exception) {
            encoder.release()
            throw error
        }
    }

    /**
     * Enfileira no máximo um buffer de entrada.
     * Retorna a quantidade de bytes aceitos, ou zero quando não há buffer.
     * O chamador deve reenviar o restante preservando timestamps corretos.
     */
    fun queuePcm(pcm: ByteBuffer, presentationTimeUs: Long, timeoutUs: Long = 10_000): Int {
        require(presentationTimeUs >= 0)
        val encoder = checkNotNull(codec) { "Codificador AAC não iniciado" }
        val index = encoder.dequeueInputBuffer(timeoutUs)
        if (index < 0) return 0
        val input = checkNotNull(encoder.getInputBuffer(index))
        input.clear()
        val bytes = minOf(input.remaining(), pcm.remaining())
        require(bytes % (channelCount * 2) == 0) { "PCM deve conter amostras completas" }
        val limit = pcm.limit()
        pcm.limit(pcm.position() + bytes)
        input.put(pcm)
        pcm.limit(limit)
        encoder.queueInputBuffer(index, 0, bytes, presentationTimeUs, 0)
        return bytes
    }

    fun signalEndOfStream(presentationTimeUs: Long) {
        val encoder = checkNotNull(codec)
        val index = encoder.dequeueInputBuffer(10_000)
        check(index >= 0) { "Sem buffer para sinalizar fim de áudio" }
        encoder.queueInputBuffer(index, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }

    fun drain(
        timeoutUs: Long = 10_000,
        onFormat: (MediaFormat) -> Unit,
        onSample: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val encoder = checkNotNull(codec) { "Codificador AAC não iniciado" }
        val info = MediaCodec.BufferInfo()
        var index = encoder.dequeueOutputBuffer(info, timeoutUs)
        while (index >= 0 || index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                onFormat(encoder.outputFormat)
            } else {
                try {
                    val output = encoder.getOutputBuffer(index)
                    if (output != null && info.size > 0) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        onSample(output.slice(), info)
                    }
                } finally {
                    encoder.releaseOutputBuffer(index, false)
                }
            }
            index = encoder.dequeueOutputBuffer(info, 0)
        }
    }

    override fun close() {
        val encoder = codec
        codec = null
        if (encoder != null) {
            runCatching { encoder.stop() }
            encoder.release()
        }
    }
}
