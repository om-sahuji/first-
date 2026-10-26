package com.focuslock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.FocusLockApp
import com.focuslock.core.TimeFormat
import com.focuslock.data.apps.AppEntry
import com.focuslock.data.apps.PermanentApp
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.AppSnapshot
import com.focuslock.data.model.EscapeResult
import com.focuslock.data.model.Schedule
import com.focuslock.data.model.SessionRules
import com.focuslock.data.model.SessionSource
import com.focuslock.data.model.StartSessionResult
import com.focuslock.domain.DomainRules
import com.focuslock.domain.EscapePolicy
import com.focuslock.enforcement.EnforcementStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * One view model for the whole app.
 *
 * It is a thin projection of [com.focuslock.data.FocusRepository] plus the transient draft a
 * user is editing; all durable decisions live in the repository so the UI cannot drift from
 * what enforcement sees.
 */
class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as FocusLockApp).container
    private val repository = container.repository
    private val catalog = container.catalog
    private val systemApps = container.systemApps
    private val statusReader = container.status
    private val clock = container.clock

    val snapshot: StateFlow<AppSnapshot> = repository.state

    private val _now = MutableStateFlow(clock.nowMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _route = MutableStateFlow<Route>(if (repository.activeSession() != null) Route.Focus else Route.Home)
    val route: StateFlow<Route> = _route.asStateFlow()

    private val _draft = MutableStateFlow(repository.state.value.settings.toHomeDraft())
    val draft: StateFlow<HomeDraft> = _draft.asStateFlow()

    private val _status = MutableStateFlow(statusReader.read())
    val enforcementStatus: StateFlow<EnforcementStatus> = _status.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _blockedLabel = MutableStateFlow<String?>(null)
    val blockedLabel: StateFlow<String?> = _blockedLabel.asStateFlow()

    private val _pickerTarget = MutableStateFlow(PickerTarget.HOME)
    val pickerTarget: StateFlow<PickerTarget> = _pickerTarget.asStateFlow()

    private val _scheduleDraft = MutableStateFlow<ScheduleDraft?>(null)
    val scheduleDraft: StateFlow<ScheduleDraft?> = _scheduleDraft.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _permanentApps = MutableStateFlow<List<PermanentApp>>(emptyList())
    val permanentApps: StateFlow<List<PermanentApp>> = _permanentApps.asStateFlow()

    private var blockedLabelJob: Job? = null

    init {
        // Package discovery is disk/IPC work: keep it off the main thread.
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.Default) { catalog.launcherApps() }
        }
        viewModelScope.launch {
            _permanentApps.value = withContext(Dispatchers.Default) { systemApps.permanentApps() }
        }
        viewModelScope.launch { repository.reconcile() }

        // UI ticker: absolute end time is authoritative, so a dropped frame or a sleeping
        // process can never make the countdown drift.
        viewModelScope.launch {
            while (isActive) {
                val nowMillis = clock.nowMillis()
                _now.value = nowMillis
                val session = repository.state.value.session
                if (session != null && !session.isActiveAt(nowMillis)) {
                    repository.reconcile(nowMillis)
                }
                delay(if (activeSession() != null) 250 else 1_000)
            }
        }

        viewModelScope.launch {
            repository.state.collect { state ->
                val active = state.session?.takeIf { it.isActiveAt(clock.nowMillis()) }
                when {
                    active != null && _route.value != Route.Focus -> {
                        _route.value = Route.Focus
                    }
                    active == null && _route.value == Route.Focus -> {
                        _route.value = Route.Home
                        _message.value = UiMessage("Focus session complete. Well done.")
                    }
                }
            }
        }
    }

    fun activeSession(): ActiveSession? = repository.activeSession(clock.nowMillis())

    fun refreshStatus() {
        _status.value = statusReader.read()
        viewModelScope.launch {
            _permanentApps.value = withContext(Dispatchers.Default) { systemApps.permanentApps() }
        }
    }

    fun navigate(route: Route) {
        _route.value = route
    }

    /** Returns true when the back press was consumed. */
    fun onBack(): Boolean {
        if (activeSession() != null) return true
        return when (val current = _route.value) {
            Route.Home, Route.Focus -> false
            is Route.ScheduleEditor -> {
                _route.value = Route.Schedules
                true
            }
            else -> {
                _route.value = Route.Home
                true
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    // ---------------------------------------------------------------- manual session draft

    fun setDuration(minutes: Int) {
        _draft.value = _draft.value.copy(durationMinutes = SessionRules.normalizeDuration(minutes))
    }

    fun toggleAllowedApp(packageName: String) {
        val current = _draft.value.allowedPackages
        if (packageName in current) {
            _draft.value = _draft.value.copy(allowedPackages = current - packageName)
            return
        }
        if (current.size >= SessionRules.MAX_ADDITIONAL_APPS) {
            _message.value = UiMessage(
                "You can allow ${SessionRules.MAX_ADDITIONAL_APPS} extra apps. Remove one first.",
                MessageTone.Warning,
            )
            return
        }
        _draft.value = _draft.value.copy(allowedPackages = current + packageName)
    }

    fun addDomain(raw: String): Boolean {
        val normalized = DomainRules.normalize(raw)
        if (normalized == null) {
            _message.value = UiMessage("Enter a domain like rollcall.iitd.ac.in", MessageTone.Warning)
            return false
        }
        if (normalized in _draft.value.domains) {
            _message.value = UiMessage("$normalized is already on the list.")
            return false
        }
        _draft.value = _draft.value.copy(domains = _draft.value.domains + normalized)
        return true
    }

    fun removeDomain(domain: String) {
        val domains = _draft.value.domains - domain
        _draft.value = _draft.value.copy(
            domains = domains,
            websiteFilterEnabled = _draft.value.websiteFilterEnabled && domains.isNotEmpty(),
        )
    }

    fun setWebsiteFilterEnabled(enabled: Boolean) {
        if (enabled && _draft.value.domains.isEmpty()) {
            _message.value = UiMessage("Add a site first, then turn the allowlist on.", MessageTone.Warning)
            return
        }
        _draft.value = _draft.value.copy(websiteFilterEnabled = enabled)
    }

    fun openAppPicker(target: PickerTarget) {
        _pickerTarget.value = target
        _route.value = Route.AppPicker
    }

    fun startSession() {
        viewModelScope.launch {
            val current = _draft.value
            val status = withContext(Dispatchers.Default) { statusReader.read() }
            _status.value = status
            if (!status.accessibilityEnabled) {
                _message.value = UiMessage(
                    "Enable the Focus Lock accessibility service before starting.",
                    MessageTone.Warning,
                )
                _route.value = Route.Setup
                return@launch
            }
            if (current.websiteFilterEnabled && current.domains.isEmpty()) {
                _message.value = UiMessage("Add a site or turn off the website allowlist.", MessageTone.Warning)
                return@launch
            }

            val permanent = withContext(Dispatchers.Default) {
                systemApps.permittedPackages().alwaysAllowed
            }
            val packages = SessionRules.sanitizeAdditionalApps(current.allowedPackages, permanent)
            val installed = withContext(Dispatchers.Default) { catalog.installedPackages() }
            val problem = SessionRules.validateStart(
                durationMinutes = current.durationMinutes,
                allowedPackages = packages,
                installedPackages = installed,
                hasActiveSession = repository.activeSession(clock.nowMillis()) != null,
            )
            if (problem != null) {
                _apps.value = withContext(Dispatchers.Default) { catalog.launcherApps(forceRefresh = true) }
                _message.value = UiMessage(problem, MessageTone.Warning)
                return@launch
            }

            when (
                val result = repository.startSession(
                    durationMinutes = current.durationMinutes,
                    allowedPackages = packages,
                    allowedDomains = current.domains,
                    websiteFilterEnabled = current.websiteFilterEnabled,
                    source = SessionSource.MANUAL,
                )
            ) {
                is StartSessionResult.Started -> {
                    repository.rememberHomeDefaults(
                        durationMinutes = current.durationMinutes,
                        packages = packages,
                        domains = current.domains,
                        websiteFilterEnabled = current.websiteFilterEnabled,
                    )
                    _route.value = Route.Focus
                    if (current.websiteFilterEnabled && !status.vpnConsentGranted) {
                        _message.value = UiMessage(
                            "Website allowlist needs VPN permission; apps are still blocked.",
                            MessageTone.Warning,
                        )
                    }
                }
                StartSessionResult.AlreadyActive -> _route.value = Route.Focus
                is StartSessionResult.Invalid -> _message.value = UiMessage(result.reason, MessageTone.Warning)
            }
        }
    }

    fun useEscape() {
        viewModelScope.launch {
            when (repository.useEscape()) {
                is EscapeResult.Used -> {
                    _route.value = Route.Home
                    _message.value = UiMessage("Cat's LIFE used. Your next escape unlocks tomorrow.")
                }
                EscapeResult.AlreadyUsedToday -> {
                    _message.value = UiMessage("Cat's LIFE is already used today.", MessageTone.Warning)
                }
                EscapeResult.NoActiveSession -> {
                    _message.value = UiMessage("There is no session to escape.")
                }
            }
        }
    }

    fun escapeAvailable(): Boolean = EscapePolicy.isAvailable(
        lastUsedEpochDay = repository.state.value.settings.escapeUsedOnEpochDay,
        nowMillis = clock.nowMillis(),
        zone = clock.zone(),
    )

    fun onBlockedAppDetected(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val label = catalog.labelFor(packageName)
        _blockedLabel.value = label
        blockedLabelJob?.cancel()
        blockedLabelJob = viewModelScope.launch {
            delay(2_500)
            _blockedLabel.value = null
        }
    }

    fun labelFor(packageName: String): String = catalog.labelFor(packageName)

    fun launchApp(packageName: String) {
        if (!catalog.launchApp(packageName)) {
            _message.value = UiMessage(
                "Could not open ${catalog.labelFor(packageName)}.",
                MessageTone.Warning,
            )
        }
    }

    fun endTimeLabel(endAtMillis: Long): String = TimeFormat.clockTime(endAtMillis, clock.zone())

    // ---------------------------------------------------------------- schedules

    fun openScheduleEditor(scheduleId: String?) {
        val existing = repository.state.value.schedules.firstOrNull { it.id == scheduleId }
        _scheduleDraft.value = if (existing != null) {
            ScheduleDraft(
                id = existing.id,
                label = existing.label,
                days = existing.days,
                startMinuteOfDay = existing.startMinuteOfDay,
                durationMinutes = existing.durationMinutes,
                allowedPackages = existing.allowedPackages,
                domains = existing.allowedDomains,
                websiteFilterEnabled = existing.websiteFilterEnabled,
                isNew = false,
            )
        } else {
            ScheduleDraft(
                id = UUID.randomUUID().toString(),
                label = "",
                days = setOf(1, 2, 3, 4, 5),
                startMinuteOfDay = Schedule.DEFAULT_START_MINUTE,
                durationMinutes = _draft.value.durationMinutes,
                allowedPackages = _draft.value.allowedPackages,
                domains = _draft.value.domains,
                websiteFilterEnabled = _draft.value.websiteFilterEnabled,
                isNew = true,
            )
        }
        _route.value = Route.ScheduleEditor(scheduleId)
    }

    fun updateScheduleDraft(transform: (ScheduleDraft) -> ScheduleDraft) {
        _scheduleDraft.value = _scheduleDraft.value?.let(transform)
    }

    fun toggleScheduleApp(packageName: String) {
        val draft = _scheduleDraft.value ?: return
        if (packageName in draft.allowedPackages) {
            _scheduleDraft.value = draft.copy(allowedPackages = draft.allowedPackages - packageName)
            return
        }
        if (draft.allowedPackages.size >= SessionRules.MAX_ADDITIONAL_APPS) {
            _message.value = UiMessage(
                "You can allow ${SessionRules.MAX_ADDITIONAL_APPS} extra apps per schedule.",
                MessageTone.Warning,
            )
            return
        }
        _scheduleDraft.value = draft.copy(allowedPackages = draft.allowedPackages + packageName)
    }

    fun addScheduleDomain(raw: String): Boolean {
        val draft = _scheduleDraft.value ?: return false
        val normalized = DomainRules.normalize(raw)
        if (normalized == null) {
            _message.value = UiMessage("Enter a domain like rollcall.iitd.ac.in", MessageTone.Warning)
            return false
        }
        if (normalized in draft.domains) return false
        _scheduleDraft.value = draft.copy(domains = draft.domains + normalized)
        return true
    }

    fun removeScheduleDomain(domain: String) {
        val draft = _scheduleDraft.value ?: return
        val domains = draft.domains - domain
        _scheduleDraft.value = draft.copy(
            domains = domains,
            websiteFilterEnabled = draft.websiteFilterEnabled && domains.isNotEmpty(),
        )
    }

    fun setScheduleWebsiteFilter(enabled: Boolean) {
        val draft = _scheduleDraft.value ?: return
        if (enabled && draft.domains.isEmpty()) {
            _message.value = UiMessage("Add a site first, then turn the allowlist on.", MessageTone.Warning)
            return
        }
        _scheduleDraft.value = draft.copy(websiteFilterEnabled = enabled)
    }

    fun saveSchedule() {
        val draft = _scheduleDraft.value ?: return
        if (draft.days.isEmpty()) {
            _message.value = UiMessage("Pick at least one day.", MessageTone.Warning)
            return
        }
        if (draft.websiteFilterEnabled && draft.domains.isEmpty()) {
            _message.value = UiMessage("Add a site or turn off the website allowlist.", MessageTone.Warning)
            return
        }
        viewModelScope.launch {
            repository.upsertSchedule(
                Schedule(
                    id = draft.id,
                    label = draft.label.trim(),
                    days = draft.days,
                    startMinuteOfDay = draft.startMinuteOfDay,
                    durationMinutes = SessionRules.normalizeDuration(draft.durationMinutes),
                    allowedPackages = draft.allowedPackages,
                    allowedDomains = draft.domains,
                    websiteFilterEnabled = draft.websiteFilterEnabled,
                    enabled = true,
                ),
            )
            _scheduleDraft.value = null
            _route.value = Route.Schedules
            _message.value = UiMessage("Schedule saved.")
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
            _scheduleDraft.value = null
            _route.value = Route.Schedules
            _message.value = UiMessage("Schedule deleted.")
        }
    }

    fun setScheduleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleEnabled(id, enabled) }
    }

    // ---------------------------------------------------------------- permissions

    fun vpnConsentIntent() = container.websiteFilter.consentIntent()

    fun onPermissionChanged() {
        refreshStatus()
        container.coordinator.syncEnforcementWithSession()
    }

    fun setStrictModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !statusReader.isDeviceOwner()) {
                _message.value = UiMessage(
                    "Strict mode needs device-owner provisioning (see the ADB steps).",
                    MessageTone.Warning,
                )
                return@launch
            }
            repository.updateSettings { it.copy(strictModeEnabled = enabled) }
            container.coordinator.syncEnforcementWithSession()
            _message.value = UiMessage(if (enabled) "Strict mode enabled." else "Strict mode disabled.")
        }
    }

    fun markSetupCompleted() {
        viewModelScope.launch {
            repository.updateSettings { it.copy(setupCompleted = true) }
        }
    }

    private fun com.focuslock.data.model.Settings.toHomeDraft() = HomeDraft(
        durationMinutes = defaultDurationMinutes,
        allowedPackages = defaultAllowedPackages,
        domains = defaultAllowedDomains,
        websiteFilterEnabled = defaultWebsiteFilterEnabled,
    )
}
