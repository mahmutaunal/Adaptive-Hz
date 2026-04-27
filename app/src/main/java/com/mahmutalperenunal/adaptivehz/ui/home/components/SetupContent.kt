package com.mahmutalperenunal.adaptivehz.ui.home.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mahmutalperenunal.adaptivehz.R

// Setup flow shown before the main dashboard becomes available
@Composable
fun SetupComponent(
    accessibilityEnabled: Boolean,
    adbGranted: Boolean,
    batteryOptimizationsIgnored: Boolean,
    notificationsGranted: Boolean,
    keepAliveEnabled: Boolean,
    requiredSetupReady: Boolean,
    onContinue: () -> Unit,
    labelOn: String,
    labelOff: String,
    labelGranted: String,
    labelRequired: String,
    toastStabilityEnabled: String,
    toastStabilityDisabled: String,
    toastOpenNotificationSettingsFailed: String,
    rootAvailable: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onVerifyAdb: () -> Unit,
    onGrantWithRoot: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSetKeepAliveEnabled: (Boolean) -> Unit,
    secondaryAdbContent: @Composable () -> Unit
) {
    // Local context is used for launching intents and showing short feedback toasts
    val context = LocalContext.current

    // Introductory text explaining why the setup steps are needed
    Text(
        text = stringResource(id = R.string.setup_description),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Required accessibility permission for detecting interaction state
    SetupCard(
        title = stringResource(id = R.string.setup_accessibility_title),
        description = stringResource(id = R.string.setup_accessibility_desc),
        ok = accessibilityEnabled,
        primaryButtonText = stringResource(id = R.string.setup_accessibility_button),
        onPrimaryClick = onOpenAccessibilitySettings
    )

    Spacer(modifier = Modifier.height(16.dp))

    // One-time ADB step required to grant privileged behavior
    SetupCard(
        title = stringResource(id = R.string.setup_adb_title),
        description = if (rootAvailable) {
            stringResource(id = R.string.setup_adb_desc_with_root)
        } else {
            stringResource(id = R.string.setup_adb_desc)
        },
        ok = adbGranted,
        primaryButtonText = stringResource(id = R.string.setup_adb_verify),
        onPrimaryClick = onVerifyAdb,
        secondaryContent = {
            secondaryAdbContent()

            if (!adbGranted && rootAvailable) {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(id = R.string.setup_root_detected_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onGrantWithRoot,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(stringResource(id = R.string.setup_root_grant_button))
                }
            }
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Recommended battery setting to reduce the chance of background interruptions
    SetupCard(
        title = stringResource(id = R.string.setup_battery_title),
        description = stringResource(id = R.string.setup_battery_desc),
        ok = batteryOptimizationsIgnored,
        primaryButtonText = stringResource(id = R.string.open_battery_settings),
        onPrimaryClick = onRequestIgnoreBatteryOptimizations,
        okLabelOverride = if (batteryOptimizationsIgnored) {
            stringResource(id = R.string.label_ok)
        } else {
            stringResource(id = R.string.label_recommended)
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Extra stability options, including keep-alive behavior and notification access guidance
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
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

            // Notification status is only relevant on Android 13 and above
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Toggles the optional keep-alive behavior after checking notification requirements
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted && !keepAliveEnabled) {
                            onRequestNotificationPermission()
                            return@OutlinedButton
                        }

                        val next = !keepAliveEnabled
                        onSetKeepAliveEnabled(next)

                        if (next) {
                            Toast.makeText(
                                context,
                                toastStabilityEnabled,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                toastStabilityDisabled,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
                ) {
                    val buttonText =
                        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted && !keepAliveEnabled) {
                            "${stringResource(id = R.string.enable)} (${stringResource(id = R.string.label_required)})"
                        } else {
                            if (keepAliveEnabled) {
                                stringResource(id = R.string.disable)
                            } else {
                                stringResource(id = R.string.enable)
                            }
                        }

                    Text(buttonText)
                }

                if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
                    // Shortcut to the app's notification settings screen when permission is still missing
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    toastOpenNotificationSettingsFailed,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(stringResource(id = R.string.setup_notifications_button))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                FilledTonalButton(
                    onClick = onContinue,
                    enabled = requiredSetupReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.setup_continue_button))
                }

                if (!requiredSetupReady) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(id = R.string.setup_continue_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// Reusable card used for each individual setup requirement
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
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = okLabelOverride
                        ?: if (ok) stringResource(id = R.string.label_ok) else stringResource(id = R.string.label_missing),
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

            // Optional extra content such as ADB instructions or helper UI
            secondaryContent?.invoke()

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onPrimaryClick,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(primaryButtonText)
                }
            }
        }
    }
}