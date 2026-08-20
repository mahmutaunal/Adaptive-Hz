@file:Suppress("SameParameterValue")

package com.mahmutalperenunal.adaptivehz.ui.settings.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.quickaccess.QuickAccessManager

private enum class ManualHelp {
    QUICK_SETTINGS,
    WIDGET
}

/** Reusable quick-access setup content used by discovery and Settings. */
@Composable
fun QuickAccessOptions(
    modifier: Modifier = Modifier,
    onConfiguredStateChanged: (tileAdded: Boolean, widgetAdded: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    var tileAdded by remember {
        mutableStateOf(QuickAccessManager.isQuickSettingsTileAdded(appContext))
    }
    var widgetAdded by remember {
        mutableStateOf(QuickAccessManager.isWidgetAdded(appContext))
    }
    var manualHelp by remember { mutableStateOf<ManualHelp?>(null) }

    LaunchedEffect(tileAdded, widgetAdded) {
        onConfiguredStateChanged(tileAdded, widgetAdded)
    }

    fun refreshState() {
        tileAdded = QuickAccessManager.isQuickSettingsTileAdded(appContext)
        widgetAdded = QuickAccessManager.isWidgetAdded(appContext)
    }

    DisposableEffect(lifecycleOwner, appContext) {
        val stopTileObserver = AdaptiveHzPrefs.observeQuickSettingsTile(appContext) {
            tileAdded = it
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            stopTileObserver()
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickAccessOption(
            icon = Icons.Outlined.DashboardCustomize,
            title = stringResource(R.string.quick_access_tile_title),
            description = stringResource(R.string.quick_access_tile_description),
            added = tileAdded,
            onAdd = {
                QuickAccessManager.requestQuickSettingsTile(appContext) { result ->
                    when (result) {
                        QuickAccessManager.RequestResult.ADDED,
                        QuickAccessManager.RequestResult.ALREADY_ADDED -> tileAdded = true

                        QuickAccessManager.RequestResult.UNSUPPORTED,
                        QuickAccessManager.RequestResult.FAILED -> {
                            manualHelp = ManualHelp.QUICK_SETTINGS
                        }

                        QuickAccessManager.RequestResult.NOT_ADDED -> {
                            Toast.makeText(
                                appContext,
                                R.string.quick_access_request_dismissed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        QuickAccessManager.RequestResult.REQUESTED -> Unit
                    }
                }
            }
        )

        QuickAccessOption(
            icon = Icons.Outlined.Widgets,
            title = stringResource(R.string.quick_access_widget_title),
            description = stringResource(R.string.quick_access_widget_description),
            added = widgetAdded,
            onAdd = {
                QuickAccessManager.requestPinWidget(appContext) { result ->
                    when (result) {
                        QuickAccessManager.RequestResult.UNSUPPORTED,
                        QuickAccessManager.RequestResult.FAILED -> {
                            manualHelp = ManualHelp.WIDGET
                        }

                        QuickAccessManager.RequestResult.ADDED,
                        QuickAccessManager.RequestResult.ALREADY_ADDED -> widgetAdded = true

                        QuickAccessManager.RequestResult.NOT_ADDED -> {
                            Toast.makeText(
                                appContext,
                                R.string.quick_access_request_dismissed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        QuickAccessManager.RequestResult.REQUESTED -> Unit
                    }
                }
            }
        )
    }

    manualHelp?.let { help ->
        AlertDialog(
            onDismissRequest = { manualHelp = null },
            title = { Text(stringResource(R.string.quick_access_manual_title)) },
            text = {
                Text(
                    text = stringResource(R.string.quick_access_request_failed) +
                        "\n\n" + stringResource(
                            if (help == ManualHelp.QUICK_SETTINGS) {
                                R.string.quick_access_tile_manual_instructions
                            } else {
                                R.string.quick_access_widget_manual_instructions
                            }
                        )
                )
            },
            confirmButton = {
                TextButton(onClick = { manualHelp = null }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun QuickAccessOption(
    icon: ImageVector,
    title: String,
    description: String,
    added: Boolean,
    onAdd: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (added) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.quick_access_status_added),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.quick_access_action_add))
                }
            }
        }
    }
}

/** One-time post-setup discovery surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessDiscoverySheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var anyShortcutAdded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_access_discovery_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.quick_access_discovery_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            QuickAccessOptions(
                modifier = Modifier.fillMaxWidth(),
                onConfiguredStateChanged = { tileAdded, widgetAdded ->
                    anyShortcutAdded = tileAdded || widgetAdded
                }
            )

            if (anyShortcutAdded) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.quick_access_action_done))
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.quick_access_action_not_now))
                }
            }
        }
    }
}

/** A single, non-blocking support invitation shown only after meaningful use. */
@Composable
fun ProjectSupportCard(
    onStar: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.support_prompt_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.support_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onStar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_item_star_github))
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_item_share_app))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.support_prompt_not_now))
            }
        }
    }
}

