package com.mahmutalperenunal.adaptivehz

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mahmutalperenunal.adaptivehz.ui.theme.AdaptiveHzTheme
import android.annotation.SuppressLint
import android.os.PowerManager
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.net.toUri
import com.mahmutalperenunal.adaptivehz.ui.home.HomeScreen
import com.mahmutalperenunal.adaptivehz.ui.settings.SettingsScreen
import androidx.core.content.edit
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzRuntimeState
import com.mahmutalperenunal.adaptivehz.core.service.StabilityForegroundService
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.apps.RecentAppsProvider
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.locale.AppLocaleController
import com.mahmutalperenunal.adaptivehz.core.prefs.AppThemeMode
import com.mahmutalperenunal.adaptivehz.core.system.RootManager
import com.mahmutalperenunal.adaptivehz.ui.home.PerAppRefreshScreen
import com.mahmutalperenunal.adaptivehz.ui.components.AdaptivePredictiveScreenHost
import com.mahmutalperenunal.adaptivehz.ui.settings.AccessibilityEventInspectorScreen
import com.mahmutalperenunal.adaptivehz.ui.settings.DiagnosticsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppScreen {
    Home,
    Settings,
    PerAppRefresh,
    Diagnostics,
    EventInspector
}

private data class SetupRuntimeSnapshot(
    val accessibilityState: AdaptiveHzRuntimeState.AccessibilityState,
    val usageAccessGranted: Boolean,
    val rootAvailable: Boolean,
    val batteryOptimizationsIgnored: Boolean,
    val notificationsGranted: Boolean
)

/**
 * App entry point that wires Compose navigation with platform setup actions.
 */
