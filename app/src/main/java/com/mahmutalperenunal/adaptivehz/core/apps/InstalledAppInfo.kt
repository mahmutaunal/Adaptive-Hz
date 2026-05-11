package com.mahmutalperenunal.adaptivehz.core.apps

import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode

/**
 * Lightweight app model used by the per-app refresh profile UI.
 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val profileMode: AppRefreshProfileMode,
    val lastUpdatedTime: Long,
    val isSystemApp: Boolean
)