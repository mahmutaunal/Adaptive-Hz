# =============================================================================
# Adaptive Hz - R8 / ProGuard configuration
# =============================================================================
# Goal:
# - Keep release builds optimized and obfuscated.
# - Preserve only runtime-critical entry points used by Android, Shizuku, Binder,
#   and AIDL.
# - Avoid broad project-wide keep rules that would reduce R8 effectiveness.
#
# Release issue this file protects against:
# - Shizuku input monitoring can work in debug but fail in signed release builds
#   when R8 renames/removes UserService, Binder, or AIDL classes used across
#   process boundaries.
# =============================================================================


# =============================================================================
# 1) Release logging policy
# =============================================================================
# Remove noisy logs from release builds.
# Keep Log.w and Log.e so release APKs still provide diagnostics for Shizuku,
# accessibility, OEM-specific refresh-rate behavior, and permission failures.
#
# Do not put required side effects inside Log.v/d/i arguments; R8 may remove the
# entire call expression.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}


# =============================================================================
# 2) Runtime metadata
# =============================================================================
# Keep metadata commonly required by AndroidX, Compose, Material, Shizuku,
# generated Binder classes, and optimized Android builds.
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod
-keepattributes Signature


# =============================================================================
# 3) Shizuku public API and framework integration
# =============================================================================
# Adaptive Hz binds a Shizuku UserService for privileged input monitoring.
# Keep the Shizuku API surface used for permission checks, binder access,
# user-service binding, and system-service access.
#
# This is narrower than keeping the whole dependency, but stable enough for
# release builds.
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.Shizuku$* { *; }
-keep class rikka.shizuku.ShizukuProvider { *; }
-keep class rikka.shizuku.ShizukuBinderWrapper { *; }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
-keep class rikka.shizuku.ShizukuServiceConnection { *; }
-keep class rikka.shizuku.SystemServiceHelper { *; }

# Shizuku internal/server Binder contracts used by the client API.
-keep class moe.shizuku.server.IShizukuService { *; }
-keep class moe.shizuku.server.IShizukuService$Stub { *; }
-keep class moe.shizuku.server.IShizukuService$Stub$Proxy { *; }
-keep class moe.shizuku.server.IShizukuApplication { *; }
-keep class moe.shizuku.server.IShizukuApplication$Stub { *; }
-keep class moe.shizuku.server.IShizukuApplication$Stub$Proxy { *; }
-keep class moe.shizuku.server.IShizukuServiceConnection { *; }
-keep class moe.shizuku.server.IShizukuServiceConnection$Stub { *; }
-keep class moe.shizuku.server.IShizukuServiceConnection$Stub$Proxy { *; }

# Shizuku may reference optional platform/server classes depending on API level
# and environment. These warnings are not actionable for this app.
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**


# =============================================================================
# 4) Adaptive Hz Shizuku UserService and Binder boundary
# =============================================================================
# These classes are used across app process <-> Shizuku privileged process.
# They must not be renamed, removed, or structurally optimized in a way that
# breaks IPC in release builds.
#
# Expected contents include:
# - InputMonitorUserService
# - ShizukuInputManager
# - IInputMonitorService / Stub / Proxy
# - IInputEventCallback / Stub / Proxy
-keep class com.mahmutalperenunal.adaptivehz.core.shizuku.** { *; }
-keep interface com.mahmutalperenunal.adaptivehz.core.shizuku.** { *; }

# Additional future-proofing for generated AIDL names if package structure is
# moved later. These rules are intentionally limited to the known IPC contracts.
-keep class **.IInputMonitorService { *; }
-keep class **.IInputMonitorService$Stub { *; }
-keep class **.IInputMonitorService$Stub$Proxy { *; }
-keep class **.IInputEventCallback { *; }
-keep class **.IInputEventCallback$Stub { *; }
-keep class **.IInputEventCallback$Stub$Proxy { *; }


# =============================================================================
# 5) Android framework entry points
# =============================================================================
# Android Gradle Plugin normally keeps manifest-declared components. These rules
# are explicit because these classes are critical to app behavior and keeping
# their names stable improves release crash reports during OEM-specific debugging.

# Main accessibility/service pipeline.
-keep class com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzService { *; }

# Quick Settings tile integration.
-keep class com.mahmutalperenunal.adaptivehz.core.service.AdaptiveHzTileService { *; }

# Boot/package replacement startup flow.
-keep class com.mahmutalperenunal.adaptivehz.core.system.BootReceiver { *; }

# Home-screen widget integration.
-keep class com.mahmutalperenunal.adaptivehz.widget.AdaptiveHzWidgetProvider { *; }
-keep class com.mahmutalperenunal.adaptivehz.widget.AdaptiveHzWidgetUpdater { *; }


# =============================================================================
# 6) Optional dependency warnings
# =============================================================================
# Keep warnings quiet for optional or compile-time-only dependencies that may be
# referenced by libraries during R8 analysis. These are not broad keep rules.
-dontwarn kotlinx.**
-dontwarn org.jetbrains.**
-dontwarn com.google.accompanist.**


# =============================================================================
# 7) Rules intentionally avoided
# =============================================================================
# Do not add these unless a real release crash proves they are required:
#
# -keep class com.mahmutalperenunal.adaptivehz.** { *; }
# -keep class androidx.** { *; }
# -keep class com.google.android.material.** { *; }
# -keep class androidx.compose.** { *; }
#
# Reason:
# Broad keep rules make the APK larger, reduce optimization, and hide real R8
# integration problems. Adaptive Hz only needs stable names for Android runtime
# entry points and cross-process Shizuku/Binder contracts.
# =============================================================================