package com.mahmutalperenunal.adaptivehz.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mahmutalperenunal.adaptivehz.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahmutalperenunal.adaptivehz.core.debug.DebugAccessibilityEvent
import com.mahmutalperenunal.adaptivehz.core.debug.DebugEventStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Debug screen for inspecting recent accessibility events and scroll signals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityEventInspectorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val clipboardLabel = stringResource(id = R.string.adaptive_hz_events_clip_label)
    val eventsCopiedMessage = stringResource(id = R.string.events_copied)

    var refreshTick by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var showOnlyScrollEvents by remember { mutableStateOf(false) }

    // Polls the in-memory event store while live mode is active.
    LaunchedEffect(paused) {
        while (!paused) {
            refreshTick++
            delay(1000L)
        }
    }

    // Applies the current debug filter without mutating stored events.
    val events = remember(refreshTick, showOnlyScrollEvents, paused) {
        val all = DebugEventStore.getAll()
        if (showOnlyScrollEvents) {
            all.filter {
                it.eventType.contains("SCROLLED") ||
                        it.scrollDeltaX != 0 ||
                        it.scrollDeltaY != 0
            }
        } else {
            all
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.event_inspector_title)) },
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
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                                modifier = Modifier.padding(14.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .padding(start = 14.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = stringResource(id = R.string.accessibility_event_inspector_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = stringResource(id = R.string.accessibility_event_inspector_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = paused,
                            onClick = {
                                paused = !paused
                                refreshTick++
                            },
                            label = {
                                Text(
                                    text = if (paused) {
                                        stringResource(id = R.string.paused)
                                    } else {
                                        stringResource(id = R.string.live)
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (paused) {
                                        Icons.Outlined.PlayArrow
                                    } else {
                                        Icons.Outlined.Pause
                                    },
                                    contentDescription = null
                                )
                            }
                        )

                        FilterChip(
                            selected = showOnlyScrollEvents,
                            onClick = {
                                showOnlyScrollEvents = !showOnlyScrollEvents
                                refreshTick++
                            },
                            label = {
                                Text(text = stringResource(id = R.string.scroll_only))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = null
                                )
                            }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                val report = buildEventReport(context, events)
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        clipboardLabel,
                                        report
                                    )
                                )

                                Toast.makeText(
                                    context,
                                    eventsCopiedMessage,
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
                                text = stringResource(id = R.string.copy),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        TextButton(
                            onClick = {
                                DebugEventStore.clear()
                                refreshTick++
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null
                            )

                            Text(
                                text = stringResource(id = R.string.clear),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(id = R.string.events_captured, events.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (events.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.no_events_captured_yet),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = events,
                        key = { index, event ->
                            "${event.timestamp}_${event.packageName}_${event.eventType}_$index"
                        }
                    ) { _, event ->
                        EventCard(event = event)
                    }
                }
            }
        }
    }
}

/**
 * Renders a captured accessibility event with compact debug metadata.
 */
@Composable
private fun EventCard(
    event: DebugAccessibilityEvent
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (
                            event.scrollDeltaX != 0 || event.scrollDeltaY != 0
                        ) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                ) {
                    Text(
                        text = event.eventType,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Text(
                    text = formatEventTime(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = event.packageName.ifBlank { "-" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EventValueChip(
                    label = stringResource(id = R.string.event_scroll_delta_x),
                    value = event.scrollDeltaX.toString()
                )

                EventValueChip(
                    label = stringResource(id = R.string.event_scroll_delta_y),
                    value = event.scrollDeltaY.toString()
                )
            }

            EventValueChip(
                label = stringResource(id = R.string.event_content_change),
                value = event.contentChangeTypes.toString()
            )
        }
    }
}

/**
 * Small label-value chip used for event metadata.
 */
@Composable
private fun EventValueChip(
    label: String,
    value: String
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Builds a plain-text report that can be copied for issue reports.
 */
private fun buildEventReport(
    context: Context,
    events: List<DebugAccessibilityEvent>
): String {
    return buildString {
        appendLine(context.getString(R.string.accessibility_event_report_title))
        appendLine(context.getString(R.string.accessibility_event_report_separator))
        events.forEach { event ->
            appendLine(
                context.getString(
                    R.string.accessibility_event_report_row,
                    formatEventTime(event.timestamp),
                    event.eventType,
                    event.packageName.ifBlank { context.getString(R.string.empty_value_dash) },
                    event.scrollDeltaX,
                    event.scrollDeltaY,
                    event.contentChangeTypes
                )
            )
        }
    }
}

/**
 * Formats event timestamps with millisecond precision for debugging.
 */
private fun formatEventTime(timestamp: Long): String {
    return SimpleDateFormat(
        "HH:mm:ss.SSS",
        Locale.getDefault()
    ).format(Date(timestamp))
}