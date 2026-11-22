package com.rednovelas.obsmobilern

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Ponte experimental entre callbacks dos codificadores e pacotes RTMP.
 * Exige sessão de teste com Publish.Start confirmado. Não captura HLS.
 * Executar em thread de trabalho; não usar na UI.
 */
internal class EncodedRtmpOutput(
    private val transport: RtmpTestTransport,
    private val streamId: Int
) {
    private val packets = RtmpMediaPacketBuilder(streamId)

    init { require(streamId > 0) }

    @Synchronized fun onVideoFormat(format: MediaFormat) {
        val sps = format.getByteBuffer("csd-0") ?: error("SPS H.264 ausente")
        val pps = format.getByteBuffer("csd-1") ?: error("PPS H.264 ausente")
        transport.sendEncodedMediaPacketForTest(
            streamId, packets.videoConfiguration(stripStartCode(sps), stripStartCode(pps))
        )
    }

    @Synchronized fun onAudioFormat(format: MediaFormat) {
        val asc = format.getByteBuffer("csd-0") ?: error("Configuração AAC ausente")
        val bytes = ByteArray(asc.remaining())
        asc.slice().get(bytes)
        transport.sendEncodedMediaPacketForTest(streamId, packets.audioConfiguration(bytes))
    }

    @Synchronized fun onVideoSample(sample: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.size <= 0 || info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        val keyframe = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        val input = sample.slice()
        val annexB = input.remaining() >= 3 &&
            input.get(input.position()) == 0.toByte() &&
            input.get(input.position() + 1) == 0.toByte() &&
            (input.get(input.position() + 2) == 1.toByte() ||
                (input.remaining() >= 4 &&
                    input.get(input.position() + 2) == 0.toByte() &&
                    input.get(input.position() + 3) == 1.toByte()))
        val encoded = if (annexB) {
            packets.videoAnnexBSample(input, keyframe, info.presentationTimeUs)
        } else {
            packets.videoSample(input, keyframe, info.presentationTimeUs)
        }
        transport.sendEncodedMediaPacketForTest(streamId, encoded)
    }

    @Synchronized fun onAudioSample(sample: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.size <= 0 || info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        transport.sendEncodedMediaPacketForTest(
            streamId, packets.audioSample(sample, info.presentationTimeUs)
        )
    }

    private fun stripStartCode(buffer: ByteBuffer): ByteArray {
        val bytes = ByteArray(buffer.remaining())
        buffer.slice().get(bytes)
        val offset = when {
            bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
                bytes[2] == 0.toByte() && bytes[3] == 1.toByte() -> 4
            bytes.size >= 3 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
                bytes[2] == 1.toByte() -> 3
            else -> 0
        }
        require(bytes.size > offset) { "NAL H.264 vazia" }
        return bytes.copyOfRange(offset, bytes.size)
    }
}
