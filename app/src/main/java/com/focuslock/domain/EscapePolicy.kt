package com.focuslock.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Cat's LIFE: exactly one emergency escape per local calendar day.
 *
 * The escape is charged to the calendar day on which it is consumed, which also defines the
 * behaviour for a session that crosses midnight: using the escape at 23:50 uses *that* day,
 * so a fresh escape is available at 00:00 even though the session is still running.
 */
object EscapePolicy {

    fun localEpochDay(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

    /**
     * A usage recorded today blocks the escape; a usage recorded in the future (clock moved
     * backwards) keeps it blocked, so changing the time cannot refresh the escape.
     */
    fun isAvailable(lastUsedEpochDay: Long?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (lastUsedEpochDay == null) return true
        return lastUsedEpochDay < localEpochDay(nowMillis, zone)
    }
}
