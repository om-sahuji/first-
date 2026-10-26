package com.focuslock.data.model

import kotlinx.serialization.Serializable

/** Where an active session came from. */
@Serializable
enum class SessionSource { MANUAL, SCHEDULE }

/** A focus session that is currently running. Persisted so process death cannot reset it. */
@Serializable
data class ActiveSession(
    val startedAtMillis: Long,
    val endAtMillis: Long,
    /** Apps the user picked on top of the permanent Phone / Messages / Camera set. */
    val allowedPackages: List<String> = emptyList(),
    /** DNS allowlist applied to permitted browser apps while the session runs. */
    val allowedDomains: List<String> = emptyList(),
    val websiteFilterEnabled: Boolean = false,
    val scheduleId: String? = null,
    val source: SessionSource = SessionSource.MANUAL,
) {
    val durationMillis: Long get() = endAtMillis - startedAtMillis

    fun isActiveAt(nowMillis: Long): Boolean = nowMillis < endAtMillis

    fun remainingMillis(nowMillis: Long): Long = (endAtMillis - nowMillis).coerceAtLeast(0L)
}

/**
 * A repeating focus window.
 *
 * [days] uses ISO day numbers (Monday = 1 ... Sunday = 7) so it can be stored directly on
 * [java.time.DayOfWeek].
 */
@Serializable
data class Schedule(
    val id: String,
    val label: String = "",
    val days: Set<Int> = emptySet(),
    val startMinuteOfDay: Int = DEFAULT_START_MINUTE,
    val durationMinutes: Int = 25,
    val allowedPackages: List<String> = emptyList(),
    val allowedDomains: List<String> = emptyList(),
    val websiteFilterEnabled: Boolean = false,
    val enabled: Boolean = true,
    /** Local epoch day of the last automatic start, so a duplicated alarm cannot fire twice. */
    val lastStartedEpochDay: Long? = null,
) {
    companion object {
        const val DEFAULT_START_MINUTE = 9 * 60
    }
}

/** User configuration that is not tied to one session. */
@Serializable
data class Settings(
    val defaultDurationMinutes: Int = 25,
    val defaultAllowedPackages: List<String> = emptyList(),
    val defaultAllowedDomains: List<String> = emptyList(),
    val defaultWebsiteFilterEnabled: Boolean = false,
    /** Local epoch day on which Cat's LIFE was last consumed. */
    val escapeUsedOnEpochDay: Long? = null,
    val setupCompleted: Boolean = false,
    val strictModeEnabled: Boolean = false,
    val homeTakeoverAcknowledged: Boolean = false,
)

/** Single source of truth persisted as one atomic JSON document. */
@Serializable
data class AppSnapshot(
    val session: ActiveSession? = null,
    val schedules: List<Schedule> = emptyList(),
    val settings: Settings = Settings(),
)

/** Outcome of a request to start a session. */
sealed interface StartSessionResult {
    data class Started(val session: ActiveSession) : StartSessionResult
    data object AlreadyActive : StartSessionResult
    data class Invalid(val reason: String) : StartSessionResult
}

/** Outcome of consuming the emergency escape. */
sealed interface EscapeResult {
    data class Used(val session: ActiveSession) : EscapeResult
    data object NoActiveSession : EscapeResult
    data object AlreadyUsedToday : EscapeResult
}
