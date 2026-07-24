# Contributing to Adaptive Hz

Thank you for helping improve Adaptive Hz. Contributions may include bug fixes, device-specific refresh-rate support, translations, tests, documentation, accessibility improvements, or performance work.

## Before opening a change

- Search existing issues and pull requests.
- For substantial behavioral or architectural changes, open a discussion first.
- Never open a public issue for a suspected vulnerability; follow `SECURITY.md`.
- Keep device-specific logic isolated in the existing vendor strategy layer where possible.

## Development setup

Recommended prerequisites:

- Android Studio compatible with the project's Android Gradle Plugin;
- a JDK version supported by the configured Android Gradle Plugin;
- Android SDK matching the configured compile SDK;
- an Android 12+ test device or emulator for general UI work;
- a physical supported device for refresh-rate, Accessibility, Shizuku, root, boot, widget, and foreground-service testing.

Clone the repository and run:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

Do not commit `local.properties`, IDE state, build outputs, APK/AAB files, signing material, logs, or secrets.

## Branch and commit guidance

Use a focused branch and keep commits reviewable. Good commit messages explain the behavior changed, for example:

```text
fix(samsung): restore system-controlled mode after service stop
```

Avoid unrelated formatting or dependency churn in the same pull request.

## Code expectations

- Follow existing Kotlin and Jetpack Compose conventions.
- Prefer small, testable functions and explicit state transitions.
- Preserve user control: privileged actions must remain deliberate and visible.
- Do not add analytics, advertising, telemetry, or network access without prior discussion and matching privacy documentation.
- Do not log user-entered text, Accessibility node contents, command secrets, or unnecessary device identifiers.
- Keep root commands narrowly scoped, argument-safe, time-bounded, and failure-tolerant.
- Treat package names, app usage, and Accessibility metadata as sensitive local context.
- Maintain compatibility with vendor-specific strategies instead of adding broad setting writes without device evidence.
- Add or update comments only where they clarify non-obvious platform behavior.

## Testing expectations

At minimum, verify the changed path and its failure path. Relevant checks may include:

- service enabled and disabled;
- permission granted and denied;
- Adaptive, minimum, maximum, and system-controlled modes;
- screen off/on and battery saver changes;
- reboot and application update behavior;
- Shizuku unavailable, stopped, denied, and granted;
- root unavailable, denied, timed out, and granted;
- per-app profiles with Usage Access allowed and denied;
- notification permission allowed and denied;
- compact and standard widget behavior;
- supported languages and right-to-left safety;
- Samsung, Xiaomi/HyperOS, and other vendor strategies where applicable.

Never claim support for a device based only on successful compilation. Include the exact device model, Android version, ROM/OEM version, and supported refresh-rate modes in the pull request.

## Pull requests

A good pull request:

- has a clear title and summary;
- links the relevant issue or discussion;
- explains user-visible and technical behavior;
- identifies affected permissions or privacy implications;
- includes test evidence;
- includes screenshots or recordings for UI changes;
- updates documentation and translations when needed;
- contains no generated binaries or sensitive information.

By contributing, you agree that your contribution is licensed under the repository's MIT License.
