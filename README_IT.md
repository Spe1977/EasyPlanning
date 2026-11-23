<p align="center">
  <img src="app/src/main/res/drawable/ic_easyplanning_fg.png" alt="EasyPlanning Logo" width="140" />
</p>

<h1 align="center">EasyPlanning</h1>

<p align="center">
  <strong>Pianificatore Android offline-first per turni di lavoro, straordinari, indennità, statistiche, report PDF e backup cifrati.</strong>
</p>

<p align="center">
  <a href="README.md">English</a> · <strong>Italiano</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20(API%2024%E2%80%9336)-3DDC84?logo=android&logoColor=white" alt="Android API 24-36" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="MIT License" />
</p>

---

## Panoramica

**EasyPlanning** è un'applicazione Android nativa sviluppata in **Kotlin** e **Jetpack Compose (Material 3)** progettata per lavoratori turnisti. Funziona completamente **offline** con database locale Room, protegge le credenziali tramite Android Keystore, esporta report mensili in PDF nativo A4 e consente di sincronizzare un vault cifrato **AES-256-GCM** sul proprio spazio privato Google Drive (`appDataFolder`) senza alcun server centrale.

---

## Funzionalità principali

### Pianificazione e Gestione Turni
- **Viste duali**: timeline cronologica continua (*Lista*) con ricerca data rapida (*Cerca Data*) e ritorno immediato a *Oggi*, affiancata da un **Calendario Mensile** interattivo con bottom sheet di dettaglio giornaliero.
- **Ricorrenze avanzate**: turni singoli, giornalieri, settimanali (selezione multipla dei giorni e intervallo), mensili (gestione automatica fine mese e anni bisestili) e **cicli a rotazione** (*N giorni lavorativi / M giorni di riposo*).
- **Condizioni di fine serie**: mai, fino a una data specifica oppure dopo *N* turni.
- **Turni notturni e multi-turno**: calcolo automatico della durata anche a cavallo della mezzanotte o di Capodanno, supporto a più turni nello stesso giorno, 10 colori personalizzabili e preset rapidi (*Mattina*, *Pomeriggio*, *Notte*, *Giornaliero*).
- **Modifica granulare**: modifica o eliminazione della **singola occorrenza** (tramite override dedicato) oppure dell'**intera serie**.

### Straordinari e Indennità
- **5 categorie di straordinario** a scatti di 30 minuti:
  - **Feriale** (`FER` / `WD`)
  - **Festivo** (`FEST` / `HOL`)
  - **Notturno** (`NOTT` / `NGT`)
  - **Notturno Festivo** (`N-FEST` / `N-H`)
  - **Programmato** (`PROG` / `SCH`)
- **Indennità flessibili**: preset integrati (es. *Buono Pasto*) e voci personalizzate con quantità e note.
- **Inserimento inline e propagazione**: configurabili sia in creazione sia in modifica turno, con interruttore dedicato per applicare gli extra a tutte le occorrenze della serie o solo alla data selezionata.

### Statistiche e Report PDF
- **Dashboard Statistiche** su periodo **Settimana**, **Mese** o **Anno**: giorni lavorati, ore ordinarie totali, riepilogo ore straordinario per ciascuna delle 5 categorie, distribuzione turni e conteggio indennità.
- **Report PDF mensile nativo (A4 multi-pagina)**: impaginazione completa del riepilogo mensile e del dettaglio giornaliero in Italiano o Inglese, con esportazione su dispositivo (SAF) e condivisione diretta via `FileProvider`.

