package com.mahmutalperenunal.adaptivehz.ui.home.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mahmutalperenunal.adaptivehz.R

/**
 * Setup flow shown before the main dashboard becomes available.
 */
@Composable
fun SetupComponent(
    accessibilityEnabled: Boolean,
    adbGranted: Boolean,
    batteryOptimizationsIgnored: Boolean,
    notificationsGranted: Boolean,
    usageAccessGranted: Boolean,
    keepAliveEnabled: Boolean,
    isXiaomiDevice: Boolean,
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
    onOpenUsageAccessSettings: () -> Unit,
    onSetKeepAliveEnabled: (Boolean) -> Unit
) {
    val context = LocalContext.current.applicationContext

    SetupHeroCard(
        title = stringResource(id = R.string.setup_title),
        description = stringResource(id = R.string.setup_description)
    )

    Spacer(modifier = Modifier.height(16.dp))

    SetupCard(
        icon = Icons.Outlined.Accessibility,
        title = stringResource(id = R.string.setup_accessibility_title),
        description = stringResource(id = R.string.setup_accessibility_desc),
        ok = accessibilityEnabled,
        primaryButtonText = stringResource(id = R.string.setup_accessibility_button),
        onPrimaryClick = onOpenAccessibilitySettings,
        successMessage = stringResource(id = R.string.setup_accessibility_enabled_message)
    )

    Spacer(modifier = Modifier.height(16.dp))

    SetupCard(
        icon = Icons.Outlined.Terminal,
        title = stringResource(id = R.string.setup_adb_title),
        description = if (rootAvailable) {
            stringResource(id = R.string.setup_adb_desc_with_root)
        } else {
            stringResource(id = R.string.setup_adb_desc)
        },
        ok = adbGranted,
        primaryButtonText = stringResource(id = R.string.setup_adb_verify),
        onPrimaryClick = onVerifyAdb,
        successMessage = stringResource(id = R.string.setup_adb_granted_message),
        secondaryContent = {
            if (!adbGranted && rootAvailable) {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(id = R.string.setup_root_detected_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Shows the equivalent manual ADB command for transparency.
                CodeBlock(
                    label = stringResource(id = R.string.setup_adb_instruction),
                    text = "adb shell pm grant com.mahmutalperenunal.adaptivehz android.permission.WRITE_SECURE_SETTINGS"
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
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.height(0.dp))
                    Text(stringResource(id = R.string.setup_root_grant_button))
                }
            }
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    SetupCard(
        icon = Icons.Outlined.BatterySaver,
        title = stringResource(id = R.string.setup_battery_title),
        description = stringResource(id = R.string.setup_battery_desc),
        ok = batteryOptimizationsIgnored,
        primaryButtonText = stringResource(id = R.string.open_battery_settings),
        onPrimaryClick = onRequestIgnoreBatteryOptimizations,
        okLabelOverride = if (batteryOptimizationsIgnored) {
            stringResource(id = R.string.label_ok)
        } else {
            stringResource(id = R.string.label_recommended)
        },
        successMessage = stringResource(id = R.string.setup_battery_ok_message)
    )

    Spacer(modifier = Modifier.height(16.dp))

    SetupCard(
        icon = Icons.Outlined.History,
        title = stringResource(id = R.string.setup_usage_access_title),
        description = stringResource(id = R.string.setup_usage_access_desc),
        ok = usageAccessGranted,
        primaryButtonText = stringResource(id = R.string.setup_usage_access_button),
        onPrimaryClick = onOpenUsageAccessSettings,
        okLabelOverride = if (usageAccessGranted) {
            stringResource(id = R.string.label_ok)
        } else {
            stringResource(id = R.string.label_recommended)
        },
        successMessage = stringResource(id = R.string.setup_usage_access_ok_message)
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Keep-alive setup depends on notification permission on Android 13+.
    SetupSwitchCard(
        icon = Icons.Outlined.Notifications,
        title = stringResource(id = R.string.setup_stability_title),
        description = stringResource(id = R.string.setup_stability_desc),
        checked = keepAliveEnabled,
        statusLabel = if (keepAliveEnabled) labelOn else labelOff,
        notificationStatus = if (Build.VERSION.SDK_INT >= 33) {
            stringResource(
                id = R.string.label_notifications_status,
                if (notificationsGranted) labelGranted else labelRequired
            )
        } else {
            null
        },
        actionText = if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted && !keepAliveEnabled) {
            "${stringResource(id = R.string.enable)} (${stringResource(id = R.string.label_required)})"
        } else {
            if (keepAliveEnabled) {
                stringResource(id = R.string.disable)
            } else {
                stringResource(id = R.string.enable)
            }
        },
        showNotificationSettingsButton = Build.VERSION.SDK_INT >= 33 && !notificationsGranted,
        onActionClick = {
            if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted && !keepAliveEnabled) {
                onRequestNotificationPermission()
                return@SetupSwitchCard
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
        onNotificationSettingsClick = {
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
        }
    )

    if (isXiaomiDevice) {
        Spacer(modifier = Modifier.height(16.dp))

        XiaomiStabilityGuideCard(
            onOpenBatterySettings = onRequestIgnoreBatteryOptimizations
        )
    }
}

/**
 * Reusable card for a single setup requirement.
 */
@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    description: String,
    ok: Boolean,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    okLabelOverride: String? = null,
    successMessage: String? = null,
    secondaryContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (ok) {
                    IconButton(onClick = onPrimaryClick) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(id = R.string.retry),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    StatusPill(
                        text = okLabelOverride ?: stringResource(id = R.string.label_missing),
                        positive = false
                    )
                }
            }

            if (ok && successMessage != null) {
                Text(
                    text = successMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.95f),
                    modifier = Modifier.padding(start = 62.dp)
                )
            }

            secondaryContent?.invoke()

            if (!ok) {
                OutlinedButton(
                    onClick = onPrimaryClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
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

@Composable
private fun SetupHeroCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    positive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (positive) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (positive) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

/**
 * Displays command snippets in a readable block.
 */
@Composable
private fun CodeBlock(
    label: String,
    text: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Setup card variant for toggleable stability features.
 */
@Composable
private fun SetupSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    statusLabel: String,
    notificationStatus: String?,
    actionText: String,
    showNotificationSettingsButton: Boolean,
    onActionClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusPill(
                    text = statusLabel,
                    positive = checked
                )
            }

            notificationStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 62.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onActionClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(actionText)
                }

                if (showNotificationSettingsButton) {
                    OutlinedButton(
                        onClick = onNotificationSettingsClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(stringResource(id = R.string.setup_notifications_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun XiaomiStabilityGuideCard(
    onOpenBatterySettings: () -> Unit
) {
    SetupCard(
        icon = Icons.Outlined.BatterySaver,
        title = stringResource(id = R.string.xiaomi_stability_title),
        description = stringResource(id = R.string.xiaomi_stability_desc),
        ok = false,
        primaryButtonText = stringResource(id = R.string.open_battery_settings),
        onPrimaryClick = onOpenBatterySettings,
        okLabelOverride = stringResource(id = R.string.label_recommended),
        secondaryContent = {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.xiaomi_stability_steps_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.xiaomi_stability_steps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}