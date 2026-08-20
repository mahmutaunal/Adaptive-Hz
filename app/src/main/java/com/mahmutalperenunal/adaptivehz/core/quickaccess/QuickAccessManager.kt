package com.mahmutalperenunal.adaptivehz.core.quickaccess

import android.app.PendingIntent
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzTileService
import com.mahmutalperenunal.adaptivehz.widget.AdaptiveHzWidgetProvider

/** Platform integration for discovering and installing Adaptive Hz quick access surfaces. */
object QuickAccessManager {

    enum class RequestResult {
        ADDED,
        ALREADY_ADDED,
        REQUESTED,
        NOT_ADDED,
        UNSUPPORTED,
        FAILED
    }

    fun isQuickSettingsTileAdded(context: Context): Boolean {
        return AdaptiveHzPrefs.isQuickSettingsTileAdded(context.applicationContext)
    }

    fun setQuickSettingsTileAdded(context: Context, added: Boolean) {
        AdaptiveHzPrefs.setQuickSettingsTileAdded(context.applicationContext, added)
    }

    fun isWidgetAdded(context: Context): Boolean {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = ComponentName(appContext, AdaptiveHzWidgetProvider::class.java)
        return manager.getAppWidgetIds(provider).isNotEmpty()
    }

    fun requestQuickSettingsTile(
        context: Context,
        onResult: (RequestResult) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < 33) {
            onResult(RequestResult.UNSUPPORTED)
            return
        }

        val appContext = context.applicationContext
        val statusBarManager = appContext.getSystemService(StatusBarManager::class.java)

        if (statusBarManager == null) {
            onResult(RequestResult.FAILED)
            return
        }

        runCatching {
            statusBarManager.requestAddTileService(
                ComponentName(appContext, AdaptiveHzTileService::class.java),
                appContext.getString(R.string.app_name),
                Icon.createWithResource(appContext, R.drawable.ic_qs_tile_adaptive_hz),
                ContextCompat.getMainExecutor(appContext)
            ) { result ->
                val mapped = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> RequestResult.ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> RequestResult.ALREADY_ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> RequestResult.NOT_ADDED
                    else -> RequestResult.FAILED
                }

                if (mapped == RequestResult.ADDED || mapped == RequestResult.ALREADY_ADDED) {
                    setQuickSettingsTileAdded(appContext, true)
                }

                onResult(mapped)
            }
        }.onFailure {
            onResult(RequestResult.FAILED)
        }
    }

    fun requestPinWidget(
        context: Context,
        onResult: (RequestResult) -> Unit
    ) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)

        if (!manager.isRequestPinAppWidgetSupported) {
            onResult(RequestResult.UNSUPPORTED)
            return
        }

        val provider = ComponentName(appContext, AdaptiveHzWidgetProvider::class.java)
        val successIntent = Intent(appContext, AdaptiveHzWidgetProvider::class.java).apply {
            action = AdaptiveHzWidgetProvider.ACTION_WIDGET_PINNED
            component = provider
        }
        val successCallback = PendingIntent.getBroadcast(
            appContext,
            204,
            successIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            val accepted = manager.requestPinAppWidget(provider, null, successCallback)
            onResult(if (accepted) RequestResult.REQUESTED else RequestResult.FAILED)
        }.onFailure {
            onResult(RequestResult.FAILED)
        }
    }
}
