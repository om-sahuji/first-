package com.focuslock.ui

/** Small hand-rolled route set: the app has six screens, a nav library would add nothing. */
sealed interface Route {
    data object Home : Route
    data object Focus : Route
    data object AppPicker : Route
    data object Websites : Route
    data object Schedules : Route
    data class ScheduleEditor(val scheduleId: String?) : Route
    data object Setup : Route
}

/** Which draft the app picker edits. */
enum class PickerTarget { HOME, SCHEDULE }

/** Editable configuration for a manual session. */
data class HomeDraft(
    val durationMinutes: Int = 25,
    val allowedPackages: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val websiteFilterEnabled: Boolean = false,
)

/** Editable schedule, kept separate so cancelling an edit cannot leak into a session. */
data class ScheduleDraft(
    val id: String,
    val label: String,
    val days: Set<Int>,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val allowedPackages: List<String>,
    val domains: List<String>,
    val websiteFilterEnabled: Boolean,
    val isNew: Boolean,
)

data class UiMessage(val text: String, val tone: MessageTone = MessageTone.Info, val id: Long = System.nanoTime())

enum class MessageTone { Info, Warning }
