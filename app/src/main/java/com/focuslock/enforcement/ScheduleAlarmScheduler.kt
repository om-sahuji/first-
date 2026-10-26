package com.focuslock.enforcement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focuslock.core.Clock
import com.focuslock.core.FocusLog
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.Schedule
import com.focuslock.domain.ScheduleMath
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Owns the two alarms Focus Lock needs:
 *
 *  - the next scheduled session start,
 *  - the end of the running session (so state is tidied if nobody looks at the UI).
 *
 * Exact alarms are used when the user granted "Alarms & reminders"; otherwise the alarm is
 * set with `setAndAllowWhileIdle`, which Android may delay in Doze. Blocking itself never
 * depends on this alarm: the accessibility service compares the persisted end time, so a
 * late alarm cannot extend a session.
 */
class ScheduleAlarmScheduler(
    private val context: Context,
    private val clock: Clock,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    fun scheduleNextStart(schedules: List<Schedule>) {
        val manager = alarmManager ?: return
        val pendingIntent = startPendingIntent()
        manager.cancel(pendingIntent)
        val next = ScheduleMath.nextStart(schedules, ZonedDateTime.now(clock.zone()))
        if (next == null) {
            FocusLog.d("No upcoming schedule to arm")
            return
        }
        setWakeAlarm(manager, next.toInstant().toEpochMilli(), pendingIntent)
        FocusLog.d("Next schedule alarm armed for $next")
    }

    fun scheduleSessionEnd(session: ActiveSession) {
        val manager = alarmManager ?: return
        val pendingIntent = endPendingIntent()
        manager.cancel(pendingIntent)
        setWakeAlarm(manager, session.endAtMillis, pendingIntent)
        FocusLog.d("Session end alarm armed for ${Instant.ofEpochMilli(session.endAtMillis)}")
    }

    fun cancelSessionEnd() {
        alarmManager?.cancel(endPendingIntent())
    }

    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun setWakeAlarm(manager: AlarmManager, atMillis: Long, pendingIntent: PendingIntent) {
        val triggerAt = maxOf(atMillis, clock.nowMillis() + 1_000L)
        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (error: SecurityException) {
            FocusLog.w("Exact alarm not permitted, falling back to inexact", error)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun startPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SCHEDULE_START,
        Intent(context, ScheduleReceiver::class.java).setAction(ScheduleReceiver.ACTION_SCHEDULE_START),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun endPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SESSION_END,
        Intent(context, SessionEndReceiver::class.java).setAction(SessionEndReceiver.ACTION_SESSION_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val REQUEST_SCHEDULE_START = 1001
        private const val REQUEST_SESSION_END = 1002
    }
}
