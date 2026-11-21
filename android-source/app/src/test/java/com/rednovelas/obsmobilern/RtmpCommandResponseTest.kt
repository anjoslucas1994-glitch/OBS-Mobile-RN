package com.rednovelas.obsmobilern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtmpCommandResponseTest {
    @Test fun readsCreateStreamResponse() {
        val payload = Amf0Writer.command(
            "_result", 4.0, Amf0Writer.nullValue(), Amf0Writer.number(1.0)
        )
        val response = RtmpCommandResponse.parse(payload)
        assertEquals("_result", response.command)
        assertEquals(4.0, response.transactionId, 0.0)
        assertEquals(1.0, response.streamId!!, 0.0)
    }

    @Test fun readsErrorWithoutStreamId() {
        val payload = Amf0Writer.command("_error", 1.0, Amf0Writer.nullValue())
        val response = RtmpCommandResponse.parse(payload)
        assertEquals("_error", response.command)
        assertNull(response.streamId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteResponse() {
        RtmpCommandResponse.parse(byteArrayOf(2, 0, 9, 65))
    }
}
