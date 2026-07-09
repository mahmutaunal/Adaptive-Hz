package com.mahmutalperenunal.adaptivehz.core.apps

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs

/**
 * Provides recently used apps using UsageStatsManager.
 */

class RecentAppsProvider(
    context: Context
) {
    private val appContext = context.applicationContext

    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val packageManager: PackageManager =
        appContext.packageManager

    /**
     * Verifies Usage Access permission state.
     */
    fun hasPermission(): Boolean {
        return try {
            val now = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000L,
                now
            )

            stats.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns recently used apps ordered by last usage time.
     */
    fun getRecentApps(
        limit: Int = 5,
        includeSystemApps: Boolean = false
    ): List<InstalledAppInfo> {

        if (!hasPermission()) return emptyList()

        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - (1000L * 60L * 60L * 24L),
            now
        )

        return stats
            .asSequence()
            .filter {
                it.totalTimeInForeground > 0
            }
            .sortedByDescending(UsageStats::getLastTimeUsed)
            .mapNotNull { usage ->
                val pkg = usage.packageName

                if (pkg == appContext.packageName) {
                    return@mapNotNull null
                }

                runCatching {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)

                    val isSystem =
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    if (!includeSystemApps && isSystem) {
                        return@mapNotNull null
                    }

                    InstalledAppInfo(
                        packageName = pkg,
                        label = packageManager.getApplicationLabel(appInfo).toString(),
                        profileMode = AdaptiveHzPrefs.getAppRefreshProfileMode(
                            context = appContext,
                            packageName = pkg
                        ),
                        lastUpdatedTime = usage.lastTimeUsed,
                        isSystemApp = isSystem
                    )
                }.getOrNull()
            }
            .distinctBy { it.packageName }
            .take(limit)
            .toList()
    }
}