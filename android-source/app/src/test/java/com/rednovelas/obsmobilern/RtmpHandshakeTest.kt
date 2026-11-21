package com.rednovelas.obsmobilern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class RtmpHandshakeTest {
    @Test fun sendsC0C1AndEchoesS1() {
        val s1 = ByteArray(1536) { (it % 251).toByte() }
        val s2 = ByteArray(1536)
        val input = ByteArrayInputStream(byteArrayOf(3) + s1 + s2)
        val output = ByteArrayOutputStream()
        RtmpHandshake.perform(input, output)
        val result = output.toByteArray()
        assertEquals(3073, result.size)
        assertEquals(3, result[0].toInt())
        assertArrayEquals(s1, result.copyOfRange(1537, 3073))
    }

    @Test(expected = EOFException::class)
    fun rejectsIncompleteServerHandshake() {
        RtmpHandshake.perform(ByteArrayInputStream(byteArrayOf(3)), ByteArrayOutputStream())
    }
}
