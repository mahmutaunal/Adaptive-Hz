package com.mahmutalperenunal.adaptivehz.ui.home

import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzActionHandler
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.service.StabilityForegroundService
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendorDetector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzRuntimeState
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzRuntimeState.getAccessibilityState
import com.mahmutalperenunal.adaptivehz.core.apps.InstalledAppInfo
import com.mahmutalperenunal.adaptivehz.core.apps.InstalledAppsRepository
import com.mahmutalperenunal.adaptivehz.core.apps.RecentAppsProvider
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.system.RootManager
import com.mahmutalperenunal.adaptivehz.core.quickaccess.QuickAccessManager
import com.mahmutalperenunal.adaptivehz.core.update.GitHubUpdateChecker
import com.mahmutalperenunal.adaptivehz.core.update.StableRelease
import com.mahmutalperenunal.adaptivehz.core.update.UpdateCheckResult
import com.mahmutalperenunal.adaptivehz.ui.home.components.DashboardComponent
import com.mahmutalperenunal.adaptivehz.ui.home.components.SetupComponent
import com.mahmutalperenunal.adaptivehz.ui.settings.components.QuickAccessDiscoverySheet
import com.mahmutalperenunal.adaptivehz.ui.settings.components.ProjectSupportCard
import com.mahmutalperenunal.adaptivehz.ui.components.UpdateAvailableDialog
import com.mahmutalperenunal.adaptivehz.ui.components.UpdateCheckConsentDialog
import java.time.LocalDate

