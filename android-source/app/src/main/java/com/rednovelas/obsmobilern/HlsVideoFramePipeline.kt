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
    private var firstFrameTimeNs = -1L
    private var lastTimestampNs = -1L
    private var logo: Bitmap? = null
    private var lowerThird = ""
    private var ticker = ""

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
                main.post { onError(error); close() }
            }
        }
    }

    fun updateGraphics(newLogo: Bitmap?, newLowerThird: String, newTicker: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        // O bitmap do logotipo deve continuar válido até a parada do pipeline.
        logo = newLogo
        lowerThird = newLowerThird
        ticker = newTicker
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
                onError(result.exceptionOrNull() ?: IllegalStateException("Captura indisponível"))
                close()
                return@request
            }
            val text = lowerThird
            val scroll = ticker
            val currentLogo = logo
            // Timestamp baseado no relógio monotônico, não no número de capturas.
            // Assim atrasos do PixelCopy não aceleram artificialmente o vídeo.
            val nowNs = android.os.SystemClock.elapsedRealtimeNanos()
            if (firstFrameTimeNs < 0L) firstFrameTimeNs = nowNs
            val timestamp = (nowNs - firstFrameTimeNs).coerceAtLeast(lastTimestampNs + 1L)
            lastTimestampNs = timestamp
            worker.post {
                try {
                    encoder?.submitFrame(frame, currentLogo, text, scroll, timestamp)
                    // Copiar o buffer no callback: MediaCodec o libera ao retornar.
                    // Drenar evita travar o encoder após preencher seus buffers.
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

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Parar na thread principal" }
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        worker.post {
            try {
                encoder?.close()
                encoder = null
            } finally {
                workerThread.quitSafely()
            }
        }
    }
}
