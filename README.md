<p align="center">
  <img src="assets/logo.png" width="120" alt="Adaptive Hz Icon" />
</p>

<p align="center">
  <img src="https://img.shields.io/github/v/release/mahmutaunal/Adaptive-Hz?label=latest%20release" />
  <img src="https://img.shields.io/github/stars/mahmutaunal/Adaptive-Hz?style=social" />
  <img src="https://img.shields.io/badge/platform-Android-green" />
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" />
</p>

# Adaptive Hz

> Bring true adaptive refresh rate to devices that don’t support it — intelligently, automatically, and without root.

<p align="center">
  <a href="https://github.com/mahmutaunal/Adaptive-Hz/releases">
    <img src="https://img.shields.io/badge/Download-APK-blue?style=for-the-badge" />
  </a>
  <a href="#installation">
    <img src="https://img.shields.io/badge/How%20to%20Install-Guide-green?style=for-the-badge" />
  </a>
</p>

---

⭐ If this project helps you, consider giving it a star!

---

If you'd like to support my work, you can sponsor me on GitHub ❤️

---

## 🚀 What is Adaptive Hz?

> ⚡ Automatically switches between minimum and maximum refresh rates based on real user interaction  
> 🔋 Saves battery without sacrificing smoothness  
> 🔒 100% offline, no tracking, no ads  

Adaptive Hz dynamically switches your device between its supported minimum and maximum refresh rates based on real user interaction.

Unlike OEM implementations, it works globally across apps and focuses on real touch behavior.

---

## ⚡ Quick Start

### 1. Install the APK
Download the latest release and install Adaptive Hz.

### 2. Grant secure settings permission
Run:

```bash
adb shell pm grant com.mahmutalperenunal.adaptivehz android.permission.WRITE_SECURE_SETTINGS
```

### 3. Enable Accessibility Service
Go to:

```text
Settings → Accessibility → Installed Services → Adaptive Hz → Enable
```

### 4. (Optional) Enable Stability Mode
Recommended for devices with aggressive battery optimization.

### 5. (Optional) Enable Advanced Input Detection
For the most accurate adaptive refresh-rate behavior:
- Install Shizuku
- Start Shizuku
- Open Adaptive Hz
- Grant Shizuku permission

> 💡 Adaptive Hz works without Shizuku. Shizuku only improves interaction accuracy on some devices/apps.

---

## Overview

### Why this exists

Many mid-range Android devices (especially Samsung & Xiaomi) either:
- Do not provide true adaptive refresh rate
- Or limit it to specific apps

This results in unnecessary battery drain or poor responsiveness.

Adaptive Hz was built to fix this gap with a simple, system-wide solution.

---

## Screenshots

<p align="center">
  <img src="assets/1.png" width="250" />
  <img src="assets/2.png" width="250" />
  <img src="assets/3.png" width="250" />
  <img src="assets/4.png" width="250" />
  <img src="assets/5.png" width="250" />
</p>

- Setup & Permissions
- Dashboard (Light mode)
- Settings
- Per-App
- Dashboard (Dark mode)

Many Android devices offer multiple refresh rates (60Hz / 90Hz / 120Hz) but:

- Do not provide true adaptive switching
- Restrict adaptive behavior to specific apps
- Or aggressively kill background services

Adaptive Hz solves this by:

- Switching to Maximum Hz when you touch or scroll
- Dropping to Minimum Hz when idle
- Operating fully automatically
- Requiring no root access
- Running completely offline (no ads, no tracking)

---

## Key Features

- Interaction-based refresh switching
- Per-app refresh rate profiles
- Respect app/system refresh behavior option
- Recent apps shortcut powered by optional Usage Access
- Event coalescing to reduce noisy Accessibility event spam
- Vendor-aware refresh control and tuning
- Optional Shizuku-powered real touch detection for improved accuracy
- Optional Stability Mode (foreground service)
- Diagnostics and Accessibility Event Inspector tools
- Boot persistence
- English & Turkish localization
- Minimal, Material You UI

---

## ⚙️ Stability Mode (Optional)

Adaptive Hz includes an optional **Stability Mode** that runs a foreground service with a persistent notification.

### Why it exists
Some Android devices (especially aggressive OEM ROMs) may kill background processes, which can interrupt adaptive refresh behavior.

### What it does
- Keeps the app alive in the background
- Improves reliability of refresh rate switching
- Prevents the system from killing the service

