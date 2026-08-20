package com.mahmutalperenunal.adaptivehz.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.apps.InstalledAppInfo
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main dashboard content for current state, quick actions and per-app shortcuts.
 */
@Composable
fun DashboardComponent(
    appEnabled: Boolean,
    accessibilityWorking: Boolean,
    accessibilityBroken: Boolean,
    vendorLabel: String,
    currentMode: AdaptiveHzMode,
    currentModeLabel: String,
    targetLabel: String,
    interactionLabel: String,
    usagePermissionGranted: Boolean,
    onGrantUsageAccessClick: () -> Unit,
    recentApps: List<InstalledAppInfo>,
    onPerAppClick: () -> Unit,
    onAppProfileClick: (InstalledAppInfo) -> Unit,
    onAppEnabledChange: (Boolean) -> Unit,
    onAdaptiveClick: () -> Unit,
    onMinimumClick: () -> Unit,
    onMaximumClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (appEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
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
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (appEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = if (appEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(28.dp)
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
                        text = if (appEnabled) {
                            stringResource(id = R.string.adaptive_hz_enabled)
                        } else {
                            stringResource(id = R.string.settings_enable_adaptive_hz)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$currentModeLabel · $targetLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (appEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Switch(
                    checked = appEnabled,
                    onCheckedChange = onAppEnabledChange,
                    thumbContent = null,
                    colors = SwitchDefaults.colors()
                )
            }

            if (appEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        icon = Icons.Outlined.Bolt,
                        text = interactionLabel,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(
                        icon = Icons.Outlined.Factory,
                        text = vendorLabel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(id = R.string.status_mode),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeButton(
                text = stringResource(id = R.string.mode_adaptive),
                icon = Icons.Outlined.Speed,
                selected = currentMode == AdaptiveHzMode.ADAPTIVE,
                enabled = appEnabled && accessibilityWorking,
                onClick = onAdaptiveClick,
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                text = stringResource(id = R.string.minimum),
                icon = Icons.Outlined.Eco,
                selected = currentMode == AdaptiveHzMode.FORCE_MIN,
                enabled = appEnabled,
                onClick = onMinimumClick,
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                text = stringResource(id = R.string.maximum),
                icon = Icons.Outlined.Bolt,
                selected = currentMode == AdaptiveHzMode.FORCE_MAX,
                enabled = appEnabled,
                onClick = onMaximumClick,
                modifier = Modifier.weight(1f)
            )
        }

        if (accessibilityBroken) {
            Text(
                text = stringResource(id = R.string.accessibility_broken_adaptive_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

    }

    Spacer(modifier = Modifier.height(20.dp))

    // Shows recent app profiles when Usage Access is available.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (appEnabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
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
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.GridView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.per_app_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.per_app_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!usagePermissionGranted) {
                OutlinedButton(
                    onClick = onGrantUsageAccessClick,
                    enabled = appEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.enable_recent_apps_access))
                }
            } else {
                if (recentApps.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.no_recent_apps_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    recentApps.forEach { app ->
                        DashboardAppProfileRow(
                            app = app,
                            enabled = appEnabled,
                            onClick = {
                                if (appEnabled) {
                                    onAppProfileClick(app)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable(enabled = appEnabled, onClick = onPerAppClick)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.view_all_apps),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (appEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun DashboardAppProfileRow(
    app: InstalledAppInfo,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashboardAppIconImage(
            packageName = app.packageName,
            modifier = Modifier.size(44.dp)
        )

        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        ModePill(
            text = profileModeLabel(app.profileMode),
            enabled = enabled,
            selected = app.profileMode != AppRefreshProfileMode.DEFAULT,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}



/**
 * Loads app icons off the main thread and falls back to a generic icon.
 */
@Composable
private fun DashboardAppIconImage(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext

    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = packageName
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = 48, height = 48)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    if (iconBitmap != null) {
        Image(
            painter = BitmapPainter(iconBitmap!!),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ModePill(
    text: String,
    enabled: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}


/**
 * Maps stored profile modes to localized labels.
 */
@Composable
private fun profileModeLabel(mode: AppRefreshProfileMode): String {
    return when (mode) {
        AppRefreshProfileMode.DEFAULT -> stringResource(id = R.string.profile_mode_default)
        AppRefreshProfileMode.SYSTEM_CONTROLLED -> stringResource(id = R.string.profile_mode_respect)
        AppRefreshProfileMode.FORCE_MIN -> stringResource(id = R.string.profile_mode_minimum)
        AppRefreshProfileMode.FORCE_MAX -> stringResource(id = R.string.profile_mode_maximum)
    }
}
