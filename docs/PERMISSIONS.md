# Permissions and Privileged Access

This document explains why Adaptive Hz requests each Android permission or special-access capability. It should be reviewed whenever the manifest or privileged code changes.

## `android.permission.WRITE_SECURE_SETTINGS`

**Purpose:** Writes supported refresh-rate related values through Android settings providers.

**Grant model:** This is a protected permission and is not granted through a normal runtime dialog. The documented setup uses ADB. On rooted devices, Adaptive Hz can optionally ask the local root manager to execute the equivalent narrowly scoped `pm grant` command.

**Risk:** The permission can modify protected device settings. A coding error or unsupported OEM key could produce undesirable display behavior.

**Mitigation:** Writes should be limited to known refresh-rate keys, validated, logged without private content, and restored to system-controlled behavior where appropriate.

Grant:

```bash
adb shell pm grant com.mahmutalperenunal.adaptivehz android.permission.WRITE_SECURE_SETTINGS
```

Revoke:

```bash
adb shell pm revoke com.mahmutalperenunal.adaptivehz android.permission.WRITE_SECURE_SETTINGS
```

## Accessibility Service

**Purpose:** Receives interaction and window/event metadata used to decide when to switch refresh rates.

**Declared capability:** The service subscribes to all Accessibility event types and declares that it can retrieve window content. The current implementation is expected to use event metadata rather than collecting text or node content.

**Risk:** Accessibility is a highly sensitive capability because Android may expose on-screen context to enabled services.

**Mitigation:** Do not read or persist text, password fields, notification/message content, screenshots, or Accessibility node trees unless a future feature is explicitly reviewed, documented, and consented to. Keep diagnostics limited to necessary metadata.

## `android.permission.QUERY_ALL_PACKAGES`

**Purpose:** Lists installed applications for per-app refresh profiles and related UI.

**Data involved:** Package name, user-visible label, system-app status, and update timestamp.

**Risk:** An installed-app list can reveal sensitive user interests or affiliations.

**Mitigation:** Process locally, store only configured package identifiers when needed, and never add transmission or analytics without explicit review.

## `android.permission.PACKAGE_USAGE_STATS`

**Purpose:** Reads recent usage metadata to show recently used apps and support app-aware controls.

**Grant model:** The user enables Usage Access in system settings.

**Data involved:** Package name, last-used time, and foreground-duration metadata exposed by Android.

**Mitigation:** Use only for user-facing local functionality and degrade gracefully when access is denied.

## Shizuku permission

**Permission:** `moe.shizuku.manager.permission.API_V23`

**Purpose:** Enables optional advanced local input detection through the separately installed Shizuku service.

**Risk:** Shizuku can expose elevated Android APIs depending on how its service is started.

**Mitigation:** Request access explicitly, keep the user-service interface narrow, validate binder lifecycle and caller assumptions, and ensure the core app still works without Shizuku.

## Root access

Root is not an Android manifest permission. Adaptive Hz only attempts a local `su` command when the user chooses the root-assisted permission path.

**Current intended command:** Grant this package `WRITE_SECURE_SETTINGS`.

**Mitigation:** Avoid concatenating untrusted input into shell commands, use a timeout, cap captured output, and do not treat root presence as user consent.

## `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

**Purpose:** Opens Android's exemption flow for users who choose Stability Mode on devices that aggressively stop background services.

**Risk:** Exempt applications may consume more background resources.

**Mitigation:** Keep optional, explain the trade-off, and avoid requesting it when the normal service lifecycle is sufficient.

## Foreground service permissions

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`

**Purpose:** Runs the optional stability service while showing the required persistent notification.

**Mitigation:** Start only for an enabled user-facing mode, provide a clear notification, and stop promptly when no longer needed.

## `android.permission.POST_NOTIFICATIONS`

**Purpose:** Displays stability, service-health, and recovery notifications on Android versions that require runtime notification permission.

**Mitigation:** Request in context and keep the application usable when denied, except where Android requires a foreground-service notification.

## `android.permission.RECEIVE_BOOT_COMPLETED`

**Purpose:** Restores enabled behavior after boot and handles application replacement/update events.

**Mitigation:** The receiver is not exported. It should only restore behavior the user previously enabled.

## Exported components

- `MainActivity` is exported because it is the launcher activity.
- `AdaptiveHzTileService` is exported but protected by Android's `BIND_QUICK_SETTINGS_TILE` permission.
- The Shizuku provider is exported as required by the integration and must remain configured according to Shizuku's official guidance.
- Internal receivers, Accessibility service, stability service, and widget receiver are not exported.

Any new exported component must document its caller, permission boundary, accepted inputs, and abuse cases.
