package com.focuslock.domain

import com.focuslock.data.model.ActiveSession

/**
 * The single decision used by the enforcement layer: should this foreground package be
 * replaced by the Focus screen?
 *
 * Pure and side-effect free so it can be unit tested without an Android runtime. The redirect
 * path only performs set lookups, which keeps the blocking decision off the I/O path.
 */
object BlockingPolicy {

    fun shouldRedirect(
        packageName: String?,
        session: ActiveSession?,
        nowMillis: Long,
        alwaysAllowedPackages: Set<String>,
        selfPackage: String,
    ): Boolean {
        if (packageName.isNullOrBlank()) return false
        // No session (or an expired one) means enforcement is dormant.
        if (session == null || !session.isActiveAt(nowMillis)) return false
        // Never fight our own UI: that would cause a relaunch loop.
        if (packageName == selfPackage) return false
        if (packageName in alwaysAllowedPackages) return false
        if (packageName in session.allowedPackages) return false
        return true
    }

    /**
     * Whether a foreground window belongs to a package we should ignore entirely (system UI,
     * IMEs, telephony). Used to skip work before doing any other checks.
     */
    fun isSystemSurface(packageName: String, systemPackages: Set<String>): Boolean =
        packageName in systemPackages
}
