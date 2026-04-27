package com.mahmutalperenunal.adaptivehz.core

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
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzMode
import android.os.Handler

/**
 * Keeps the app process alive more reliably on aggressive OEM ROMs (e.g. HyperOS)
 * by running a minimal foreground service with a persistent notification.
 *
 * This does NOT perform any heavy work — it's purely for stability.
 */
class StabilityForegroundService : Service() {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val delayedStopRunnable = Runnable {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
    }

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

        val contentText = when (currentMode) {
            AdaptiveHzMode.OFF -> getString(R.string.label_off)
            AdaptiveHzMode.ADAPTIVE -> getString(R.string.mode_adaptive)
            AdaptiveHzMode.FORCE_MIN -> getString(R.string.minimum)
            AdaptiveHzMode.FORCE_MAX -> getString(R.string.maximum)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.stability_notification_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)

        if (currentMode == AdaptiveHzMode.OFF) {
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

    private fun scheduleDelayedStop() {
        cancelPendingStop()
        mainHandler.postDelayed(delayedStopRunnable, OFF_GRACE_PERIOD_MS)
    }

    private fun cancelPendingStop() {
        mainHandler.removeCallbacks(delayedStopRunnable)
    }

    override fun onDestroy() {
        cancelPendingStop()
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

        fun start(context: Context) {
            val intent = Intent(context, StabilityForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StabilityForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun refreshNotification(context: Context) {
            if (!AdaptiveHzPrefs.isKeepAliveEnabled(context)) return
            if (!AdaptiveHzPrefs.isAppEnabled(context)) return

            val intent = Intent(context, StabilityForegroundService::class.java).apply {
                action = ACTION_REFRESH_NOTIFICATION
            }

            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) { }
        }
    }
}