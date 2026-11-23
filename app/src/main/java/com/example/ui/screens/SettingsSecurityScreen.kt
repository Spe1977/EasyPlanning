package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.AuthProvider
import com.example.data.model.ReminderOption
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.BrandBlueStart
import com.example.ui.theme.BrandIndigoEnd
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSecurityScreen(
    prefs: UserPreferences,
    onLinkGoogleAccount: () -> Unit,
    onLogout: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDefaultReminder: (Int) -> Unit,
    onSaveBackupPassword: (String) -> Unit,
    onSetupAppLockPin: (String) -> Unit,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetScreenSecurity: (Boolean) -> Unit,
    onRequestLocalBackupExport: (password: String) -> Unit,
    onRequestLocalBackupImport: (password: String) -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onSyncDriveUpload: (password: String) -> Unit,
    onRestoreDriveDownload: (password: String) -> Unit,
    onSetDriveAutoSync: (Boolean) -> Unit,
    onExecuteSecureReset: (password: String?, onResult: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showSetupPinDialog by remember { mutableStateOf(false) }
    var showLocalExportPwdDialog by remember { mutableStateOf(false) }
    var showLocalImportPwdDialog by remember { mutableStateOf(false) }
    var showDriveUploadPwdDialog by remember { mutableStateOf(false) }
    var showDriveRestorePwdDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val dateTimeFmt = remember(strings.language) {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", strings.javaLocale)
    }

    fun formatTimestamp(millis: Long?): String {
        if (millis == null) return strings.neverSynced
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFmt)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Section 0: User Profile, Offline-to-Google Linking & Logout
        item {
            val primaryDisplayName = prefs.googleDisplayName
                ?: prefs.username
                ?: prefs.googleEmail
                ?: strings.appName
            val initials = primaryDisplayName
                .split(" ", ".", "@")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "EP" }

            val providerBadge = when (prefs.authProvider) {
                AuthProvider.OFFLINE_LOCAL -> strings.badgeOfflineOnly
                AuthProvider.GOOGLE -> strings.badgeGoogleAccount
                AuthProvider.HYBRID -> strings.badgeHybridAccount
                AuthProvider.NONE -> strings.badgeOfflineOnly
            }

            SettingsCard(title = strings.sectionProfile, icon = Icons.Filled.Person) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(BrandBlueStart, BrandIndigoEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = primaryDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!prefs.googleEmail.isNullOrBlank()) {
                            Text(
                                text = prefs.googleEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (!prefs.username.isNullOrBlank() && prefs.username != primaryDisplayName) {
                            Text(
                                text = "@${prefs.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = providerBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Offline -> Google seamless upgrade CTA
                if (prefs.authProvider == AuthProvider.OFFLINE_LOCAL || prefs.googleEmail.isNullOrBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Text(
                        text = strings.linkGoogleAccountDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = onLinkGoogleAccount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("link_google_account_button")
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.linkGoogleAccount)
                    }
                }

                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = strings.logoutButton,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.logoutButton)
                }
            }
        }

        // Section 1: Appearance & Language
        item {
            SettingsCard(title = strings.sectionAppearance, icon = Icons.Filled.Palette) {
                // Language switcher (IT / EN)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(text = strings.languageLabel, style = MaterialTheme.typography.titleSmall)
                }

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("language_switcher")
                ) {
                    SegmentedButton(
                        selected = prefs.language == AppLanguage.ITALIAN,
                        onClick = { onSetLanguage(AppLanguage.ITALIAN) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("lang_it_button")
                    ) { Text("Italiano") }
                    SegmentedButton(
                        selected = prefs.language == AppLanguage.ENGLISH,
                        onClick = { onSetLanguage(AppLanguage.ENGLISH) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("lang_en_button")
                    ) { Text("English") }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Theme selector
                Text(text = strings.themeLabel, style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("theme_switcher")
                ) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to strings.themeSystem,
                        ThemeMode.LIGHT to strings.themeLight,
                        ThemeMode.DARK to strings.themeDark
                    )
                    modes.forEachIndexed { idx, (mode, label) ->
                        SegmentedButton(
                            selected = prefs.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(idx, modes.size),
                            modifier = Modifier.testTag("theme_${mode.key}_button")
                        ) { Text(label) }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsSwitchRow(
                        title = strings.dynamicColorLabel,
                        subtitle = strings.dynamicColorDesc,
                        checked = prefs.dynamicColor,
                        onCheckedChange = onSetDynamicColor,
                        testTag = "dynamic_color_switch"
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Default reminder lead time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(text = strings.defaultReminderLabel, style = MaterialTheme.typography.titleSmall)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReminderOption.entries.forEach { opt ->
                        FilterChip(
                            selected = prefs.defaultReminderMinutes == opt.minutesBefore,
                            onClick = { onSetDefaultReminder(opt.minutesBefore) },
                            label = { Text(strings.reminderText(opt)) }
                        )
                    }
                }
            }
        }

        // Section 2: Security & Privacy
        item {
            SettingsCard(title = strings.sectionSecurity, icon = Icons.Filled.Shield) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(strings.backupPasswordLabel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (prefs.hasBackupPassword) strings.backupPasswordConfigured else strings.backupPasswordNotSet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { showSetPasswordDialog = true },
                        modifier = Modifier.testTag("configure_backup_password_button")
                    ) {
                        Text(if (prefs.hasBackupPassword) strings.changePassword else strings.setPassword)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                SettingsSwitchRow(
                    title = strings.appLockLabel,
                    subtitle = strings.appLockDesc,
                    checked = prefs.appLockEnabled,
                    onCheckedChange = { enable ->
                        if (enable && !prefs.hasAppLockPin) {
                            showSetupPinDialog = true
                        } else {
                            onSetAppLockEnabled(enable)
                        }
                    },
                    icon = Icons.Filled.Lock,
                    testTag = "app_lock_switch"
                )

                if (prefs.appLockEnabled) {
                    OutlinedButton(
                        onClick = { showSetupPinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${strings.changePassword} ${strings.appLockPinTitle}")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                SettingsSwitchRow(
                    title = strings.screenSecurityLabel,
                    subtitle = strings.screenSecurityDesc,
                    checked = prefs.screenSecurityEnabled,
                    onCheckedChange = onSetScreenSecurity,
                    icon = Icons.Filled.VisibilityOff,
                    testTag = "screen_security_switch"
                )
            }
        }

        // Section 3: Encrypted Local Backup & Google Drive Sync
        item {
            SettingsCard(title = strings.sectionBackupSync, icon = Icons.Filled.EnhancedEncryption) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.encryptionBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Local SAF encrypted backup buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showLocalExportPwdDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_local_backup_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.localBackupExport, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showLocalImportPwdDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_local_backup_button")
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.localBackupImport, maxLines = 1)
                    }
                }

                Text(
                    text = "${strings.lastSyncLabel}: ${formatTimestamp(prefs.lastLocalBackupMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Google Drive Personal Sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.googleDriveTitle, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (prefs.driveConnected) {
                                "${strings.googleDriveConnectedAs} · ${prefs.driveAccountEmail ?: "appDataFolder"}"
                            } else {
                                strings.googleDriveNotConnected
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (prefs.driveConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strings.googleDriveSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!prefs.driveConnected) {
                    Button(
                        onClick = onConnectDrive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_google_drive_button")
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.connectGoogleDrive)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDriveUploadPwdDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("drive_sync_now_button")
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.syncNowDrive, maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { showDriveRestorePwdDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("drive_restore_button")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.restoreFromDrive, maxLines = 1)
                        }
                    }

                    SettingsSwitchRow(
                        title = strings.autoSyncLabel,
                        subtitle = "${strings.lastSyncLabel}: ${formatTimestamp(prefs.lastDriveSyncMillis)}",
                        checked = prefs.driveAutoSync,
                        onCheckedChange = onSetDriveAutoSync,
                        testTag = "drive_auto_sync_switch"
                    )

                    OutlinedButton(
                        onClick = onDisconnectDrive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.disconnectGoogleDrive)
                    }
                }
            }
        }

        // Section 4: Danger Zone — Multi-Confirmation Secure Reset
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = strings.sectionDangerZone,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = strings.resetAllDataDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("reset_all_data_button")
                    ) {
                        Text(strings.resetAllDataTitle)
                    }
                }
            }
        }
    }

    if (showSetPasswordDialog) {
        SetBackupPasswordDialog(
            onDismiss = { showSetPasswordDialog = false },
            onConfirm = onSaveBackupPassword
        )
    }

    if (showSetupPinDialog) {
        SetupPinDialog(
            onDismiss = { showSetupPinDialog = false },
            onConfirm = onSetupAppLockPin
        )
    }

    if (showLocalExportPwdDialog) {
        EnterBackupPasswordDialog(
            title = strings.localBackupExport,
            requireMinLength = true,
            onDismiss = { showLocalExportPwdDialog = false },
            onConfirm = onRequestLocalBackupExport
        )
    }

    if (showLocalImportPwdDialog) {
        EnterBackupPasswordDialog(
            title = strings.localBackupImport,
            requireMinLength = false,
            onDismiss = { showLocalImportPwdDialog = false },
            onConfirm = onRequestLocalBackupImport
        )
    }

    if (showDriveUploadPwdDialog) {
        EnterBackupPasswordDialog(
            title = strings.syncNowDrive,
            requireMinLength = true,
            onDismiss = { showDriveUploadPwdDialog = false },
            onConfirm = onSyncDriveUpload
        )
    }

    if (showDriveRestorePwdDialog) {
        EnterBackupPasswordDialog(
            title = strings.restoreFromDrive,
            requireMinLength = false,
            onDismiss = { showDriveRestorePwdDialog = false },
            onConfirm = onRestoreDriveDownload
        )
    }

    if (showResetDialog) {
        MultiStepResetDialog(
            hasBackupPassword = prefs.hasBackupPassword,
            onDismiss = { showResetDialog = false },
            onExecuteReset = onExecuteSecureReset
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 3.dp,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
