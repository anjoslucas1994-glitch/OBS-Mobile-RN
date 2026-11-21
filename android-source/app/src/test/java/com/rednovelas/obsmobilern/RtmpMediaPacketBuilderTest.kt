package com.rednovelas.obsmobilern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class RtmpMediaPacketBuilderTest {
    @Test fun packagesVideoAndAudioIntoRtmpMessages() {
        val builder = RtmpMediaPacketBuilder(1)
        val video = builder.videoSample(
            ByteBuffer.wrap(byteArrayOf(0, 0, 0, 2, 0x65, 0x01)),
            true, 1_000_000
        )
        val audio = builder.audioSample(ByteBuffer.wrap(byteArrayOf(0x11, 0x22)), 1_020_000)
        val decodedVideo = RtmpSimpleChunkReader.read(ByteArrayInputStream(video))
        val decodedAudio = RtmpSimpleChunkReader.read(ByteArrayInputStream(audio))
        assertEquals(9, decodedVideo.typeId)
        assertEquals(8, decodedAudio.typeId)
        assertEquals(0L, decodedVideo.timestampMs)
        assertEquals(20L, decodedAudio.timestampMs)
        assertArrayEquals(
            byteArrayOf(0x17, 0x01, 0, 0, 0, 0, 0, 0, 0, 2, 0x65, 0x01),
            decodedVideo.payload
        )
        assertArrayEquals(
            byteArrayOf(0xaf.toByte(), 0x01, 0x11, 0x22),
            decodedAudio.payload
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBackwardsTimestamp() {
        val builder = RtmpMediaPacketBuilder(1)
        builder.audioSample(ByteBuffer.wrap(byteArrayOf(1)), 2000)
        builder.audioSample(ByteBuffer.wrap(byteArrayOf(1)), 1000)
    }
}
