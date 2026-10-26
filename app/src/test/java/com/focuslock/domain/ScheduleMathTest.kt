package com.focuslock.domain

import com.focuslock.data.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleMathTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
        zone: ZoneId = kolkata,
    ): ZonedDateTime = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    private fun weekdaysAtNine() = Schedule(
        id = "weekday",
        days = setOf(1, 2, 3, 4, 5),
        startMinuteOfDay = 9 * 60,
        durationMinutes = 25,
    )

    @Test
    fun `starts later the same day when the time has not passed`() {
        // 2026-10-26 is a Monday.
        val next = ScheduleMath.nextStart(weekdaysAtNine(), at(2026, 10, 26, 8))
        assertEquals(at(2026, 10, 26, 9), next)
    }

    @Test
    fun `rolls to the next enabled day when the time has passed`() {
        val next = ScheduleMath.nextStart(weekdaysAtNine(), at(2026, 10, 26, 9, 30))
        assertEquals(at(2026, 10, 27, 9), next)
    }

    @Test
    fun `skips the weekend`() {
        // Friday 2026-10-30 after the start time -> Monday 2026-11-02.
        val next = ScheduleMath.nextStart(weekdaysAtNine(), at(2026, 10, 30, 10))
        assertEquals(at(2026, 11, 2, 9), next)
    }

    @Test
    fun `disabled schedule never starts`() {
        val next = ScheduleMath.nextStart(weekdaysAtNine().copy(enabled = false), at(2026, 10, 26, 8))
        assertNull(next)
    }

    @Test
    fun `schedule without days never starts`() {
        val next = ScheduleMath.nextStart(weekdaysAtNine().copy(days = emptySet()), at(2026, 10, 26, 8))
        assertNull(next)
    }

    @Test
    fun `keeps the local wall clock time across a daylight saving change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val schedule = weekdaysAtNine().copy(days = setOf(7))
        // 2026-03-29 is the spring-forward date in Europe/Berlin.
        val next = ScheduleMath.nextStart(schedule, at(2026, 3, 28, 12, zone = berlin))
        assertEquals(9, next?.hour)
        assertEquals(29, next?.dayOfMonth)
        assertEquals(java.time.ZoneOffset.ofHours(2), next?.offset)
    }

    @Test
    fun `earliest upcoming start wins across schedules`() {
        val tomorrow = weekdaysAtNine().copy(id = "b", days = setOf(2), startMinuteOfDay = 8 * 60)
        val today = weekdaysAtNine().copy(id = "a", days = setOf(1), startMinuteOfDay = 10 * 60)
        val next = ScheduleMath.nextStart(listOf(tomorrow, today), at(2026, 10, 26, 9))
        assertEquals(at(2026, 10, 26, 10), next)
    }

    @Test
    fun `due window tolerates a late alarm but not a stale one`() {
        val schedule = weekdaysAtNine()
        assertTrue(ScheduleMath.isDueNow(schedule, at(2026, 10, 26, 9, 1)))
        assertTrue(!ScheduleMath.isDueNow(schedule, at(2026, 10, 26, 9, 5)))
        // Sunday is not part of the weekday schedule.
        assertTrue(!ScheduleMath.isDueNow(schedule, at(2026, 11, 1, 9, 1)))
        assertTrue(!ScheduleMath.isDueNow(schedule, at(2026, 10, 26, 8, 58)))
    }

    @Test
    fun `a schedule already fired today is not fired twice`() {
        val schedule = weekdaysAtNine().copy(lastStartedEpochDay = at(2026, 10, 26, 9).toLocalDate().toEpochDay())
        assertTrue(ScheduleMath.alreadyStartedToday(schedule, at(2026, 10, 26, 9, 1)))
        assertTrue(!ScheduleMath.alreadyStartedToday(schedule, at(2026, 10, 27, 9, 1)))
    }
}