### Notifiche e Promemoria
- Promemoria configurabili per ogni turno (da *All'inizio* fino a *1 giorno prima*, o disabilitati) e predefiniti nelle Impostazioni.
- Pianificazione automatica degli allarmi locali su finestra scorrevole di 30 giorni e **ripristino automatico al riavvio del dispositivo** (`BOOT_COMPLETED`).
- Gestione esplicita del permesso `POST_NOTIFICATIONS` su Android 13+.

### Sicurezza, Privacy e Backup
- **Architettura Offline-First**: nessun backend proprietario; i turni restano nel database locale SQLite/Room del dispositivo.
- **Account flessibile**: utilizzo completamente offline (*Username + Password*), accesso con **Google Sign-In** (Jetpack Credential Manager) oppure upgrade ad account **Ibrido**.
- **Cifratura autenticata**: backup esportati localmente (`.epbak`) e su **Google Drive (`appDataFolder`)** cifrati client-side con **AES-256-GCM** e chiave derivata con **PBKDF2-HMAC-SHA256**; azzeramento dei `CharArray` sensibili in memoria dopo l'uso.
- **Protezione locale**: credenziali protette tramite **Android Keystore**, blocco app con **PIN e Biometria**, protezione screenshot/app recenti (`FLAG_SECURE`) e reset completo protetto da conferma multi-step (fino a 3 passaggi).

### Esperienza Utente e Accessibilità
- **Material Design 3** con temi *Sistema*, *Chiaro*, *Scuro* e **Material You Dynamic Color** (Android 12+).
- **Layout adattivo**: `NavigationBar` inferiore su smartphone e `NavigationRail` laterale su tablet e pieghevoli (`>= 600dp`).
- **Multilingua istantaneo**: interfaccia e report PDF disponibili in **Italiano** e **Inglese**.

---

## Stack Tecnologico

| Ambito | Tecnologie |
| :--- | :--- |
| **Linguaggio & UI** | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| **Architettura** | MVVM (`ViewModel`, `StateFlow` / `SharedFlow`, Coroutines & Flow) |
| **Persistenza locale** | Room Database (v2 con migrazione v1 → v2), Jetpack DataStore Preferences |
| **Sicurezza** | Android Keystore, AES-256-GCM, PBKDF2-HMAC-SHA256, AndroidX Biometric |
| **Auth & Cloud Sync (opzionale)** | Jetpack Credential Manager (Google Sign-In), Google Play Services Auth, Retrofit + OkHttp + Moshi (Google Drive `appDataFolder`) |
| **PDF & Notifiche** | Android `PdfDocument` nativo, `AlarmManager`, `NotificationCompat`, `FileProvider` |
| **Test** | JUnit 4, Kotlin Coroutines Test, Robolectric, Jetpack Compose UI Test |

Per i dettagli completi sui moduli, lo schema Room e il formato del vault cifrato, consulta [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Requisiti e Avvio Locale

### Prerequisiti
- **Android Studio** (versione compatibile con AGP recente e Android SDK 36)
- **JDK 17** (target bytecode Java 11 con core library desugaring)
- **Android SDK**: `compileSdk 36`, `targetSdk 36`, `minSdk 24`

### Clonazione, Build e Test
```bash
git clone https://github.com/Spe1977/EasyPlanning.git
cd EasyPlanning

# Build Debug APK
./gradlew :app:assembleDebug

# Esecuzione suite completa di test locali (JVM + Robolectric + Compose UI)
./gradlew :app:testDebugUnitTest
```

> **Nota sulle funzionalità Google (opzionali):** EasyPlanning funziona integralmente in modalità offline senza alcuna configurazione cloud. Se desideri abilitare **Google Sign-In** e la sincronizzazione su **Google Drive (`appDataFolder`)** nella tua build personale, configura le credenziali OAuth 2.0 (Android + Web Client ID) su Google Cloud Console tramite configurazione locale non versionata. Non committare mai segreti o chiavi private nel repository.

---

## Contributori

- **[@Spe1977](https://github.com/Spe1977)** — Ideatore, Direzione Prodotto e Maintainer
- **Gemini (Google AI)** — Sviluppo AI, Architettura e Suite di Test Automatici

Consulta [`CONTRIBUTING.md`](CONTRIBUTING.md) per partecipare allo sviluppo.

---

## Sicurezza e Licenza

- Consulta [`SECURITY.md`](SECURITY.md) per l'architettura di sicurezza e la segnalazione responsabile di vulnerabilità.
- Distribuito con licenza **MIT** — consulta il file [`LICENSE`](LICENSE) per i dettagli completi.
