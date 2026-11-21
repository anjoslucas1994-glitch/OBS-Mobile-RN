package com.rednovelas.obsmobilern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtmpNegotiationTest {
    @Test fun connectThenCreateStream() {
        val negotiation = RtmpNegotiation()
        assertEquals(RtmpNegotiation.Stage.WAIT_CONNECT, negotiation.stage)
        negotiation.onCommand(RtmpCommandResponse.Response("_result", 1.0, null))
        assertEquals(RtmpNegotiation.Stage.WAIT_STREAM, negotiation.stage)
        negotiation.onCommand(RtmpCommandResponse.Response("_result", 4.0, 1.0))
        assertEquals(RtmpNegotiation.Stage.READY, negotiation.stage)
        assertEquals(1, negotiation.streamId)
    }

    @Test fun ignoresOutOfOrderResponses() {
        val negotiation = RtmpNegotiation()
        negotiation.onCommand(RtmpCommandResponse.Response("_result", 4.0, 2.0))
        assertEquals(RtmpNegotiation.Stage.WAIT_CONNECT, negotiation.stage)
        assertNull(negotiation.streamId)
    }

    @Test fun rejectsInvalidStreamId() {
        val negotiation = RtmpNegotiation()
        negotiation.onCommand(RtmpCommandResponse.Response("_result", 1.0, null))
        negotiation.onCommand(RtmpCommandResponse.Response("_result", 4.0, -1.0))
        assertEquals(RtmpNegotiation.Stage.FAILED, negotiation.stage)
    }

    @Test fun serverErrorStopsNegotiation() {
        val negotiation = RtmpNegotiation()
        negotiation.onCommand(RtmpCommandResponse.Response("_error", 1.0, null))
        assertEquals(RtmpNegotiation.Stage.FAILED, negotiation.stage)
    }
}
