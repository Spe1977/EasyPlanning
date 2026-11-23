package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.AuthProvider
import com.example.data.model.ReminderOption
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.easyPlanningDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "easyplanning_preferences"
)

class PreferencesStore(private val context: Context) {

    private object Keys {
        val HAS_REGISTERED_ACCOUNT = booleanPreferencesKey("has_registered_account")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val AUTH_PROVIDER = stringPreferencesKey("auth_provider")
        val USERNAME_ENC = stringPreferencesKey("username_enc")
        val GOOGLE_DISPLAY_NAME_ENC = stringPreferencesKey("google_display_name_enc")
        val GOOGLE_EMAIL_ENC = stringPreferencesKey("google_email_enc")

        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_REMINDER = intPreferencesKey("default_reminder")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val SCREEN_SECURITY = booleanPreferencesKey("screen_security")
        val BACKUP_PWD_VERIFIER = stringPreferencesKey("backup_pwd_verifier")
        val APP_LOCK_PIN_VERIFIER = stringPreferencesKey("app_lock_pin_verifier")
        val DRIVE_CONNECTED = booleanPreferencesKey("drive_connected")
        val DRIVE_EMAIL_ENC = stringPreferencesKey("drive_email_enc")
        val DRIVE_AUTO_SYNC = booleanPreferencesKey("drive_auto_sync")
        val LAST_LOCAL_BACKUP = longPreferencesKey("last_local_backup")
        val LAST_DRIVE_SYNC = longPreferencesKey("last_drive_sync")
    }

    private fun decryptOptional(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return runCatching { CryptoManager.decryptWithKeystore(encoded) }.getOrNull()
    }

