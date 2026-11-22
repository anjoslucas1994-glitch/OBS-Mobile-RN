package com.rednovelas.obsmobilern

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.ui.PlayerView

/**
 * Pipeline experimental: prévia HLS -> PixelCopy -> composição -> H264.
 * Não transmite RTMP nem captura áudio. Requer Android 8+ e prévia SurfaceView.
 * Mantém no máximo uma captura em andamento para evitar fila de Bitmaps.
 */
internal class HlsVideoFramePipeline(
    private val playerView: PlayerView,
    private val width: Int = 640,
    private val height: Int = 360,
    private val bitrate: Int = 2_500_000,
    private val fps: Int = 15,
    private val onError: (Throwable) -> Unit,
    private val onVideoFormat: (android.media.MediaFormat) -> Unit,
    private val onVideoSample: (java.nio.ByteBuffer, android.media.MediaCodec.BufferInfo) -> Unit
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val workerThread = HandlerThread("RN-HLS-Encoder")
    private lateinit var worker: Handler
    private var encoder: CompositedVideoEncoder? = null
    private var running = false
    private var busy = false
    private var previewFailures = 0
    private var firstFrameTimeNs = -1L
    private var lastTimestampNs = -1L
    private var logo: Bitmap? = null
    private var lowerThird = ""
    private var ticker = ""
    private var logoLayout = LogoLayout()

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Iniciar na thread principal" }
        check(!running) { "Captura já iniciada" }
        require(android.os.Build.VERSION.SDK_INT >= 26) { "Requer Android 8+" }
        running = true
        workerThread.start()
        worker = Handler(workerThread.looper)
        worker.post {
            try {
                val startedEncoder = CompositedVideoEncoder(width, height, bitrate, fps)
                startedEncoder.start()
                if (running) {
                    encoder = startedEncoder
                    main.post { if (running) scheduleNext() }
                } else {
                    startedEncoder.close()
                }
            } catch (error: Throwable) {
                main.post {
                    if (running) {
                        onError(error)
                        close()
                    } else {
                        workerThread.quitSafely()
                    }
                }
            }
        }
    }

    fun updateGraphics(newLogo: Bitmap?, newLowerThird: String, newTicker: String,
                       newLogoLayout: LogoLayout = LogoLayout()) {
        check(Looper.myLooper() == Looper.getMainLooper())
        // O bitmap do logotipo deve continuar válido até a parada do pipeline.
        logo = newLogo
        lowerThird = newLowerThird
        ticker = newTicker
        logoLayout = newLogoLayout
    }

    private fun scheduleNext() {
        if (!running || busy) return
        busy = true
        PlayerFrameCapture.request(playerView, width, height, main) { result ->
            if (!running) {
                result.getOrNull()?.recycle()
                busy = false
                return@request
            }
            val frame = result.getOrNull()
            if (frame == null) {
                busy = false
                // A superfície do player pode não estar pronta nos primeiros
                // segundos de buffering. Dar tempo ao HLS para iniciar.
                if (++previewFailures <= 20) {
                    main.postDelayed({ if (running) scheduleNext() }, 250L)
                } else {
                    onError(result.exceptionOrNull() ?: IllegalStateException("Captura indisponível"))
                    close()
                }
                return@request
            }
            previewFailures = 0
            val text = lowerThird
            val scroll = ticker
            val currentLogo = logo
            val currentLogoLayout = logoLayout
            // Timestamp baseado no relógio monotônico, não no número de capturas.
            // Assim atrasos do PixelCopy não aceleram artificialmente o vídeo.
            val nowNs = android.os.SystemClock.elapsedRealtimeNanos()
            if (firstFrameTimeNs < 0L) firstFrameTimeNs = nowNs
            val timestamp = (nowNs - firstFrameTimeNs).coerceAtLeast(lastTimestampNs + 1L)
            lastTimestampNs = timestamp
            worker.post {
                try {
                    // Liberar buffers antes de enviar outro quadro ao EGL:
                    // alguns codecs bloqueiam eglSwapBuffers quando a saída enche.
                    encoder?.drain(
                        timeoutUs = 0,
                        onFormat = onVideoFormat,
                        onSample = onVideoSample
                    )
                    encoder?.submitFrame(frame, currentLogo, text, scroll, timestamp, currentLogoLayout)
                    // Copiar o buffer no callback: MediaCodec o libera ao retornar.
                    encoder?.drain(
                        timeoutUs = 0,
                        onFormat = onVideoFormat,
                        onSample = onVideoSample
                    )
                } catch (error: Throwable) {
                    main.post { if (running) { onError(error); close() } }
                } finally {
                    frame.recycle()
                    main.post {
                        busy = false
                        if (running) main.postDelayed({ scheduleNext() }, (1000L / fps).coerceAtLeast(1L))
                    }
                }
            }
        }
    }

    /** Aguarda o fim dos buffers H.264 antes de fechar o arquivo MP4. */
    fun stopWhenClosed(onClosed: () -> Unit) {
        stopInternal(onClosed)
    }

    override fun close() {
        stopInternal(null)
    }

    private fun stopInternal(onClosed: (() -> Unit)?) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Parar na thread principal" }
        if (!running) {
            onClosed?.invoke()
            return
        }
        running = false
        main.removeCallbacksAndMessages(null)
        worker.post {
            try {
                val active = encoder
                if (onClosed != null && active != null) {
                    active.signalEndOfStream()
                    var gotEnd = false
                    repeat(30) {
                        if (!gotEnd) {
                            gotEnd = active.drain(
                                timeoutUs = 50_000,
                                onFormat = onVideoFormat,
                                onSample = onVideoSample
                            )
                        }
                    }
                    check(gotEnd) { "Codificador não finalizou o vídeo" }
                }
            } catch (error: Throwable) {
                if (onClosed != null) {
                    main.post { onError(error) }
                }
            } finally {
                try {
                    encoder?.close()
                    encoder = null
                } finally {
                    workerThread.quitSafely()
                    if (onClosed != null) main.post(onClosed)
                }
            }
        }
    }
}
