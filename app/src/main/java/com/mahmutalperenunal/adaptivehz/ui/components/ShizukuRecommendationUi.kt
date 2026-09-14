package com.mahmutalperenunal.adaptivehz.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.shizuku.ShizukuAccess
import com.mahmutalperenunal.adaptivehz.core.shizuku.ShizukuAccessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

@Composable
fun rememberShizukuAccessState(): ShizukuAccessState {
    var refreshTick by remember { mutableIntStateOf(0) }
    var accessState by remember { androidx.compose.runtime.mutableStateOf(ShizukuAccessState.NOT_RUNNING) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Retry listener registration when Shizuku transitions from stopped to running.
    DisposableEffect(lifecycleOwner, accessState) {
        val permissionListener = OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == ShizukuAccess.UI_PERMISSION_REQUEST_CODE) {
                scope.launch { refreshTick++ }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }

        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        }
    }

    LaunchedEffect(refreshTick) {
        accessState = withContext(Dispatchers.IO) { ShizukuAccess.readState() }
    }
    return accessState
}

@Composable
fun ShizukuRecommendationDialog(
    onDismiss: () -> Unit,
    onContinueWithoutShizuku: () -> Unit,
    onRequestPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shizuku_recommendation_title)) },
        text = { Text(stringResource(R.string.shizuku_recommendation_message)) },
        confirmButton = {
            TextButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.shizuku_grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onContinueWithoutShizuku) {
                Text(stringResource(R.string.shizuku_continue_without))
            }
        }
    )
}
