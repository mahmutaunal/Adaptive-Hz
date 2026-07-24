# Support

Adaptive Hz interacts with OEM-specific display settings, so behavior can differ by device, Android release, ROM, battery-management policy, and available refresh-rate modes.

## Where to ask

- Use **GitHub Discussions** for setup help, questions, and general troubleshooting.
- Use the **Bug Report** issue template for reproducible defects.
- Use the **Device Support Request** template for an unsupported or partially supported device.
- Use the **Feature Request** template for product suggestions.
- Follow `SECURITY.md` for vulnerabilities; do not report them publicly.

## Information to include

Provide:

- Adaptive Hz version;
- device manufacturer and exact model;
- Android version and ROM/OEM skin version;
- supported display refresh rates;
- whether `WRITE_SECURE_SETTINGS` is granted;
- whether Accessibility, Usage Access, Stability Mode, Shizuku, or root is enabled;
- selected global mode and relevant per-app profile;
- exact reproduction steps;
- expected and observed results;
- minimal, redacted logs when useful.

Do not post serial numbers, account details, private notifications, message content, complete bug reports, access tokens, or unredacted Accessibility information.

## Basic checks

Before reporting:

1. Install the latest release.
2. Confirm the device exposes more than one supported refresh rate.
3. Confirm `WRITE_SECURE_SETTINGS` is granted.
4. Disable and re-enable the Accessibility service.
5. Exclude Adaptive Hz from aggressive OEM battery restrictions when Stability Mode is needed.
6. Test without Shizuku and then with Shizuku, if the issue concerns input detection.
7. Temporarily remove per-app overrides to isolate global behavior.
8. Reboot once after changing privileged setup.

## Scope of support

The project can investigate application behavior but cannot guarantee that every OEM firmware exposes writable refresh-rate settings. Custom ROMs, rooted modifications, vendor updates, or other refresh-rate utilities may conflict with Adaptive Hz.

There is no guaranteed response time. Community reports with complete reproduction details are more likely to be actionable.
