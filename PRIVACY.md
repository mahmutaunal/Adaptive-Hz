# Privacy Policy

**Effective date:** July 24, 2026  
**Project:** Adaptive Hz  
**Package name:** `com.mahmutalperenunal.adaptivehz`

Adaptive Hz is an open-source Android utility that adjusts supported display refresh-rate settings in response to device interaction. The application is designed to operate locally on the user's device.

## Summary

- Adaptive Hz does not contain advertising or analytics SDKs.
- The current application manifest does not request Android's `INTERNET` permission.
- The application does not create an account or operate a project-controlled backend service.
- App configuration and limited diagnostic state are stored locally on the device.
- Some optional Android privileges expose sensitive device context; they are used to provide the requested refresh-rate behavior and related configuration screens.

## Data processed on the device

Depending on enabled features and permissions, Adaptive Hz may process:

### Accessibility event metadata

The Accessibility service receives Android Accessibility events to detect interaction and app/window changes. The implementation uses event metadata such as event type, package name, content-change type, and scroll deltas.

Adaptive Hz does not intentionally collect or transmit typed text, passwords, message contents, form values, screenshots, or the Accessibility node tree. A built-in diagnostics inspector may temporarily keep up to 50 recent event-metadata records in memory. These records are cleared when the process ends or when the user clears them.

### Installed application information

To display per-app refresh-rate controls, Adaptive Hz may read installed application package names, display labels, system-app status, and update timestamps. Per-app refresh preferences are stored locally by package name.

### App usage information

When the user grants Usage Access, Adaptive Hz may read recent app usage timestamps and foreground-duration metadata to show recently used apps and support app-aware behavior. This information is processed locally.

### Interaction signals through Shizuku

When the user explicitly enables the optional Shizuku integration, Adaptive Hz uses a Shizuku user service to improve input detection. The integration is local to the device and depends on the separate Shizuku application and its permission model.

### Root command results

On rooted devices, the user may choose an optional root-assisted path to grant `WRITE_SECURE_SETTINGS`. Adaptive Hz runs narrowly scoped local shell commands and may process their exit code, standard output, and error output to determine success. This information is not intentionally transmitted by Adaptive Hz.

### Settings and diagnostics

Adaptive Hz stores local preferences such as selected refresh mode, per-app profiles, feature toggles, timing values, setup state, service health timestamps, and limited diagnostic metadata. Android log output may contain technical state such as event types, setting-write results, or package names. Users should review and redact logs before sharing them publicly.

## Permissions and special access

Adaptive Hz may request or direct the user to grant:

- **Accessibility Service:** detects interaction-related events used by the adaptive engine.
- **Write Secure Settings:** changes supported system refresh-rate settings; normally granted using ADB, with an optional root-assisted path.
- **Usage Access:** reads recent usage metadata for app-aware controls.
- **Query All Packages:** lists installed apps for per-app refresh profiles.
- **Shizuku permission:** enables optional advanced local input detection.
- **Notification permission:** shows service status, health, or recovery notifications on supported Android versions.
- **Ignore battery optimizations:** optional stability feature for devices that aggressively stop background work.
- **Run at startup:** restores user-selected behavior after boot or app update.
- **Foreground service:** keeps the optional stability service active with a persistent system notification.

See `docs/PERMISSIONS.md` for a technical explanation of each privilege.

## Storage, backup, and retention

Application preferences remain in the app's private storage until changed, cleared, or the app is uninstalled. The manifest disables Android application backup (`android:allowBackup="false"`). The application does not intentionally upload its preferences to a project-controlled service.

In-memory diagnostic events are limited to the current process lifetime. Android system logs are controlled by the operating system and development/debugging tools rather than by a project server.

## Data sharing

Adaptive Hz does not intentionally sell, rent, or share user data. It does not include a project-operated analytics, advertising, or cloud synchronization service.

Optional integrations are separate components:

- Android and the device manufacturer provide system settings, Accessibility, Usage Access, notifications, and package-management services.
- Shizuku is a separately installed third-party project with its own behavior and policies.
- Root-management software is separately installed and controls root authorization.
- Opening GitHub or Google Play links leaves the app and is subject to those services' policies.

## User choices and controls

Users can:

- disable the Adaptive Hz Accessibility service in Android Settings;
- revoke Usage Access;
- revoke Shizuku authorization;
- disable notifications or battery-optimization exemptions;
- remove per-app profiles or clear app storage;
- revoke `WRITE_SECURE_SETTINGS` through ADB;
- uninstall Adaptive Hz to remove its private local data.

Example ADB revocation command:

```bash
adb shell pm revoke com.mahmutalperenunal.adaptivehz android.permission.WRITE_SECURE_SETTINGS
```

## Children

Adaptive Hz is a device utility and is not directed to children. The project does not knowingly collect personal information from children through a project-controlled service.

## Security

Adaptive Hz minimizes network exposure by operating locally and not requesting the `INTERNET` permission in the reviewed version. However, elevated permissions can affect device behavior. Install releases only from sources you trust, review source changes, and grant only features you intend to use.

Security reports should follow `SECURITY.md` and should not be submitted as public issues.

## Changes to this policy

This policy may change when features, permissions, dependencies, or distribution methods change. Material changes should be recorded in the repository history and reflected by an updated effective date.

## Contact

For privacy questions, use the repository's GitHub Discussions or issue tracker. Do not post sensitive logs or personal information publicly. For suspected vulnerabilities, use GitHub private vulnerability reporting as described in `SECURITY.md`.