class MainActivity : AppCompatActivity() {

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                getString(R.string.toast_open_accessibility_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            val pkg = packageName
            if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$pkg".toUri()
                }
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.toast_battery_already_ignored),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Exception) {
            Toast.makeText(
                this,
                getString(R.string.toast_battery_settings_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Initializes app state, permission launchers and the Compose navigation graph.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }

        super.onCreate(savedInstanceState)

        setContent {
            val appContext = this@MainActivity.applicationContext
            var themeMode by remember {
                mutableStateOf(AdaptiveHzPrefs.getThemeMode(appContext))
            }

            var appLanguage by remember {
                mutableStateOf(AdaptiveHzPrefs.getAppLanguage(appContext))
            }

            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            AdaptiveHzTheme(darkTheme = darkTheme) {
                var currentScreen by remember { mutableStateOf(AppScreen.Home) }
                var screenHistory by remember { mutableStateOf(emptyList<AppScreen>()) }
                var isBackNavigation by remember { mutableStateOf(false) }

                val navigateTo: (AppScreen) -> Unit = { destination ->
                    if (destination != currentScreen) {
                        screenHistory = screenHistory + currentScreen
                        isBackNavigation = false
                        currentScreen = destination
                    }
                }

                val navigateBack: () -> Unit = {
                    screenHistory.lastOrNull()?.let { destination ->
                        screenHistory = screenHistory.dropLast(1)
                        isBackNavigation = true
                        currentScreen = destination
                    }
                }

                val prefs = remember {
                    appContext.getSharedPreferences("adaptive_hz_prefs", MODE_PRIVATE)
                }
                var keepAliveEnabled by remember {
                    mutableStateOf(prefs.getBoolean("keep_alive_enabled", false))
                }

                var batteryOptimizationsIgnored by remember { mutableStateOf(false) }
                var notificationsGranted by remember { mutableStateOf(true) }

                var accessibilityState by remember {
                    mutableStateOf(AdaptiveHzRuntimeState.AccessibilityState.DISABLED)
                }

                var adbGranted by remember {
                    mutableStateOf(AdaptiveHzPrefs.isAdbGranted(appContext))
                }

                var usageAccessGranted by remember { mutableStateOf(false) }

                var rootAvailable by remember {
                    mutableStateOf(false)
                }

                val setupStateScope = rememberCoroutineScope()

                // Refreshes permission and runtime states after returning from system screens.
                val refreshSetupStates: () -> Unit = {
                    adbGranted = AdaptiveHzPrefs.isAdbGranted(appContext)

                    setupStateScope.launch {
                        val runtimeSnapshot = withContext(Dispatchers.IO) {
                            SetupRuntimeSnapshot(
                                accessibilityState =
                                    AdaptiveHzRuntimeState.getAccessibilityState(appContext),
                                usageAccessGranted = RecentAppsProvider(appContext).hasPermission(),
                                rootAvailable =
                                    RootManager.getRootState() is RootManager.RootState.Available,
                                batteryOptimizationsIgnored = runCatching {
                                    appContext.getSystemService(PowerManager::class.java)
                                        ?.isIgnoringBatteryOptimizations(appContext.packageName) == true
                                }.getOrDefault(false),
                                notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
                                    ContextCompat.checkSelfPermission(
                                        appContext,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                } else {
                                    true
                                }
                            ).also {
                                AccessibilityHealthMonitor.check(
                                    context = appContext,
                                    reason = "main_activity_refresh_setup_states"
                                )
                            }
                        }

                        accessibilityState = runtimeSnapshot.accessibilityState
                        usageAccessGranted = runtimeSnapshot.usageAccessGranted
                        rootAvailable = runtimeSnapshot.rootAvailable
                        batteryOptimizationsIgnored = runtimeSnapshot.batteryOptimizationsIgnored
                        notificationsGranted = runtimeSnapshot.notificationsGranted
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    notificationsGranted = granted
                }

                val requestNotificationPermission: () -> Unit = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        notificationsGranted = true
                    }
                }

                val openUsageAccessSettings: () -> Unit = {
                    try {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Verifies WRITE_SECURE_SETTINGS with both permission and safe write checks.
                val verifyAdbPermission: () -> Unit = {
                    setupStateScope.launch {
                        val verified = withContext(Dispatchers.IO) {
                            val permission = "android.permission.WRITE_SECURE_SETTINGS"
                            try {
                                val pmGranted = ContextCompat.checkSelfPermission(
                                    appContext,
                                    permission
                                ) == PackageManager.PERMISSION_GRANTED

                                val cr = appContext.contentResolver
                                val key = Settings.Global.ANIMATOR_DURATION_SCALE
                                val current = Settings.Global.getFloat(cr, key, 1f)
                                val wrote = Settings.Global.putFloat(cr, key, current)
                                val after = Settings.Global.getFloat(cr, key, 1f)

                                pmGranted || (wrote && after == current)
                            } catch (_: SecurityException) {
                                false
                            } catch (_: Exception) {
                                ContextCompat.checkSelfPermission(
                                    appContext,
                                    permission
                                ) == PackageManager.PERMISSION_GRANTED
                            }
                        }

                        AdaptiveHzPrefs.setAdbGranted(appContext, verified)
                        adbGranted = verified
                        Toast.makeText(
                            appContext,
                            if (verified) R.string.toast_adb_verified
                            else R.string.toast_adb_permission_missing,
                            if (verified) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Optional root path for granting the required secure settings permission.
                val grantAdbWithRoot: () -> Unit = {
                    setupStateScope.launch {
                        when (val result = withContext(Dispatchers.IO) {
                            RootManager.grantWriteSecureSettings(appContext)
                        }) {
                            is RootManager.RootState.Available -> {
                                AdaptiveHzPrefs.setAdbGranted(appContext, true)
                                adbGranted = true
                                Toast.makeText(
                                    appContext,
                                    R.string.root_grant_success,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Denied -> {
                                Toast.makeText(
                                    appContext,
                                    R.string.root_grant_denied,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Unavailable -> {
                                Toast.makeText(
                                    appContext,
                                    R.string.root_not_available,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Failed -> {
                                Toast.makeText(
                                    appContext,
                                    result.reason ?: getString(R.string.root_grant_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    refreshSetupStates()
                }

                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshSetupStates()
                        }
                    }

                    lifecycleOwner.lifecycle.addObserver(observer)

                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(darkTheme) {
                    val systemBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT
                        )
                    }

                    enableEdgeToEdge(
                        statusBarStyle = systemBarStyle,
                        navigationBarStyle = systemBarStyle
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AdaptivePredictiveScreenHost(
                        current = currentScreen,
                        previous = screenHistory.lastOrNull(),
                        onBack = navigateBack,
                        depth = { screen ->
                            when (screen) {
                                AppScreen.Home -> 0
                                AppScreen.Settings, AppScreen.PerAppRefresh -> 1
                                AppScreen.Diagnostics, AppScreen.EventInspector -> 2
                            }
                        },
                        isBackNavigation = isBackNavigation
                    ) { displayedScreen ->
                        when (displayedScreen) {
                            AppScreen.Home -> {
                                HomeScreen(
                                    getAccessibilityState = { AdaptiveHzRuntimeState.getAccessibilityState(appContext) },
                                    openAccessibilitySettings = { openAccessibilitySettings() },
                                    requestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                                    batteryOptimizationsIgnored = batteryOptimizationsIgnored,
                                    notificationsGranted = notificationsGranted,
                                    onRequestNotificationPermission = requestNotificationPermission,
                                    openSettingsScreen = { navigateTo(AppScreen.Settings) },
                                    openPerAppScreen = { navigateTo(AppScreen.PerAppRefresh) },
                                    keepAliveEnabled = keepAliveEnabled,
                                    onKeepAliveEnabledChange = { next ->
                                        prefs.edit { putBoolean("keep_alive_enabled", next) }
                                        keepAliveEnabled = next

                                        if (next) {
                                            StabilityForegroundService.start(appContext)
                                        } else {
                                            StabilityForegroundService.stop(appContext)
                                        }
                                    }
                                )
                            }

                            AppScreen.Settings -> {
                                SettingsScreen(
                                    onBack = navigateBack,
                                    accessibilityState = accessibilityState,
                                    adbGranted = adbGranted,
                                    usageAccessGranted = usageAccessGranted,
                                    rootAvailable = rootAvailable,
                                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                                    onVerifyAdb = verifyAdbPermission,
                                    onGrantWithRoot = grantAdbWithRoot,
                                    onOpenUsageAccessSettings = openUsageAccessSettings,
                                    keepAliveEnabled = keepAliveEnabled,
                                    batteryOptimizationsIgnored = batteryOptimizationsIgnored,
                                    notificationsGranted = notificationsGranted,
                                    onRequestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                                    onRequestNotificationPermission = requestNotificationPermission,
                                    onOpenDiagnostics = { navigateTo(AppScreen.Diagnostics) },
                                    onOpenEventInspector = { navigateTo(AppScreen.EventInspector) },
                                    onKeepAliveChanged = { next ->
                                        prefs.edit { putBoolean("keep_alive_enabled", next) }
                                        keepAliveEnabled = next

                                        if (next) {
                                            StabilityForegroundService.start(appContext)
                                        } else {
                                            StabilityForegroundService.stop(appContext)
                                        }
                                    },
                                    themeMode = themeMode,
                                    onThemeModeChanged = { next ->
                                        themeMode = next
                                        AdaptiveHzPrefs.setThemeMode(appContext, next)
                                    },
                                    appLanguage = appLanguage,
                                    onAppLanguageChanged = { next ->
                                        if (next == appLanguage) return@SettingsScreen

                                        appLanguage = next
                                        AdaptiveHzPrefs.setAppLanguage(appContext, next)
                                        AppLocaleController.apply(next)
                                    }
                                )
                            }

                            AppScreen.PerAppRefresh -> {
                                PerAppRefreshScreen(
                                    onBack = navigateBack
                                )
                            }

                            AppScreen.Diagnostics -> {
                                DiagnosticsScreen(
                                    onBack = navigateBack
                                )
                            }

                            AppScreen.EventInspector -> {
                                AccessibilityEventInspectorScreen(
                                    onBack = navigateBack
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
