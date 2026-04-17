package com.mahmutalperenunal.adaptivehz.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.mahmutalperenunal.adaptivehz.MainActivity
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.AdaptiveHzRuntimeState
import com.mahmutalperenunal.adaptivehz.core.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzMode

// Updates all widget instances and maps app state to widget UI.
object AdaptiveHzWidgetUpdater {

    fun refreshAll(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, AdaptiveHzWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(provider)

        if (widgetIds.isEmpty()) return

        // Refresh each placed widget instance individually
        widgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val views = buildRemoteViews(context, options)
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun buildRemoteViews(
        context: Context,
        options: Bundle?
    ): RemoteViews {
        val currentMode = AdaptiveHzPrefs.getCurrentMode(context)
        val setupReady = AdaptiveHzRuntimeState.isSetupReady(context)
        val compact = isCompactWidget(options)

        // Choose layout based on current widget size
        val layoutRes = if (compact) {
            R.layout.widget_adaptive_hz_compact
        } else {
            R.layout.widget_adaptive_hz
        }

        val views = RemoteViews(context.packageName, layoutRes)

        // Header always opens the app when tapped
        bindOpenAppClicks(context, views)

        setHeaderState(context, views, currentMode, setupReady)

        // Action buttons only change modes after setup is completed
        if (setupReady) {
            bindActionClicks(context, views)
        } else {
            clearActionClicks(context, views)
        }

        if (compact) {
            applyCompactSelectionState(views, currentMode, setupReady)
            applyCompactEnabledState(views, setupReady)
        } else {
            applySelectionState(views, currentMode, setupReady)
            applyEnabledState(views, setupReady)
        }

        return views
    }

    private fun isCompactWidget(options: Bundle?): Boolean {
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0

        // Use compact layout for smaller widget bounds
        return minWidth < 220 || minHeight < 110
    }

    private fun setHeaderState(
        context: Context,
        views: RemoteViews,
        currentMode: AdaptiveHzMode,
        setupReady: Boolean
    ) {
        views.setTextViewText(
            R.id.tvWidgetBadge,
            when {
                !setupReady -> context.getString(R.string.widget_badge_setup)
                currentMode == AdaptiveHzMode.OFF -> context.getString(R.string.widget_off)
                currentMode == AdaptiveHzMode.FORCE_MIN -> context.getString(R.string.widget_min)
                currentMode == AdaptiveHzMode.ADAPTIVE -> context.getString(R.string.widget_adaptive)
                currentMode == AdaptiveHzMode.FORCE_MAX -> context.getString(R.string.widget_max)
                else -> context.getString(R.string.widget_off)
            }
        )
    }

    private fun bindActionClicks(context: Context, views: RemoteViews) {
        views.setOnClickPendingIntent(
            R.id.btnWidgetOff,
            AdaptiveHzWidgetProvider.getBroadcastPendingIntent(
                context,
                AdaptiveHzWidgetProvider.ACTION_SET_OFF,
                100
            )
        )

        views.setOnClickPendingIntent(
            R.id.btnWidgetMin,
            AdaptiveHzWidgetProvider.getBroadcastPendingIntent(
                context,
                AdaptiveHzWidgetProvider.ACTION_SET_MIN,
                101
            )
        )

        views.setOnClickPendingIntent(
            R.id.btnWidgetAdaptive,
            AdaptiveHzWidgetProvider.getBroadcastPendingIntent(
                context,
                AdaptiveHzWidgetProvider.ACTION_SET_ADAPTIVE,
                102
            )
        )

        views.setOnClickPendingIntent(
            R.id.btnWidgetMax,
            AdaptiveHzWidgetProvider.getBroadcastPendingIntent(
                context,
                AdaptiveHzWidgetProvider.ACTION_SET_MAX,
                103
            )
        )
    }

    private fun clearActionClicks(context: Context, views: RemoteViews) {
        // Redirect mode taps to the app until setup is completed
        val openAppIntent = getLaunchAppPendingIntent(context)

        views.setOnClickPendingIntent(R.id.btnWidgetOff, openAppIntent)
        views.setOnClickPendingIntent(R.id.btnWidgetMin, openAppIntent)
        views.setOnClickPendingIntent(R.id.btnWidgetAdaptive, openAppIntent)
        views.setOnClickPendingIntent(R.id.btnWidgetMax, openAppIntent)
    }

    private fun bindOpenAppClicks(context: Context, views: RemoteViews) {
        val launchIntent = getLaunchAppPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widgetRoot, launchIntent)
        views.setOnClickPendingIntent(R.id.tvWidgetTitle, launchIntent)
        views.setOnClickPendingIntent(R.id.tvWidgetBadge, launchIntent)
    }

    private fun getLaunchAppPendingIntent(context: Context): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            200,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun applySelectionState(
        views: RemoteViews,
        currentMode: AdaptiveHzMode,
        setupReady: Boolean
    ) {
        // Selected state stays visually disabled until setup is ready
        val selected = if (setupReady) {
            R.drawable.bg_widget_button_selected
        } else {
            R.drawable.bg_widget_button_disabled
        }

        val normal = if (setupReady) {
            R.drawable.bg_widget_button
        } else {
            R.drawable.bg_widget_button_disabled
        }

        // Highlight the currently active mode
        views.setInt(R.id.btnWidgetOff, "setBackgroundResource",
            if (currentMode == AdaptiveHzMode.OFF) selected else normal)
        views.setInt(R.id.btnWidgetMin, "setBackgroundResource",
            if (currentMode == AdaptiveHzMode.FORCE_MIN) selected else normal)
        views.setInt(R.id.btnWidgetAdaptive, "setBackgroundResource",
            if (currentMode == AdaptiveHzMode.ADAPTIVE) selected else normal)
        views.setInt(R.id.btnWidgetMax, "setBackgroundResource",
            if (currentMode == AdaptiveHzMode.FORCE_MAX) selected else normal)
    }

    private fun applyCompactSelectionState(
        views: RemoteViews,
        currentMode: AdaptiveHzMode,
        setupReady: Boolean
    ) {
        applySelectionState(views, currentMode, setupReady)
    }

    private fun applyEnabledState(
        views: RemoteViews,
        setupReady: Boolean
    ) {
        // Dim controls to indicate setup is still incomplete
        val alpha = if (setupReady) 1.0f else 0.45f

        views.setFloat(R.id.btnWidgetOff, "setAlpha", alpha)
        views.setFloat(R.id.btnWidgetMin, "setAlpha", alpha)
        views.setFloat(R.id.btnWidgetAdaptive, "setAlpha", alpha)
        views.setFloat(R.id.btnWidgetMax, "setAlpha", alpha)

        views.setFloat(R.id.ivWidgetOff, "setAlpha", alpha)
        views.setFloat(R.id.ivWidgetMin, "setAlpha", alpha)
        views.setFloat(R.id.ivWidgetAdaptive, "setAlpha", alpha)
        views.setFloat(R.id.ivWidgetMax, "setAlpha", alpha)

        views.setFloat(R.id.tvWidgetOff, "setAlpha", alpha)
        views.setFloat(R.id.tvWidgetMin, "setAlpha", alpha)
        views.setFloat(R.id.tvWidgetAdaptive, "setAlpha", alpha)
        views.setFloat(R.id.tvWidgetMax, "setAlpha", alpha)
    }

    private fun applyCompactEnabledState(
        views: RemoteViews,
        setupReady: Boolean
    ) {
        applyEnabledState(views, setupReady)
    }
}