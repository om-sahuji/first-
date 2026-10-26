package com.focuslock.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.FocusLockApp
import com.focuslock.core.FocusLog
import com.focuslock.core.StartupTrace
import com.focuslock.ui.components.MessageBar
import com.focuslock.ui.screens.AppPickerScreen
import com.focuslock.ui.screens.FocusScreen
import com.focuslock.ui.screens.HomeScreen
import com.focuslock.ui.screens.ScheduleEditorScreen
import com.focuslock.ui.screens.SchedulesScreen
import com.focuslock.ui.screens.SetupScreen
import com.focuslock.ui.screens.WebsitesScreen
import com.focuslock.ui.theme.FocusLockTheme

/**
 * Single activity host.
 *
 * `singleTask` matters for enforcement: a redirect from the accessibility service reuses this
 * instance instead of stacking copies, which is what kept the prototype slow and flickery.
 */
class MainActivity : ComponentActivity() {

    private val vm: FocusViewModel by viewModels()

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.onPermissionChanged() }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.onPermissionChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusLockTheme {
                val route by vm.route.collectAsStateWithLifecycle()
                val snapshot by vm.snapshot.collectAsStateWithLifecycle()
                val message by vm.message.collectAsStateWithLifecycle()
                val session = snapshot.session?.takeIf { it.isActiveAt(System.currentTimeMillis()) }
                val focusActive = route == Route.Focus && session != null

                BackHandler {
                    if (!vm.onBack()) finish()
                }

                // First committed frame: record launch cost and let the system report it.
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    StartupTrace.onFirstFrame()
                    reportFullyDrawn()
                }

                // Session finished (timer or Cat's LIFE): leave strict lock task mode.
                LaunchedEffect(session == null) {
                    if (session == null) exitLockTaskIfNeeded()
                }
                LaunchedEffect(focusActive) {
                    if (focusActive) enterLockTaskIfNeeded()
                }

                ImmersiveFocus(active = focusActive)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    when (route) {
                        Route.Home -> HomeScreen(vm)
                        Route.Focus -> if (session != null) {
                            FocusScreen(
                                vm = vm,
                                session = session,
                                onOpenAccessibilitySettings = { SystemSettings.accessibility(this@MainActivity) },
                            )
                        } else {
                            HomeScreen(vm)
                        }
                        Route.AppPicker -> AppPickerScreen(vm)
                        Route.Websites -> WebsitesScreen(vm, onRequestVpnConsent = ::requestVpnConsent)
                        Route.Schedules -> SchedulesScreen(vm)
                        is Route.ScheduleEditor -> ScheduleEditorScreen(vm)
                        Route.Setup -> SetupScreen(
                            vm = vm,
                            onRequestVpnConsent = ::requestVpnConsent,
                            onRequestNotifications = ::requestNotifications,
                        )
                    }
                    MessageBar(
                        message = message,
                        onDismiss = vm::dismissMessage,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(16.dp),
                    )
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus()
        if (vm.snapshot.value.settings.strictModeEnabled) enterLockTaskIfNeeded()
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(FocusIntents.EXTRA_BLOCKED_PACKAGE)?.let(vm::onBlockedAppDetected)
    }

    private fun requestVpnConsent() {
        val consent = vm.vpnConsentIntent()
        if (consent != null) {
            vpnConsentLauncher.launch(consent)
        } else {
            vm.onPermissionChanged()
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.onPermissionChanged()
        }
    }

    /**
     * Strict mode: only ever entered when the phone is already a device owner and the user
     * enabled the option. Without device owner this is a no-op, so ordinary mode is unaffected.
     */
    private fun enterLockTaskIfNeeded() {
        val container = (application as FocusLockApp).container
        val session = vm.activeSession() ?: return
        if (!vm.snapshot.value.settings.strictModeEnabled) return
        if (!container.kiosk.isDeviceOwner) return
        if (container.status.isLockTaskActive()) return
        container.kiosk.applyLockTaskPackages(session)
        try {
            startLockTask()
            FocusLog.d("Lock task entered for session ${session.startedAtMillis}")
        } catch (error: Exception) {
            FocusLog.w("Lock task refused by the system", error)
        }
    }

    private fun exitLockTaskIfNeeded() {
        val container = (application as FocusLockApp).container
        if (!container.status.isLockTaskActive()) return
        try {
            stopLockTask()
            FocusLog.d("Lock task released")
        } catch (error: Exception) {
            FocusLog.w("Could not leave lock task", error)
        }
    }
}

/**
 * Hides the status and navigation bars while a session runs, making the Focus screen the whole
 * environment. Bars reappear on a deliberate swipe; nothing here blocks system functionality.
 */
@Composable
private fun ImmersiveFocus(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (active) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
