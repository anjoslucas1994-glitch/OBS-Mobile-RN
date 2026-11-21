package com.rednovelas.obsmobilern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RtmpSimpleChunkReaderTest {
    @Test fun decodesMessageFromWriter() {
        val payload = Amf0Writer.command("_result", 4.0, Amf0Writer.nullValue(), Amf0Writer.number(1.0))
        val encoded = RtmpChunkWriter.encode(3, 0, 20, 1234, payload, 8)
        val decoded = RtmpSimpleChunkReader.read(ByteArrayInputStream(encoded), 8)
        assertEquals(20, decoded.typeId)
        assertEquals(1234L, decoded.timestampMs)
        assertArrayEquals(payload, decoded.payload)
        assertEquals(1.0, RtmpCommandResponse.parse(decoded.payload).streamId!!, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMessageExceedingLimit() {
        val encoded = RtmpChunkWriter.encode(3, 0, 20, 0, ByteArray(20))
        RtmpSimpleChunkReader.read(ByteArrayInputStream(encoded), maxMessageSize = 10)
    }
}
