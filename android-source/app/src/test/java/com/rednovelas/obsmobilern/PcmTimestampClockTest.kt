package com.rednovelas.obsmobilern

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmTimestampClockTest {
    @Test fun oneSecondOfStereoPcmIsOneSecondOfTimestamps() {
        val clock = PcmTimestampClock(48000, 2)
        assertEquals(0L, clock.timestampUs())
        clock.advancePcmBytes(48000 * 2 * 2)
        assertEquals(1_000_000L, clock.timestampUs())
    }

    @Test fun resettingChangesTheStartingTimestamp() {
        val clock = PcmTimestampClock(44100, 1)
        clock.advancePcmBytes(44100 * 2)
        clock.reset(5_000_000L)
        assertEquals(5_000_000L, clock.timestampUs())
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteStereoFrameIsRejected() {
        PcmTimestampClock(48000, 2).advancePcmBytes(3)
    }
}
