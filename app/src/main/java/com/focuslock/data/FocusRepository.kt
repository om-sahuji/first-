package com.focuslock.data

import com.focuslock.core.Clock
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.AppSnapshot
import com.focuslock.data.model.EscapeResult
import com.focuslock.data.model.Schedule
import com.focuslock.data.model.SessionRules
import com.focuslock.data.model.SessionSource
import com.focuslock.data.model.Settings
import com.focuslock.data.model.StartSessionResult
import com.focuslock.data.store.SnapshotStore
import com.focuslock.domain.EscapePolicy
import com.focuslock.domain.DomainRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns session state and configuration.
 *
 * [state] is kept hot in memory, so the enforcement path reads the current session with a
 * field access instead of touching disk. All writes go through [FocusStore] and are reflected
 * back through the flow.
 */
class FocusRepository(
    private val store: SnapshotStore,
    private val clock: Clock,
    scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(store.readBlocking())
    val state: StateFlow<AppSnapshot> = _state.asStateFlow()

    init {
        scope.launch {
            store.data.collect { snapshot -> _state.value = snapshot }
        }
    }

    val currentSession: ActiveSession? get() = _state.value.session

    fun activeSession(nowMillis: Long = clock.nowMillis()): ActiveSession? =
        _state.value.session?.takeIf { it.isActiveAt(nowMillis) }

    /** Ends a session whose end time has passed. Cheap no-op when nothing changed. */
    suspend fun reconcile(nowMillis: Long = clock.nowMillis()) {
        val session = _state.value.session ?: return
        if (session.isActiveAt(nowMillis)) return
        store.update { snapshot ->
            val stored = snapshot.session
            if (stored != null && !stored.isActiveAt(nowMillis)) snapshot.copy(session = null) else snapshot
        }
    }

    suspend fun startSession(
        durationMinutes: Int,
        allowedPackages: List<String>,
        allowedDomains: List<String>,
        websiteFilterEnabled: Boolean,
        source: SessionSource = SessionSource.MANUAL,
        scheduleId: String? = null,
    ): StartSessionResult {
        val now = clock.nowMillis()
        val duration = SessionRules.normalizeDuration(durationMinutes)
        val packages = SessionRules.sanitizeAdditionalApps(allowedPackages, emptySet())
        val domains = SessionRules.sanitizeDomains(allowedDomains)
            .mapNotNull { DomainRules.normalize(it) }
            .distinct()
        var result: StartSessionResult = StartSessionResult.AlreadyActive
        store.update { snapshot ->
            val existing = snapshot.session
            if (existing != null && existing.isActiveAt(now)) {
                result = StartSessionResult.AlreadyActive
                snapshot
            } else {
                val session = ActiveSession(
                    startedAtMillis = now,
                    endAtMillis = now + duration * 60_000L,
                    allowedPackages = packages,
                    allowedDomains = domains,
                    websiteFilterEnabled = websiteFilterEnabled && domains.isNotEmpty(),
                    scheduleId = scheduleId,
                    source = source,
                )
                result = StartSessionResult.Started(session)
                snapshot.copy(session = session)
            }
        }
        return result
    }

    suspend fun endSession() {
        store.update { snapshot ->
            if (snapshot.session == null) snapshot else snapshot.copy(session = null)
        }
    }

    /**
     * Consumes Cat's LIFE. The read-check-write happens inside a single DataStore
     * transaction, so a double tap cannot spend two escapes or one escape twice.
     */
    suspend fun useEscape(nowMillis: Long = clock.nowMillis()): EscapeResult {
        var outcome: EscapeResult = EscapeResult.NoActiveSession
        store.update { snapshot ->
            val session = snapshot.session
            when {
                session == null || !session.isActiveAt(nowMillis) -> {
                    outcome = EscapeResult.NoActiveSession
                    snapshot
                }
                !EscapePolicy.isAvailable(snapshot.settings.escapeUsedOnEpochDay, nowMillis, clock.zone()) -> {
                    outcome = EscapeResult.AlreadyUsedToday
                    snapshot
                }
                else -> {
                    outcome = EscapeResult.Used(session)
                    snapshot.copy(
                        session = null,
                        settings = snapshot.settings.copy(
                            escapeUsedOnEpochDay = EscapePolicy.localEpochDay(nowMillis, clock.zone()),
                        ),
                    )
                }
            }
        }
        return outcome
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        store.update { snapshot -> snapshot.copy(settings = transform(snapshot.settings)) }
    }

    /** Remembers the manual setup so the home screen opens with the last used configuration. */
    suspend fun rememberHomeDefaults(
        durationMinutes: Int,
        packages: List<String>,
        domains: List<String>,
        websiteFilterEnabled: Boolean,
    ) {
        updateSettings { settings ->
            settings.copy(
                defaultDurationMinutes = SessionRules.normalizeDuration(durationMinutes),
                defaultAllowedPackages = packages.take(SessionRules.MAX_ADDITIONAL_APPS),
                defaultAllowedDomains = domains,
                defaultWebsiteFilterEnabled = websiteFilterEnabled,
            )
        }
    }

    suspend fun upsertSchedule(schedule: Schedule) {
        store.update { snapshot ->
            val existing = snapshot.schedules
            val updated = if (existing.any { it.id == schedule.id }) {
                existing.map { if (it.id == schedule.id) schedule else it }
            } else {
                existing + schedule
            }
            snapshot.copy(schedules = updated.sortedBy { it.startMinuteOfDay })
        }
    }

    suspend fun deleteSchedule(id: String) {
        store.update { snapshot ->
            snapshot.copy(schedules = snapshot.schedules.filterNot { it.id == id })
        }
    }

    suspend fun setScheduleEnabled(id: String, enabled: Boolean) {
        store.update { snapshot ->
            snapshot.copy(
                schedules = snapshot.schedules.map {
                    if (it.id == id) it.copy(enabled = enabled) else it
                },
            )
        }
    }

    suspend fun markScheduleStarted(id: String, localEpochDay: Long) {
        store.update { snapshot ->
            snapshot.copy(
                schedules = snapshot.schedules.map {
                    if (it.id == id) it.copy(lastStartedEpochDay = localEpochDay) else it
                },
            )
        }
    }
}
