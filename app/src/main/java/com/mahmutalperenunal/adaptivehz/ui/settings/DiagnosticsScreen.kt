package com.mahmutalperenunal.adaptivehz.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mahmutalperenunal.adaptivehz.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahmutalperenunal.adaptivehz.BuildConfig
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzRuntimeState
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug screen exposing runtime state, engine activity and system diagnostics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val clipboardLabel = stringResource(id = R.string.adaptive_hz_diagnostics_clip_label)
    val diagnosticsCopiedMessage = stringResource(id = R.string.diagnostics_copied)

    var refreshTick by remember { mutableIntStateOf(0) }

    // Refreshes controller state only when the user explicitly requests it.
    val status = remember(refreshTick) {
        RefreshRateController.readStatus(appContext)
    }

    val accessibilityState = remember(refreshTick) {
        AdaptiveHzRuntimeState.getAccessibilityState(appContext)
    }

    val powerSaveMode = remember(refreshTick) {
        runCatching {
            val pm = appContext.getSystemService(PowerManager::class.java)
            pm?.isPowerSaveMode == true
        }.getOrDefault(false)
    }

    val lastHeartbeat = AdaptiveHzPrefs.getAccessibilityLastHeartbeat(appContext)
    val debugUpdatedAt = AdaptiveHzPrefs.getDebugLastUpdatedAt(appContext)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(14.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .padding(start = 14.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = stringResource(id = R.string.diagnostics_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = stringResource(id = R.string.diagnostics_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                val report = buildDiagnosticsReport(
                                    context = appContext,
                                    status = status,
                                    accessibilityState = accessibilityState.name,
                                    powerSaveMode = powerSaveMode,
                                    lastHeartbeat = lastHeartbeat,
                                    debugUpdatedAt = debugUpdatedAt
                                )

                                val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        clipboardLabel,
                                        report
                                    )
                                )

                                Toast.makeText(
                                    appContext,
                                    diagnosticsCopiedMessage,
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null
                            )

                            Text(
                                text = stringResource(id = R.string.copy_report),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        TextButton(
                            onClick = { refreshTick++ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null
                            )

                            Text(
                                text = stringResource(id = R.string.refresh),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            ElevatedCard(
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(id = R.string.diagnostics_system_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    DebugRow(stringResource(id = R.string.diagnostics_vendor), status.vendor.toString())
                    DebugRow(
                        stringResource(id = R.string.diagnostics_current_display),
                        stringResource(id = R.string.refresh_rate_hz, status.displayHz.toInt())
                    )
                    DebugRow(stringResource(id = R.string.diagnostics_setting_key), status.selectedWritePath)
                    DebugRow(stringResource(id = R.string.diagnostics_setting_value), status.selectedValue)
                    DebugRow(stringResource(id = R.string.diagnostics_battery_saver), powerSaveMode.toString())
                }
            }

            ElevatedCard(
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    DebugRow(stringResource(id = R.string.diagnostics_current_mode), AdaptiveHzPrefs.getCurrentMode(appContext).name)
                    DebugRow(stringResource(id = R.string.diagnostics_adb_granted), AdaptiveHzPrefs.isAdbGranted(appContext).toString())
                    DebugRow(stringResource(id = R.string.diagnostics_keep_alive), AdaptiveHzPrefs.isKeepAliveEnabled(appContext).toString())
                    DebugRow(stringResource(id = R.string.diagnostics_accessibility), accessibilityState.name)
                    DebugRow(stringResource(id = R.string.diagnostics_last_heartbeat), formatTime(appContext, lastHeartbeat))
                }
            }

            ElevatedCard(
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(id = R.string.diagnostics_engine_debug_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    DebugRow(
                        stringResource(id = R.string.diagnostics_foreground_package),
                        AdaptiveHzPrefs.getDebugForegroundPackage(appContext).ifBlank {
                            stringResource(id = R.string.empty_value_dash)
                        }
                    )
                    DebugRow(
                        stringResource(id = R.string.diagnostics_last_event),
                        AdaptiveHzPrefs.getDebugLastEvent(appContext).ifBlank {
                            stringResource(id = R.string.empty_value_dash)
                        }
                    )
                    DebugRow(
                        stringResource(id = R.string.diagnostics_last_write),
                        AdaptiveHzPrefs.getDebugLastWrite(appContext).ifBlank {
                            stringResource(id = R.string.empty_value_dash)
                        }
                    )
                    DebugRow(stringResource(id = R.string.diagnostics_last_write_success), AdaptiveHzPrefs.wasDebugLastWriteSuccess(appContext).toString())
                    DebugRow(stringResource(id = R.string.diagnostics_updated_at), formatTime(appContext, debugUpdatedAt))
                }
            }
        }
    }
}

/**
 * Compact key-value row used across diagnostics sections.
 */
@Composable
private fun DebugRow(
    label: String,
    value: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.42f)
            )

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.weight(0.58f)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Formats timestamps for human-readable diagnostics output.
 */
