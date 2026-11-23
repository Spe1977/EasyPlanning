# Security Policy

## EasyPlanning Security Model

EasyPlanning is engineered around **privacy-first** and **offline-first** principles:

- **No central backend**: All shifts, overtime records, and allowances stay locally on the user's device.
- **Client-side authenticated encryption**: Both local `.epbak` exports and optional Google Drive backups are encrypted before leaving the app using **AES-256-GCM** with **PBKDF2-HMAC-SHA256** key derivation and fresh random salt/IV values.
- **Least-privilege cloud scope**: Optional Google Drive sync uses only the isolated `appDataFolder` scope and cannot view or modify any other user Drive files.
- **Local credential protection**: PINs and password verifiers are protected with **Android Keystore**, and sensitive `CharArray` buffers are zeroized in memory immediately after use.
- **UI & access hardening**: Includes PIN + Biometric App Lock, optional `FLAG_SECURE` screenshot/recents protection, and multi-step confirmation before data erasure.

## Reporting a Vulnerability

If you discover a security vulnerability:

1. **Please do not open a public GitHub issue** containing exploitable details.
2. Report it privately via **GitHub Private Vulnerability Reporting** (under the repository's *Security* tab) or reach out directly to the maintainer [@Spe1977](https://github.com/Spe1977).
3. Include a clear description of the issue, reproduction steps, and potential impact.

We will acknowledge your report promptly and coordinate a fix responsibly.
