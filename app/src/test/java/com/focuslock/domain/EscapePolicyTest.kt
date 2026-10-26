package com.focuslock.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class EscapePolicyTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `escape is available when never used`() {
        assertTrue(EscapePolicy.isAvailable(null, millis("2026-10-26T06:00:00Z"), kolkata))
    }

    @Test
    fun `escape is blocked for the rest of the local day`() {
        val usedAt = millis("2026-10-26T04:00:00Z") // 09:30 in Kolkata
        val day = EscapePolicy.localEpochDay(usedAt, kolkata)
        assertFalse(EscapePolicy.isAvailable(day, millis("2026-10-26T18:29:00Z"), kolkata)) // 23:59 local
        assertTrue(EscapePolicy.isAvailable(day, millis("2026-10-26T18:31:00Z"), kolkata)) // 00:01 local
    }

    @Test
    fun `yesterday's usage does not block today`() {
        val used = EscapePolicy.localEpochDay(millis("2026-10-24T10:00:00Z"), kolkata)
        assertTrue(EscapePolicy.isAvailable(used, millis("2026-10-25T10:00:00Z"), kolkata))
    }

    @Test
    fun `moving the clock backwards does not refresh the escape`() {
        val used = EscapePolicy.localEpochDay(millis("2026-10-26T10:00:00Z"), kolkata)
        // Same local day after a clock rollback.
        assertFalse(EscapePolicy.isAvailable(used, millis("2026-10-26T09:00:00Z"), kolkata))
    }

    @Test
    fun `session crossing midnight grants a new escape after midnight`() {
        val beforeMidnight = millis("2026-10-26T18:20:00Z") // 23:50 local
        val usedDay = EscapePolicy.localEpochDay(beforeMidnight, kolkata)
        val afterMidnight = millis("2026-10-26T18:40:00Z") // 00:10 local, next calendar day
        assertTrue(EscapePolicy.isAvailable(usedDay, afterMidnight, kolkata))
    }
}