    val preferencesFlow: Flow<UserPreferences> = context.easyPlanningDataStore.data.map { prefs ->
        UserPreferences(
            isPreferencesLoaded = true,
            hasRegisteredAccount = prefs[Keys.HAS_REGISTERED_ACCOUNT] ?: false,
            isLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: false,
            authProvider = AuthProvider.fromKey(prefs[Keys.AUTH_PROVIDER]),
            username = decryptOptional(prefs[Keys.USERNAME_ENC]),
            googleDisplayName = decryptOptional(prefs[Keys.GOOGLE_DISPLAY_NAME_ENC]),
            googleEmail = decryptOptional(prefs[Keys.GOOGLE_EMAIL_ENC]),
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            defaultReminderMinutes = prefs[Keys.DEFAULT_REMINDER] ?: ReminderOption.MIN_30.minutesBefore,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            screenSecurityEnabled = prefs[Keys.SCREEN_SECURITY] ?: false,
            hasBackupPassword = !prefs[Keys.BACKUP_PWD_VERIFIER].isNullOrBlank(),
            hasAppLockPin = !prefs[Keys.APP_LOCK_PIN_VERIFIER].isNullOrBlank(),
            driveConnected = prefs[Keys.DRIVE_CONNECTED] ?: false,
            driveAccountEmail = decryptOptional(prefs[Keys.DRIVE_EMAIL_ENC]) ?: decryptOptional(prefs[Keys.GOOGLE_EMAIL_ENC]),
            driveAutoSync = prefs[Keys.DRIVE_AUTO_SYNC] ?: false,
            lastLocalBackupMillis = prefs[Keys.LAST_LOCAL_BACKUP],
            lastDriveSyncMillis = prefs[Keys.LAST_DRIVE_SYNC]
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Registers an offline local account with a unified password used for login,
     * AES-256-GCM backups, and multi-confirmation data reset.
     */
    suspend fun registerOfflineAccount(username: String, password: CharArray) {
        val cleanUser = username.trim()
        val (encUsername, protectedVerifier) = withContext(Dispatchers.Default) {
            CryptoManager.encryptWithKeystore(cleanUser) to CryptoManager.createProtectedVerifier(password)
        }
        context.easyPlanningDataStore.edit { prefs ->
            prefs[Keys.HAS_REGISTERED_ACCOUNT] = true
            prefs[Keys.IS_LOGGED_IN] = true
            val existingProvider = AuthProvider.fromKey(prefs[Keys.AUTH_PROVIDER])
            prefs[Keys.AUTH_PROVIDER] = if (existingProvider == AuthProvider.GOOGLE || existingProvider == AuthProvider.HYBRID) {
                AuthProvider.HYBRID.key
            } else {
                AuthProvider.OFFLINE_LOCAL.key
            }
            prefs[Keys.USERNAME_ENC] = encUsername
            prefs[Keys.BACKUP_PWD_VERIFIER] = protectedVerifier
        }
    }

    /**
     * Verifies username + password locally in constant time against the Keystore-protected verifier.
     */
    suspend fun verifyOfflineLogin(username: String, password: CharArray): Boolean {
        val prefs = context.easyPlanningDataStore.data.first()
        val ok = withContext(Dispatchers.Default) {
            val storedUsername = decryptOptional(prefs[Keys.USERNAME_ENC])
            val storedVerifier = prefs[Keys.BACKUP_PWD_VERIFIER]
            if (storedUsername.isNullOrBlank() || storedVerifier.isNullOrBlank()) {
                password.fill('\u0000')
                return@withContext false
            }
            val usernameMatches = storedUsername.equals(username.trim(), ignoreCase = true)
            val passwordMatches = CryptoManager.verifyProtectedSecret(password, storedVerifier)
            usernameMatches && passwordMatches
        }
        if (ok) {
            context.easyPlanningDataStore.edit { it[Keys.IS_LOGGED_IN] = true }
        }
        return ok
    }

    /**
     * Signs in with Google OR upgrades an existing offline account to HYBRID without losing any data.
     */
    suspend fun signInOrLinkGoogleAccount(email: String, displayName: String?) {
        val cleanEmail = email.trim()
        val cleanName = displayName?.trim()?.takeIf { it.isNotBlank() }
        val (encEmail, encName) = withContext(Dispatchers.IO) {
            CryptoManager.encryptWithKeystore(cleanEmail) to cleanName?.let { CryptoManager.encryptWithKeystore(it) }
        }
        context.easyPlanningDataStore.edit { prefs ->
            val hadOffline = !prefs[Keys.USERNAME_ENC].isNullOrBlank()
            prefs[Keys.HAS_REGISTERED_ACCOUNT] = true
            prefs[Keys.IS_LOGGED_IN] = true
            prefs[Keys.AUTH_PROVIDER] = if (hadOffline) AuthProvider.HYBRID.key else AuthProvider.GOOGLE.key
            prefs[Keys.GOOGLE_EMAIL_ENC] = encEmail
            if (encName != null) {
                prefs[Keys.GOOGLE_DISPLAY_NAME_ENC] = encName
            }
            prefs[Keys.DRIVE_EMAIL_ENC] = encEmail
        }
    }

    /**
     * Non-destructive logout: closes session without deleting shifts, recurrences, or account profile.
     */
    suspend fun logout() {
        context.easyPlanningDataStore.edit { it[Keys.IS_LOGGED_IN] = false }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.easyPlanningDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.easyPlanningDataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.easyPlanningDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultReminder(minutes: Int) {
        context.easyPlanningDataStore.edit { it[Keys.DEFAULT_REMINDER] = minutes }
    }

    suspend fun setScreenSecurity(enabled: Boolean) {
        context.easyPlanningDataStore.edit { it[Keys.SCREEN_SECURITY] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.easyPlanningDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun saveBackupPassword(password: CharArray) {
        val protectedVerifier = withContext(Dispatchers.Default) {
            CryptoManager.createProtectedVerifier(password)
        }
        context.easyPlanningDataStore.edit { it[Keys.BACKUP_PWD_VERIFIER] = protectedVerifier }
    }

    suspend fun verifyBackupPassword(candidate: CharArray): Boolean {
        val stored = context.easyPlanningDataStore.data.first()[Keys.BACKUP_PWD_VERIFIER]
            ?: run {
                candidate.fill('\u0000')
                return false
            }
        return withContext(Dispatchers.Default) {
            CryptoManager.verifyProtectedSecret(candidate, stored)
        }
    }

    suspend fun saveAppLockPin(pin: CharArray) {
        val protectedVerifier = withContext(Dispatchers.Default) {
            CryptoManager.createProtectedVerifier(pin)
        }
        context.easyPlanningDataStore.edit {
            it[Keys.APP_LOCK_PIN_VERIFIER] = protectedVerifier
            it[Keys.APP_LOCK_ENABLED] = true
        }
    }

    suspend fun verifyAppLockPin(candidate: CharArray): Boolean {
        val stored = context.easyPlanningDataStore.data.first()[Keys.APP_LOCK_PIN_VERIFIER]
            ?: run {
                candidate.fill('\u0000')
                return false
            }
        return withContext(Dispatchers.Default) {
            CryptoManager.verifyProtectedSecret(candidate, stored)
        }
    }

    suspend fun setDriveConnection(connected: Boolean, email: String?) {
        val encEmail = if (connected && !email.isNullOrBlank()) {
            withContext(Dispatchers.IO) { CryptoManager.encryptWithKeystore(email) }
        } else {
            null
        }
        context.easyPlanningDataStore.edit { prefs ->
            prefs[Keys.DRIVE_CONNECTED] = connected
            if (connected && encEmail != null) {
                prefs[Keys.DRIVE_EMAIL_ENC] = encEmail
            } else if (!connected) {
                prefs.remove(Keys.DRIVE_EMAIL_ENC)
                prefs[Keys.DRIVE_AUTO_SYNC] = false
            }
        }
    }

    suspend fun setDriveAutoSync(enabled: Boolean) {
        context.easyPlanningDataStore.edit { it[Keys.DRIVE_AUTO_SYNC] = enabled }
    }

    suspend fun markLocalBackupNow() {
        context.easyPlanningDataStore.edit { it[Keys.LAST_LOCAL_BACKUP] = System.currentTimeMillis() }
    }

    suspend fun markDriveSyncNow() {
        context.easyPlanningDataStore.edit { it[Keys.LAST_DRIVE_SYNC] = System.currentTimeMillis() }
    }

    suspend fun clearAllUserPreferencesPreserveLocaleAndTheme() {
        val current = preferencesFlow.first()
        context.easyPlanningDataStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.LANGUAGE] = current.language.code
            prefs[Keys.THEME_MODE] = current.themeMode.key
            prefs[Keys.DYNAMIC_COLOR] = current.dynamicColor
        }
    }
}