/**
 * Home route that coordinates setup state, dashboard state and quick actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    getAccessibilityState: () -> AdaptiveHzRuntimeState.AccessibilityState,
    batteryOptimizationsIgnored: Boolean,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    requestIgnoreBatteryOptimizations: () -> Unit,
    openSettingsScreen: () -> Unit,
    openPerAppScreen: () -> Unit,
    keepAliveEnabled: Boolean,
    onKeepAliveEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember { AdaptiveHzPrefs }
    val githubRepositoryUrl = "https://github.com/mahmutaunal/Adaptive-Hz"
    val shareTitle = stringResource(R.string.settings_share_title, stringResource(R.string.app_name))
    val shareBody = stringResource(
        R.string.settings_share_text,
        stringResource(R.string.app_name),
        githubRepositoryUrl
    )

    val toastAdbVerified = stringResource(id = R.string.toast_adb_verified)
    val toastAdbPermissionMissing = stringResource(id = R.string.toast_adb_permission_missing)
    val toastStabilityEnabled = stringResource(id = R.string.toast_stability_enabled)
    val toastStabilityDisabled = stringResource(id = R.string.toast_stability_disabled)
    val toastOpenNotificationSettingsFailed = stringResource(id = R.string.toast_open_notification_settings_failed)
    val toastAdaptiveApplied = stringResource(id = R.string.toast_adaptive_applied)
    val toastMinimumApplied = stringResource(id = R.string.toast_minimum_applied)
    val toastMaximumApplied = stringResource(id = R.string.toast_maximum_applied)
    val toastSecureSettingsMissing = stringResource(id = R.string.toast_secure_settings_missing)
    val toastRootGrantSuccess = stringResource(id = R.string.root_grant_success)
    val toastRootGrantDenied = stringResource(id = R.string.root_grant_denied)
    val toastRootNotAvailable = stringResource(id = R.string.root_not_available)
    val toastRootGrantFailed = stringResource(id = R.string.root_grant_failed)
    val labelOn = stringResource(id = R.string.label_on)
    val labelOff = stringResource(id = R.string.label_off)
    val labelGranted = stringResource(id = R.string.label_granted)
    val labelRequired = stringResource(id = R.string.label_required)

    val labelIdle = stringResource(id = R.string.label_idle)
    val labelActive = stringResource(id = R.string.label_active)
    val labelAdaptiveTarget = stringResource(id = R.string.label_target_max_on_touch)
    val labelManualTargetMin = stringResource(id = R.string.label_target_minimum)
    val labelManualTargetMax = stringResource(id = R.string.label_target_maximum)
    val labelSystemDefault = stringResource(id = R.string.label_target_system_default)

    val scrollState = rememberScrollState()

    // UI state restored from persisted app preferences.
    var adbGranted by remember { mutableStateOf(prefs.isAdbGranted(appContext)) }
    val currentMode = remember { mutableStateOf(prefs.getCurrentMode(appContext)) }

    // Keeps the visible dashboard synchronized with mode changes initiated by
    // external app surfaces such as the Quick Settings tile or notification.
    DisposableEffect(appContext) {
        val stopObserving = prefs.observeCurrentMode(appContext) { mode ->
            currentMode.value = mode
        }

        onDispose(stopObserving)
    }

    val accessibilityState = remember { mutableStateOf(getAccessibilityState()) }

    // Vendor is stable during the app session.
    val vendorLabel = remember { DeviceVendorDetector.detect().toString() }
    val isXiaomiDevice = remember { DeviceVendorDetector.detect() == DeviceVendor.XIAOMI }

    var rootAvailable by remember { mutableStateOf(false) }

    val initialSetupCompleted = remember { mutableStateOf(prefs.isInitialSetupCompleted(appContext)) }

    val installedAppsRepository = remember(appContext) { InstalledAppsRepository(appContext) }
    val recentAppsProvider = remember(appContext) { RecentAppsProvider(appContext) }
    var dashboardApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var usagePermissionGranted by remember { mutableStateOf(recentAppsProvider.hasPermission()) }
    val selectedDashboardApp = remember { mutableStateOf<InstalledAppInfo?>(null) }

    // Refreshes the compact recent-app list shown on the dashboard.
    fun reloadDashboardApps() {
        dashboardApps = installedAppsRepository.getDashboardApps(limit = 5)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-reads runtime state after setup changes or lifecycle resume.
    fun refreshStates() {
        accessibilityState.value = getAccessibilityState(appContext)
        currentMode.value = prefs.getCurrentMode(appContext)
        usagePermissionGranted = recentAppsProvider.hasPermission()

        reloadDashboardApps()

        adbGranted = prefs.isAdbGranted(appContext)

        rootAvailable = when (RootManager.getRootState()) {
            is RootManager.RootState.Available -> true
            else -> false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Performs the first state sync after composition.
    LaunchedEffect(Unit) {
        refreshStates()
    }

    LaunchedEffect(Unit) {
        rootAvailable = when (RootManager.getRootState()) {
            is RootManager.RootState.Available -> true
            else -> false
        }
    }

    val accessibilityConfigured = accessibilityState.value != AdaptiveHzRuntimeState.AccessibilityState.DISABLED
    val accessibilityWorking = accessibilityState.value == AdaptiveHzRuntimeState.AccessibilityState.WORKING
    val accessibilityBroken = accessibilityState.value == AdaptiveHzRuntimeState.AccessibilityState.BROKEN

    // Setup is complete only after required permissions are ready and acknowledged.
    val requiredSetupReady = accessibilityConfigured && adbGranted
    val setupComplete = requiredSetupReady && initialSetupCompleted.value
    var showQuickAccessDiscovery by remember { mutableStateOf(false) }
    var showUpdateConsent by remember { mutableStateOf(false) }
    var automaticUpdateChecks by remember {
        mutableStateOf(prefs.isAutomaticUpdateCheckEnabled(appContext))
    }
    var availableRelease by remember { mutableStateOf<StableRelease?>(null) }
    var showProjectSupport by remember { mutableStateOf(false) }

    // Introduces optional features one at a time after required setup is complete.
    LaunchedEffect(setupComplete) {
        if (!setupComplete) return@LaunchedEffect

        if (prefs.shouldShowQuickAccessDiscovery(appContext)) {
            prefs.markQuickAccessDiscoveryShown(appContext)
            val quickAccessAlreadyConfigured =
                QuickAccessManager.isQuickSettingsTileAdded(appContext) &&
                    QuickAccessManager.isWidgetAdded(appContext)
            showQuickAccessDiscovery = !quickAccessAlreadyConfigured
            if (quickAccessAlreadyConfigured && !prefs.hasMadeUpdateCheckChoice(appContext)) {
                showUpdateConsent = true
            }
        } else if (!prefs.hasMadeUpdateCheckChoice(appContext)) {
            showUpdateConsent = true
        }
    }

    // Network access is opt-in, rate-limited and only checks stable GitHub releases.
    LaunchedEffect(setupComplete, automaticUpdateChecks) {
        if (!setupComplete || !prefs.shouldRunAutomaticUpdateCheck(appContext)) {
            return@LaunchedEffect
        }

        prefs.markUpdateCheckAttempt(appContext)
        when (val result = GitHubUpdateChecker.check()) {
            is UpdateCheckResult.Available -> {
                if (!prefs.wasReleaseAlreadyPrompted(appContext, result.release.tagName)) {
                    prefs.markReleasePrompted(appContext, result.release.tagName)
                    availableRelease = result.release
                }
            }

            UpdateCheckResult.UpToDate,
            UpdateCheckResult.InvalidResponse,
            is UpdateCheckResult.Failed -> Unit
        }
    }

    val appEnabled = currentMode.value != AdaptiveHzMode.OFF

    // Counts only qualified, well-spaced local sessions; nothing leaves the device.
    LaunchedEffect(setupComplete, appEnabled) {
        if (!setupComplete || !appEnabled) return@LaunchedEffect
        prefs.recordSupportSession(
            context = appContext,
            localEpochDay = LocalDate.now().toEpochDay()
        )
        if (prefs.shouldShowSupportPrompt(appContext)) {
            prefs.markSupportPromptShown(appContext)
            showProjectSupport = true
        }
    }

    val currentModeLabel = when (currentMode.value) {
        AdaptiveHzMode.OFF -> stringResource(id = R.string.label_off)
        AdaptiveHzMode.ADAPTIVE -> stringResource(id = R.string.mode_adaptive)
        AdaptiveHzMode.FORCE_MIN,
        AdaptiveHzMode.FORCE_MAX -> stringResource(id = R.string.mode_manual)
    }

    val targetLabel = when (currentMode.value) {
        AdaptiveHzMode.OFF -> labelSystemDefault
        AdaptiveHzMode.ADAPTIVE -> labelAdaptiveTarget
        AdaptiveHzMode.FORCE_MIN -> labelManualTargetMin
        AdaptiveHzMode.FORCE_MAX -> labelManualTargetMax
    }

    val interactionLabel = if (currentMode.value == AdaptiveHzMode.ADAPTIVE) {
        labelActive
    } else {
        labelIdle
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            ),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.home_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            actions = {
                if (setupComplete) {
                    IconButton(onClick = openSettingsScreen) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(id = R.string.settings_title)
                        )
                    }
                }
            }
        )

        // Main content switches between setup flow and dashboard.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .widthIn(max = 520.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (accessibilityBroken) {
                RecoveryCard(
                    onRetry = {
                        accessibilityState.value = getAccessibilityState(appContext)
                    },
                    onOpenAccessibilitySettings = openAccessibilitySettings,
                    onOpenBatterySettings = requestIgnoreBatteryOptimizations
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (!setupComplete) {
                SetupComponent(
                    accessibilityEnabled = accessibilityConfigured,
                    adbGranted = adbGranted,
                    batteryOptimizationsIgnored = batteryOptimizationsIgnored,
                    notificationsGranted = notificationsGranted,
                    usageAccessGranted = usagePermissionGranted,
                    keepAliveEnabled = keepAliveEnabled,
                    isXiaomiDevice = isXiaomiDevice,
                    labelOn = labelOn,
                    labelOff = labelOff,
                    labelGranted = labelGranted,
                    labelRequired = labelRequired,
                    toastStabilityEnabled = toastStabilityEnabled,
                    toastStabilityDisabled = toastStabilityDisabled,
                    toastOpenNotificationSettingsFailed = toastOpenNotificationSettingsFailed,
                    rootAvailable = rootAvailable,
                    onOpenAccessibilitySettings = openAccessibilitySettings,
                    // Verifies WRITE_SECURE_SETTINGS through permission and safe write checks.
                    onVerifyAdb = {
                        val permission = "android.permission.WRITE_SECURE_SETTINGS"

                        val verified = try {
                            val pmGranted = ContextCompat.checkSelfPermission(
                                appContext,
                                permission
                            ) == PackageManager.PERMISSION_GRANTED

                            val cr = appContext.contentResolver
                            val key = Settings.Global.ANIMATOR_DURATION_SCALE
                            val current = Settings.Global.getFloat(cr, key, 1f)
                            val wrote = Settings.Global.putFloat(cr, key, current)
                            val after = Settings.Global.getFloat(cr, key, 1f)

                            pmGranted || (wrote && after == current)
                        } catch (_: SecurityException) {
                            false
                        } catch (_: Exception) {
                            ContextCompat.checkSelfPermission(
                                appContext,
                                permission
                            ) == PackageManager.PERMISSION_GRANTED
                        }

                        if (verified) {
                            prefs.setAdbGranted(appContext, true)
                            adbGranted = true
                            Toast.makeText(appContext, toastAdbVerified, Toast.LENGTH_SHORT).show()
                        } else {
                            prefs.setAdbGranted(appContext, false)
                            adbGranted = false
                            Toast.makeText(appContext, toastAdbPermissionMissing, Toast.LENGTH_LONG).show()
                        }
                    },
                    // Optional root fallback for granting the secure settings permission.
                    onGrantWithRoot = {
                        when (val result = RootManager.grantWriteSecureSettings(appContext)) {
                            is RootManager.RootState.Available -> {
                                prefs.setAdbGranted(appContext, true)
                                adbGranted = true
                                Toast.makeText(
                                    appContext,
                                    toastRootGrantSuccess,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Denied -> {
                                Toast.makeText(
                                    appContext,
                                    toastRootGrantDenied,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Unavailable -> {
                                Toast.makeText(
                                    appContext,
                                    toastRootNotAvailable,
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is RootManager.RootState.Failed -> {
                                Toast.makeText(
                                    appContext,
                                    result.reason ?: toastRootGrantFailed,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onRequestIgnoreBatteryOptimizations = {
                        requestIgnoreBatteryOptimizations()
                    },
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenUsageAccessSettings = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onSetKeepAliveEnabled = { next ->
                        prefs.setKeepAliveEnabled(appContext, next)
                        onKeepAliveEnabledChange(next)

                        if (next) {
                            StabilityForegroundService.start(appContext)
                        } else {
                            StabilityForegroundService.stop(appContext)
                        }
                    }
                )
            } else {
                DashboardComponent(
                    appEnabled = appEnabled,
                    accessibilityWorking = accessibilityWorking,
                    accessibilityBroken = accessibilityBroken,
                    vendorLabel = vendorLabel,
                    currentMode = currentMode.value,
                    currentModeLabel = currentModeLabel,
                    targetLabel = targetLabel,
                    interactionLabel = interactionLabel,
                    usagePermissionGranted = usagePermissionGranted,
                    onGrantUsageAccessClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        )
                    },
                    recentApps = dashboardApps,
                    onPerAppClick = openPerAppScreen,
                    onAppProfileClick = { app ->
                        selectedDashboardApp.value = app
                    },
                    onAppEnabledChange = { enabled ->
                        try {
                            if (enabled) {
                                AdaptiveHzActionHandler.turnOn(appContext)
                            } else {
                                AdaptiveHzActionHandler.turnOff(appContext)
                            }
                            currentMode.value = AdaptiveHzActionHandler.getCurrentMode(appContext)
                        } catch (_: SecurityException) {
                            Toast.makeText(
                                appContext,
                                toastSecureSettingsMissing,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onAdaptiveClick = {
                        try {
                            AdaptiveHzActionHandler.setAdaptive(appContext)
                            currentMode.value = AdaptiveHzActionHandler.getCurrentMode(appContext)
                            Toast.makeText(appContext, toastAdaptiveApplied, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(
                                appContext,
                                toastSecureSettingsMissing,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onMinimumClick = {
                        try {
                            AdaptiveHzActionHandler.setMinimum(appContext)
                            currentMode.value = AdaptiveHzActionHandler.getCurrentMode(appContext)
                            Toast.makeText(appContext, toastMinimumApplied, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(
                                appContext,
                                toastSecureSettingsMissing,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onMaximumClick = {
                        try {
                            AdaptiveHzActionHandler.setMaximum(appContext)
                            currentMode.value = AdaptiveHzActionHandler.getCurrentMode(appContext)
                            Toast.makeText(appContext, toastMaximumApplied, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(
                                appContext,
                                toastSecureSettingsMissing,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )

                if (showProjectSupport) {
                    Spacer(modifier = Modifier.height(20.dp))
                    ProjectSupportCard(
                        onStar = {
                            openExternalUrl(context, githubRepositoryUrl)
                            showProjectSupport = false
                        },
                        onShare = {
                            shareProject(context, shareTitle, shareBody)
                            showProjectSupport = false
                        },
                        onDismiss = { showProjectSupport = false }
                    )
                }
            }
        }

        if (!setupComplete) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilledTonalButton(
                    onClick = {
                        if (requiredSetupReady) {
                            prefs.setInitialSetupCompleted(appContext, true)
                            initialSetupCompleted.value = true
                        }
                    },
                    enabled = requiredSetupReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.setup_continue_button))
                }

                if (!requiredSetupReady) {
                    Text(
                        text = stringResource(id = R.string.setup_continue_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    selectedDashboardApp.value?.let { app ->
        ProfileModePickerDialog(
            app = app,
            onDismiss = { selectedDashboardApp.value = null },
            onModeSelected = { mode ->
                AdaptiveHzPrefs.setAppRefreshProfileMode(
                    context = appContext,
                    packageName = app.packageName,
                    mode = mode
                )
                selectedDashboardApp.value = null
                reloadDashboardApps()
            }
        )
    }

    if (showQuickAccessDiscovery) {
        QuickAccessDiscoverySheet(
            onDismiss = { showQuickAccessDiscovery = false }
        )
    }

    if (showUpdateConsent) {
        UpdateCheckConsentDialog(
            onEnable = {
                prefs.setAutomaticUpdateChecksEnabled(appContext, true)
                automaticUpdateChecks = true
                showUpdateConsent = false
            },
            onDecline = {
                prefs.setAutomaticUpdateChecksEnabled(appContext, false)
                automaticUpdateChecks = false
                showUpdateConsent = false
            }
        )
    }

    availableRelease?.let { release ->
        UpdateAvailableDialog(
            release = release,
            onViewRelease = {
                val intent = Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri())
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                }
                availableRelease = null
            },
            onDismiss = { availableRelease = null }
        )
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
    }
}

private fun shareProject(context: android.content.Context, title: String, body: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, title))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Recovery prompt shown when accessibility is enabled but not sending heartbeats.
 */
@Composable
private fun RecoveryCard(
    onRetry: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                Icon(
                    imageVector = Icons.Outlined.ReportProblem,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )

                Text(
                    text = stringResource(id = R.string.recovery_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            Text(
                text = stringResource(id = R.string.recovery_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.retry))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.setup_accessibility_button))
                    }

                    OutlinedButton(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.open_battery_settings))
                    }
                }
            }
        }
    }
}
