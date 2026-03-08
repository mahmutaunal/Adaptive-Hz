package com.mahmutalperenunal.adaptivehz.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahmutalperenunal.adaptivehz.R

// Main dashboard section showing current refresh-rate state and quick actions
@Composable
fun DashboardComponent(
    appEnabled: Boolean,
    currentDisplayHz: Int,
    vendorLabel: String,
    currentModeLabel: String,
    targetLabel: String,
    interactionLabel: String,
    onAppEnabledChange: (Boolean) -> Unit,
    onAdaptiveClick: () -> Unit,
    onMinimumClick: () -> Unit,
    onMaximumClick: () -> Unit
) {
    // Cached labels used multiple times in the status card
    val labelTarget = stringResource(id = R.string.status_target)
    val labelInteraction = stringResource(id = R.string.status_interaction)

    Spacer(modifier = Modifier.height(16.dp))

    // Status summary card with the app toggle and current refresh-rate information
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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

                // Master switch for enabling or disabling Adaptive Hz behavior
                Switch(
                    checked = appEnabled,
                    onCheckedChange = onAppEnabledChange,
                    thumbContent = null,
                    colors = SwitchDefaults.colors()
                )
            }

            Text(
                text = stringResource(id = R.string.settings_enable_adaptive_hz_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )

            StatusLine(
                label = stringResource(id = R.string.status_current_display),
                value = "$currentDisplayHz Hz"
            )
            StatusLine(
                label = stringResource(id = R.string.status_vendor),
                value = vendorLabel
            )
            StatusLine(
                label = stringResource(id = R.string.status_mode),
                value = currentModeLabel
            )
            StatusLine(
                label = labelTarget,
                value = targetLabel
            )
            StatusLine(
                label = labelInteraction,
                value = interactionLabel
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Quick mode actions shown below the status summary
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Default action to switch back to adaptive mode
        FilledTonalButton(
            onClick = onAdaptiveClick,
            enabled = appEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.mode_adaptive))
        }

        // Preset actions for locking the refresh rate to min or max
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onMinimumClick,
                enabled = appEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(id = R.string.minimum))
            }

            OutlinedButton(
                onClick = onMaximumClick,
                enabled = appEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(id = R.string.maximum))
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Informational card for the upcoming per-app refresh-rate feature
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (appEnabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
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

            // Reminds the user that the main feature must be enabled first
            if (!appEnabled) {
                Text(
                    text = stringResource(id = R.string.settings_enable_adaptive_hz_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    Spacer(modifier = Modifier.height(12.dp))
}

// Reusable row used for a single label/value pair in the status section
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