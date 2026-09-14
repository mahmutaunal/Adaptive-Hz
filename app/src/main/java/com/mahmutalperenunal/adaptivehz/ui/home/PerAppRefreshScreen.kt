package com.mahmutalperenunal.adaptivehz.ui.home

import android.annotation.SuppressLint
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.apps.InstalledAppInfo
import com.mahmutalperenunal.adaptivehz.core.apps.InstalledAppsRepository
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.system.CustomRefreshRateController
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateCapabilities
import com.mahmutalperenunal.adaptivehz.core.shizuku.ShizukuAccess
import com.mahmutalperenunal.adaptivehz.core.shizuku.ShizukuAccessState
import com.mahmutalperenunal.adaptivehz.ui.components.ShizukuRecommendationDialog
import com.mahmutalperenunal.adaptivehz.ui.components.rememberShizukuAccessState

/**
 * Per-app profile screen with search, filtering and paginated app loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppRefreshScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { InstalledAppsRepository(appContext) }
    val listState = rememberLazyListState()

    var query by remember { mutableStateOf("") }
    var includeSystemApps by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isInitialLoading by remember { mutableStateOf(false) }
    var isPageLoading by remember { mutableStateOf(false) }
    val hasMoreApps = remember { mutableStateOf(true) }

    val selectedApp = remember { mutableStateOf<InstalledAppInfo?>(null) }
    var selectedProfileFilter by remember { mutableStateOf<AppRefreshProfileMode?>(null) }
    val showBulkModeDialog = remember { mutableStateOf(false) }

    /**
     * Loads the first page or the next page without duplicating app rows.
     */
    suspend fun loadAppsPage(reset: Boolean) {
        if (isInitialLoading || isPageLoading) return
        if (!reset && !hasMoreApps.value) return

        if (reset) {
            isInitialLoading = true
            hasMoreApps.value = true
        } else {
            isPageLoading = true
        }

        val offset = if (reset) 0 else apps.size
        val currentQuery = query
        val currentIncludeSystemApps = includeSystemApps
        val currentProfileFilter = selectedProfileFilter

        try {
            val page = withContext(Dispatchers.IO) {
                repository.getInstalledAppsPage(
                    includeSystemApps = currentIncludeSystemApps,
                    query = currentQuery,
                    profileFilter = currentProfileFilter,
                    offset = offset,
                    limit = PAGE_SIZE
                )
            }

            if (reset) {
                apps = page
                listState.scrollToItem(0)
            } else {
                val existingPackages = apps.map { it.packageName }.toSet()
                apps = apps + page.filterNot { it.packageName in existingPackages }
            }

            hasMoreApps.value = page.size == PAGE_SIZE
        } finally {
            isInitialLoading = false
            isPageLoading = false
        }
    }

    // Reloads from the first page whenever filters change.
    LaunchedEffect(includeSystemApps, query, selectedProfileFilter) {
        loadAppsPage(reset = true)
    }

    // Triggers pagination when the user approaches the end of the list.
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            lastVisibleIndex to totalItemsCount
        }.collectLatest { (lastVisibleIndex, totalItemsCount) ->
            val shouldLoadMore = totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 4

            if (shouldLoadMore) {
                loadAppsPage(reset = false)
            }
        }
    }

    val filteredApps = apps

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.per_app_profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null
                                )
                            },
                            placeholder = { Text(text = stringResource(id = R.string.search_apps)) },
                            shape = MaterialTheme.shapes.large
                        )

                        ProfileFilterChips(
                            selectedFilter = selectedProfileFilter,
                            onFilterSelected = { selectedProfileFilter = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.show_system_apps),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(id = R.string.show_system_apps_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = includeSystemApps,
                                onCheckedChange = { includeSystemApps = it }
                            )
                        }
                    }
                }
            }

            item {
                BulkProfileActionRow(
                    appCount = apps.size,
                    includeSystemApps = includeSystemApps,
                    onClick = { showBulkModeDialog.value = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isInitialLoading) {
                item {
                    LoadingCard(
                        text = stringResource(id = R.string.loading_apps),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(
                    items = filteredApps,
                    key = { it.packageName },
                    contentType = { "app" }
                ) { app ->
                    AppProfileRow(
                        app = app,
                        onClick = { selectedApp.value = app },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (isPageLoading) {
                item {
                    LoadingCard(
                        text = stringResource(id = R.string.loading_more_apps),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    selectedApp.value?.let { app ->
        ProfileModePickerDialog(
            app = app,
            onDismiss = { selectedApp.value = null },
            onModeSelected = { mode ->
                AdaptiveHzPrefs.setAppRefreshProfileMode(
                    context = appContext,
                    packageName = app.packageName,
                    mode = mode
                )

                apps = apps.map {
                    if (it.packageName == app.packageName) {
                        it.copy(profileMode = mode)
                    } else {
                        it
                    }
                }
            }
        )
    }

    if (showBulkModeDialog.value) {
        BulkProfileModePickerDialog(
            appCount = apps.size,
            includeSystemApps = includeSystemApps,
            onDismiss = { showBulkModeDialog.value = false },
            onModeSelected = { mode ->
                val targetPackages = apps.map { it.packageName }.toSet()

                apps.forEach { app ->
                    AdaptiveHzPrefs.setAppRefreshProfileMode(
                        context = appContext,
                        packageName = app.packageName,
                        mode = mode
                    )
                }

                apps = apps.map {
                    if (it.packageName in targetPackages) {
                        it.copy(profileMode = mode)
                    } else {
                        it
                    }
                }

                showBulkModeDialog.value = false
            }
        )
    }
}

/**
 * Displays one app row with its current refresh profile.
 */
@Composable
fun AppProfileRow(
    app: InstalledAppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                packageName = app.packageName,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
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

            Spacer(modifier = Modifier.width(8.dp))

            ModePill(
                text = app.profileMode.title(),
                selected = app.profileMode != AppRefreshProfileMode.DEFAULT
            )
        }
    }
}

/**
 * Loads and caches app icons off the main thread.
 */
@Composable
private fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val cachedIcon = remember(packageName) { AppIconMemoryCache.get(packageName) }

    val iconBitmap by produceState(
        initialValue = cachedIcon,
        key1 = packageName
    ) {
        if (cachedIcon == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.packageManager
                        .getApplicationIcon(packageName)
                        .toBitmap(width = 48, height = 48)
                        .asImageBitmap()
                }.getOrNull()?.also { bitmap ->
                    AppIconMemoryCache.put(packageName, bitmap)
                }
            }
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

/**
 * Small in-memory cache to avoid repeatedly decoding app icons.
 */
private object AppIconMemoryCache {
    private val cache = LruCache<String, ImageBitmap>(120)

    fun get(packageName: String): ImageBitmap? {
        return cache.get(packageName)
    }

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }
}

@Composable
private fun ModePill(
    text: String,
    selected: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
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
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Dialog for changing a single app refresh profile.
 */
@Composable
fun ProfileModePickerDialog(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    onModeSelected: (AppRefreshProfileMode) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val capabilities by produceState(
        initialValue = RefreshRateCapabilities(emptyList(), false),
        key1 = app.packageName
    ) {
        value = withContext(Dispatchers.IO) {
            CustomRefreshRateController.resolveCapabilities(context)
        }
    }
    var selectedMode by remember(app.packageName) { mutableStateOf(app.profileMode) }
    var customMinimum by remember(app.packageName) {
        mutableStateOf(AdaptiveHzPrefs.getAppCustomMinimumRate(context, app.packageName))
    }
    var customMaximum by remember(app.packageName) {
        mutableStateOf(AdaptiveHzPrefs.getAppCustomMaximumRate(context, app.packageName))
    }
    val shizukuAccessState = rememberShizukuAccessState()
    var showShizukuRecommendation by remember(app.packageName) { mutableStateOf(false) }

    fun applyProfile() {
        when (selectedMode) {
            AppRefreshProfileMode.FORCE_MIN ->
                AdaptiveHzPrefs.setAppCustomMinimumRate(
                    context, app.packageName, customMinimum
                )
            AppRefreshProfileMode.FORCE_MAX ->
                AdaptiveHzPrefs.setAppCustomMaximumRate(
                    context, app.packageName, customMaximum
                )
            else -> Unit
        }
        onModeSelected(selectedMode)
        onDismiss()
    }

    fun requestShizukuPermission() {
        if (shizukuAccessState == ShizukuAccessState.PERMISSION_REQUIRED &&
            ShizukuAccess.requestPermission()) {
            return
        }
        Toast.makeText(context, R.string.toast_shizuku_not_running, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(shizukuAccessState) {
        if (showShizukuRecommendation && shizukuAccessState == ShizukuAccessState.READY) {
            showShizukuRecommendation = false
            applyProfile()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(R.string.custom_refresh_profile_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppRefreshProfileMode.entries.forEach { mode ->
                    ProfileModeOption(
                        title = mode.title(),
                        description = mode.description(),
                        icon = mode.icon(),
                        selected = selectedMode == mode,
                        onClick = {
                            selectedMode = mode
                        }
                    )
                }

                if (!capabilities.customRateSelectionSupported &&
                    (selectedMode == AppRefreshProfileMode.FORCE_MIN ||
                        selectedMode == AppRefreshProfileMode.FORCE_MAX)) {
                    Text(
                        stringResource(R.string.custom_refresh_rate_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (capabilities.customRateSelectionSupported &&
                    selectedMode == AppRefreshProfileMode.FORCE_MIN) {
                    InlineRefreshRateSelector(
                        label = stringResource(R.string.custom_refresh_rate_label),
                        value = customMinimum,
                        rates = capabilities.supportedRates,
                        enabled = capabilities.customRateSelectionSupported,
                        onSelected = {
                            customMinimum = it
                        }
                    )
                }
                if (capabilities.customRateSelectionSupported &&
                    selectedMode == AppRefreshProfileMode.FORCE_MAX) {
                    InlineRefreshRateSelector(
                        label = stringResource(R.string.custom_refresh_rate_label),
                        value = customMaximum,
                        rates = capabilities.supportedRates,
                        enabled = capabilities.customRateSelectionSupported,
                        onSelected = {
                            customMaximum = it
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.action_cancel))
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    if (shizukuAccessState == ShizukuAccessState.READY) {
                        applyProfile()
                    } else {
                        showShizukuRecommendation = true
                    }
                }
            ) {
                Text(text = stringResource(id = R.string.apply_profile))
            }
        }
    )

    if (showShizukuRecommendation) {
        ShizukuRecommendationDialog(
            onDismiss = { showShizukuRecommendation = false },
            onContinueWithoutShizuku = {
                showShizukuRecommendation = false
                applyProfile()
            },
            onRequestPermission = {
                requestShizukuPermission()
            }
        )
    }
}

@Composable
private fun ProfileModeOption(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
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

            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun InlineRefreshRateSelector(
    label: String,
    value: Int?,
    rates: List<Int>,
    enabled: Boolean,
    onSelected: (Int?) -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = value == null,
                onClick = { onSelected(null) },
                enabled = enabled,
                label = { Text(stringResource(R.string.custom_refresh_rate_default)) }
            )
            rates.forEach { rate ->
                FilterChip(
                    selected = value == rate,
                    onClick = { onSelected(rate) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.custom_refresh_rate_hz, rate)) }
                )
            }
        }
    }
}

private fun AppRefreshProfileMode.icon(): ImageVector = when (this) {
    AppRefreshProfileMode.DEFAULT -> Icons.Outlined.SettingsSuggest
    AppRefreshProfileMode.SYSTEM_CONTROLLED -> Icons.Outlined.Apps
    AppRefreshProfileMode.FORCE_MIN -> Icons.Outlined.Eco
    AppRefreshProfileMode.FORCE_MAX -> Icons.Outlined.Bolt
}

/**
 * Dialog for applying one profile to all currently listed apps.
 */
@Composable
private fun BulkProfileModePickerDialog(
    appCount: Int,
    includeSystemApps: Boolean,
    onDismiss: () -> Unit,
    onModeSelected: (AppRefreshProfileMode) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val shizukuAccessState = rememberShizukuAccessState()
    var pendingMode by remember { mutableStateOf<AppRefreshProfileMode?>(null) }

    fun selectMode(mode: AppRefreshProfileMode) {
        if (shizukuAccessState == ShizukuAccessState.READY) {
            onModeSelected(mode)
        } else {
            pendingMode = mode
        }
    }

    LaunchedEffect(shizukuAccessState) {
        if (shizukuAccessState == ShizukuAccessState.READY) {
            pendingMode?.let { mode ->
                pendingMode = null
                onModeSelected(mode)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.apply_profile_to_all))
        },
        text = {
            Column {
                Text(
                    text = if (includeSystemApps) {
                        stringResource(
                            id = R.string.bulk_profile_dialog_message_including_system,
                            appCount
                        )
                    } else {
                        stringResource(
                            id = R.string.bulk_profile_dialog_message_user_apps,
                            appCount
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.size(8.dp))

                AppRefreshProfileMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectMode(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = false,
                            onClick = { selectMode(mode) }
                        )

                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = mode.title(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = mode.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.close))
            }
        }
    )

    pendingMode?.let { mode ->
        ShizukuRecommendationDialog(
            onDismiss = { pendingMode = null },
            onContinueWithoutShizuku = {
                pendingMode = null
                onModeSelected(mode)
            },
            onRequestPermission = {
                if (shizukuAccessState != ShizukuAccessState.PERMISSION_REQUIRED ||
                    !ShizukuAccess.requestPermission()) {
                    Toast.makeText(
                        context,
                        R.string.toast_shizuku_not_running,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun BulkProfileActionRow(
    appCount: Int,
    includeSystemApps: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useStackedLayout = maxWidth < 400.dp || LocalDensity.current.fontScale > 1.15f

            if (useStackedLayout) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BulkProfileIcon()
                        Spacer(modifier = Modifier.width(14.dp))
                        BulkProfileSummary(
                            appCount = appCount,
                            includeSystemApps = includeSystemApps,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    BulkProfileButton(
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BulkProfileIcon()
                    Spacer(modifier = Modifier.width(14.dp))
                    BulkProfileSummary(
                        appCount = appCount,
                        includeSystemApps = includeSystemApps,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BulkProfileButton(onClick = onClick)
                }
            }
        }
    }
}

@Composable
private fun BulkProfileIcon() {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun BulkProfileSummary(
    appCount: Int,
    includeSystemApps: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(id = R.string.all_listed_apps),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = if (includeSystemApps) {
                stringResource(
                    id = R.string.app_count_including_system_apps,
                    appCount
                )
            } else {
                stringResource(
                    id = R.string.user_app_count,
                    appCount
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BulkProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = stringResource(id = R.string.apply_profile),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun LoadingCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Horizontally scrollable Material filter chips. A partially visible last chip
 * naturally communicates that more filters are available.
 */
@SuppressLint("FrequentlyChangingValue")
@Composable
private fun ProfileFilterChips(
    selectedFilter: AppRefreshProfileMode?,
    onFilterSelected: (AppRefreshProfileMode?) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onFilterSelected(null) },
                label = { Text(text = stringResource(id = R.string.all)) }
            )

            AppRefreshProfileMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedFilter == mode,
                    onClick = { onFilterSelected(mode) },
                    label = { Text(mode.title()) }
                )
            }
    }
}

/**
 * Returns the localized title for a profile mode.
 */
@Composable
private fun AppRefreshProfileMode.title(): String {
    return when (this) {
        AppRefreshProfileMode.DEFAULT -> stringResource(id = R.string.profile_mode_default)
        AppRefreshProfileMode.SYSTEM_CONTROLLED -> stringResource(id = R.string.profile_mode_respect_app)
        AppRefreshProfileMode.FORCE_MIN -> stringResource(id = R.string.profile_mode_minimum)
        AppRefreshProfileMode.FORCE_MAX -> stringResource(id = R.string.profile_mode_maximum)
    }
}

/**
 * Returns the localized helper text for a profile mode.
 */
@Composable
private fun AppRefreshProfileMode.description(): String {
    return when (this) {
        AppRefreshProfileMode.DEFAULT -> stringResource(id = R.string.profile_mode_default_description)
        AppRefreshProfileMode.SYSTEM_CONTROLLED -> stringResource(id = R.string.profile_mode_respect_app_description)
        AppRefreshProfileMode.FORCE_MIN -> stringResource(id = R.string.profile_mode_minimum_description)
        AppRefreshProfileMode.FORCE_MAX -> stringResource(id = R.string.profile_mode_maximum_description)
    }
}

private const val PAGE_SIZE = 12
