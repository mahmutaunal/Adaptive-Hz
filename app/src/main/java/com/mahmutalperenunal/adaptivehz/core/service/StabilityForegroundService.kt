package com.mahmutalperenunal.adaptivehz.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import android.os.Handler
import android.provider.Settings
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzRuntimeState
import com.mahmutalperenunal.adaptivehz.core.health.AccessibilityHealthMonitor
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs

/**
 * Keeps the app process alive more reliably on aggressive OEM ROMs (e.g. HyperOS)
 * by running a minimal foreground service with a persistent notification.
 *
 * This does NOT perform any heavy work — it's purely for stability.
 */
class StabilityForegroundService : Service() {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Delays shutdown briefly to avoid notification flicker during quick toggles.
     */
    private val delayedStopRunnable = Runnable {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val healthMonitorRunnable = object : Runnable {
        override fun run() {
            if (
                AdaptiveHzPrefs.isKeepAliveEnabled(this@StabilityForegroundService) &&
                AdaptiveHzPrefs.isAppEnabled(this@StabilityForegroundService)
            ) {
                AccessibilityHealthMonitor.check(
                    context = this@StabilityForegroundService,
                    reason = "stability_foreground_service"
                )

                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, buildNotification())

                mainHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        startHealthMonitor()
    }

    /**
     * Handles notification actions and foreground lifecycle updates.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_ON -> {
                cancelPendingStop()
                AdaptiveHzActionHandler.turnOn(this)
            }

            ACTION_TOGGLE_OFF -> {
                AdaptiveHzActionHandler.turnOffForNotification(this)
                scheduleDelayedStop()
            }

            ACTION_SET_ADAPTIVE -> {
                cancelPendingStop()
                AdaptiveHzActionHandler.setAdaptive(this)
            }

            ACTION_SET_MIN -> {
                cancelPendingStop()
                AdaptiveHzActionHandler.setMinimum(this)
            }

            ACTION_SET_MAX -> {
                cancelPendingStop()
                AdaptiveHzActionHandler.setMaximum(this)
            }

            ACTION_STOP -> {
                cancelPendingStop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REFRESH_NOTIFICATION -> {
                cancelPendingStop()
            }

            null -> {
                cancelPendingStop()
            }
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Builds the persistent foreground notification with quick mode actions.
     */
    private fun buildNotification(): Notification {
        ensureChannel()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val currentMode = AdaptiveHzActionHandler.getCurrentMode(this)

        val accessibilityState = AdaptiveHzRuntimeState.getAccessibilityState(this)
        val needsRecovery =
            currentMode != AdaptiveHzMode.OFF &&
                    accessibilityState == AdaptiveHzRuntimeState.AccessibilityState.BROKEN

        val contentText = when (currentMode) {
            AdaptiveHzMode.OFF -> getString(R.string.label_off)
            AdaptiveHzMode.ADAPTIVE -> getString(R.string.mode_adaptive)
            AdaptiveHzMode.FORCE_MIN -> getString(R.string.minimum)
            AdaptiveHzMode.FORCE_MAX -> getString(R.string.maximum)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                if (needsRecovery) {
                    getString(R.string.stability_notification_recovery_title)
                } else {
                    getString(R.string.stability_notification_title)
                }
            )
            .setContentText(
                if (needsRecovery) {
                    getString(R.string.stability_notification_recovery_text)
                } else {
                    contentText
                }
            )
            .setContentIntent(
                if (needsRecovery) {
                    createAccessibilitySettingsIntent()
                } else {
                    contentIntent
                }
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)

        if (needsRecovery) {
            builder.addAction(
                0,
                getString(R.string.stability_notification_recovery_action),
                createAccessibilitySettingsIntent()
            )

            builder.addAction(
                0,
                getString(R.string.disable),
                createServiceIntent(ACTION_TOGGLE_OFF)
            )
        } else if (currentMode == AdaptiveHzMode.OFF) {
            builder.addAction(
                0,
                getString(R.string.enable),
                createServiceIntent(ACTION_TOGGLE_ON)
            )
        } else {
            builder.addAction(
                0,
                getString(R.string.disable),
                createServiceIntent(ACTION_TOGGLE_OFF)
            )

            val alternatives = AdaptiveHzActionHandler.getAlternativeModesForNotification(this)

            alternatives.forEach { mode ->
                val label = when (mode) {
                    AdaptiveHzMode.ADAPTIVE -> getString(R.string.mode_adaptive)
                    AdaptiveHzMode.FORCE_MIN -> getString(R.string.minimum)
                    AdaptiveHzMode.FORCE_MAX -> getString(R.string.maximum)
                    else -> return@forEach
                }

                val action = when (mode) {
                    AdaptiveHzMode.ADAPTIVE -> ACTION_SET_ADAPTIVE
                    AdaptiveHzMode.FORCE_MIN -> ACTION_SET_MIN
                    AdaptiveHzMode.FORCE_MAX -> ACTION_SET_MAX
                }

                builder.addAction(
                    0,
                    label,
                    createServiceIntent(action)
                )
            }
        }

        return builder.build()
    }

    /**
     * Creates immutable notification action intents.
     */
    private fun createServiceIntent(action: String): PendingIntent {
        val intent = Intent(this, StabilityForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Creates the notification channel on first launch.
     */
    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.stability_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    /**
     * Keeps the service alive briefly after switching OFF.
     */
    private fun scheduleDelayedStop() {
        cancelPendingStop()
        mainHandler.postDelayed(delayedStopRunnable, OFF_GRACE_PERIOD_MS)
    }

    private fun cancelPendingStop() {
        mainHandler.removeCallbacks(delayedStopRunnable)
    }

    private fun createAccessibilitySettingsIntent(): PendingIntent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return PendingIntent.getActivity(
            this,
            ACTION_OPEN_ACCESSIBILITY_SETTINGS.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun startHealthMonitor() {
        mainHandler.removeCallbacks(healthMonitorRunnable)
        mainHandler.postDelayed(healthMonitorRunnable, HEALTH_CHECK_INTERVAL_MS)
    }

    private fun stopHealthMonitor() {
        mainHandler.removeCallbacks(healthMonitorRunnable)
    }

    override fun onDestroy() {
        cancelPendingStop()
        stopHealthMonitor()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "adaptive_hz_stability"
        private const val NOTIF_ID = 1001

        private const val ACTION_TOGGLE_ON = "action_toggle_on"
        private const val ACTION_TOGGLE_OFF = "action_toggle_off"
        private const val ACTION_SET_ADAPTIVE = "action_set_adaptive"
        private const val ACTION_SET_MIN = "action_set_min"
        private const val ACTION_SET_MAX = "action_set_max"
        private const val ACTION_STOP = "com.mahmutalperenunal.adaptivehz.action.STOP_FOREGROUND"

        private const val ACTION_REFRESH_NOTIFICATION = "com.mahmutalperenunal.adaptivehz.action.REFRESH_NOTIFICATION"

        private const val OFF_GRACE_PERIOD_MS = 60_000L

        private const val ACTION_OPEN_ACCESSIBILITY_SETTINGS =
            "com.mahmutalperenunal.adaptivehz.action.OPEN_ACCESSIBILITY_SETTINGS"

        private const val HEALTH_CHECK_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, StabilityForegroundService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, StabilityForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            appContext.startService(intent)
        }

        /**
         * Refreshes notification state without restarting engine logic.
         */
        fun refreshNotification(context: Context) {
            val appContext = context.applicationContext
            if (!AdaptiveHzPrefs.isKeepAliveEnabled(appContext)) return
            if (!AdaptiveHzPrefs.isAppEnabled(appContext)) return

            val intent = Intent(appContext, StabilityForegroundService::class.java).apply {
                action = ACTION_REFRESH_NOTIFICATION
            }

            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Throwable) { }
        }
    }
}