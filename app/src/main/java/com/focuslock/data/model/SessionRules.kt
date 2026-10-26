package com.focuslock.data.model

/**
 * Validation shared by manual sessions and schedules. Keeping the rules here means a schedule
 * can never be configured with options a manual session would reject.
 */
object SessionRules {
    const val MAX_ADDITIONAL_APPS = 5
    const val MIN_DURATION_MINUTES = 5
    const val MAX_DURATION_MINUTES = 180
    const val WHEEL_STEP_MINUTES = 5

    val DURATION_RANGE = MIN_DURATION_MINUTES..MAX_DURATION_MINUTES

    /** Quick presets shown on the home screen. */
    val PRESETS = listOf(15, 25, 45, 60, 90)

    fun normalizeDuration(minutes: Int): Int =
        minutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)

    /**
     * Deduplicates and trims an app selection to the five additional slots, dropping packages
     * that are always available anyway (permanent apps and the app itself).
     */
    fun sanitizeAdditionalApps(
        packages: Collection<String>,
        permanentPackages: Set<String>,
    ): List<String> = packages
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .filterNot { it in permanentPackages }
        .take(MAX_ADDITIONAL_APPS)
        .toList()

    fun sanitizeDomains(domains: Collection<String>): List<String> = domains
        .asSequence()
        .mapNotNull { it.trim().ifEmpty { null } }
        .distinct()
        .toList()

    /** Problems that must be resolved before a manual session can start. */
    fun validateStart(
        durationMinutes: Int,
        allowedPackages: Collection<String>,
        installedPackages: Set<String>,
        hasActiveSession: Boolean,
    ): String? = when {
        hasActiveSession -> "A session is already running."
        durationMinutes !in DURATION_RANGE ->
            "Choose a duration between $MIN_DURATION_MINUTES and $MAX_DURATION_MINUTES minutes."
        allowedPackages.size > MAX_ADDITIONAL_APPS ->
            "You can allow at most $MAX_ADDITIONAL_APPS extra apps."
        allowedPackages.any { it !in installedPackages } ->
            "One of the selected apps is no longer installed."
        else -> null
    }
}
