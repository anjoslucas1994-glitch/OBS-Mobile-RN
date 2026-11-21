package com.rednovelas.obsmobilern

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RtmpChunkStreamReaderTest {
    @Test fun readsChunkedMessageFromWriter() {
        val payload = ByteArray(270) { it.toByte() }
        val encoded = RtmpChunkWriter.encode(3, 1, 20, 125, payload, 128)
        val result = RtmpChunkStreamReader(ByteArrayInputStream(encoded)).read()
        assertEquals(3, result.chunkStreamId)
        assertEquals(20, result.typeId)
        assertEquals(125L, result.timestampMs)
        assertArrayEquals(payload, result.payload)
    }

    @Test fun readsFmtOneAndTwoWithDeltaTimestamps() {
        val first = RtmpChunkWriter.encode(3, 1, 20, 100, byteArrayOf(1))
        // fmt=1: delta 20, length 1, type 20, payload 2
        val second = byteArrayOf(0x43, 0, 0, 20, 0, 0, 1, 20, 2)
        // fmt=2: delta 30, payload 3 (length/type inherited)
        val third = byteArrayOf(0x83.toByte(), 0, 0, 30, 3)
        val reader = RtmpChunkStreamReader(ByteArrayInputStream(first + second + third))
        assertEquals(100L, reader.read().timestampMs)
        val b = reader.read()
        assertEquals(120L, b.timestampMs)
        assertArrayEquals(byteArrayOf(2), b.payload)
        val c = reader.read()
        assertEquals(150L, c.timestampMs)
        assertArrayEquals(byteArrayOf(3), c.payload)
    }

    @Test fun readsInterleavedChunksFromTwoStreams() {
        val aPayload = ByteArray(5) { 0x11 }
        val a = RtmpChunkWriter.encode(3, 1, 20, 0, aPayload, 3)
        val b = RtmpChunkWriter.encode(4, 0, 4, 0, byteArrayOf(7), 3)
        // first A chunk: 12-byte header + 3 bytes; then complete B; then A remainder.
        val interleaved = a.copyOfRange(0, 15) + b + a.copyOfRange(15, a.size)
        val reader = RtmpChunkStreamReader(ByteArrayInputStream(interleaved), chunkSize = 3)
        val first = reader.read()
        assertEquals(4, first.chunkStreamId)
        val second = reader.read()
        assertEquals(3, second.chunkStreamId)
        assertArrayEquals(aPayload, second.payload)
    }
}
