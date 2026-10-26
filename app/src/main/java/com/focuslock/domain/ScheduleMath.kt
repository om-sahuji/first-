package com.focuslock.domain

import com.focuslock.data.model.Schedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Schedule arithmetic.
 *
 * All calculations happen in the device time zone so a 09:00 schedule stays 09:00 across DST
 * changes. Pure functions keep this testable without an emulator.
 */
object ScheduleMath {

    private const val MINUTES_PER_DAY = 24 * 60

    /** The next time [schedule] should start strictly after [from]. */
    fun nextStart(schedule: Schedule, from: ZonedDateTime): ZonedDateTime? {
        if (!schedule.enabled || schedule.days.isEmpty()) return null
        val zone = from.zone
        for (offset in 0..7) {
            val date: LocalDate = from.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in schedule.days) continue
            // Resolve the wall-clock time on that date instead of adding an offset to
            // midnight: during a DST change the night is 23 or 25 hours long, and adding
            // minutes to midnight would shift a 09:00 schedule to 10:00 (or 08:00).
            val time = LocalTime.ofSecondOfDay(schedule.startMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) * 60L)
            val candidate = date.atTime(time).atZone(zone)
            if (candidate.isAfter(from)) return candidate
        }
        return null
    }

    /** Earliest upcoming start across all enabled schedules. */
    fun nextStart(schedules: List<Schedule>, from: ZonedDateTime): ZonedDateTime? =
        schedules.mapNotNull { nextStart(it, from) }.minOrNull()

    /**
     * Whether the schedule's window contains [now] within a tolerance used when an alarm is
     * delivered late (Doze, reboot). A wider window is intentionally avoided: a session that
     * starts an hour late would be surprising.
     */
    fun isDueNow(schedule: Schedule, now: ZonedDateTime, toleranceMinutes: Long = 2): Boolean {
        if (!schedule.enabled || schedule.days.isEmpty()) return false
        if (now.dayOfWeek.value !in schedule.days) return false
        val minutes = (now.hour * 60 + now.minute).toLong()
        val delta = minutes - schedule.startMinuteOfDay
        return delta in 0..toleranceMinutes
    }

    /** True when this exact local day already started the schedule once. */
    fun alreadyStartedToday(schedule: Schedule, now: ZonedDateTime): Boolean =
        schedule.lastStartedEpochDay == now.toLocalDate().toEpochDay()
}
