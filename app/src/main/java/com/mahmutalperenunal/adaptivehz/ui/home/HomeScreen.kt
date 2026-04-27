package com.mahmutalperenunal.adaptivehz.ui.home

import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBar
import androidx.core.content.ContextCompat
import com.mahmutalperenunal.adaptivehz.core.AdaptiveHzActionHandler
import com.mahmutalperenunal.adaptivehz.core.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.StabilityForegroundService
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.AdaptiveHzRuntimeState
import com.mahmutalperenunal.adaptivehz.core.system.RootManager
import com.mahmutalperenunal.adaptivehz.ui.home.components.DashboardComponent
import com.mahmutalperenunal.adaptivehz.ui.home.components.SetupComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    getAccessibilityState: () -> AdaptiveHzRuntimeState.AccessibilityState,
    batteryOptimizationsIgnored: Boolean,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    requestIgnoreBatteryOptimizations: () -> Unit,
    openSettingsScreen: () -> Unit,
    keepAliveEnabled: Boolean,
    onKeepAliveEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    // Shared preferences used to persist lightweight app state
    val prefs = remember { AdaptiveHzPrefs }

    val toastAdbVerified = stringResource(id = R.string.toast_adb_verified)
    val toastAdbPermissionMissing = stringResource(id = R.string.toast_adb_permission_missing)
    val toastStabilityEnabled = stringResource(id = R.string.toast_stability_enabled)
    val toastStabilityDisabled = stringResource(id = R.string.toast_stability_disabled)
    val toastOpenNotificationSettingsFailed = stringResource(id = R.string.toast_open_notification_settings_failed)
    val toastAdaptiveApplied = stringResource(id = R.string.toast_adaptive_applied)
    val toastMinimumApplied = stringResource(id = R.string.toast_minimum_applied)
    val toastMaximumApplied = stringResource(id = R.string.toast_maximum_applied)
    val toastSecureSettingsMissing = stringResource(id = R.string.toast_secure_settings_missing)
    val toastRootGrantSuccess = stringResource(id = R.string.root_grant_success)
    val toastRootGrantDenied = stringResource(id = R.string.root_grant_denied)
    val toastRootNotAvailable = stringResource(id = R.string.root_not_available)
    val toastRootGrantFailed = stringResource(id = R.string.root_grant_failed)
    val labelOn = stringResource(id = R.string.label_on)
    val labelOff = stringResource(id = R.string.label_off)
    val labelGranted = stringResource(id = R.string.label_granted)
    val labelRequired = stringResource(id = R.string.label_required)

    val labelIdle = stringResource(id = R.string.label_idle)
    val labelActive = stringResource(id = R.string.label_active)
    val labelAdaptiveTarget = stringResource(id = R.string.label_target_max_on_touch)
    val labelManualTargetMin = stringResource(id = R.string.label_target_minimum)
    val labelManualTargetMax = stringResource(id = R.string.label_target_maximum)
    val labelSystemDefault = stringResource(id = R.string.label_target_system_default)

    // Scroll state for the main content area
    val scrollState = rememberScrollState()

    // Restored state backed by SharedPreferences
    var adbGranted by remember { mutableStateOf(prefs.isAdbGranted(context)) }
    val currentMode = remember { mutableStateOf(prefs.getCurrentMode(context)) }

    // Frequently refreshed runtime checks
    val accessibilityState = remember { mutableStateOf(getAccessibilityState()) }
    var status by remember { mutableStateOf(RefreshRateController.readStatus(context)) }

    var rootAvailable by remember { mutableStateOf(false) }

    val initialSetupCompleted = remember { mutableStateOf(prefs.isInitialSetupCompleted(context)) }


    // Periodically sync UI state with the latest system/app values
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityState.value = getAccessibilityState()
            currentMode.value = prefs.getCurrentMode(context)
            status = RefreshRateController.readStatus(context)
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        rootAvailable = when (RootManager.getRootState()) {
            is RootManager.RootState.Available -> true
            else -> false
        }
    }

    val accessibilityConfigured = accessibilityState.value != AdaptiveHzRuntimeState.AccessibilityState.DISABLED
    val accessibilityWorking = accessibilityState.value == AdaptiveHzRuntimeState.AccessibilityState.WORKING
    val accessibilityBroken = accessibilityState.value == AdaptiveHzRuntimeState.AccessibilityState.BROKEN

    // Home switches from setup to dashboard only after all required steps are completed
    val requiredSetupReady = accessibilityConfigured && adbGranted
    val setupComplete = requiredSetupReady && initialSetupCompleted.value

    val appEnabled = currentMode.value != AdaptiveHzMode.OFF

    // Derived labels shown in the dashboard summary
    val currentModeLabel = when (currentMode.value) {
        AdaptiveHzMode.OFF -> stringResource(id = R.string.label_off)
        AdaptiveHzMode.ADAPTIVE -> stringResource(id = R.string.mode_adaptive)
        AdaptiveHzMode.FORCE_MIN,
        AdaptiveHzMode.FORCE_MAX -> stringResource(id = R.string.mode_manual)
    }

    val targetLabel = when (currentMode.value) {
        AdaptiveHzMode.OFF -> labelSystemDefault
        AdaptiveHzMode.ADAPTIVE -> labelAdaptiveTarget
        AdaptiveHzMode.FORCE_MIN -> labelManualTargetMin
        AdaptiveHzMode.FORCE_MAX -> labelManualTargetMax
    }

    val interactionLabel = if (currentMode.value == AdaptiveHzMode.ADAPTIVE) {
        labelActive
    } else {
        labelIdle
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            ),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.home_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            actions = {
                if (setupComplete) {
                    IconButton(onClick = openSettingsScreen) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(id = R.string.settings_title)
                        )
                    }
                }
            }
        )

        // Main scrollable area showing either setup steps or the dashboard
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .widthIn(max = 520.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (accessibilityBroken) {
                RecoveryCard(
                    onRetry = {
                        accessibilityState.value = getAccessibilityState()
                    },
                    onOpenAccessibilitySettings = openAccessibilitySettings,
                    onOpenBatterySettings = requestIgnoreBatteryOptimizations
                )
            }

            if (!setupComplete) {
                // Setup flow shown until all required permissions and services are ready
                SetupComponent(
                    accessibilityEnabled = accessibilityConfigured,
                    adbGranted = adbGranted,
                    batteryOptimizationsIgnored = batteryOptimizationsIgnored,
                    notificationsGranted = notificationsGranted,
                    keepAliveEnabled = keepAliveEnabled,
                    requiredSetupReady = requiredSetupReady,
                    onContinue = {
                        if (requiredSetupReady) {
                            prefs.setInitialSetupCompleted(context, true)
                            initialSetupCompleted.value = true
                        }
                    },
                    labelOn = labelOn,
                    labelOff = labelOff,
                    labelGranted = labelGranted,
                    labelRequired = labelRequired,
                    toastStabilityEnabled = toastStabilityEnabled,
                    toastStabilityDisabled = toastStabilityDisabled,
                    toastOpenNotificationSettingsFailed = toastOpenNotificationSettingsFailed,
                    rootAvailable = rootAvailable,
                    onOpenAccessibilitySettings = openAccessibilitySettings,
                    onVerifyAdb = {
                        val permission = "android.permission.WRITE_SECURE_SETTINGS"

                        val verified = try {
                            val pmGranted = ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) == PackageManager.PERMISSION_GRANTED

                            val cr = context.contentResolver
                            val key = Settings.Global.ANIMATOR_DURATION_SCALE
                            val current = Settings.Global.getFloat(cr, key, 1f)
                            val wrote = Settings.Global.putFloat(cr, key, current)
                            val after = Settings.Global.getFloat(cr, key, 1f)

                            pmGranted || (wrote && after == current)
                        } catch (_: SecurityException) {
                            false
                        } catch (_: Exception) {
                            ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) == PackageManager.PERMISSION_GRANTED
                        }

                        if (verified) {
                            prefs.setAdbGranted(context, true)
                            adbGranted = true
                            Toast.makeText(context, toastAdbVerified, Toast.LENGTH_SHORT).show()
                        } else {
                            prefs.setAdbGranted(context, false)
                            adbGranted = false
                            Toast.makeText(context, toastAdbPermissionMissing, Toast.LENGTH_LONG).show()
                        }
                    },
                    onGrantWithRoot = {
                        when (val result = RootManager.grantWriteSecureSettings(context)) {
                            is RootManager.RootState.Available -> {
                                prefs.setAdbGranted(context, true)
                                adbGranted = true
                                Toast.makeText(
                                    context,
                                    toastRootGrantSuccess,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Denied -> {
                                Toast.makeText(
                                    context,
                                    toastRootGrantDenied,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Unavailable -> {
                                Toast.makeText(
                                    context,
                                    toastRootNotAvailable,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Failed -> {
                                Toast.makeText(
                                    context,
                                    result.reason ?: toastRootGrantFailed,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onRequestIgnoreBatteryOptimizations = {
                        requestIgnoreBatteryOptimizations()
                    },
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onSetKeepAliveEnabled = { next ->
                        prefs.setKeepAliveEnabled(context, next)
                        onKeepAliveEnabledChange(next)

                        if (next) {
                            StabilityForegroundService.start(context)
                        } else {
                            StabilityForegroundService.stop(context)
                        }
                    },
                    secondaryAdbContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(id = R.string.setup_adb_instruction),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CodeLine(
                                text = "adb shell pm grant com.mahmutalperenunal.adaptivehz android.permission.WRITE_SECURE_SETTINGS"
                            )
                        }
                    }
                )
            } else {
                // Main dashboard shown after setup is fully completed
                DashboardComponent(
                    appEnabled = appEnabled,
                    accessibilityWorking = accessibilityWorking,
                    accessibilityBroken = accessibilityBroken,
                    currentDisplayHz = status.displayHz.toInt(),
                    vendorLabel = status.vendor.toString(),
                    currentModeLabel = currentModeLabel,
                    targetLabel = targetLabel,
                    interactionLabel = interactionLabel,
                    onAppEnabledChange = { enabled ->
                        try {
                            if (enabled) {
                                AdaptiveHzActionHandler.turnOn(context)
                            } else {
                                AdaptiveHzActionHandler.turnOff(context)
                            }
                            currentMode.value = AdaptiveHzActionHandler.getCurrentMode(context)
                        } catch (_: SecurityException) {
                            Toast.makeText(
                                context,
                                toastSecureSettingsMissing,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onAdaptiveClick = {
                        try {
                            AdaptiveHzActionHandler.setAdaptive(context)
                            currentMode.value = AdaptiveHzActionHandler.getCurrentMode(context)
                            Toast.makeText(context, toastAdaptiveApplied, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(
                                context,
                                toastSecureSettingsMissing,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onMinimumClick = {
                        try {
                            AdaptiveHzActionHandler.setMinimum(context)
                            currentMode.value = AdaptiveHzActionHandler.getCurrentMode(context)
                            Toast.makeText(context, toastMinimumApplied, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(
                                context,
                                toastSecureSettingsMissing,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onMaximumClick = {
                        try {
                            AdaptiveHzActionHandler.setMaximum(context)
                            currentMode.value = AdaptiveHzActionHandler.getCurrentMode(context)
                            Toast.makeText(context, toastMaximumApplied, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(
                                context,
                                toastSecureSettingsMissing,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }
}

// Small helper used to present copyable-looking command text
@Composable
private fun CodeLine(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RecoveryCard(
    onRetry: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReportProblem,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )

                Text(
                    text = stringResource(id = R.string.recovery_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            Text(
                text = stringResource(id = R.string.recovery_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.retry))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.setup_accessibility_button))
                    }

                    OutlinedButton(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.open_battery_settings))
                    }
                }
            }
        }
    }
}