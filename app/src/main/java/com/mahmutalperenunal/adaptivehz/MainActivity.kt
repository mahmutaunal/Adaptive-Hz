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
import androidx.compose.ui.Modifier
import com.mahmutalperenunal.adaptivehz.ui.theme.AdaptiveHzTheme
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import android.content.ComponentName
import com.mahmutalperenunal.adaptivehz.core.AdaptiveHzService
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


// Main entry point of the app. Sets up navigation and provides system-level helpers used by the UI.
class MainActivity : ComponentActivity() {

    // Checks whether the AdaptiveHz accessibility service is currently enabled by the user
    private fun isAdaptiveServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        // Component name used to match the service against the enabled accessibility services list
        val cn = ComponentName(this, AdaptiveHzService::class.java)
        val expectedShort = cn.flattenToShortString()
        val expectedFull = cn.flattenToString()

        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { it.id == expectedShort || it.id == expectedFull }
    }

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
                                isAdaptiveServiceEnabled = { isAdaptiveServiceEnabled() },
                                openAccessibilitySettings = { openAccessibilitySettings() },
                                requestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                                openSettingsScreen = { navController.navigate("settings") }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
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