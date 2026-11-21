package com.rednovelas.obsmobilern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FlvTagWriterTest {
    @Test fun headerContainsAudioAndVideoFlags() {
        assertArrayEquals(
            byteArrayOf(0x46, 0x4c, 0x56, 1, 5, 0, 0, 0, 9, 0, 0, 0, 0),
            FlvTagWriter.header()
        )
    }

    @Test fun audioTagContainsTimestampPayloadAndPreviousTagSize() {
        val result = FlvTagWriter.tag(8, 0x01020304, byteArrayOf(0x11, 0x22))
        assertEquals(17, result.size)
        assertEquals(8, result[0].toInt())
        assertEquals(2, result[3].toInt())
        assertEquals(2, result[4].toInt())
        assertEquals(3, result[5].toInt())
        assertEquals(4, result[6].toInt())
        assertEquals(1, result[7].toInt())
        assertEquals(0x11, result[11].toInt())
        assertEquals(0x22, result[12].toInt())
        assertEquals(13, result[16].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidType() {
        FlvTagWriter.tag(18, 0, byteArrayOf())
    }
}
