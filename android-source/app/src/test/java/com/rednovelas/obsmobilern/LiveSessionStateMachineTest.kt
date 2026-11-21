package com.rednovelas.obsmobilern

import org.junit.Assert.*
import org.junit.Test

class LiveSessionStateMachineTest {
    @Test fun successfulLifecycle() {
        val session = LiveSessionStateMachine()
        assertEquals(LiveState.STOPPED, session.state)
        assertTrue(session.requestStart())
        assertEquals(LiveState.CONNECTING, session.state)
        assertTrue(session.connected())
        assertEquals(LiveState.LIVE, session.state)
        assertTrue(session.requestStop())
        assertEquals(LiveState.STOPPING, session.state)
        assertTrue(session.stopped())
        assertEquals(LiveState.STOPPED, session.state)
    }

    @Test fun rejectsInvalidTransitions() {
        val session = LiveSessionStateMachine()
        assertFalse(session.connected())
        assertFalse(session.requestStop())
        assertFalse(session.stopped())
        assertTrue(session.requestStart())
        assertFalse(session.requestStart())
        assertTrue(session.requestStop())
        assertFalse(session.connected())
    }

    @Test fun errorCanBeReset() {
        val session = LiveSessionStateMachine()
        session.requestStart()
        session.failed()
        assertEquals(LiveState.ERROR, session.state)
        session.reset()
        assertEquals(LiveState.STOPPED, session.state)
    }
}
