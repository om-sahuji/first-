package com.focuslock

import android.app.Application
import com.focuslock.core.Clock
import com.focuslock.core.StartupTrace
import com.focuslock.core.SystemTime
import com.focuslock.data.FocusRepository
import com.focuslock.data.apps.AppCatalog
import com.focuslock.data.apps.SystemAppResolver
import com.focuslock.data.store.FocusStore
import com.focuslock.enforcement.EnforcementStatusReader
import com.focuslock.enforcement.KioskManager
import com.focuslock.enforcement.ScheduleAlarmScheduler
import com.focuslock.session.SessionCoordinator
import com.focuslock.web.WebsiteFilterController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph.
 *
 * The app is small enough that Hilt would only add build time and indirection; every
 * dependency is explicit and constructed in one place.
 */
class AppContainer(application: Application) {

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = SystemTime

    val store = FocusStore(application)
    val repository = FocusRepository(store, clock, scope)
    val catalog = AppCatalog(application)
    val systemApps = SystemAppResolver(application)
    val status = EnforcementStatusReader(application)

    val alarms = ScheduleAlarmScheduler(application, clock)
    val kiosk = KioskManager(application)
    val websiteFilter = WebsiteFilterController(application, catalog)

    val coordinator = SessionCoordinator(
        context = application,
        repository = repository,
        alarms = alarms,
        websiteFilter = websiteFilter,
        kiosk = kiosk,
        clock = clock,
        scope = scope,
    )

    fun start() {
        coordinator.start()
    }
}

class FocusLockApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        StartupTrace.onApplicationCreate()
        container = AppContainer(this)
        container.start()
    }
}
