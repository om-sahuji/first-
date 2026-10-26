package com.focuslock.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `countdown rounds up so a running timer never shows zero early`() {
        assertEquals("00:00", TimeFormat.remaining(0))
        assertEquals("00:01", TimeFormat.remaining(1))
        assertEquals("00:01", TimeFormat.remaining(999))
        assertEquals("00:01", TimeFormat.remaining(1_000))
        assertEquals("01:00", TimeFormat.remaining(59_400))
        assertEquals("25:00", TimeFormat.remaining(25 * 60_000L))
    }

    @Test
    fun `countdown grows to hours above sixty minutes`() {
        assertEquals("59:59", TimeFormat.remaining(3_599_000))
        assertEquals("1:00:00", TimeFormat.remaining(3_600_000))
        assertEquals("3:00:00", TimeFormat.remaining(3 * 3_600_000L))
    }

    @Test
    fun `negative remaining clamps to zero`() {
        assertEquals("00:00", TimeFormat.remaining(-5_000))
    }

    @Test
    fun `duration labels stay compact`() {
        assertEquals("25m", TimeFormat.durationLabel(25))
        assertEquals("1h", TimeFormat.durationLabel(60))
        assertEquals("1h 30m", TimeFormat.durationLabel(90))
    }

    @Test
    fun `minute of day renders as a clock time`() {
        assertEquals("09:00", TimeFormat.minuteOfDayLabel(9 * 60))
        assertEquals("09:30", TimeFormat.minuteOfDayLabel(9 * 60 + 30))
        assertEquals("00:05", TimeFormat.minuteOfDayLabel(5))
    }
}
