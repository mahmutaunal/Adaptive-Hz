package com.mahmutalperenunal.adaptivehz.ui.home

import android.annotation.SuppressLint
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import com.mahmutalperenunal.adaptivehz.R

/**
 * Per-app profile screen with search, filtering and paginated app loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppRefreshScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { InstalledAppsRepository(context) }
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
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.GridView,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .padding(start = 14.dp)
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.per_app_profiles_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(id = R.string.per_app_profiles_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

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
                            label = { Text(text = stringResource(id = R.string.search_apps)) },
                            shape = RoundedCornerShape(18.dp)
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
                    context = context,
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

                selectedApp.value = null
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
                        context = context,
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
        shape = RoundedCornerShape(24.dp),
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
    val context = LocalContext.current
    val cachedIcon = remember(packageName) { AppIconMemoryCache.get(packageName) }

    val iconBitmap by produceState(
        initialValue = cachedIcon,
        key1 = packageName
    ) {
        if (cachedIcon == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(app.label)
        },
        text = {
            Column {
                AppRefreshProfileMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = app.profileMode == mode,
                            onClick = { onModeSelected(mode) }
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
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = false,
                            onClick = { onModeSelected(mode) }
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
}

@Composable
private fun BulkProfileActionRow(
    appCount: Int,
    includeSystemApps: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.all_listed_apps),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            ModePill(
                text = stringResource(id = R.string.apply_profile),
                selected = true
            )
        }
    }
}

@Composable
private fun LoadingCard(
    text: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
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
 * Horizontally scrollable filter chips with edge indicators.
 */
@SuppressLint("FrequentlyChangingValue")
@Composable
private fun ProfileFilterChips(
    selectedFilter: AppRefreshProfileMode?,
    onFilterSelected: (AppRefreshProfileMode?) -> Unit
) {
    val scrollState = rememberScrollState()
    val canScrollBackward = scrollState.value > 0
    val canScrollForward = scrollState.value < scrollState.maxValue

    Box(modifier = Modifier.fillMaxWidth()) {
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

        if (canScrollBackward) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(26.dp),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (canScrollForward) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(26.dp),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
        AppRefreshProfileMode.ADAPTIVE -> stringResource(id = R.string.profile_mode_adaptive)
        AppRefreshProfileMode.FORCE_MIN -> stringResource(id = R.string.profile_mode_minimum)
        AppRefreshProfileMode.FORCE_MAX -> stringResource(id = R.string.profile_mode_maximum)
        AppRefreshProfileMode.RESPECT_APP -> stringResource(id = R.string.profile_mode_respect_app)
        AppRefreshProfileMode.DISABLED -> stringResource(id = R.string.profile_mode_disabled)
    }
}

/**
 * Returns the localized helper text for a profile mode.
 */
@Composable
private fun AppRefreshProfileMode.description(): String {
    return when (this) {
        AppRefreshProfileMode.DEFAULT -> stringResource(id = R.string.profile_mode_default_description)
        AppRefreshProfileMode.ADAPTIVE -> stringResource(id = R.string.profile_mode_adaptive_description)
        AppRefreshProfileMode.FORCE_MIN -> stringResource(id = R.string.profile_mode_minimum_description)
        AppRefreshProfileMode.FORCE_MAX -> stringResource(id = R.string.profile_mode_maximum_description)
        AppRefreshProfileMode.RESPECT_APP -> stringResource(id = R.string.profile_mode_respect_app_description)
        AppRefreshProfileMode.DISABLED -> stringResource(id = R.string.profile_mode_disabled_description)
    }
}

private const val PAGE_SIZE = 12