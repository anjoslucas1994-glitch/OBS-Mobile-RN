package com.rednovelas.obsmobilern

import android.graphics.Bitmap
import android.os.Looper
import androidx.media3.ui.PlayerView

/**
 * Integra o caminho de VÍDEO HLS -> PixelCopy -> gráficos -> H.264 -> RTMP.
 * Somente usar com streamId confirmado por Publish.Start em servidor de testes.
 *
 * Ainda NÃO captura/transmite o áudio HLS e não deve ser acionado pela UI
 * de transmissão ao vivo até o pipeline A/V completo ser validado.
 */
internal class HlsRtmpVideoSession(
    playerView: PlayerView,
    transport: RtmpTestTransport,
    publishedStreamId: Int,
    onError: (Throwable) -> Unit,
    bitrate: Int = 1_600_000
) : AutoCloseable {
    private val output = EncodedRtmpOutput(transport, publishedStreamId)
    private val video = HlsVideoFramePipeline(
        playerView = playerView,
        bitrate = bitrate,
        onError = onError,
        onVideoFormat = output::onVideoFormat,
        onVideoSample = output::onVideoSample
    )

    fun start(logo: Bitmap?, lowerThird: String, ticker: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Iniciar sessão de vídeo na thread principal"
        }
        video.updateGraphics(logo, lowerThird, ticker)
        video.start()
    }

    fun updateGraphics(logo: Bitmap?, lowerThird: String, ticker: String) {
        video.updateGraphics(logo, lowerThird, ticker)
    }

    fun stopWhenClosed(onClosed: () -> Unit) = video.stopWhenClosed(onClosed)

    override fun close() {
        video.close()
        // O transporte é compartilhado com a futura saída AAC; seu dono
        // é responsável por encerrar o socket RTMP após finalizar a sessão.
    }
}
