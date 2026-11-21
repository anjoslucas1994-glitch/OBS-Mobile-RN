package com.rednovelas.obsmobilern

/**
 * Liga os callbacks reais dos codificadores Android à saída RTMP.
 * O produtor precisa alimentar a Surface de vídeo e a entrada PCM.
 * Deve rodar em thread de trabalho; não captura HLS por conta própria.
 */
internal class EncoderDrainToRtmp(
    private val videoEncoder: AvcSurfaceEncoder,
    private val audioEncoder: AacPcmEncoder,
    private val output: EncodedRtmpOutput
) {
    fun drainAvailable() {
        videoEncoder.drain(
            timeoutUs = 0,
            onFormat = output::onVideoFormat,
            onSample = output::onVideoSample
        )
        audioEncoder.drain(
            timeoutUs = 0,
            onFormat = output::onAudioFormat,
            onSample = output::onAudioSample
        )
    }
}
