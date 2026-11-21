package com.rednovelas.obsmobilern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class FlvPacketizerTest {
    @Test fun aacPacketsHaveFlvHeaders() {
        assertArrayEquals(
            byteArrayOf(0xaf.toByte(), 0x00, 0x12, 0x10),
            FlvPacketizer.aacConfiguration(byteArrayOf(0x12, 0x10))
        )
        assertArrayEquals(
            byteArrayOf(0xaf.toByte(), 0x01, 0x11, 0x22),
            FlvPacketizer.aacFrame(ByteBuffer.wrap(byteArrayOf(0x11, 0x22)))
        )
    }

    @Test fun avcKeyframePreservesLengthPrefixedNal() {
        val input = byteArrayOf(0, 0, 0, 2, 0x65, 0x01)
        val output = FlvPacketizer.avcFrame(ByteBuffer.wrap(input), true)
        assertEquals(0x17, output[0].toInt() and 255)
        assertEquals(0x01, output[1].toInt() and 255)
        assertArrayEquals(input, output.copyOfRange(5, output.size))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteNal() {
        FlvPacketizer.avcFrame(ByteBuffer.wrap(byteArrayOf(0, 0, 0, 9, 0x65)), false)
    }
}
