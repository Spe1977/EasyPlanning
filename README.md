<p align="center">
  <img src="app/src/main/res/drawable/ic_easyplanning_fg.png" alt="EasyPlanning Logo" width="140" />
</p>

<h1 align="center">EasyPlanning</h1>

<p align="center">
  <strong>Offline-first Android shift planner built with Kotlin & Jetpack Compose — featuring recurring/rotating shifts, 5 overtime categories, allowances, analytics, native PDF reports, and AES-256-GCM encrypted backups.</strong>
</p>

<p align="center">
  <strong>English</strong> · <a href="README_IT.md">Italiano</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20(API%2024%E2%80%9336)-3DDC84?logo=android&logoColor=white" alt="Android API 24-36" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="MIT License" />
</p>

---

## Overview

**EasyPlanning** is a native Android application built in **Kotlin** and **Jetpack Compose (Material 3)** tailored for shift workers. Designed with a strict **offline-first** and **privacy-first** architecture, it stores schedules locally in Room, protects credentials via Android Keystore, generates multi-page A4 monthly PDF reports natively, and optionally syncs **AES-256-GCM** encrypted vaults to the user's private Google Drive (`appDataFolder`) with no central app server.

---

## Key Features

### Shift Scheduling & Recurrence
- **Dual Schedule Views**: Continuous chronological timeline (*List*) with instant *Search Date* and *Today* jump controls, paired with an interactive **Monthly Calendar** and day-detail bottom sheet.
- **Advanced Recurrence Engine**: Single shifts, daily, weekly (multi-weekday mask + custom interval), monthly (automatic end-of-month & leap-year clamping), and **rotating cycles** (*N work days / M off days*).
- **Flexible End Conditions**: Repeat *Never*, *Until date*, or *After N shifts*.
- **Overnight & Multi-Shift Support**: Accurate duration calculation across midnight and year boundaries, multiple shifts per day, 10 shift color swatches, and quick presets (*Morning*, *Afternoon*, *Night*, *Full Day*).
- **Granular Editing**: Edit or delete a **single occurrence** (via isolated overrides) or the **entire series**.

### Overtime & Allowances
- **5 Overtime Categories** in 30-minute increments:
  - **Weekday** (`WD` / `FER`)
  - **Holiday** (`HOL` / `FEST`)
  - **Night** (`NGT` / `NOTT`)
  - **Night Holiday** (`N-H` / `N-FEST`)
  - **Scheduled** (`SCH` / `PROG`)
- **Allowances**: Built-in presets (such as *Meal Voucher*) plus custom allowance labels with quantity and notes.
- **Inline Creation & Editing**: Add or edit overtime and allowances directly while creating or editing shifts, with a dedicated switch to propagate extras across every occurrence in a recurring series or keep them on a single date.

### Analytics & Native PDF Reports
- **Statistics Dashboard** across **Week**, **Month**, and **Year** periods: worked days, total regular hours, overtime breakdown across all 5 categories, shift distribution, and allowance totals.
- **Multi-Page A4 Monthly PDF Reports**: Native Android `PdfDocument` rendering in English or Italian, supporting both device export (SAF) and direct sharing via `FileProvider`.

### Reminders & Notifications
- Configurable per-shift and default lead-time reminders (from *At start* up to *1 day before*, or disabled).
- Automatic 30-day rolling alarm scheduling and **reboot persistence** via `BOOT_COMPLETED`.
- Graceful Android 13+ `POST_NOTIFICATIONS` runtime permission handling.

### Security, Privacy & Encrypted Backups
- **Zero-Backend Offline-First Design**: All schedule data lives locally on-device in SQLite/Room.
- **Flexible Accounts**: Fully offline mode (*Username + Password*), **Google Sign-In** (Jetpack Credential Manager), or seamless upgrade to a **Hybrid** account.
- **Authenticated Client-Side Encryption**: Local backups (`.epbak`) and optional **Google Drive (`appDataFolder`)** sync use **AES-256-GCM** with **PBKDF2-HMAC-SHA256** key derivation and per-export random salt/IV. Sensitive `CharArray` buffers are zeroized after use.
- **On-Device Hardening**: Local secrets protected with **Android Keystore**, **PIN + Biometric App Lock**, screenshot/recents protection (`FLAG_SECURE`), and multi-step secure reset (up to 3 confirmation steps).

