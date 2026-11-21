package com.rednovelas.obsmobilern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Amf0WriterTest {
    @Test fun encodesShortString() {
        assertArrayEquals(byteArrayOf(2, 0, 2, 0x6f, 0x6b), Amf0Writer.string("ok"))
    }

    @Test fun encodesBooleanAndNull() {
        assertArrayEquals(byteArrayOf(1, 1), Amf0Writer.boolean(true))
        assertArrayEquals(byteArrayOf(1, 0), Amf0Writer.boolean(false))
        assertArrayEquals(byteArrayOf(5), Amf0Writer.nullValue())
    }

    @Test fun commandContainsNameAndTransactionNumber() {
        val result = Amf0Writer.command("publish", 1.0, Amf0Writer.nullValue())
        assertArrayEquals(Amf0Writer.string("publish"), result.copyOfRange(0, 10))
        assertEquals(0, result[10].toInt())
        assertEquals(5, result.last().toInt())
    }

    @Test fun objectHasTerminator() {
        val result = Amf0Writer.objectValue(mapOf("app" to Amf0Writer.string("live")))
        assertEquals(3, result.first().toInt())
        assertArrayEquals(byteArrayOf(0, 0, 9), result.takeLast(3).toByteArray())
    }
}
