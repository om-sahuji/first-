package com.focuslock.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.net.toUri
import com.focuslock.core.FocusLog

/** An installed, launchable app. */
data class AppEntry(
    val packageName: String,
    val label: String,
)

/**
 * Installed-app discovery.
 *
 * The launcher query is relatively expensive, so results are cached for the process lifetime
 * and refreshed only on explicit request. The accessibility service never calls this: it reads
 * package names straight from the event and checks them against the session's sets.
 */
class AppCatalog(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val lock = Any()

    @Volatile
    private var cachedApps: List<AppEntry>? = null

    @Volatile
    private var cachedBrowsers: Set<String>? = null

    fun launcherApps(forceRefresh: Boolean = false): List<AppEntry> {
        if (!forceRefresh) cachedApps?.let { return it }
        synchronized(lock) {
            if (!forceRefresh) cachedApps?.let { return it }
            val apps = queryLauncherApps()
            cachedApps = apps
            return apps
        }
    }

    fun installedPackages(): Set<String> = launcherApps().mapTo(mutableSetOf()) { it.packageName }

    fun labelFor(packageName: String): String {
        cachedApps?.firstOrNull { it.packageName == packageName }?.let { return it.label }
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(packageManager)?.toString() ?: packageName
        } catch (error: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /**
     * Packages that can open http(s) links. The website allowlist is scoped to these apps so
     * that permitted non-browser apps keep normal network access.
     */
    fun browserPackages(): Set<String> {
        cachedBrowsers?.let { return it }
        synchronized(lock) {
            cachedBrowsers?.let { return it }
            val browsers = mutableSetOf<String>()
            for (scheme in listOf("https", "http")) {
                val intent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
                runCatching {
                    packageManager.queryIntentActivities(intent, 0)
                        .forEach { resolveInfo ->
                            resolveInfo.activityInfo?.packageName?.let(browsers::add)
                        }
                }.onFailure { FocusLog.w("Browser query failed for $scheme", it) }
            }
            cachedBrowsers = browsers
            return browsers
        }
    }

    private fun queryLauncherApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = mutableListOf<AppEntry>()
        runCatching {
            packageManager.queryIntentActivities(intent, 0).forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
                if (packageName == appContext.packageName) return@forEach
                val label = runCatching {
                    resolveInfo.loadLabel(packageManager)?.toString()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
                entries += AppEntry(packageName, label)
            }
        }.onFailure { FocusLog.w("Launcher app query failed", it) }
        return entries
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * Opens an app from the Focus grid.
     *
     * Some system apps (dialer, camera, messaging) have no launcher activity on every OEM
     * build, so standard intents are tried as a fallback before giving up.
     */
    fun launchApp(packageName: String): Boolean {
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            if (startActivity(intent)) return true
        }
        for (fallback in fallbackIntents()) {
            val resolved = resolvePackage(fallback)
            if (resolved == packageName && startActivity(fallback)) return true
        }
        FocusLog.w("No launchable entry point for $packageName")
        return false
    }

    private fun fallbackIntents(): List<Intent> = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR),
    )

    private fun startActivity(intent: Intent): Boolean = try {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: Exception) {
        FocusLog.w("Activity start failed for ${intent.action}", error)
        false
    }

    /** Resolves the activity that handles an intent, used for Phone / Messages / Camera. */
    fun resolvePackage(intent: Intent): String? = try {
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    } catch (error: Exception) {
        null
    }

    fun queryPackages(intent: Intent): Set<String> = try {
        packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    } catch (error: Exception) {
        emptySet()
    }
}
