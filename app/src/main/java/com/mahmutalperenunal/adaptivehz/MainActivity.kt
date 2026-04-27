package com.mahmutalperenunal.adaptivehz

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mahmutalperenunal.adaptivehz.ui.theme.AdaptiveHzTheme
import android.annotation.SuppressLint
import android.os.PowerManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mahmutalperenunal.adaptivehz.ui.home.HomeScreen
import com.mahmutalperenunal.adaptivehz.ui.settings.SettingsScreen
import androidx.core.content.edit
import com.mahmutalperenunal.adaptivehz.core.AdaptiveHzRuntimeState
import com.mahmutalperenunal.adaptivehz.core.StabilityForegroundService
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect


// Main entry point of the app. Sets up navigation and provides system-level helpers used by the UI.
class MainActivity : ComponentActivity() {

    // Opens the system Accessibility Settings screen so the user can enable the service
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
    // Requests the system to exclude the app from battery optimizations
    private fun requestIgnoreBatteryOptimizations() {
        try {
            // PowerManager is used to check whether the app is already exempt from optimizations
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

    // Activity initialization: splash screen, theme setup, and Compose navigation graph
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            AdaptiveHzTheme {
                // Navigation controller managing the app's two main screens
                val navController = rememberNavController()

                val prefs = remember {
                    getSharedPreferences("adaptive_hz_prefs", MODE_PRIVATE)
                }
                var keepAliveEnabled by remember {
                    mutableStateOf(prefs.getBoolean("keep_alive_enabled", false))
                }

                var batteryOptimizationsIgnored by remember { mutableStateOf(false) }
                var notificationsGranted by remember { mutableStateOf(true) }

                val refreshBatteryState: () -> Unit = {
                    batteryOptimizationsIgnored = try {
                        val pm = getSystemService(PowerManager::class.java)
                        pm?.isIgnoringBatteryOptimizations(packageName) == true
                    } catch (_: Exception) {
                        false
                    }
                }

                val refreshNotificationState: () -> Unit = {
                    notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
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

                LaunchedEffect(Unit) {
                    refreshBatteryState()
                    refreshNotificationState()
                }

                SetupSystemBars()
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                getAccessibilityState = { AdaptiveHzRuntimeState.getAccessibilityState(this@MainActivity) },
                                openAccessibilitySettings = { openAccessibilitySettings() },
                                requestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                                batteryOptimizationsIgnored = batteryOptimizationsIgnored,
                                notificationsGranted = notificationsGranted,
                                onRequestNotificationPermission = requestNotificationPermission,
                                openSettingsScreen = { navController.navigate("settings") },
                                keepAliveEnabled = keepAliveEnabled,
                                onKeepAliveEnabledChange = { next ->
                                    prefs.edit { putBoolean("keep_alive_enabled", next) }
                                    keepAliveEnabled = next

                                    if (next) {
                                        StabilityForegroundService.start(this@MainActivity)
                                    } else {
                                        StabilityForegroundService.stop(this@MainActivity)
                                    }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                keepAliveEnabled = keepAliveEnabled,
                                batteryOptimizationsIgnored = batteryOptimizationsIgnored,
                                notificationsGranted = notificationsGranted,
                                onRequestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                                onRequestNotificationPermission = requestNotificationPermission,
                                onKeepAliveChanged = { next ->
                                    prefs.edit { putBoolean("keep_alive_enabled", next) }
                                    keepAliveEnabled = next

                                    if (next) {
                                        StabilityForegroundService.start(this@MainActivity)
                                    } else {
                                        StabilityForegroundService.stop(this@MainActivity)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Applies transparent system bars to better match the Material 3 edge-to-edge layout
@Composable
fun SetupSystemBars() {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isSystemInDarkTheme()

    // Ensures system bar colors are updated whenever composition occurs
    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color.Transparent,
            darkIcons = useDarkIcons
        )
        systemUiController.setNavigationBarColor(
            color = Color.Transparent,
            darkIcons = useDarkIcons
        )
    }
}