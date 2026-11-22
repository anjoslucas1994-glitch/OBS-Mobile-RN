package com.rednovelas.obsmobilern

import android.graphics.Bitmap
import android.media.MediaCodec
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import android.os.Looper
import androidx.media3.ui.PlayerView

/**
 * Integra o caminho de VÍDEO HLS -> PixelCopy -> gráficos -> H.264 -> RTMP.
 * Somente usar com streamId confirmado por Publish.Start em servidor de testes.
 *
 * Captura PCM real do player via audioTap quando fornecido. Sincronização
 * A/V, tratamento de atrasos de rede e reprodução longa ainda experimentais.
 */
internal class HlsRtmpVideoSession(
    playerView: PlayerView,
    transport: RtmpTestTransport,
    publishedStreamId: Int,
    onError: (Throwable) -> Unit,
    bitrate: Int = 1_600_000,
    audioTap: HlsAudioPcmTap? = null
) : AutoCloseable {
    private val output = EncodedRtmpOutput(transport, publishedStreamId)
    private val videoPackets = AtomicLong(0L)
    val videoPacketCount: Long get() = videoPackets.get()
    private val audio = audioTap?.let { tap -> HlsAacRtmpBridge(tap, output, onError) }
    val audioPacketCount: Int get() = audio?.packetCount ?: 0
    fun setAudioMuted(muted: Boolean) { audio?.setMuted(muted) }
    fun isAudioMuted(): Boolean = audio?.isMuted() ?: false
    fun setAudioVolumePercent(percent: Int) { audio?.setVolumePercent(percent) }
    private val video = HlsVideoFramePipeline(
        playerView = playerView,
        bitrate = bitrate,
        onError = onError,
        onVideoFormat = output::onVideoFormat,
        onVideoSample = { sample: ByteBuffer, info: MediaCodec.BufferInfo ->
            output.onVideoSample(sample, info)
            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                videoPackets.incrementAndGet()
            }
        }
    )

    fun start(logo: Bitmap?, lowerThird: String, ticker: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Iniciar sessão de vídeo na thread principal"
        }
        video.updateGraphics(logo, lowerThird, ticker)
        video.start()
        try {
            audio?.start()
        } catch (error: Throwable) {
            video.close()
            throw error
        }
    }

    fun updateGraphics(logo: Bitmap?, lowerThird: String, ticker: String) {
        video.updateGraphics(logo, lowerThird, ticker)
    }

    fun stopWhenClosed(onClosed: () -> Unit) {
        audio?.close()
        video.stopWhenClosed(onClosed)
    }

    override fun close() {
        audio?.close()
        video.close()
        // O transporte é compartilhado com a futura saída AAC; seu dono
        // é responsável por encerrar o socket RTMP após finalizar a sessão.
    }
}
