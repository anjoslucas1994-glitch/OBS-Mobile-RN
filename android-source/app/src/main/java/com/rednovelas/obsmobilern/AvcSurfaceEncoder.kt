package com.rednovelas.obsmobilern

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface

/**
 * Codificador experimental de vídeo H.264 com superfície de entrada.
 *
 * Não captura o ExoPlayer, não mistura áudio, não envia RTMP.
 * A superfície deve ser alimentada por um renderizador EGL em thread própria.
 * Quem chamar drain() deve consumir os buffers antes de liberá-los.
 */
internal class AvcSurfaceEncoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int = 2_500_000,
    private val fps: Int = 30
) : AutoCloseable {
    init {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
        require(bitrate in 100_000..20_000_000)
        require(fps in 1..60)
    }

    private var codec: MediaCodec? = null
    private var surface: Surface? = null

    val inputSurface: Surface
        get() = checkNotNull(surface) { "Codificador não iniciado" }

    fun start() {
        check(codec == null) { "Codificador já iniciado" }
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface()
            surface = input
            encoder.start()
            codec = encoder
        } catch (error: Exception) {
            surface?.release()
            surface = null
            encoder.release()
            throw error
        }
    }

    /**
     * Recebe bytes codificados. O callback deve copiar os bytes enquanto
     * o buffer está válido; eles são liberados ao retornar.
     * O formato pode mudar (INFO_OUTPUT_FORMAT_CHANGED) antes dos frames.
     */
    /**
     * Drena os quadros codificados. Retorna true quando MediaCodec sinaliza EOS,
     * inclusive quando o último buffer contém zero bytes.
     */
    fun drain(
        timeoutUs: Long = 10_000,
        onFormat: (MediaFormat) -> Unit,
        onSample: (java.nio.ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ): Boolean {
        val encoder = checkNotNull(codec) { "Codificador não iniciado" }
        val info = MediaCodec.BufferInfo()
        var reachedEnd = false
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
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        reachedEnd = true
                    }
                } finally {
                    encoder.releaseOutputBuffer(index, false)
                }
            }
            index = encoder.dequeueOutputBuffer(info, 0)
        }
        return reachedEnd
    }

    fun signalEndOfStream() {
        checkNotNull(codec).signalEndOfInputStream()
    }

    override fun close() {
        val encoder = codec
        codec = null
        surface?.release()
        surface = null
        if (encoder != null) {
            runCatching { encoder.stop() }
            encoder.release()
        }
    }
}
