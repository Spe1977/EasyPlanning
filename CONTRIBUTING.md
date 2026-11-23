# Contributing to EasyPlanning

Thank you for your interest in contributing to **EasyPlanning**!

## How to Contribute

1. **Fork** the repository and create a dedicated feature/fix branch from `main`:
   ```bash
   git checkout -b feat/short-description
   ```
2. Keep changes focused, modular, and aligned with the project's engineering standards:
   - **Kotlin** + **Jetpack Compose** + **Material Design 3** (avoid hardcoded hex colors in Composables; use `MaterialTheme.colorScheme`).
   - Follow accessibility guidelines (meaningful `contentDescription` values and minimum `48.dp` interactive touch targets).
   - Keep both **English** and **Italian** localizations in sync inside `AppStrings.kt` for every user-facing string.
   - Never commit API keys, signing keystores, `.env` files, or private credentials.
3. Add or update automated tests for every new feature or bug fix.

## Pre-PR Verification Checklist

Before opening a Pull Request, run the debug build and full unit test suite locally:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Ensure all tests pass cleanly and include a concise summary of the change and verification steps in your Pull Request description.
