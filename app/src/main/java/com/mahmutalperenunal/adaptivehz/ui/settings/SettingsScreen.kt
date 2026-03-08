@file:Suppress("SameParameterValue")

package com.mahmutalperenunal.adaptivehz.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import com.mahmutalperenunal.adaptivehz.BuildConfig
import com.mahmutalperenunal.adaptivehz.R
import androidx.core.net.toUri

/**
Settings screen for the app. Displays app info, developer links, support options, and legal notices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
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

    // Holds the currently visible legal dialog (Privacy Policy / Open Source Notices)
    val dialog = remember { mutableStateOf<LegalDialog?>(null) }

    // Scroll state for the vertically scrollable settings content
    val scrollState = rememberScrollState()
    val topBarState = rememberTopAppBarState()

    // Main layout structure with top app bar and scrollable content
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // App tagline / short description shown at the top
            AppHeader(appTagline = appTaglineText)

            Spacer(modifier = Modifier.height(4.dp))

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

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

            Spacer(modifier = Modifier.height(8.dp))
            FooterNote(
                text = stringResource(R.string.settings_footer_tip)
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

// Simple header displaying the app tagline
@Composable
private fun AppHeader(
    appTagline: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = appTagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Section label used to group related settings items
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// Reusable row component for a single settings item
@Composable
private fun SettingsRow(
    leading: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)?,
) {
    // Row becomes clickable only if an action is provided
    val clickable = onClick != null
    ListItem(
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = subtitleMaxLines != 1
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = leading,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingContent = trailing?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier.then(
            if (clickable) Modifier.clickable(onClick = onClick) else Modifier
        )
    )
}

// Small informational note shown at the bottom of the settings screen
@Composable
private fun FooterNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// Represents the different legal dialogs that can be displayed
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

/* ---------- Intent helpers ---------- */
// Utility functions for launching common Android intents

// Opens a URL in the user's default browser
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

// Launches the email client with a prefilled recipient and subject
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

// Opens the Android share sheet with plain text content
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