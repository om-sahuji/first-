package com.focuslock.web

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.focuslock.core.FocusLog
import com.focuslock.data.apps.AppCatalog
import com.focuslock.data.model.ActiveSession

/**
 * Decides whether a session needs the website filter and starts/stops the VPN service.
 *
 * Kept deliberately separate from app blocking: if VPN consent is missing or the tunnel cannot
 * be established, apps are still blocked. Nothing here is on the enforcement hot path.
 */
class WebsiteFilterController(
    private val context: Context,
    private val catalog: AppCatalog,
) {

    /**
     * The filter only applies to browser apps the user permitted. Other permitted apps keep
     * normal DNS resolution, so a focus session cannot accidentally break messaging.
     */
    fun browsersFor(session: ActiveSession): Set<String> =
        catalog.browserPackages().intersect(session.allowedPackages.toSet())

    fun shouldRun(session: ActiveSession?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (session == null || !session.isActiveAt(nowMillis)) return false
        if (!WebsiteFilterRule.isApplicable(session.websiteFilterEnabled, session.allowedDomains)) return false
        return browsersFor(session).isNotEmpty()
    }

    fun sync(session: ActiveSession?) {
        if (shouldRun(session)) start() else stop()
    }

    fun hasConsent(): Boolean = try {
        VpnService.prepare(context) == null
    } catch (error: Exception) {
        false
    }

    /** Intent that must be launched from an Activity to ask for VPN consent, or null. */
    fun consentIntent(): Intent? = try {
        VpnService.prepare(context)
    } catch (error: Exception) {
        FocusLog.w("VPN prepare failed", error)
        null
    }

    fun start() {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WebsiteFilterService::class.java),
            )
        } catch (error: Exception) {
            FocusLog.e("Could not start website filter", error)
        }
    }

    fun stop() {
        try {
            context.stopService(Intent(context, WebsiteFilterService::class.java))
        } catch (error: Exception) {
            FocusLog.w("Could not stop website filter", error)
        }
    }
}
