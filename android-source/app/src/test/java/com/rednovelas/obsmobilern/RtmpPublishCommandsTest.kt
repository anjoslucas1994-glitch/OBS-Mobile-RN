package com.rednovelas.obsmobilern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtmpPublishCommandsTest {
    @Test fun connectStartsWithAmfCommandName() {
        val payload = RtmpPublishCommands.connect("live", "rtmp://example.test/live")
        assertArrayEquals(Amf0Writer.string("connect"), payload.copyOfRange(0, 10))
        assertTrue(payload.size > 100)
    }

    @Test fun publishIncludesLiveMode() {
        val payload = RtmpPublishCommands.publish("test-stream")
        val live = Amf0Writer.string("live")
        assertArrayEquals(live, payload.copyOfRange(payload.size - live.size, payload.size))
    }

    @Test fun createStreamUsesAmfCommand() {
        val payload = RtmpPublishCommands.createStream()
        val prefix = Amf0Writer.string("createStream")
        assertArrayEquals(prefix, payload.copyOfRange(0, prefix.size))
    }

    @Test fun publishCommandIsEncodedOnRequestedStreamId() {
        val payload = RtmpPublishCommands.publish("test-stream")
        val chunk = RtmpChunkWriter.encode(
            chunkStreamId = 8,
            messageStreamId = 7,
            messageTypeId = 20,
            timestampMs = 0,
            payload = payload
        )
        // RTMP fmt=0 header: chunk stream, timestamp, size, type, streamId LE.
        assertTrue(chunk.size > 12)
        assertTrue((chunk[0].toInt() and 255) == 8)
        assertTrue((chunk[7].toInt() and 255) == 20)
        assertTrue((chunk[8].toInt() and 255) == 7)
        assertTrue((chunk[9].toInt() and 255) == 0)
        assertArrayEquals(Amf0Writer.string("publish"), chunk.copyOfRange(12, 22))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidConnectionUrl() {
        RtmpPublishCommands.connect("live", "https://example.test/live")
    }
}
