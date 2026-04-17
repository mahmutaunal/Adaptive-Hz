package com.mahmutalperenunal.adaptivehz.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.mahmutalperenunal.adaptivehz.core.AdaptiveHzActionHandler
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzMode

// AppWidgetProvider that receives widget updates and mode change actions.
class AdaptiveHzWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // Update every active widget instance
        appWidgetIds.forEach { widgetId ->
            AdaptiveHzWidgetUpdater.updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Rebuild the widget when its size or bounds change
        AdaptiveHzWidgetUpdater.updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // Dispatch widget button actions and refresh requests
        when (intent.action) {
            ACTION_SET_OFF -> AdaptiveHzActionHandler.applyMode(context, AdaptiveHzMode.OFF)
            ACTION_SET_MIN -> AdaptiveHzActionHandler.applyMode(context, AdaptiveHzMode.FORCE_MIN)
            ACTION_SET_ADAPTIVE -> AdaptiveHzActionHandler.applyMode(context, AdaptiveHzMode.ADAPTIVE)
            ACTION_SET_MAX -> AdaptiveHzActionHandler.applyMode(context, AdaptiveHzMode.FORCE_MAX)
            ACTION_REFRESH -> AdaptiveHzWidgetUpdater.refreshAll(context)
        }
    }

    companion object {
        const val ACTION_SET_OFF =
            "com.mahmutalperenunal.adaptivehz.widget.action.SET_OFF"
        const val ACTION_SET_MIN =
            "com.mahmutalperenunal.adaptivehz.widget.action.SET_MIN"
        const val ACTION_SET_ADAPTIVE =
            "com.mahmutalperenunal.adaptivehz.widget.action.SET_ADAPTIVE"
        const val ACTION_SET_MAX =
            "com.mahmutalperenunal.adaptivehz.widget.action.SET_MAX"
        const val ACTION_REFRESH =
            "com.mahmutalperenunal.adaptivehz.widget.action.REFRESH"

        fun getBroadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            // Target this provider directly so the widget action is handled reliably
            val intent = Intent(context, AdaptiveHzWidgetProvider::class.java).apply {
                this.action = action
                component = ComponentName(context, AdaptiveHzWidgetProvider::class.java)
            }

            // Use stable request codes so each widget action keeps its own PendingIntent
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}