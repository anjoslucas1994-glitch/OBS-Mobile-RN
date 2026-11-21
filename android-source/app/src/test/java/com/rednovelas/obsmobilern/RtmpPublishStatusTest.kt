package com.rednovelas.obsmobilern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtmpPublishStatusTest {
    private fun status(code: String): ByteArray = Amf0Writer.command(
        "onStatus", 0.0, Amf0Writer.nullValue(),
        Amf0Writer.objectValue(
            linkedMapOf(
                "level" to Amf0Writer.string("status"),
                "code" to Amf0Writer.string(code),
                "description" to Amf0Writer.string("Test")
            )
        )
    )

    @Test fun recognizesPublishStart() {
        val payload = status("NetStream.Publish.Start")
        assertEquals("NetStream.Publish.Start", RtmpPublishStatus.code(payload))
        assertTrue(RtmpPublishStatus.isPublishStarted(payload))
    }

    @Test fun doesNotTreatPublishFailureAsSuccess() {
        val payload = status("NetStream.Publish.BadName")
        assertFalse(RtmpPublishStatus.isPublishStarted(payload))
    }

    @Test fun recognizesEcmaArrayPublishStart() {
        val objectBytes = Amf0Writer.objectValue(
            linkedMapOf("code" to Amf0Writer.string("NetStream.Publish.Start"))
        )
        val ecmaArray = byteArrayOf(8, 0, 0, 0, 1) + objectBytes.copyOfRange(1, objectBytes.size)
        val payload = Amf0Writer.command(
            "onStatus", 0.0, Amf0Writer.nullValue(), ecmaArray
        )
        assertTrue(RtmpPublishStatus.isPublishStarted(payload))
    }

    @Test fun rejectsMalformedStatus() {
        assertFalse(RtmpPublishStatus.isPublishStarted(byteArrayOf(1, 2, 3)))
    }
}
