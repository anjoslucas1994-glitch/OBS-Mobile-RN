package com.rednovelas.obsmobilern

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testa a montagem e leitura de mensagens RTMP sem acessar a Web TV real.
 * Não substitui testes com um servidor RTMP compatível.
 */
class RtmpNegotiationIntegrationTest {
    private fun roundTrip(payload: ByteArray, streamId: Int = 0): RtmpSimpleChunkReader.Message {
        val bytes = RtmpChunkWriter.encode(
            chunkStreamId = 3,
            messageStreamId = streamId,
            messageTypeId = 20,
            timestampMs = 0,
            payload = payload,
            chunkSize = 128
        )
        return RtmpSimpleChunkReader.read(ByteArrayInputStream(bytes))
    }

    @Test fun connectThenCreateStreamNegotiation() {
        val state = RtmpNegotiation()
        val connect = roundTrip(
            Amf0Writer.command("_result", 1.0, Amf0Writer.nullValue())
        )
        assertEquals(20, connect.typeId)
        assertEquals(
            RtmpNegotiation.Stage.WAIT_STREAM,
            state.onCommand(RtmpCommandResponse.parse(connect.payload))
        )
        val created = roundTrip(
            Amf0Writer.command(
                "_result", 4.0, Amf0Writer.nullValue(), Amf0Writer.number(7.0)
            )
        )
        assertEquals(
            RtmpNegotiation.Stage.READY,
            state.onCommand(RtmpCommandResponse.parse(created.payload))
        )
        assertEquals(7, state.streamId)
    }

    @Test fun publishConfirmationRoundTrip() {
        val status = Amf0Writer.command(
            "onStatus", 0.0, Amf0Writer.nullValue(),
            Amf0Writer.objectValue(
                mapOf("code" to Amf0Writer.string("NetStream.Publish.Start"))
            )
        )
        val message = roundTrip(status, streamId = 7)
        assertEquals(7, message.messageStreamId)
        assertTrue(RtmpPublishStatus.isPublishStarted(message.payload))
    }
}
