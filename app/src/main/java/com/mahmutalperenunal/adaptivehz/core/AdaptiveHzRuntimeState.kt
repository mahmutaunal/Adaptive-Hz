package com.mahmutalperenunal.adaptivehz.core

// Holds runtime checks for whether required permissions and services are properly enabled.

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object AdaptiveHzRuntimeState {

    enum class AccessibilityState {
        DISABLED,
        WORKING,
        BROKEN
    }

    private const val SERVICE_CLASS_NAME = "com.mahmutalperenunal.adaptivehz.core.AdaptiveHzService"
    private const val HEARTBEAT_TIMEOUT_MS = 8_000L

    fun isSetupReady(context: Context): Boolean {
        // Setup is considered ready only if both ADB permission and Accessibility Service are enabled
        return isAdbReady(context) && isAccessibilityReady(context)
    }

    fun isAdbReady(context: Context): Boolean {
        // Checks whether privileged ADB permission has been granted and persisted
        return AdaptiveHzPrefs.isAdbGranted(context)
    }

    fun getAccessibilityState(context: Context): AccessibilityState {
        val configured = isAccessibilityConfigured(context)
        if (!configured) return AccessibilityState.DISABLED

        val now = System.currentTimeMillis()
        val lastHeartbeat = AdaptiveHzPrefs.getAccessibilityLastHeartbeat(context)
        val alive = now - lastHeartbeat <= HEARTBEAT_TIMEOUT_MS

        return if (alive) AccessibilityState.WORKING else AccessibilityState.BROKEN
    }

    fun isAccessibilityConfigured(context: Context): Boolean {
        val expectedShort = ComponentName(context, AdaptiveHzService::class.java).flattenToShortString()
        val expectedFull = ComponentName(context, AdaptiveHzService::class.java).flattenToString()

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabledServices.split(':').any {
            it == expectedShort || it == expectedFull || it.endsWith("/$SERVICE_CLASS_NAME")
        }
    }

    fun isAccessibilityReady(context: Context): Boolean {
        // Primary check using AccessibilityManager API
        return runCatching {
            val accessibilityManager =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            enabledServices.any { service ->
                // Build full service ID to compare with our service
                val id = service.resolveInfo.serviceInfo.packageName + "/" +
                        service.resolveInfo.serviceInfo.name

                id == "${context.packageName}/com.mahmutalperenunal.adaptivehz.core.AdaptiveHzService" ||
                        service.resolveInfo.serviceInfo.packageName == context.packageName &&
                        service.resolveInfo.serviceInfo.name == "com.mahmutalperenunal.adaptivehz.core.AdaptiveHzService"
            }
        }.getOrElse {
            // Fallback: manually read enabled services from secure settings if API call fails
            // Raw colon-separated list of enabled accessibility services
            val enabledServices =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()

            enabledServices.contains("${context.packageName}/com.mahmutalperenunal.adaptivehz.core.AdaptiveHzService")
        }
    }
}