### Modern Adaptive UI
- **Material Design 3** with *System*, *Light*, and *Dark* themes plus **Material You Dynamic Color** (Android 12+).
- **Adaptive Navigation**: Bottom `NavigationBar` on compact screens and side `NavigationRail` on tablets/foldables (`>= 600dp`).
- **Instant Bilingual Support**: Full UI and PDF report localization in **English** and **Italian**.

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language & UI** | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| **Architecture** | MVVM (`ViewModel`, `StateFlow` / `SharedFlow`, Kotlin Coroutines & Flow) |
| **Local Persistence** | Room Database (v2 with `MIGRATION_1_2`), Jetpack DataStore Preferences |
| **Security** | Android Keystore, AES-256-GCM, PBKDF2-HMAC-SHA256, AndroidX Biometric |
| **Auth & Cloud Sync (optional)** | Jetpack Credential Manager (Google Sign-In), Play Services Auth, Retrofit + OkHttp + Moshi (Google Drive `appDataFolder`) |
| **PDF & Notifications** | Android `PdfDocument`, `AlarmManager`, `NotificationCompat`, `FileProvider` |
| **Testing** | JUnit 4, Kotlin Coroutines Test, Robolectric, Jetpack Compose UI Test |

For module structure, Room v2 schema details, virtual recurring extras design, and vault security internals, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Getting Started

### Prerequisites
- **Android Studio** (compatible with AGP and Android SDK 36)
- **JDK 17** (targeting Java 11 bytecode with core library desugaring)
- **Android SDK**: `compileSdk 36`, `targetSdk 36`, `minSdk 24`

### Clone, Build & Test
```bash
git clone https://github.com/Spe1977/EasyPlanning.git
cd EasyPlanning

# Build Debug APK
./gradlew :app:assembleDebug

# Run full local test suite (JVM + Robolectric + Compose UI)
./gradlew :app:testDebugUnitTest
```

> **Optional Google Integrations:** EasyPlanning works 100% offline out of the box without any cloud setup. To enable **Google Sign-In** and **Google Drive (`appDataFolder`)** sync in your own build, configure OAuth 2.0 credentials (Android + Web Client ID) in Google Cloud Console and provide your local untracked config. Never commit private keys, keystores, or `.env` secrets to a public repository.

---

## Automated Test Suite

The automated test suite validates domain logic, database migrations, cryptography, ViewModel flows, notifications, PDF rendering, and Compose UI components:

- **Domain & Recurrence**: All recurrence types, leap-year clamping, overnight/24h shifts, multi-period statistics, resilient JSON codec, and AES-GCM encryption/tamper rejection.
- **Database & Backups**: Room `v1 → v2` migration, encrypted backup round-trips, legacy v1 payload backward compatibility, input sanitization, and atomic rejection of malformed payloads.
- **Auth, Security & ViewModel**: Offline registration/login, non-destructive logout, hybrid upgrade, PIN lock/unlock, persistent app preferences, dynamic timeline expansion, and selective deletion of inherited vs direct extras.
- **Notifications & PDF**: Rolling 30-day alarm scheduling/cancellation, `BootRescheduleReceiver`, Android 13+ notification permissions, and multi-page A4 PDF rendering in EN & IT.
- **Compose UI & Adaptive Shell**: Calendar, day-detail sheet, shift editor (presets, recurrences, inline extras), security/reset dialogs, settings controls, App Lock gate, and `NavigationBar` vs `NavigationRail`.

---

## Contributors

- **[@Spe1977](https://github.com/Spe1977)** — Project Creator, Product Vision & Maintainer
- **Gemini (Google AI)** — AI Engineering, Architecture & Automated Testing Contributor

See [`CONTRIBUTING.md`](CONTRIBUTING.md) if you would like to contribute to EasyPlanning.

---

## Security & License

- Read [`SECURITY.md`](SECURITY.md) for EasyPlanning's security model and vulnerability disclosure guidelines.
- Licensed under the **MIT License** — see [`LICENSE`](LICENSE) for details.