/**
 * Header card displaying the app name and tagline.
 */
@Composable
internal fun SettingsHeroCard(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Section label used to group related settings items.
 */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp)
    )
}

/**
 * Reusable settings row with optional subtitle, trailing icon and click action.
 */
@Composable
internal fun SettingsRow(
    leading: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)?,
) {
    val clickable = onClick != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = leading,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = Int.MAX_VALUE
                )

                subtitle?.let {
                    Text(
                        text = it,
                        maxLines = subtitleMaxLines,
                        softWrap = subtitleMaxLines != 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            trailing?.let {
                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Reusable settings row for toggleable options.
 */
@Composable
internal fun SettingsSwitchRow(
    leading: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = leading,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = Int.MAX_VALUE
                )

                subtitle?.let {
                    Text(
                        text = it,
                        maxLines = Int.MAX_VALUE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors()
            )
        }
    }
}

@Composable
internal fun SettingsSliderRow(
    leading: ImageVector,
    title: String,
    subtitle: String,
    options: List<Long>,
    selectedValue: Long,
    onValueSelected: (Long) -> Unit,
) {
    val selectedIndex = options.indexOf(selectedValue)
        .takeIf { it >= 0 }
        ?: options.indexOf(
            AdaptiveHzPrefs.DEFAULT_INTERACTION_DROP_DELAY_MS
        ).coerceAtLeast(0)

    var sliderValue by remember(selectedIndex) {
        mutableFloatStateOf(selectedIndex.toFloat())
    }

    val activeIndex = sliderValue
        .toInt()
        .coerceIn(0, options.lastIndex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = leading,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..options.lastIndex.toFloat(),
                steps = (options.size - 2).coerceAtLeast(0),
                onValueChangeFinished = {
                    val normalizedIndex = sliderValue
                        .toInt()
                        .coerceIn(0, options.lastIndex)

                    onValueSelected(options[normalizedIndex])
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                options.forEachIndexed { index, option ->
                    val selected = index == activeIndex

                    Text(
                        text = if (selected) formatDropDelay(option) else "",
                        style = if (selected) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        },
                        fontWeight = if (selected) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                }
            }
        }
    }
}

internal fun formatDropDelay(delayMs: Long): String {
    return when {
        delayMs == 0L -> "Instant"
        delayMs < 1000L -> "${delayMs}ms"
        delayMs % 1000L == 0L -> "${delayMs / 1000L}s"
        else -> "${delayMs / 1000f}s"
    }
}

internal enum class UpdateCheckUiState {
    Idle,
    Checking,
    UpToDate,
    Failed,
    InvalidResponse,
}

@Composable
internal fun DialogOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Represents legal dialogs backed by string resources.
 */
@Immutable
internal sealed class LegalDialog(
    val titleRes: Int,
    val bodyRes: Int,
) {
    data object PrivacyPolicy : LegalDialog(
        R.string.settings_dialog_privacy_title,
        R.string.settings_dialog_privacy_body
    )

    data object OpenSourceNotices : LegalDialog(
        R.string.settings_dialog_notices_title,
        R.string.settings_dialog_notices_body
    )
}

/**
 * Opens a URL in the default browser.
 */
internal fun openUrl(context: Context, url: String) {
    val appContext = context.applicationContext
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        appContext.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(appContext, R.string.error, Toast.LENGTH_SHORT).show()
    }
}

/** Opens an app in Google Play, with a browser fallback when Play Store is unavailable. */
internal fun openPlayStoreApp(context: Context, packageName: String) {
    val appContext = context.applicationContext
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$packageName".toUri()
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    try {
        appContext.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        openUrl(appContext, "https://play.google.com/store/apps/details?id=$packageName")
    }
}

/**
 * Launches an email client with a prefilled recipient and subject.
 */
internal fun composeEmail(context: Context, email: String, subject: String) {
    val appContext = context.applicationContext
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$email".toUri()
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        appContext.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(appContext, R.string.error, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens the Android share sheet with plain text content.
 */
internal fun shareText(context: Context, title: String, text: String) {
    val appContext = context.applicationContext
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        appContext.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(appContext, R.string.error, Toast.LENGTH_SHORT).show()
    }
}

