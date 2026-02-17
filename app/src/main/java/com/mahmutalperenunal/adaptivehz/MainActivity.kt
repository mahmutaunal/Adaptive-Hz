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
import com.mahmutalperenunal.adaptivehz.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private fun isAdaptiveServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false

        // Android can represent the same component in different string forms.
        // Examples:
        // - com.pkg/.core.AdaptiveHzService
        // - com.pkg/com.pkg.core.AdaptiveHzService
        val cn = ComponentName(this, AdaptiveHzService::class.java)
        val expectedShort = cn.flattenToShortString()
        val expectedFull = cn.flattenToString()

        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { it.id == expectedShort || it.id == expectedFull }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            AdaptiveHzTheme {
                SetupSystemBars()
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HomeScreen(
                        isAdaptiveServiceEnabled = { isAdaptiveServiceEnabled() },
                        openAccessibilitySettings = { openAccessibilitySettings() },
                        requestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() }
                    )
                }
            }
        }
    }
}

@Composable
fun SetupSystemBars() {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isSystemInDarkTheme()

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