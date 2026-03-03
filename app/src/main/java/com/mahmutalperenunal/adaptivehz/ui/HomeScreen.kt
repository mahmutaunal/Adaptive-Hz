package com.mahmutalperenunal.adaptivehz.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.StabilityForegroundService
import kotlinx.coroutines.delay
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import com.mahmutalperenunal.adaptivehz.R

@Composable
fun HomeScreen(
    isAdaptiveServiceEnabled: () -> Boolean,
    openAccessibilitySettings: () -> Unit,
    requestIgnoreBatteryOptimizations: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("adaptive_hz_prefs", Context.MODE_PRIVATE) }

    val toastAdbVerified = stringResource(id = R.string.toast_adb_verified)
    val toastAdbPermissionMissing = stringResource(id = R.string.toast_adb_permission_missing)
    val toastStabilityEnabled = stringResource(id = R.string.toast_stability_enabled)
    val toastStabilityDisabled = stringResource(id = R.string.toast_stability_disabled)
    val toastOpenNotificationSettingsFailed = stringResource(id = R.string.toast_open_notification_settings_failed)
    val toastAdaptiveApplied = stringResource(id = R.string.toast_adaptive_applied)
    val toastMinimumApplied = stringResource(id = R.string.toast_minimum_applied)
    val toastMaximumApplied = stringResource(id = R.string.toast_maximum_applied)
    val toastSecureSettingsMissing = stringResource(id = R.string.toast_secure_settings_missing)
    val labelOn = stringResource(id = R.string.label_on)
    val labelOff = stringResource(id = R.string.label_off)
    val labelGranted = stringResource(id = R.string.label_granted)
    val labelRequired = stringResource(id = R.string.label_required)

    val scrollState = rememberScrollState()

    // Persistent flags (best-effort)
    var adbGranted by remember { mutableStateOf(prefs.getBoolean("adb_granted", false)) }
    var keepAliveEnabled by remember { mutableStateOf(prefs.getBoolean("keep_alive_enabled", false)) }
    var dynamicEnabled by remember { mutableStateOf(prefs.getBoolean("dynamic_enabled", false)) }

    // Live checks
    var accessibilityEnabled by remember { mutableStateOf(isAdaptiveServiceEnabled()) }
    var status by remember { mutableStateOf(RefreshRateController.readStatus(context)) }

    var batteryOptimizationsIgnored by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(true) }

    val refreshBatteryState: () -> Unit = {
        batteryOptimizationsIgnored = try {
            val pm = context.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (_: Exception) {
            false
        }
    }

    val refreshNotificationState: () -> Unit = {
        notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Android 13+ notification permission helper
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        // If the user granted permission, and they already asked to enable stability,
        // they can now enable it using the same button.
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Refresh live status/checks periodically
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = isAdaptiveServiceEnabled()
            dynamicEnabled = prefs.getBoolean("dynamic_enabled", false)
            keepAliveEnabled = prefs.getBoolean("keep_alive_enabled", false)
            refreshBatteryState()
            refreshNotificationState()
            status = RefreshRateController.readStatus(context)
            delay(500)
        }
    }

    LaunchedEffect(Unit) {
        refreshBatteryState()
        refreshNotificationState()
    }

    val setupComplete = accessibilityEnabled && adbGranted && batteryOptimizationsIgnored && keepAliveEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Fixed Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.home_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider()
        }

        // Main content (centered)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .widthIn(max = 520.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        // -------------------------
        // SETUP (shown until complete)
        // -------------------------
        if (!setupComplete) {
            Text(
                text = stringResource(id = R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 1) Accessibility
            SetupCard(
                title = stringResource(id = R.string.setup_accessibility_title),
                description = stringResource(id = R.string.setup_accessibility_desc),
                ok = accessibilityEnabled,
                primaryButtonText = stringResource(id = R.string.setup_accessibility_button),
                onPrimaryClick = openAccessibilitySettings
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2) ADB / WRITE_SECURE_SETTINGS
            SetupCard(
                title = stringResource(id = R.string.setup_adb_title),
                description = stringResource(id = R.string.setup_adb_desc),
                ok = adbGranted,
                primaryButtonText = stringResource(id = R.string.setup_adb_verify),
                onPrimaryClick = {
                    // Verify WRITE_SECURE_SETTINGS by performing a no-op write to Settings.Global.
                    // This avoids false positives from vendor paths that may work without the permission.
                    val permission = "android.permission.WRITE_SECURE_SETTINGS"

                    val verified = try {
                        // 1) Fast path: ask PackageManager whether the permission is granted.
                        val pmGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

                        // 2) Strong verification: attempt a no-op write (write back the same value).
                        val cr = context.contentResolver
                        val key = Settings.Global.ANIMATOR_DURATION_SCALE
                        val current = Settings.Global.getFloat(cr, key, 1f)
                        val wrote = Settings.Global.putFloat(cr, key, current)
                        val after = Settings.Global.getFloat(cr, key, 1f)

                        pmGranted || (wrote && after == current)
                    } catch (_: SecurityException) {
                        false
                    } catch (_: Exception) {
                        // Some devices may restrict access to this key even when the permission is granted.
                        // In that case, fall back to the PackageManager result.
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    }

                    if (verified) {
                        prefs.edit { putBoolean("adb_granted", true) }
                        adbGranted = true
                        Toast.makeText(context, toastAdbVerified, Toast.LENGTH_SHORT).show()
                    } else {
                        // Keep the persistent flag false to avoid breaking setup logic.
                        prefs.edit { putBoolean("adb_granted", false) }
                        adbGranted = false
                        Toast.makeText(context, toastAdbPermissionMissing, Toast.LENGTH_LONG).show()
                    }
                },
                secondaryContent = {
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

            Spacer(modifier = Modifier.height(16.dp))

            // 3) Battery optimizations
            SetupCard(
                title = stringResource(id = R.string.setup_battery_title),
                description = stringResource(id = R.string.setup_battery_desc),
                ok = batteryOptimizationsIgnored,
                primaryButtonText = stringResource(id = R.string.open_battery_settings),
                onPrimaryClick = {
                    requestIgnoreBatteryOptimizations()
                    // When user returns, the periodic refresh will update the badge.
                },
                okLabelOverride = if (batteryOptimizationsIgnored) {
                    stringResource(id = R.string.label_ok)
                } else {
                    stringResource(id = R.string.label_recommended)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4) Stability Mode toggle (Foreground service)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(id = R.string.setup_stability_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (keepAliveEnabled) labelOn else labelOff,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.setup_stability_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                    )

                    if (Build.VERSION.SDK_INT >= 33) {
                        Text(
                            text = stringResource(
                                id = R.string.label_notifications_status,
                                if (notificationsGranted) labelGranted else labelRequired
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                // On Android 13+, enabling Stability Mode requires notification permission.
                                if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted && !keepAliveEnabled) {
                                    requestNotificationPermission()
                                    return@OutlinedButton
                                }

                                val next = !keepAliveEnabled
                                prefs.edit { putBoolean("keep_alive_enabled", next) }
                                keepAliveEnabled = next

                                if (next) {
                                    StabilityForegroundService.start(context)
                                    Toast.makeText(context, toastStabilityEnabled, Toast.LENGTH_SHORT).show()
                                } else {
                                    StabilityForegroundService.stop(context)
                                    Toast.makeText(context, toastStabilityDisabled, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            val enableText = if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted && !keepAliveEnabled) {
                                // Make the dependency explicit so it doesn't look like "Enable" alone is enough.
                                "${stringResource(id = R.string.enable)} (${stringResource(id = R.string.label_required)})"
                            } else {
                                if (keepAliveEnabled) stringResource(id = R.string.disable) else stringResource(id = R.string.enable)
                            }
                            Text(enableText)
                        }

                        // Show the notification action only when it is actually needed (Android 13+ and missing permission).
                        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, toastOpenNotificationSettingsFailed, Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(id = R.string.setup_notifications_button))
                            }
                        }
                    }
                }
            }
        } else {
            // -------------------------
            // DASHBOARD (main area)
            // -------------------------
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.dashboard_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live status card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(id = R.string.dashboard_status_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }

                    StatusLine(label = stringResource(id = R.string.status_current_display), value = "${status.displayHz.toInt()} Hz")
                    StatusLine(label = stringResource(id = R.string.status_vendor), value = status.vendor.toString())
                    StatusLine(label = stringResource(id = R.string.status_mode), value = if (dynamicEnabled) stringResource(id = R.string.mode_adaptive) else stringResource(id = R.string.mode_manual))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Global controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        try {
                            prefs.edit {
                                putBoolean("dynamic_enabled", true)
                            }
                            dynamicEnabled = true

                            // Start at Minimum; service will boost to Maximum on interaction.
                            RefreshRateController.applyForceMinimum(context)

                            Toast.makeText(context, toastAdaptiveApplied, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(context, toastSecureSettingsMissing, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.mode_adaptive))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                prefs.edit { putBoolean("dynamic_enabled", false) }
                                dynamicEnabled = false
                                RefreshRateController.applyForceMinimum(context)
                                Toast.makeText(context, toastMinimumApplied, Toast.LENGTH_SHORT).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(context, toastSecureSettingsMissing, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(id = R.string.minimum)) }

                    OutlinedButton(
                        onClick = {
                            try {
                                prefs.edit { putBoolean("dynamic_enabled", false) }
                                dynamicEnabled = false
                                RefreshRateController.applyForceMaximum(context)
                                Toast.makeText(context, toastMaximumApplied, Toast.LENGTH_SHORT).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(context, toastSecureSettingsMissing, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(id = R.string.maximum)) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Placeholder for future per-app controls (as you planned)
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(id = R.string.per_app_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.per_app_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                    Text(
                        text = stringResource(id = R.string.per_app_desc2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        }

        if (setupComplete) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Footer
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.footer_title),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(id = R.string.footer_desc),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/mahmutaunal".toUri()
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.footer_github))
                    }

                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://play.google.com/store/apps/dev?id=5245599652065968716".toUri()
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.footer_play))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SetupCard(
    title: String,
    description: String,
    ok: Boolean,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    okLabelOverride: String? = null,
    secondaryContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = okLabelOverride ?: if (ok) stringResource(id = R.string.label_ok) else stringResource(id = R.string.label_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )

            secondaryContent?.invoke()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPrimaryClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(primaryButtonText)
                }
            }
        }
    }
}

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