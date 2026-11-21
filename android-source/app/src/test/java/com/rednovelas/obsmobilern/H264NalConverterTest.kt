package com.rednovelas.obsmobilern

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class H264NalConverterTest {
    @Test fun convertsMixedStartCodesAndTwoNals() {
        val input = byteArrayOf(
            0, 0, 0, 1, 0x65, 0x11,
            0, 0, 1, 0x41, 0x22, 0x33
        )
        val result = H264NalConverter.annexBToAvcc(ByteBuffer.wrap(input))
        val bytes = ByteArray(result.remaining())
        result.get(bytes)
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 2, 0x65, 0x11, 0, 0, 0, 3, 0x41, 0x22, 0x33),
            bytes
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonAnnexB() {
        H264NalConverter.annexBToAvcc(ByteBuffer.wrap(byteArrayOf(0x65, 0x01)))
    }
}
