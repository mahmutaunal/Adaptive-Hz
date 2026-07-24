# Release Checklist

## Source and build

- [ ] Version code and version name are updated.
- [ ] Release notes match the actual changes.
- [ ] `./gradlew clean test lint assembleRelease` succeeds.
- [ ] The release is built from a clean, reviewed commit.
- [ ] Signing keys and credentials are not present in the repository or logs.
- [ ] Minification rules preserve required Shizuku, AIDL, service, widget, and Compose behavior.

## Security and privacy

- [ ] Manifest permissions and exported components were reviewed.
- [ ] Root and Shizuku commands/interfaces were reviewed for scope and input safety.
- [ ] Accessibility handling does not read or persist unnecessary content.
- [ ] Logs contain no user text, secrets, or unnecessary identifiers.
- [ ] `PRIVACY.md`, `SECURITY.md`, and `docs/PERMISSIONS.md` still match the release.
- [ ] Dependency vulnerability alerts and license changes were reviewed.
- [ ] Backup remains intentionally disabled or its behavior is documented.

## Device behavior

- [ ] Setup works using the documented ADB command.
- [ ] Permission denial and revocation are handled safely.
- [ ] Adaptive, minimum, maximum, and system-controlled modes were tested.
- [ ] Refresh-rate state is restored appropriately when the service stops.
- [ ] Accessibility reconnect, screen state, battery saver, reboot, and app-update paths were tested.
- [ ] Stability Mode notification and stop behavior were tested.
- [ ] Per-app profiles were tested with and without Usage Access.
- [ ] Shizuku available, unavailable, denied, and restarted paths were tested.
- [ ] Root available, denied, unavailable, and timeout paths were tested.
- [ ] Widget and Quick Settings tile were tested.

## Compatibility and distribution

- [ ] At least one Samsung/One UI device was tested when Samsung behavior changed.
- [ ] At least one Xiaomi/HyperOS device was tested when Xiaomi behavior changed.
- [ ] Generic/other-vendor behavior was tested when shared logic changed.
- [ ] Screenshots, README instructions, and supported-device claims are current.
- [ ] APK checksum is published with the release when distributing outside an app store.
- [ ] Release artifacts contain only expected files.
