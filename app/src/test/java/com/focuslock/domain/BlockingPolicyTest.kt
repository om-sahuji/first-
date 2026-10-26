package com.focuslock.domain

import com.focuslock.data.model.ActiveSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingPolicyTest {

    private val self = "com.focuslock"
    private val now = 1_000_000_000L
    private val session = ActiveSession(
        startedAtMillis = now - 60_000,
        endAtMillis = now + 60_000,
        allowedPackages = listOf("com.example.reader"),
    )
    private val systemPackages = setOf("com.android.systemui", "com.android.dialer", self)

    @Test
    fun `nothing is blocked without a session`() {
        assertFalse(BlockingPolicy.shouldRedirect("com.instagram.android", null, now, systemPackages, self))
    }

    @Test
    fun `nothing is blocked after the end time even if state is stale`() {
        val expired = session.copy(endAtMillis = now - 1)
        assertFalse(BlockingPolicy.shouldRedirect("com.instagram.android", expired, now, systemPackages, self))
    }

    @Test
    fun `own package is never redirected`() {
        assertFalse(BlockingPolicy.shouldRedirect(self, session, now, systemPackages, self))
    }

    @Test
    fun `system surfaces stay reachable`() {
        assertFalse(BlockingPolicy.shouldRedirect("com.android.systemui", session, now, systemPackages, self))
        assertFalse(BlockingPolicy.shouldRedirect("com.android.dialer", session, now, systemPackages, self))
    }

    @Test
    fun `user allowed apps stay reachable`() {
        assertFalse(BlockingPolicy.shouldRedirect("com.example.reader", session, now, systemPackages, self))
    }

    @Test
    fun `unlisted apps are redirected`() {
        assertTrue(BlockingPolicy.shouldRedirect("com.instagram.android", session, now, systemPackages, self))
        assertTrue(BlockingPolicy.shouldRedirect("com.android.settings", session, now, systemPackages, self))
        assertTrue(BlockingPolicy.shouldRedirect("com.google.android.apps.nexuslauncher", session, now, systemPackages, self))
    }

    @Test
    fun `blank package names are ignored`() {
        assertFalse(BlockingPolicy.shouldRedirect(null, session, now, systemPackages, self))
        assertFalse(BlockingPolicy.shouldRedirect("", session, now, systemPackages, self))
    }
}
