package com.mahmutalperenunal.adaptivehz.core.health

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mahmutalperenunal.adaptivehz.R
import com.mahmutalperenunal.adaptivehz.core.engine.AdaptiveHzRuntimeState
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.service.StabilityForegroundService

/**
 * Central health monitor for AdaptiveHzService.
 *
 * Detects when Accessibility Service is expected to be active but stops responding.
 *
 * Behavior:
 * - When Stability Mode is enabled, the foreground notification is refreshed into recovery state.
 * - When Stability Mode is disabled, a standard recovery notification is shown.
 * - Notifications are protected by state transition + cooldown logic.
 */
object AccessibilityHealthMonitor {

    private const val TAG = "AccessibilityHealthMonitor"

    private const val CHANNEL_ID = "adaptive_hz_health"
    private const val NOTIFICATION_ID = 2001

    private const val COOLDOWN_MS = 3 * 60 * 60 * 1000L // 3 hours

    private const val STATE_DISABLED = "DISABLED"
    private const val STATE_WORKING = "WORKING"
    private const val STATE_BROKEN = "BROKEN"

    /**
     * Runs a single health check.
     *
     * Call this from:
     * - StabilityForegroundService periodic loop
     * - MainActivity ON_RESUME
     * - BootReceiver after boot/update
     */
    fun check(context: Context, reason: String) {
        val appContext = context.applicationContext

        val mode = AdaptiveHzPrefs.getCurrentMode(appContext)
        val accessibilityState = AdaptiveHzRuntimeState.getAccessibilityState(appContext)
        val keepAliveEnabled = AdaptiveHzPrefs.isKeepAliveEnabled(appContext)

        val expectedToWork = mode != AdaptiveHzMode.OFF
        val currentStateName = accessibilityState.name
        val previousStateName = AdaptiveHzPrefs.getAccessibilityLastHealthState(appContext)

        AdaptiveHzPrefs.setAccessibilityLastHealthState(appContext, currentStateName)

        Log.d(
            TAG,
            "check reason=$reason mode=$mode accessibility=$accessibilityState " +
                    "keepAlive=$keepAliveEnabled previous=$previousStateName"
        )

        if (!expectedToWork) {
            cancelRecoveryNotification(appContext)
            return
        }

        when (accessibilityState) {
            AdaptiveHzRuntimeState.AccessibilityState.WORKING -> {
                cancelRecoveryNotification(appContext)
            }

            AdaptiveHzRuntimeState.AccessibilityState.DISABLED,
            AdaptiveHzRuntimeState.AccessibilityState.BROKEN -> {
                if (!shouldNotify(appContext, previousStateName, currentStateName)) return

                AdaptiveHzPrefs.setAccessibilityLastRecoveryNotifiedAt(
                    appContext,
                    System.currentTimeMillis()
                )

                if (keepAliveEnabled) {
                    StabilityForegroundService.refreshNotification(appContext)
                } else {
                    showRecoveryNotification(appContext, accessibilityState)
                }
            }
        }
    }

    private fun shouldNotify(
        context: Context,
        previousStateName: String,
        currentStateName: String
    ): Boolean {
        val isBrokenState = currentStateName == STATE_BROKEN || currentStateName == STATE_DISABLED
        if (!isBrokenState) return false

        val previousWasHealthy = previousStateName == STATE_WORKING || previousStateName.isBlank()
        val stateChangedToBroken = previousWasHealthy && currentStateName != previousStateName

        val now = System.currentTimeMillis()
        val lastNotifiedAt = AdaptiveHzPrefs.getAccessibilityLastRecoveryNotifiedAt(context)
        val cooldownPassed = now - lastNotifiedAt >= COOLDOWN_MS

        return stateChangedToBroken || cooldownPassed
    }

    @Suppress("MissingPermission")
    private fun showRecoveryNotification(
        context: Context,
        state: AdaptiveHzRuntimeState.AccessibilityState
    ) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "Cannot show recovery notification: notification permission missing")
            return
        }

        ensureChannel(context)

        val title = context.getString(R.string.accessibility_health_notification_title)

        val text = when (state) {
            AdaptiveHzRuntimeState.AccessibilityState.DISABLED ->
                context.getString(R.string.accessibility_health_notification_disabled_text)

            AdaptiveHzRuntimeState.AccessibilityState.BROKEN ->
                context.getString(R.string.accessibility_health_notification_broken_text)

            AdaptiveHzRuntimeState.AccessibilityState.WORKING ->
                context.getString(R.string.accessibility_health_notification_broken_text)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(createAccessibilitySettingsIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(
                0,
                context.getString(R.string.accessibility_health_notification_action),
                createAccessibilitySettingsIntent(context)
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (securityException: SecurityException) {
            Log.w(
                TAG,
                "Cannot show recovery notification: notification permission rejected",
                securityException
            )
        }
    }

    fun cancelRecoveryNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        } catch (securityException: SecurityException) {
            Log.w(
                TAG,
                "Cannot cancel recovery notification",
                securityException
            )
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.accessibility_health_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun createAccessibilitySettingsIntent(context: Context): PendingIntent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return PendingIntent.getActivity(
            context,
            Settings.ACTION_ACCESSIBILITY_SETTINGS.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}