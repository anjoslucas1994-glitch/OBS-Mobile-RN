package com.rednovelas.obsmobilern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RtmpChunkWriterTest {
    @Test fun singleVideoChunkHasCorrectHeader() {
        val result = RtmpChunkWriter.encode(6, 1, 9, 100, byteArrayOf(0x17, 0x01))
        assertArrayEquals(
            byteArrayOf(6, 0, 0, 100, 0, 0, 2, 9, 1, 0, 0, 0, 0x17, 0x01),
            result
        )
    }

    @Test fun continuationUsesFmt3() {
        val result = RtmpChunkWriter.encode(4, 1, 8, 0, byteArrayOf(1, 2, 3, 4, 5), 2)
        assertEquals(20, result.size)
        assertEquals(0xc4, result[14].toInt() and 255)
        assertEquals(0xc4, result[17].toInt() and 255)
    }

    @Test fun extendedTimestampIsWrittenOnEveryChunk() {
        val result = RtmpChunkWriter.encode(6, 1, 9, 0x01020304, byteArrayOf(1, 2), 1)
        assertEquals(26, result.size)
        assertEquals(0xff, result[1].toInt() and 255)
        assertEquals(0x01, result[12].toInt() and 255)
        assertEquals(0xc6, result[17].toInt() and 255)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidChunkStreamId() {
        RtmpChunkWriter.encode(1, 1, 9, 0, byteArrayOf(1))
    }
}
