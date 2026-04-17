package com.mahmutalperenunal.adaptivehz.core

// Holds runtime checks for whether required permissions and services are properly enabled.

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object AdaptiveHzRuntimeState {

    fun isSetupReady(context: Context): Boolean {
        // Setup is considered ready only if both ADB permission and Accessibility Service are enabled
        return isAdbReady(context) && isAccessibilityReady(context)
    }

    fun isAdbReady(context: Context): Boolean {
        // Checks whether privileged ADB permission has been granted and persisted
        return AdaptiveHzPrefs.isAdbGranted(context)
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