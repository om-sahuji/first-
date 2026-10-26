package com.focuslock.data.apps

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.focuslock.core.FocusLog

/** The three apps that are always reachable during a session. */
enum class PermanentRole { PHONE, MESSAGES, CAMERA }

data class PermanentApp(
    val role: PermanentRole,
    val packageName: String?,
    val label: String,
) {
    val available: Boolean get() = packageName != null
}

/**
 * Resolves the permanent apps and the set of system surfaces that must never be blocked.
 *
 * Called when a session starts or when the accessibility service connects, and cached, so the
 * per-event blocking path does no package resolution at all.
 */
class SystemAppResolver(private val context: Context) {

    private val catalog = AppCatalog(context)

    @Volatile
    private var cachedPermissions: PermittedSystemPackages? = null

    fun permittedPackages(forceRefresh: Boolean = false): PermittedSystemPackages {
        if (!forceRefresh) cachedPermissions?.let { return it }
        val resolved = build()
        cachedPermissions = resolved
        return resolved
    }

    fun permanentApps(): List<PermanentApp> = listOf(
        PermanentApp(PermanentRole.PHONE, dialerPackage(), "Phone"),
        PermanentApp(PermanentRole.MESSAGES, smsPackage(), "Messages"),
        PermanentApp(PermanentRole.CAMERA, cameraPackage(), "Camera"),
    )

    private fun build(): PermittedSystemPackages {
        val always = mutableSetOf(
            context.packageName,
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.android.emergency",
            "com.android.dialer",
        )
        dialerPackage()?.let(always::add)
        smsPackage()?.let(always::add)
        always += cameraPackages()

        val inputMethods = try {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.enabledInputMethodList?.mapNotNull { it.packageName }?.toSet().orEmpty()
        } catch (error: Exception) {
            FocusLog.w("Could not read enabled input methods", error)
            emptySet()
        }
        always += inputMethods

        // Camera apps that are not also IMEs or the dialer are permanent by requirement.
        val camera = cameraPackages()
        return PermittedSystemPackages(
            alwaysAllowed = always,
            inputMethods = inputMethods,
            cameraPackages = camera,
        )
    }

    private fun dialerPackage(): String? = try {
        val telecom = context.getSystemService(TelecomManager::class.java)
        telecom?.defaultDialerPackage
            ?: catalog.resolvePackage(Intent(Intent.ACTION_DIAL))
    } catch (error: Exception) {
        FocusLog.w("Could not resolve default dialer", error)
        null
    }

    private fun smsPackage(): String? = try {
        Telephony.Sms.getDefaultSmsPackage(context)
    } catch (error: Exception) {
        FocusLog.w("Could not resolve default SMS app", error)
        null
    }

    private fun cameraPackage(): String? = cameraPackages().firstOrNull()

    private fun cameraPackages(): Set<String> =
        catalog.queryPackages(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
}

/** Pre-computed allowlist of system surfaces for the enforcement hot path. */
data class PermittedSystemPackages(
    val alwaysAllowed: Set<String>,
    val inputMethods: Set<String>,
    val cameraPackages: Set<String>,
) {
    fun allows(packageName: String): Boolean = packageName in alwaysAllowed
}
