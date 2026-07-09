package com.mahmutalperenunal.adaptivehz.core.engine

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.mahmutalperenunal.adaptivehz.core.prefs.AdaptiveHzPrefs
import com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzService

/**
 * Central runtime state checks used during setup and diagnostics.
 */
object AdaptiveHzRuntimeState {

    /**
     * Represents the current Accessibility Service health state.
     */
    enum class AccessibilityState {
        DISABLED,
        WORKING,
        BROKEN
    }

    private const val SERVICE_CLASS_NAME = "com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzService"
    const val HEARTBEAT_TIMEOUT_MS = 8_000L

    /**
     * Verifies whether the minimum required setup is completed.
     */
    fun isSetupReady(context: Context): Boolean {
        val appContext = context.applicationContext
        // Setup is considered ready only if both ADB permission and Accessibility Service are enabled
        return isAdbReady(appContext) && isAccessibilityReady(appContext)
    }

    fun isAdbReady(context: Context): Boolean {
        val appContext = context.applicationContext
        // Checks whether privileged ADB permission has been granted and persisted
        return AdaptiveHzPrefs.isAdbGranted(appContext)
    }

    /**
     * Uses heartbeat timestamps to detect broken accessibility states.
     */
    fun getAccessibilityState(context: Context): AccessibilityState {
        val appContext = context.applicationContext
        val configured = isAccessibilityConfigured(appContext)
        if (!configured) return AccessibilityState.DISABLED

        val now = System.currentTimeMillis()
        val lastHeartbeat = AdaptiveHzPrefs.getAccessibilityLastHeartbeat(appContext)
        val alive = now - lastHeartbeat <= HEARTBEAT_TIMEOUT_MS

        return if (alive) AccessibilityState.WORKING else AccessibilityState.BROKEN
    }

    /**
     * Checks whether the service is enabled in system accessibility settings.
     */
    fun isAccessibilityConfigured(context: Context): Boolean {
        val appContext = context.applicationContext
        val expectedShort = ComponentName(appContext, AdaptiveHzService::class.java).flattenToShortString()
        val expectedFull = ComponentName(appContext, AdaptiveHzService::class.java).flattenToString()

        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabledServices.split(':').any {
            it == expectedShort || it == expectedFull || it.endsWith("/$SERVICE_CLASS_NAME")
        }
    }

    /**
     * Confirms that the accessibility service is actively running.
     */
    fun isAccessibilityReady(context: Context): Boolean {
        val appContext = context.applicationContext
        // Primary check using AccessibilityManager API
        return runCatching {
            val accessibilityManager =
                appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            enabledServices.any { service ->
                // Build full service ID to compare with our service
                val id = service.resolveInfo.serviceInfo.packageName + "/" +
                        service.resolveInfo.serviceInfo.name

                id == "${appContext.packageName}/com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzService" ||
                        service.resolveInfo.serviceInfo.packageName == appContext.packageName &&
                        service.resolveInfo.serviceInfo.name == "com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzService"
            }
        }.getOrElse {
            // Fallback: manually read enabled services from secure settings if API call fails
            // Raw colon-separated list of enabled accessibility services
            val enabledServices =
                Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()

            enabledServices.contains("${appContext.packageName}/com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzService")
        }
    }
}