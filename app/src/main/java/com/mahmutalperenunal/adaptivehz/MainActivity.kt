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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.mahmutalperenunal.adaptivehz.ui.settings.AccessibilityEventInspectorScreen
import com.mahmutalperenunal.adaptivehz.ui.settings.DiagnosticsScreen

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
                val navController = rememberNavController()

                val prefs = remember {
                    appContext.getSharedPreferences("adaptive_hz_prefs", MODE_PRIVATE)
                }
                var keepAliveEnabled by remember {
                    mutableStateOf(prefs.getBoolean("keep_alive_enabled", false))
                }

                var batteryOptimizationsIgnored by remember { mutableStateOf(false) }
                var notificationsGranted by remember { mutableStateOf(true) }

                var accessibilityState by remember {
                    mutableStateOf(AdaptiveHzRuntimeState.getAccessibilityState(appContext))
                }

                var adbGranted by remember {
                    mutableStateOf(AdaptiveHzPrefs.isAdbGranted(appContext))
                }

                var usageAccessGranted by remember {
                    mutableStateOf(RecentAppsProvider(appContext).hasPermission())
                }

                var rootAvailable by remember {
                    mutableStateOf(false)
                }

                val refreshBatteryState: () -> Unit = {
                    batteryOptimizationsIgnored = try {
                        val pm = appContext.getSystemService(PowerManager::class.java)
                        pm?.isIgnoringBatteryOptimizations(appContext.packageName) == true
                    } catch (_: Exception) {
                        false
                    }
                }

                val refreshNotificationState: () -> Unit = {
                    notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
                        ContextCompat.checkSelfPermission(
                            appContext,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                }

                // Refreshes permission and runtime states after returning from system screens.
                val refreshSetupStates: () -> Unit = {
                    accessibilityState = AdaptiveHzRuntimeState.getAccessibilityState(appContext)
                    adbGranted = AdaptiveHzPrefs.isAdbGranted(appContext)
                    usageAccessGranted = RecentAppsProvider(appContext).hasPermission()

                    rootAvailable = when (RootManager.getRootState()) {
                        is RootManager.RootState.Available -> true
                        else -> false
                    }

                    refreshBatteryState()
                    refreshNotificationState()

                    AccessibilityHealthMonitor.check(
                        context = appContext,
                        reason = "main_activity_refresh_setup_states"
                    )
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
                    val permission = "android.permission.WRITE_SECURE_SETTINGS"

                    val verified = try {
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

                    AdaptiveHzPrefs.setAdbGranted(appContext, verified)
                    adbGranted = verified

                    Toast.makeText(
                        appContext,
                        if (verified) R.string.toast_adb_verified else R.string.toast_adb_permission_missing,
                        if (verified) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    ).show()
                }

                // Optional root path for granting the required secure settings permission.
                val grantAdbWithRoot: () -> Unit = {
                    when (val result = RootManager.grantWriteSecureSettings(appContext)) {
                        is RootManager.RootState.Available -> {
                            AdaptiveHzPrefs.setAdbGranted(appContext, true)
                            adbGranted = true
                            Toast.makeText(appContext, R.string.root_grant_success, Toast.LENGTH_LONG).show()
                        }

                        is RootManager.RootState.Denied -> {
                            Toast.makeText(appContext, R.string.root_grant_denied, Toast.LENGTH_LONG).show()
                        }

                        is RootManager.RootState.Unavailable -> {
                            Toast.makeText(appContext, R.string.root_not_available, Toast.LENGTH_LONG).show()
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
                    // Single-activity navigation graph for all app screens.
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                getAccessibilityState = { AdaptiveHzRuntimeState.getAccessibilityState(appContext) },
                                openAccessibilitySettings = { openAccessibilitySettings() },
                                requestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                                batteryOptimizationsIgnored = batteryOptimizationsIgnored,
                                notificationsGranted = notificationsGranted,
                                onRequestNotificationPermission = requestNotificationPermission,
                                openSettingsScreen = { navController.navigate("settings") },
                                openPerAppScreen = { navController.navigate("per_app_refresh") },
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

                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
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
                                onOpenDiagnostics = { navController.navigate("diagnostics") },
                                onOpenEventInspector = { navController.navigate("event_inspector") },
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

                        composable("per_app_refresh") {
                            PerAppRefreshScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("diagnostics") {
                            DiagnosticsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("event_inspector") {
                            AccessibilityEventInspectorScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}