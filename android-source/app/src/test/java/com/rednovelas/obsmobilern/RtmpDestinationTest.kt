package com.rednovelas.obsmobilern

import org.junit.Assert.assertEquals
import org.junit.Test

class RtmpDestinationTest {
    @Test fun separatesAppAndSecretKey() {
        val destination = RtmpDestination.parse("rtmp://example.test:1935/live", "private-key")
        assertEquals("live", destination.app)
        assertEquals("private-key", destination.streamName)
        assertEquals("rtmp://example.test:1935/live", destination.tcUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingApp() {
        RtmpDestination.parse("rtmp://example.test", "key")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsKeyEmbeddedInServerPath() {
        RtmpDestination.parse("rtmp://example.test/live/secret", "key")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankKey() {
        RtmpDestination.parse("rtmp://example.test/live", "")
    }
}
