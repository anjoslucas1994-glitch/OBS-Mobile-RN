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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidConnectionUrl() {
        RtmpPublishCommands.connect("live", "https://example.test/live")
    }
}
