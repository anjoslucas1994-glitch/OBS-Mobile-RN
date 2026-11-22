package com.rednovelas.obsmobilern

import android.graphics.Bitmap

/**
 * Liga compositor de logo/textos -> OpenGL/EGL -> MediaCodec H.264.
 * O chamador fornece quadros decodificados; ainda não captura o ExoPlayer.
 * Todos os métodos devem ser usados na mesma thread de vídeo.
 */
internal class CompositedVideoEncoder(
    private val width: Int,
    private val height: Int,
    bitrate: Int = 2_500_000,
    fps: Int = 30
) : AutoCloseable {
    private val encoder = AvcSurfaceEncoder(width, height, bitrate, fps)
    private val compositor = VideoFrameCompositor()
    private var egl: EglEncoderSurface? = null
    private var renderer: BitmapEncoderFrameRenderer? = null
    private var lastTimestampNs = -1L

    fun start() {
        check(egl == null) { "Vídeo já iniciado" }
        encoder.start()
        try {
            val surface = EglEncoderSurface(encoder.inputSurface)
            egl = surface
            surface.makeCurrent()
            renderer = BitmapEncoderFrameRenderer()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    /**
     * Entrega um quadro com logo e textos para codificação.
     * timestampNs deve ser monotônico e relativo ao início da mídia.
     */
    fun submitFrame(
        frame: Bitmap,
        logo: Bitmap?,
        lowerThird: String,
        ticker: String,
        timestampNs: Long,
        logoLayout: LogoLayout = LogoLayout(),
        tickerSpeedPercent: Int = 100
    ) {
        check(timestampNs >= 0 && timestampNs > lastTimestampNs) {
            "Timestamp de vídeo precisa ser crescente"
        }
        val surface = checkNotNull(egl) { "Vídeo não iniciado" }
        val gl = checkNotNull(renderer) { "Renderizador não iniciado" }
        surface.makeCurrent()
        val composed = compositor.compose(frame, logo, lowerThird, ticker, logoLayout, timestampNs, tickerSpeedPercent)
        try {
            gl.draw(composed, width, height)
            surface.present(timestampNs)
            lastTimestampNs = timestampNs
        } finally {
            composed.recycle()
        }
    }

    fun drain(
        timeoutUs: Long = 0,
        onFormat: (android.media.MediaFormat) -> Unit,
        onSample: (java.nio.ByteBuffer, android.media.MediaCodec.BufferInfo) -> Unit
    ) = encoder.drain(timeoutUs, onFormat, onSample)

    fun signalEndOfStream() = encoder.signalEndOfStream()

    override fun close() {
        // OpenGL resources must be released on the same thread with EGL current.
        if (egl != null) runCatching { egl?.makeCurrent() }
        runCatching { renderer?.close() }
        renderer = null
        runCatching { egl?.close() }
        egl = null
        encoder.close()
        lastTimestampNs = -1L
    }
}
