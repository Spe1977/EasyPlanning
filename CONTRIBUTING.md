# Contribuire a EasyPlanning

Grazie per l'interesse nel contribuire a **EasyPlanning**!

## Come contribuire

1. Esegui il **fork** del repository e crea un branch dedicato dal branch `main`:
   ```bash
   git checkout -b feat/descrizione-breve
   ```
2. Mantieni le modifiche focalizzate, modulari e coerenti con lo stile del progetto:
   - **Kotlin** + **Jetpack Compose** + **Material Design 3** (evita colori esadecimali hardcoded nei Composable; utilizza `MaterialTheme.colorScheme`).
   - Rispetta i requisiti di accessibilità (`contentDescription` significativi e target di tocco minimi di `48.dp`).
   - Aggiorna entrambe le localizzazioni (**Italiano** e **Inglese**) in `AppStrings.kt` per ogni nuovo testo visibile all'utente.
   - Non committare mai chiavi API, keystore di produzione, file `.env` o credenziali private.
3. Aggiungi o aggiorna i test automatici per ogni nuova funzionalità o correzione di bug.

## Verifica prima della Pull Request

Esegui la compilazione e l'intera suite di test locali prima di inviare una Pull Request:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Assicurati che tutti i test vengano superati senza errori e descrivi chiaramente nella Pull Request il motivo della modifica e i casi verificati.
