package com.focuslock.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {

    private val permanent = setOf("com.android.dialer", "com.google.android.apps.messaging", "com.camera")

    @Test
    fun `selection is capped at five extra apps`() {
        val many = (1..9).map { "com.example.app$it" }
        val sanitized = SessionRules.sanitizeAdditionalApps(many, permanent)
        assertEquals(5, sanitized.size)
        assertEquals("com.example.app1", sanitized.first())
    }

    @Test
    fun `permanent apps are not counted as extra selections`() {
        val sanitized = SessionRules.sanitizeAdditionalApps(
            listOf("com.android.dialer", "com.camera", "com.example.reader"),
            permanent,
        )
        assertEquals(listOf("com.example.reader"), sanitized)
    }

    @Test
    fun `duplicates and blanks are removed`() {
        val sanitized = SessionRules.sanitizeAdditionalApps(
            listOf("com.example.a", "com.example.a", "  ", "com.example.b"),
            permanent,
        )
        assertEquals(listOf("com.example.a", "com.example.b"), sanitized)
    }

    @Test
    fun `duration is clamped into the supported range`() {
        assertEquals(SessionRules.MIN_DURATION_MINUTES, SessionRules.normalizeDuration(0))
        assertEquals(SessionRules.MAX_DURATION_MINUTES, SessionRules.normalizeDuration(600))
        assertEquals(45, SessionRules.normalizeDuration(45))
    }

    @Test
    fun `validation blocks a second session and missing apps`() {
        assertNotNull(
            SessionRules.validateStart(
                durationMinutes = 25,
                allowedPackages = emptyList(),
                installedPackages = emptySet(),
                hasActiveSession = true,
            ),
        )
        assertNotNull(
            SessionRules.validateStart(
                durationMinutes = 25,
                allowedPackages = listOf("com.example.gone"),
                installedPackages = setOf("com.example.present"),
                hasActiveSession = false,
            ),
        )
        assertNull(
            SessionRules.validateStart(
                durationMinutes = 25,
                allowedPackages = listOf("com.example.present"),
                installedPackages = setOf("com.example.present"),
                hasActiveSession = false,
            ),
        )
    }

    @Test
    fun `presets are all valid durations`() {
        assertTrue(SessionRules.PRESETS.all { it in SessionRules.DURATION_RANGE })
    }
}
