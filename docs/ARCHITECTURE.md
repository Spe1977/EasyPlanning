# Architettura di EasyPlanning

Questo documento descrive l'organizzazione dei moduli, il modello dati, il motore delle ricorrenze e la pipeline di sicurezza di **EasyPlanning**.

---

## Struttura dei package (`app/src/main/java/com/example/`)

```text
com.example
├── MainActivity.kt                  # Entry point edge-to-edge e gestione FLAG_SECURE
├── data/
│   ├── local/
│   │   ├── Entities.kt              # Entità Room (shift_series, shift_overrides, overtime_entries, allowance_entries)
│   │   ├── EasyPlanningDao.kt       # Query reattive Flow + transazioni atomiche
│   │   ├── EasyPlanningDatabase.kt  # Room DB v2 con MIGRATION_1_2
│   │   └── PreferencesStore.kt      # Jetpack DataStore cifrato tramite Android Keystore
│   ├── model/
│   │   ├── ShiftEnums.kt            # RecurrenceType, OvertimeType (5 categorie), ReminderOption, AuthProvider, ThemeMode
│   │   └── ShiftModels.kt           # ShiftInstance, DaySummary, StatsSummary, BackupPayload, ShiftExtrasCodec
│   └── repository/
│       └── EasyPlanningRepository.kt# Coordinamento Room, DataStore, Reminder, PDF e Backup
├── domain/
│   ├── RecurrenceEngine.kt          # Motore puro di espansione turni ed extra ereditati/diretti
│   ├── StatsCalculator.kt           # Aggregazione statistiche Settimana / Mese / Anno
│   └── PdfExportService.kt          # Generatore nativo A4 multi-pagina (IT / EN)
├── notifications/
│   ├── ShiftReminderScheduler.kt    # Pianificazione allarmi su finestra di 30 giorni
│   ├── ShiftReminderReceiver.kt     # Emissione notifica con controllo permessi Android 13+
│   └── BootRescheduleReceiver.kt    # Ripristino allarmi dopo ACTION_BOOT_COMPLETED
├── security/
│   ├── CryptoManager.kt             # AES-256-GCM, PBKDF2-HMAC-SHA256, Android Keystore, zeroizzazione CharArray
│   ├── BiometricHelper.kt           # Integrazione AndroidX BiometricPrompt
│   └── GoogleAuthManager.kt         # Google Sign-In tramite Jetpack Credential Manager
├── sync/
│   ├── BackupRepository.kt          # Serializzazione Moshi, validazione rigorosa e cifratura/decifratura
│   ├── GoogleDriveApi.kt            # Interfaccia Retrofit verso Google Drive v3
│   └── GoogleDriveSyncManager.kt    # Gestione OAuth e file cifrato in appDataFolder
├── i18n/
│   └── AppStrings.kt                # Risorse e formattatori completi Italiano / Inglese
└── ui/
    ├── EasyPlanningViewModel.kt     # StateFlow reattivi, orchestrazione azioni e UiEvent
    ├── EasyPlanningApp.kt           # Routing di sicurezza (Auth / AppLock) e navigazione adattiva
    ├── screens/                     # Schermate Compose (Schedule, Stats, Settings, Auth, AppLock, Editor, Dialogs)
    └── theme/                       # Palette Material 3, tipografia e colori dei turni
```

---

## Modello Dati e Persistenza (Room v2)

Il database locale `easyplanning_local.db` utilizza 4 tabelle principali:

1. **`shift_series`**: definisce sia i turni singoli (`NONE`) sia le serie ricorrenti (`DAILY`, `WEEKLY`, `MONTHLY`, `ROTATING_CYCLE`), includendo eventuali straordinari (`defaultOvertimeJson`) e indennità (`defaultAllowancesJson`) ereditati da ogni occorrenza.
2. **`shift_overrides`**: memorizza eccezioni per singola data (`seriesId + occurrenceEpochDay` univoci), consentendo eliminazioni puntuali (`isDeleted = true`) oppure personalizzazioni di orario, titolo, colore, note, promemoria ed extra (`customOvertimeJson`, `customAllowancesJson`).
3. **`overtime_entries`**: registrazioni dirette di straordinari collegate a una data (e opzionalmente a una serie), espresse in mezz'ore (`halfHours`) e tipizzate sulle 5 categorie (`WEEKDAY`, `HOLIDAY`, `NIGHT`, `NIGHT_HOLIDAY`, `SCHEDULED`).
4. **`allowance_entries`**: indennità dirette (preset o personalizzate) con quantità e note.

### Migrazione Database `v1 → v2`
`EasyPlanningDatabase.MIGRATION_1_2` aggiunge in modo non distruttivo le colonne JSON per straordinari e indennità ricorrenti preservando tutti i dati esistenti.

---

## Motore Ricorrenze ed Extra Virtuali

`RecurrenceEngine` è puro e privo di dipendenze Android:
- Espande le occorrenze su qualsiasi finestra temporale (default `-24` / `+36` mesi, auto-espandibile con *Cerca Data*).
- Combina gli extra diretti salvati in Room con gli extra ereditati dalla serie (`effectiveOvertime` / `effectiveAllowances`).
- Gli extra ereditati ricevono ID negativi virtuali: quando l'utente elimina un extra ereditato su un singolo giorno, `EasyPlanningRepository` crea/aggiorna solo l'override di quella data senza alterare gli altri giorni della serie.

---

## Sicurezza e Backup Cifrati

- **Credenziali locali (PIN, verificatori password, profili)**: protette tramite chiave AES-256-GCM custodita in **Android Keystore**; i verificatori password usano derivazione PBKDF2-HMAC-SHA256 con salt casuale e confronto a tempo costante.
- **Vault Backup (`.epbak` e Google Drive `appDataFolder`)**:
  - Serializzazione JSON (`BackupPayload`, `schemaVersion = 1`).
  - Derivazione chiave utente con **PBKDF2-HMAC-SHA256** e salt casuale per ogni esportazione.
  - Cifratura autenticata **AES-256-GCM** (rileva automaticamente password errate o manomissioni anche di un solo bit).
  - Validazione rigorosa dei limiti temporali e sanitizzazione dei campi **prima** della sostituzione atomica (`dao.replaceAllData`), garantendo che un file corrotto non cancelli mai i dati esistenti.
- **Pulizia memoria**: tutti i `CharArray` contenenti password o PIN vengono azzerati (`\u0000`) nei blocchi `finally` e in `ViewModel.onCleared()`.
