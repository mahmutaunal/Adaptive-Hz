# Security Policy

Adaptive Hz changes device refresh-rate settings and can optionally integrate with Android Accessibility, Usage Access, Shizuku, or a root shell. Reports involving privilege boundaries, exported components, command execution, package visibility, or unintended data exposure are treated as security-sensitive.

## Supported versions

Security fixes are provided for the latest published release and the current default branch. Older releases may not receive fixes; users should reproduce an issue on the newest release before reporting it when practical.

## Reporting a vulnerability

Do **not** open a public issue for a suspected vulnerability.

Use GitHub's private vulnerability reporting feature for this repository:

1. Open the repository's **Security** tab.
2. Select **Advisories**.
3. Select **Report a vulnerability**.

Include, where possible:

- affected version or commit;
- Android version, device model, and ROM/OEM skin;
- required permissions or setup state;
- reproducible steps or a minimal proof of concept;
- expected and observed behavior;
- realistic impact and attack prerequisites;
- relevant logs with personal or device-identifying information removed;
- any suggested remediation.

Please do not include passwords, private keys, account tokens, complete device dumps, or unrelated personal data.

## Disclosure process

The maintainer will aim to:

- acknowledge a complete report within 7 days;
- validate and triage the report;
- coordinate a fix and release before public disclosure when warranted;
- credit the reporter unless anonymity is requested.

Response and remediation times depend on severity, reproducibility, platform behavior, and maintainer availability. Please allow a reasonable remediation window before disclosure.

## Scope

Examples of in-scope reports include:

- unauthorized execution through the optional root or Shizuku paths;
- improper access to Accessibility event content;
- leakage of installed-app, foreground-app, or usage information;
- insecure exported Android components or intent handling;
- privilege escalation or bypass of a user-visible consent step;
- dependency vulnerabilities that are reachable in Adaptive Hz;
- unsafe handling of local diagnostics or preferences.

Typically out of scope:

- issues requiring a device already fully compromised by malicious root software;
- OEM refresh-rate behavior that causes no security impact;
- denial-of-service caused only by unsupported device modifications;
- reports based only on automated scanner output without a reproducible impact;
- social engineering, phishing, or attacks against GitHub itself.

## Safe-harbor intent

Good-faith research that avoids privacy violations, data destruction, persistence, and disruption is welcomed. The maintainer will not pursue action against researchers who follow this policy and applicable law.