private fun formatTime(context: Context, value: Long): String {
    val appContext = context.applicationContext
    if (value <= 0L) return appContext.getString(R.string.empty_value_dash)
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(value))
}

/**
 * Builds a shareable plain-text diagnostics report.
 */
private fun buildDiagnosticsReport(
    context: Context,
    status: RefreshRateController.Status,
    accessibilityState: String,
    powerSaveMode: Boolean,
    lastHeartbeat: Long,
    debugUpdatedAt: Long
): String {
    val appContext = context.applicationContext
    return buildString {
        appendLine(appContext.getString(R.string.diagnostics_report_title))
        appendLine(appContext.getString(R.string.diagnostics_report_separator))
        appendLine(
            appContext.getString(
                R.string.diagnostics_report_app_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
        )
        appendLine(appContext.getString(R.string.diagnostics_report_vendor, status.vendor.toString()))
        appendLine(
            appContext.getString(
                R.string.diagnostics_report_current_display,
                status.displayHz.toInt()
            )
        )
        appendLine(appContext.getString(R.string.diagnostics_report_setting_key, status.selectedWritePath))
        appendLine(appContext.getString(R.string.diagnostics_report_setting_value, status.selectedValue))
        appendLine(appContext.getString(R.string.diagnostics_report_battery_saver, powerSaveMode.toString()))
        appendLine()
        appendLine(appContext.getString(R.string.diagnostics_report_current_mode, AdaptiveHzPrefs.getCurrentMode(appContext).name))
        appendLine(appContext.getString(R.string.diagnostics_report_adb_granted, AdaptiveHzPrefs.isAdbGranted(appContext).toString()))
        appendLine(appContext.getString(R.string.diagnostics_report_keep_alive, AdaptiveHzPrefs.isKeepAliveEnabled(appContext).toString()))
        appendLine(appContext.getString(R.string.diagnostics_report_accessibility, accessibilityState))
        appendLine(appContext.getString(R.string.diagnostics_report_last_heartbeat, formatTime(appContext, lastHeartbeat)))
        appendLine()
        appendLine(
            appContext.getString(
                R.string.diagnostics_report_foreground_package,
                AdaptiveHzPrefs.getDebugForegroundPackage(appContext).ifBlank {
                    appContext.getString(R.string.empty_value_dash)
                }
            )
        )
        appendLine(
            appContext.getString(
                R.string.diagnostics_report_last_event,
                AdaptiveHzPrefs.getDebugLastEvent(appContext).ifBlank {
                    appContext.getString(R.string.empty_value_dash)
                }
            )
        )
        appendLine(
            appContext.getString(
                R.string.diagnostics_report_last_write,
                AdaptiveHzPrefs.getDebugLastWrite(appContext).ifBlank {
                    appContext.getString(R.string.empty_value_dash)
                }
            )
        )
        appendLine(appContext.getString(R.string.diagnostics_report_last_write_success, AdaptiveHzPrefs.wasDebugLastWriteSuccess(appContext).toString()))
        appendLine(appContext.getString(R.string.diagnostics_report_debug_updated_at, formatTime(appContext, debugUpdatedAt)))
    }
}