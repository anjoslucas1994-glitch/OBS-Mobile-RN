package com.rednovelas.obsmobilern

import java.nio.ByteBuffer

/**
 * Liga os codificadores H.264/AAC ao serializador de chunks RTMP.
 * Só recebe amostras já codificadas; não captura HLS, não abre socket e não publica.
 * Timestamp em microssegundos é convertido para milissegundos relativos à sessão.
 */
internal class RtmpMediaPacketBuilder(
    private val messageStreamId: Int,
    private val chunkSize: Int = 128
) {
    init {
        require(messageStreamId > 0)
        require(chunkSize > 0)
    }

    private var startTimeUs: Long? = null

    fun reset() {
        startTimeUs = null
    }

    private fun timestampMs(presentationTimeUs: Long): Long {
        require(presentationTimeUs >= 0)
        val start = startTimeUs ?: presentationTimeUs.also { startTimeUs = it }
        // Amostras de áudio e vídeo podem chegar fora de ordem no início.
        // Evita timestamps negativos até o pipeline definir um relógio comum.
        return (presentationTimeUs - start).coerceAtLeast(0L) / 1000
    }

    fun videoConfiguration(sps: ByteArray, pps: ByteArray): ByteArray =
        RtmpChunkWriter.encode(6, messageStreamId, 9, 0,
            FlvPacketizer.avcConfiguration(sps, pps), chunkSize)

    fun audioConfiguration(audioSpecificConfig: ByteArray): ByteArray =
        RtmpChunkWriter.encode(4, messageStreamId, 8, 0,
            FlvPacketizer.aacConfiguration(audioSpecificConfig), chunkSize)

    fun videoSample(avccSample: ByteBuffer, keyframe: Boolean, presentationTimeUs: Long): ByteArray =
        RtmpChunkWriter.encode(6, messageStreamId, 9, timestampMs(presentationTimeUs),
            FlvPacketizer.avcFrame(avccSample, keyframe), chunkSize)

    fun audioSample(rawAac: ByteBuffer, presentationTimeUs: Long): ByteArray =
        RtmpChunkWriter.encode(4, messageStreamId, 8, timestampMs(presentationTimeUs),
            FlvPacketizer.aacFrame(rawAac), chunkSize)
}