### Trade-offs
- Shows a persistent notification while active
- Can be disabled anytime from Settings

> 💡 If you prefer a clean status bar, you can safely disable it — the app will still work, but background stability may be reduced on some devices.

---

## 🧩 Per-app Refresh Rate Profiles

Adaptive Hz supports per-app refresh rate profiles so users can control how the engine behaves for specific apps.

Available per-app modes:

| Mode | Behavior |
|------|----------|
| Default | Follows the global Adaptive Hz mode |
| Respect app/system | Does not override the app or system refresh behavior |
| Minimum | Keeps that app at the minimum refresh rate |
| Maximum | Keeps that app at the maximum refresh rate |

This is useful for apps that already manage their own refresh behavior, video playback apps, games, browsers, and battery-sensitive apps.

### Recent apps

Adaptive Hz can optionally show recently used apps on the dashboard for faster profile editing.

This requires Android's **Usage Access** permission and is optional. If Usage Access is not granted, per-app profiles still work through the full app list.

---

## Supported Vendors

### Samsung
Uses:

```
refresh_rate_mode
```

### Xiaomi / HyperOS
Uses:

```
miui_refresh_rate
```

Vendor detection is automatic.

---

## Detection Strategy

To balance responsiveness and stability:

- Immediate boost on real interaction
- Event coalescing to reduce repeated Accessibility event bursts
- Vendor-specific tuning for different OEM event behavior
- Idle fallback to return to the minimum refresh rate
- Lock screen and Always-On Display ignored
- Per-app override handling before global mode decisions
- Accessibility-based interaction detection fallback
- Optional Shizuku-powered low-level input monitoring
- Real touch verification to ignore passive UI updates (video subtitles, animations, etc.)

This prevents infinite refresh loops and unnecessary maximum-Hz usage.

---

## Permissions

| Permission | Required | Purpose |
|------------|----------|---------|
| WRITE_SECURE_SETTINGS | Yes | Modify refresh rate system setting |
| Accessibility Service | Yes | Detect global interaction |
| Shizuku Permission | Optional | Enables low-level real touch detection via input events |
| PACKAGE_USAGE_STATS / Usage Access | Optional | Show recently used apps on the dashboard |
| QUERY_ALL_PACKAGES | Optional | List installed apps for per-app profiles |
| Foreground Service | Optional | Stability Mode |
| Disable Battery Optimization | Recommended | Prevent background kill |
| Notification Permission | Conditional | Required for Stability Mode on Android 13+ |

Grant secure permission via ADB:

```bash
adb shell pm grant com.mahmutalperenunal.adaptivehz android.permission.WRITE_SECURE_SETTINGS
```

---

### 🔓 Optional Root-Assisted Setup

Adaptive Hz does **not require root** and works fully using standard Android permissions.

However, if your device is rooted, you can optionally grant the required permission directly inside the app without using ADB.

#### What it does
- Detects if root access is available on the device
- Attempts to grant `WRITE_SECURE_SETTINGS` automatically
- Falls back to manual ADB setup if the operation fails

#### Notes
- Root support is completely optional
- Behavior may vary depending on ROM and root implementation (Magisk, etc.)
- No background or persistent root access is used — only a one-time permission grant attempt

> 💡 If automatic setup fails, you can always use the manual ADB command above.

---

## ⚡ Advanced Input Detection (Shizuku Optional)

Adaptive Hz includes an optional advanced interaction detection mode powered by Shizuku.

### Why this exists
Some Android apps continuously emit noisy Accessibility events even when the user is not touching the screen.

Common examples:
- YouTube subtitle updates
- Passive animations
- UI auto-refresh events
- Dynamic content updates

This can cause traditional Accessibility-only refresh switching systems to incorrectly keep the display at maximum refresh rate.

### What Adaptive Hz does differently
When Shizuku is enabled, Adaptive Hz can monitor low-level Linux input events directly from the touchscreen device.

This allows the engine to:
- Detect real physical touch input
- Distinguish passive UI updates from actual user interaction
- Prevent false refresh-rate boosts during video playback
- Improve battery efficiency while keeping scrolling smooth

### Behavior
- Shizuku support is completely optional
- The app still works normally without Shizuku
- Accessibility remains the fallback interaction system
- Input monitoring is only used to verify real touch behavior
- No continuous polling loops are used

### Privacy
Adaptive Hz does not collect, store, or transmit touch data.
All processing happens locally on-device.

