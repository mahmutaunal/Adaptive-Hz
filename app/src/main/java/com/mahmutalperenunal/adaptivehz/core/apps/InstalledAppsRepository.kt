package com.mahmutalperenunal.adaptivehz.core.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode

/**
 * Provides installed app data used by dashboard and per-app profile screens.
 */

class InstalledAppsRepository(
    private val context: Context
) {
    private val pm = context.packageManager

    private val recentAppsProvider = RecentAppsProvider(context)

    /**
     * Returns the full installed app list.
     */
    fun getInstalledApps(
        includeSystemApps: Boolean = false
    ): List<InstalledAppInfo> {
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { app ->
                runCatching {
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    if (!includeSystemApps && isSystem) return@mapNotNull null

                    InstalledAppInfo(
                        packageName = app.packageName,
                        label = pm.getApplicationLabel(app).toString(),
                        profileMode = AdaptiveHzPrefs.getAppRefreshProfileMode(
                            context = context,
                            packageName = app.packageName
                        ),
                        lastUpdatedTime = runCatching {
                            pm.getPackageInfo(app.packageName, 0).lastUpdateTime
                        }.getOrDefault(0L),
                        isSystemApp = isSystem
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Returns a filtered and paginated app list.
     */
    fun getInstalledAppsPage(
        includeSystemApps: Boolean = false,
        query: String = "",
        profileFilter: AppRefreshProfileMode? = null,
        offset: Int = 0,
        limit: Int = 20
    ): List<InstalledAppInfo> {
        val normalizedQuery = query.trim().lowercase()

        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { app ->
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                includeSystemApps || !isSystem
            }
            .mapNotNull { app ->
                runCatching {
                    val label = pm.getApplicationLabel(app).toString()
                    val packageName = app.packageName

                    if (
                        normalizedQuery.isNotBlank() &&
                        !label.lowercase().contains(normalizedQuery) &&
                        !packageName.lowercase().contains(normalizedQuery)
                    ) {
                        return@mapNotNull null
                    }

                    AppListCandidate(
                        app = app,
                        label = label,
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .drop(offset)
            .take(limit)
            .mapNotNull { candidate ->
                val app = candidate.app

                runCatching {
                    val profileMode = AdaptiveHzPrefs.getAppRefreshProfileMode(
                        context = context,
                        packageName = app.packageName
                    )

                    if (profileFilter != null && profileMode != profileFilter) {
                        return@mapNotNull null
                    }

                    InstalledAppInfo(
                        packageName = app.packageName,
                        label = candidate.label,
                        profileMode = profileMode,
                        lastUpdatedTime = runCatching {
                            pm.getPackageInfo(app.packageName, 0).lastUpdateTime
                        }.getOrDefault(0L),
                        isSystemApp = candidate.isSystemApp
                    )
                }.getOrNull()
            }
            .toList()
    }

    /**
     * Prioritizes recent apps before falling back to configured profiles.
     */
    fun getDashboardApps(limit: Int = 5): List<InstalledAppInfo> {
        val recentApps = recentAppsProvider.getRecentApps(limit = limit)

        if (recentApps.isNotEmpty()) {
            return recentApps
        }

        val apps = getInstalledApps(includeSystemApps = false)

        val configuredApps = apps
            .filter { it.profileMode != AppRefreshProfileMode.DEFAULT }
            .sortedBy { it.label.lowercase() }

        return if (configuredApps.isNotEmpty()) {
            configuredApps.take(limit)
        } else {
            apps.sortedByDescending { it.lastUpdatedTime }.take(limit)
        }
    }

    /**
     * Internal lightweight model used during app list filtering.
     */
    private data class AppListCandidate(
        val app: ApplicationInfo,
        val label: String,
        val isSystemApp: Boolean
    )
}