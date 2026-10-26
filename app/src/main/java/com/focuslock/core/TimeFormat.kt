package com.focuslock.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Countdown formatting shared by the focus screen and diagnostics. */
object TimeFormat {

    /** `MM:SS` under an hour, `H:MM:SS` above it. Rounds up so 1 ms never renders as 00:00. */
    fun remaining(millis: Long): String {
        val totalSeconds = ((millis.coerceAtLeast(0L) + 999L) / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** "9:30 AM" style local time for a duration label. */
    fun clockTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withZone(zone)
            .format(Instant.ofEpochMilli(epochMillis))

    fun durationLabel(minutes: Int): String =
        if (minutes >= 60) {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
        } else {
            "${minutes}m"
        }

    fun minuteOfDayLabel(minuteOfDay: Int): String {
        val hours = (minuteOfDay / 60).coerceIn(0, 23)
        val minutes = (minuteOfDay % 60).coerceIn(0, 59)
        return String.format(Locale.US, "%02d:%02d", hours, minutes)
    }
}