### Setup
1. Install Shizuku
2. Start Shizuku using wireless debugging or ADB
3. Open Adaptive Hz
4. Grant the Shizuku permission when prompted

> 💡 This mode is intended for advanced users who want the most accurate adaptive refresh-rate behavior possible.

---

## Installation

1. Install APK

```bash
adb install AdaptiveHz.apk
```

2. Grant secure permission (see above)

3. Enable Accessibility Service:

Settings → Accessibility → Installed Services → Adaptive Hz → Enable

4. (Recommended) Enable Stability Mode inside the app

---

## How It Works

| Global Mode | Behavior |
|-------------|----------|
| Adaptive | Automatically switches between minimum and maximum based on interaction |
| Minimum | Locks refresh rate to minimum globally |
| Maximum | Locks refresh rate to maximum globally |
| Off | Restores system default behavior |

Per-app profiles can override the global mode for specific apps. For example, a browser can be set to **Respect app/system**, a game to **Maximum**, and a reader app to **Minimum**.

### Notification Controls

When **Stability Mode** is enabled, Adaptive Hz provides quick controls directly from the notification:

- **Off state:**
  - Shows a single **On** button

- **Active state:**
  - Shows **Off + 2 dynamic mode buttons** (Adaptive / Min / Max depending on current state)

- **Smart behavior:**
  - Turning off from notification keeps the service alive briefly (grace period)
  - Allows quick re-enable without reopening the app
  - Automatically stops itself after a short time if not used

The system remains fully event-driven and does not run continuous background loops.

---

## ⚡ Quick Settings Tile

Adaptive Hz includes a **Quick Settings Tile** for fast access directly from the system panel.

### What it does
- Toggle Adaptive Hz **On / Off** with a single tap
- Mirrors the app’s default behavior:
  - **On → Adaptive mode**
  - **Off → System default**

### Behavior
- Tile shows current state (**On / Off**) with dynamic subtitle
- Fully synced with in-app state and notification controls
- Long press opens the app for detailed settings

### Design
- Uses a **monochrome icon** optimized for Quick Settings
- Automatically adapts to system light/dark theme
- Built following Android system UI guidelines

> 💡 This provides the fastest way to control Adaptive Hz without opening the app or using notifications.

---

## 🧩 Home Screen Widget

Adaptive Hz includes a **resizable home screen widget** for quick access to refresh rate modes.

### What it does
- Instantly switch between:
  - **Off**
  - **Minimum**
  - **Adaptive**
  - **Maximum**
- Shows the current active mode with a highlighted state and badge
- Provides one-tap control without opening the app

### Behavior
- Fully synced with:
  - In-app state
  - Notification controls
  - Quick Settings tile
- Automatically updates after every action
- Detects setup state:
  - If required permissions are missing, widget enters a **passive state**
  - Tapping the widget opens the app to complete setup

### Layout & Resizing
- Default layout is optimized for **wide (4x1) usage**
- Supports resizing:
  - Expands horizontally for better spacing
  - Switches to a compact layout when space is limited
- Designed to match system widget behavior on **OneUI and HyperOS**

### Design
- Minimal, clean card-style layout
- Supports light/dark system themes
- Uses subtle visual states:
  - Active mode highlight
  - Disabled state when setup is incomplete

> 💡 The widget is the fastest way to control Adaptive Hz modes directly from your home screen.

---

## Architecture

