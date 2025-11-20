package com.mahmutalperenunal.adaptivehz

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mahmutalperenunal.adaptivehz.ui.theme.AdaptiveHzTheme
import androidx.core.content.edit
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private fun isAdaptiveServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val targetId = "$packageName/.AdaptiveHzService"
        return enabled.any { it.id == targetId }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        val shouldShowAccessibilityDialog = !isAdaptiveServiceEnabled()
        setContent {
            AdaptiveHzTheme {
                val view = LocalView.current
                val window = (view.context as Activity).window
                val color = MaterialTheme.colorScheme.background

                SideEffect {
                    window.statusBarColor = color.toArgb()
                    window.navigationBarColor = color.toArgb()

                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainScreen(
                        onApplyAdaptive = {
                            try {
                                val prefs = getSharedPreferences("adaptive_hz_prefs", MODE_PRIVATE)
                                prefs.edit { putBoolean("dynamic_enabled", true) }

                                // Let's start at 60 Hz, and when you start touching it, the service will increase to 90.
                                RefreshRateController.applyForce60(this@MainActivity)

                                Toast.makeText(
                                    this,
                                    getString(R.string.toast_adaptive_applied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(
                                    this,
                                    getString(R.string.toast_permission_missing),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onForce60 = {
                            RefreshRateController.applyForce60(this)
                            Toast.makeText(
                                this,
                                getString(R.string.toast_60hz_forced),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onForce90 = {
                            RefreshRateController.applyForce90(this)
                            Toast.makeText(
                                this,
                                getString(R.string.toast_90hz_forced),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        initialShowAccessibilityDialog = shouldShowAccessibilityDialog,
                        onOpenAccessibilitySettings = { openAccessibilitySettings() }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onApplyAdaptive: () -> Unit,
    onForce60: () -> Unit,
    onForce90: () -> Unit,
    initialShowAccessibilityDialog: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    var showAccessibilityDialog by remember { mutableStateOf(initialShowAccessibilityDialog) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = stringResource(id = R.string.description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onApplyAdaptive,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.apply_adaptive))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onForce60,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(id = R.string.force_60hz))
                }
                OutlinedButton(
                    onClick = onForce90,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(id = R.string.force_90hz))
                }
            }
        }
    }

    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = {
                Text(text = stringResource(id = R.string.dialog_accessibility_title))
            },
            text = {
                Text(
                    text = stringResource(id = R.string.dialog_accessibility_message)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccessibilityDialog = false
                        onOpenAccessibilitySettings()
                    }
                ) {
                    Text(text = stringResource(id = R.string.dialog_accessibility_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text(text = stringResource(id = R.string.dialog_accessibility_cancel))
                }
            }
        )
    }
}