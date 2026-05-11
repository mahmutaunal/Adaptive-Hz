@file:Suppress("SameParameterValue")

package com.mahmutalperenunal.adaptivehz.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.mahmutalperenunal.adaptivehz.BuildConfig
import com.mahmutalperenunal.adaptivehz.R
import androidx.core.net.toUri
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzRuntimeState

/**
 * Settings route for setup actions, diagnostics, support links and legal notices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    accessibilityState: AdaptiveHzRuntimeState.AccessibilityState,
    adbGranted: Boolean,
    usageAccessGranted: Boolean,
    rootAvailable: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onVerifyAdb: () -> Unit,
    onGrantWithRoot: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    keepAliveEnabled: Boolean,
    onKeepAliveChanged: (Boolean) -> Unit,
    batteryOptimizationsIgnored: Boolean,
    notificationsGranted: Boolean,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenEventInspector: () -> Unit,
    modifier: Modifier = Modifier,
    appName: String? = null,
    appTagline: String? = null,
    developerName: String? = null,
    developerEmail: String? = null,
    websiteUrl: String? = null,
    githubRepoUrl: String = "https://github.com/mahmutaunal/Adaptive-Hz",
    githubIssuesUrl: String = "https://github.com/mahmutaunal/Adaptive-Hz/issues",
    playStoreDeveloperUrl: String = "https://play.google.com/store/apps/dev?id=5245599652065968716",
) {
    val context = LocalContext.current

    val appNameText = appName ?: stringResource(R.string.settings_app_name)
    val appTaglineText = appTagline ?: stringResource(R.string.settings_app_tagline)
    val developerNameText = developerName ?: stringResource(R.string.settings_developer_name)
    val developerEmailText = developerEmail ?: stringResource(R.string.settings_developer_email)
    val websiteUrlText = websiteUrl ?: stringResource(R.string.settings_website_url)

    val emailSubject = stringResource(R.string.settings_email_subject, appNameText)
    val shareTitle = stringResource(R.string.settings_share_title, appNameText)
    val shareBody = stringResource(R.string.settings_share_text, appNameText, githubRepoUrl)

    val dialog = remember { mutableStateOf<LegalDialog?>(null) }
    val scrollState = rememberScrollState()
    val topBarState = rememberTopAppBarState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsHeroCard(
                title = appNameText,
                subtitle = appTaglineText
            )

            SectionTitle(stringResource(R.string.settings_section_behavior))

            SettingsRow(
                leading = Icons.Outlined.Accessibility,
                title = stringResource(R.string.setup_accessibility_title),
                subtitle = if (accessibilityState == AdaptiveHzRuntimeState.AccessibilityState.WORKING) {
                    stringResource(R.string.settings_accessibility_working_summary)
                } else {
                    stringResource(R.string.settings_accessibility_required_summary)
                },
                onClick = onOpenAccessibilitySettings
            )

            SettingsRow(
                leading = Icons.Outlined.Terminal,
                title = stringResource(R.string.setup_adb_title),
                subtitle = if (adbGranted) {
                    stringResource(R.string.settings_adb_granted_summary)
                } else if (rootAvailable) {
                    stringResource(R.string.settings_adb_or_root_summary)
                } else {
                    stringResource(R.string.settings_adb_required_summary)
                },
                onClick = onVerifyAdb
            )

            if (!adbGranted && rootAvailable) {
                SettingsRow(
                    leading = Icons.Outlined.AdminPanelSettings,
                    title = stringResource(R.string.setup_root_grant_button),
                    subtitle = stringResource(R.string.setup_root_detected_desc),
                    onClick = onGrantWithRoot
                )
            }

            SettingsRow(
                leading = Icons.Outlined.History,
                title = stringResource(R.string.setup_usage_access_title),
                subtitle = if (usageAccessGranted) {
                    stringResource(R.string.settings_usage_access_granted_summary)
                } else {
                    stringResource(R.string.settings_usage_access_recommended_summary)
                },
                onClick = onOpenUsageAccessSettings
            )

            SettingsRow(
                leading = Icons.Outlined.BatterySaver,
                title = stringResource(R.string.settings_item_battery_optimization),
                subtitle = if (batteryOptimizationsIgnored) {
                    stringResource(R.string.settings_battery_optimizations_disabled_summary)
                } else {
                    stringResource(R.string.settings_battery_optimization_recommended_summary)
                },
                onClick = onRequestIgnoreBatteryOptimizations
            )

            if (Build.VERSION.SDK_INT >= 33) {
                SettingsRow(
                    leading = Icons.Outlined.Notifications,
                    title = stringResource(R.string.settings_item_notification_permission),
                    subtitle = if (notificationsGranted) {
                        stringResource(R.string.settings_notifications_enabled_summary)
                    } else {
                        stringResource(R.string.settings_notifications_required_summary)
                    },
                    onClick = onRequestNotificationPermission
                )
            }

            SettingsSwitchRow(
                leading = Icons.Outlined.Sync,
                title = stringResource(R.string.settings_item_stability_mode),
                subtitle = stringResource(R.string.settings_value_stability_mode_summary),
                checked = keepAliveEnabled,
                onCheckedChange = { next ->
                    // Stability mode requires foreground notifications on Android 13+.
                    if (
                        next &&
                        Build.VERSION.SDK_INT >= 33 &&
                        !notificationsGranted
                    ) {
                        Toast.makeText(
                            context,
                            R.string.settings_toast_notification_required_for_stability,
                            Toast.LENGTH_LONG
                        ).show()

                        onRequestNotificationPermission()
                    } else {
                        onKeepAliveChanged(next)
                    }
                }
            )

            SectionTitle(stringResource(R.string.settings_section_debug))
            SettingsRow(
                leading = Icons.Outlined.BugReport,
                title = stringResource(R.string.diagnostics_title),
                subtitle = stringResource(R.string.settings_diagnostics_summary),
                onClick = onOpenDiagnostics
            )
            SettingsRow(
                leading = Icons.Outlined.BugReport,
                title = stringResource(R.string.accessibility_event_inspector_title),
                subtitle = stringResource(R.string.settings_event_inspector_summary),
                onClick = onOpenEventInspector
            )

            SectionTitle(stringResource(R.string.settings_section_about))
            SettingsRow(
                leading = Icons.Outlined.Info,
                title = stringResource(R.string.settings_item_version),
                subtitle = BuildConfig.VERSION_NAME,
                onClick = null
            )
            SettingsRow(
                leading = Icons.Outlined.Language,
                title = stringResource(R.string.settings_item_repository),
                subtitle = githubRepoUrl,
                subtitleMaxLines = 1,
                trailing = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openUrl(context, githubRepoUrl) }
            )

            SectionTitle(stringResource(R.string.settings_section_developer))
            SettingsRow(
                leading = Icons.Outlined.Storefront,
                title = developerNameText,
                subtitle = stringResource(R.string.settings_value_other_apps),
                trailing = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openUrl(context, playStoreDeveloperUrl) }
            )
            SettingsRow(
                leading = Icons.Outlined.Email,
                title = stringResource(R.string.settings_item_email),
                subtitle = developerEmailText,
                trailing = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { composeEmail(context, developerEmailText, subject = emailSubject) }
            )
            SettingsRow(
                leading = Icons.Outlined.Language,
                title = stringResource(R.string.settings_item_website),
                subtitle = websiteUrlText,
                trailing = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openUrl(context, websiteUrlText) }
            )

            SectionTitle(stringResource(R.string.settings_section_support))
            SettingsRow(
                leading = Icons.Outlined.BugReport,
                title = stringResource(R.string.settings_item_report_issue),
                subtitle = stringResource(R.string.settings_value_github_issues),
                trailing = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openUrl(context, githubIssuesUrl) }
            )
            SettingsRow(
                leading = Icons.Outlined.Share,
                title = stringResource(R.string.settings_item_share_app),
                subtitle = stringResource(R.string.settings_value_send_github_link),
                trailing = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = {
                    shareText(
                        context,
                        title = shareTitle,
                        text = shareBody
                    )
                }
            )

            SectionTitle(stringResource(R.string.settings_section_legal))
            SettingsRow(
                leading = Icons.Outlined.PrivacyTip,
                title = stringResource(R.string.settings_item_privacy_policy),
                subtitle = stringResource(R.string.settings_value_privacy_short),
                onClick = { dialog.value = LegalDialog.PrivacyPolicy }
            )
            SettingsRow(
                leading = Icons.Outlined.Description,
                title = stringResource(R.string.settings_item_open_source_notices),
                subtitle = stringResource(R.string.settings_value_third_party_licenses),
                onClick = { dialog.value = LegalDialog.OpenSourceNotices }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    val d = dialog.value
    if (d != null) {
        AlertDialog(
            onDismissRequest = { dialog.value = null },
            confirmButton = {
                Text(
                    text = stringResource(R.string.action_close),
                    modifier = Modifier
                        .padding(PaddingValues(12.dp))
                        .clickable { dialog.value = null },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            dismissButton = {
                Text(
                    text = stringResource(R.string.settings_action_view_on_github),
                    modifier = Modifier
                        .padding(PaddingValues(12.dp))
                        .clickable {
                            dialog.value = null
                            openUrl(context, githubRepoUrl)
                        },
                    style = MaterialTheme.typography.labelLarge
                )
            },
            title = { Text(text = stringResource(d.titleRes)) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(d.bodyRes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }
}

/**
 * Header card displaying the app name and tagline.
 */
@Composable
private fun SettingsHeroCard(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Section label used to group related settings items.
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

/**
 * Reusable settings row with optional subtitle, trailing icon and click action.
 */
@Composable
private fun SettingsRow(
    leading: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = leading,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                subtitle?.let {
                    Text(
                        text = it,
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
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
private fun SettingsSwitchRow(
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = leading,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                subtitle?.let {
                    Text(
                        text = it,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
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

/**
 * Represents legal dialogs backed by string resources.
 */
@Immutable
private sealed class LegalDialog(
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
private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Launches an email client with a prefilled recipient and subject.
 */
private fun composeEmail(context: Context, email: String, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$email".toUri()
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens the Android share sheet with plain text content.
 */
private fun shareText(context: Context, title: String, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
    }
}