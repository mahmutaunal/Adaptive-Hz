package com.mahmutalperenunal.adaptivehz.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.update.StableRelease

@Composable
fun UpdateCheckConsentDialog(
    onEnable: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.update_consent_title)) },
        text = { Text(stringResource(R.string.update_consent_body)) },
        confirmButton = {
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.update_consent_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.update_consent_not_now))
            }
        }
    )
}

@Composable
fun UpdateAvailableDialog(
    release: StableRelease,
    onViewRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.update_available_body, release.name),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (release.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = release.notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onViewRelease) {
                Text(stringResource(R.string.update_view_release))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}