```
Adaptive Hz
├── core
│   ├── apps
│   │   ├── InstalledAppInfo
│   │   ├── InstalledAppsRepository
│   │   └── RecentAppsProvider
│   ├── debug
│   │   ├── DebugAccessibilityEvent
│   │   └── DebugEventStore
│   ├── engine
│   │   ├── model
│   │   │   ├── DeviceVendor.kt
│   │   │   ├── EngineModels.kt
│   │   │   ├── VendorStrategy
│   │   │   └── VendorTuning
│   │   ├── strategy
│   │   │   ├── OtherStrategy
│   │   │   ├── SamsungStrategy
│   │   │   └── XiaomiStrategy
│   │   ├── AdaptiveHzEngine
│   │   └── AdaptiveHzRuntimeState
│   ├── input
│   │   └── InteractionSignalProvider
│   ├── locale
│   │   └── AppLocaleController
│   ├── prefs
│   │   └── AdaptiveHzPrefs
│   ├── service
│   │   ├── AdaptiveHzActionHandler
│   │   ├── AdaptiveHzService
│   │   ├── AdaptiveHzTileService
│   │   └── StabilityForegroundService
│   ├── shizuku
│   │   ├── InputMonitorUserService
│   │   ├── ShizukuInputManager
│   │   ├── IInputEventCallback.aidl
│   │   └── IInputMonitorService.aidl
│   ├── system
│   │   ├── BootReceiver
│   │   ├── RefreshRateController
│   │   └── RootManager
│   └── widget
│       ├── AdaptiveHzWidgetProvider
│       └── AdaptiveHzWidgetUpdater
├── ui
│   ├── home
│   │   ├── components
│   │   │   ├── DashboardContent.kt
│   │   │   └── SetupContent.kt
│   │   ├── HomeScreen.kt
│   │   └── PerAppRefreshScreen.kt
│   ├── settings
│   │   ├── AccessibilityEventInspectorScreen.kt
│   │   ├── DiagnosticsScreen.kt
│   │   └── SettingsScreen.kt
│   └── theme
│
└── MainActivity.kt
```

---

## Performance

- Very low CPU usage
- No polling loops
- Battery savings typically 5–15% per day (usage dependent)

---

## 🔬 Technical Highlights

- Event-driven architecture (no polling)
- Minimal CPU overhead
- OEM-aware system setting control
- Accessibility-based interaction detection
- Optional Shizuku-based low-level input monitoring
- Real touch validation against noisy Accessibility events
- Per-app profile decision layer
- Event coalescing for noisy Accessibility event streams
- Diagnostics screen for runtime state inspection
- Accessibility Event Inspector for device-specific debugging

---

## 📊 Benchmark & Testing

Typical battery savings: **~5–15% per day** (usage dependent)

Test methodology:
- Device: Samsung Galaxy A52 (120Hz)
- Scenario: Mixed usage (scrolling + idle periods)
- Comparison: Fixed 120Hz vs Adaptive Hz enabled
- Measurement: Battery usage over a full day

Note: Results may vary depending on usage patterns and device behavior.

---

## Known Limitations

- Depends on OEM allowing secure setting writes
- Some ROMs may override refresh policies
- User force-stop disables background switching until reopened
- Accessibility service must remain enabled
- Recent apps shortcuts require optional Usage Access permission

---

## Tested Devices

- Samsung Galaxy A52 (Android 14 / OneUI 6)
- Redmi Note 14 Pro 5G (HyperOS 3.x – community tested)

More devices welcome.

---

## 📱 Compatibility

| Brand   | Device                  | Android | ROM        | Status |
|---------|------------------------|--------|-----------|--------|
| Samsung | Galaxy A52             | 14     | OneUI 6    | ✅ Stable |
| Xiaomi  | Redmi Note 14 Pro 5G   | -      | HyperOS 3.x| ⚠️ Community tested |

More devices are welcome via issues or PRs.

---

## ❓ FAQ

### Is this safe for my device?
Yes. Adaptive Hz only changes system refresh rate settings. It does not modify hardware behavior.

### Does it require root?
No. It works using standard Android permissions.

### Does it collect any data?
No. The app is completely offline and does not track users.

### Will it drain battery?
No — it is designed to reduce battery usage by lowering refresh rate when idle.

---

## 💬 Community

Join the discussion and connect with other users:

- Ask questions in Q&A
- Share device compatibility results
- Suggest new features and improvements
- Discuss ROM-specific behavior

👉 Visit Discussions: https://github.com/mahmutaunal/Adaptive-Hz/discussions

---

## 🤝 Contributing

Contributions, feedback, and device reports are welcome.

You can help by:

- Reporting bugs via Issues
- Sharing device compatibility results in Discussions
- Suggesting features in Ideas
- Improving code via Pull Requests

Please include when relevant:

- Device model
- Android version
- ROM / UI
- Supported refresh rates
- Diagnostics report if available
- Accessibility Event Inspector output for event-related issues

---

## License

MIT License

---

## 🗺️ Roadmap

- [x] Per-app refresh rate profiles
- [x] Diagnostics screen
- [x] Accessibility Event Inspector
- [x] Event coalescing
- [ ] More vendor support (Pixel, OnePlus)
- [ ] Export / import per-app profiles

Made with care by AlpWare Studio

---

## 🤝 Acknowledgements

Inspired by limitations in OEM adaptive refresh rate implementations.

---</file>