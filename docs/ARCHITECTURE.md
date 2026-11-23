# EasyPlanning Architecture

This document describes the module layout, Room database schema, recurrence engine, and security pipeline of **EasyPlanning**.

---

## Package Structure (`app/src/main/java/com/example/`)

```text
com.example
├── MainActivity.kt                  # Edge-to-edge entry point & FLAG_SECURE lifecycle management
├── data/
│   ├── local/
│   │   ├── Entities.kt              # Room entities (shift_series, shift_overrides, overtime_entries, allowance_entries)
│   │   ├── EasyPlanningDao.kt       # Reactive Flow queries & atomic @Transaction operations
│   │   ├── EasyPlanningDatabase.kt  # Room DB v2 with non-destructive MIGRATION_1_2
│   │   └── PreferencesStore.kt      # Jetpack DataStore protected with Android Keystore
│   ├── model/
│   │   ├── ShiftEnums.kt            # RecurrenceType, OvertimeType (5 categories), ReminderOption, AuthProvider, ThemeMode
│   │   └── ShiftModels.kt           # ShiftInstance, DaySummary, StatsSummary, BackupPayload, ShiftExtrasCodec
│   └── repository/
│       └── EasyPlanningRepository.kt# Single coordination point for Room, DataStore, Reminders, PDF & Backups
├── domain/
│   ├── RecurrenceEngine.kt          # Pure recurrence expander & effective inherited/direct extras combiner
│   ├── StatsCalculator.kt           # Weekly, Monthly & Yearly aggregation engine
│   └── PdfExportService.kt          # Native multi-page A4 monthly PDF renderer (EN / IT)
├── notifications/
│   ├── ShiftReminderScheduler.kt    # 30-day rolling AlarmManager scheduler & cancellation tracker
│   ├── ShiftReminderReceiver.kt     # Notification publisher with Android 13+ permission check
│   └── BootRescheduleReceiver.kt    # Restores scheduled alarms on ACTION_BOOT_COMPLETED
├── security/
│   ├── CryptoManager.kt             # AES-256-GCM, PBKDF2-HMAC-SHA256, Android Keystore, CharArray zeroization
│   ├── BiometricHelper.kt           # AndroidX BiometricPrompt integration
│   └── GoogleAuthManager.kt         # Google Sign-In via Jetpack Credential Manager
├── sync/
│   ├── BackupRepository.kt          # Moshi JSON serialization, strict bounds validation & authenticated encryption
│   ├── GoogleDriveApi.kt            # Retrofit interface for Google Drive v3 API
│   └── GoogleDriveSyncManager.kt    # OAuth resolution & encrypted vault upload/download in appDataFolder
├── i18n/
│   └── AppStrings.kt                # Complete English & Italian localization and number/date formatters
└── ui/
    ├── EasyPlanningViewModel.kt     # Reactive StateFlows, background orchestration & UiEvent channel
    ├── EasyPlanningApp.kt           # Auth/AppLock security gating & adaptive NavigationBar / NavigationRail
    ├── screens/                     # Compose screens (Schedule, Stats, Settings, Auth, AppLock, Editor, Dialogs)
    └── theme/                       # Material 3 color schemes, typography & 10-color shift palette
```

---

## Data Model & Local Persistence (Room v2)

The local SQLite database (`easyplanning_local.db`) consists of 4 tables:

1. **`shift_series`**: Stores both one-off shifts (`NONE`) and recurring series (`DAILY`, `WEEKLY`, `MONTHLY`, `ROTATING_CYCLE`), plus optional JSON-encoded series-level extras (`defaultOvertimeJson`, `defaultAllowancesJson`) inherited by each occurrence.
2. **`shift_overrides`**: Stores per-date exceptions indexed uniquely by `(seriesId, occurrenceEpochDay)`, supporting single-day deletions (`isDeleted = true`) or overrides for title, times, color, notes, reminder, and extras (`customOvertimeJson`, `customAllowancesJson`).
3. **`overtime_entries`**: Direct overtime logs linked to a date (and optionally a series), stored in 30-minute units (`halfHours`) across all 5 categories (`WEEKDAY`, `HOLIDAY`, `NIGHT`, `NIGHT_HOLIDAY`, `SCHEDULED`).
4. **`allowance_entries`**: Direct allowances (preset or custom) with label, quantity, and optional notes.

### Database Migration `v1 → v2`
`EasyPlanningDatabase.MIGRATION_1_2` adds the v2 JSON columns non-destructively so existing v1 schedules and overrides upgrade seamlessly without data loss.

---

## Recurrence Engine & Virtual Inherited Extras

`RecurrenceEngine` is a pure Kotlin domain component with zero Android dependencies:
- Expands shift occurrences across arbitrary windows (default `-24` / `+36` months, dynamically expanded by *Search Date*).
- Merges direct Room rows with series-inherited extras (`effectiveOvertime` / `effectiveAllowances`).
- Inherited extras carry negative virtual IDs (`id < 0`): deleting an inherited chip on a single day causes `EasyPlanningRepository` to persist a targeted `ShiftOverrideEntity` for that date only, leaving all other occurrences untouched.

---

## Security & Encrypted Vault Pipeline

- **On-device secrets (PIN, password verifiers, user profile metadata)**: Encrypted using an AES-256-GCM key backed by **Android Keystore**. Secret verifiers use salted **PBKDF2-HMAC-SHA256** and constant-time comparison.
- **Encrypted Backup Vault (`.epbak` & Google Drive `appDataFolder`)**:
  - Serialized to JSON (`BackupPayload`, `schemaVersion = 1`).
  - User key derived via **PBKDF2-HMAC-SHA256** with a fresh random salt per export.
  - Authenticated **AES-256-GCM** encryption rejects wrong passwords or single-bit tampering.
  - Strict schema/time-bound validation and field sanitization run **before** the atomic Room transaction (`dao.replaceAllData`), ensuring corrupted or malicious backups never wipe existing user data.
- **Memory hygiene**: All `CharArray` password/PIN buffers are explicitly wiped (`\u0000`) inside `finally` blocks and `ViewModel.onCleared()`.
