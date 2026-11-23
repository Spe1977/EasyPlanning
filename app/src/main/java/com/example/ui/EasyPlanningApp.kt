package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.ShiftInstance
import com.example.i18n.LocalAppStrings
import com.example.i18n.stringsFor
import com.example.ui.screens.AllowanceEntryDialog
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EditMode
import com.example.ui.screens.EnterBackupPasswordDialog
import com.example.ui.screens.OvertimeEntryDialog
import com.example.ui.screens.RecurringScopeDialog
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SettingsSecurityScreen
import com.example.ui.screens.ShiftEditorSheet
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.BrandIndigoEnd
import com.example.ui.theme.EasyPlanningTheme
import java.time.LocalDate
import java.time.YearMonth

const val ROUTE_SCHEDULE = "schedule"
const val ROUTE_STATS = "stats"
const val ROUTE_SETTINGS = "settings"

@Composable
fun EasyPlanningApp(viewModel: EasyPlanningViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val viewMode by viewModel.scheduleViewMode.collectAsStateWithLifecycle()
    val monthSummaries by viewModel.monthDaySummaries.collectAsStateWithLifecycle()
    val activeTimelineDays by viewModel.activeTimelineDays.collectAsStateWithLifecycle()
    val selectedDaySummary by viewModel.selectedDaySummary.collectAsStateWithLifecycle()
    val scrollSignal by viewModel.scrollSignal.collectAsStateWithLifecycle()
    val statsPeriod by viewModel.statsPeriod.collectAsStateWithLifecycle()
    val statsSummary by viewModel.statsSummary.collectAsStateWithLifecycle()

    val appStrings = remember(prefs.language) { stringsFor(prefs.language) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Notification permission state (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    // Pending passwords for SAF backup export/import
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingImportPassword by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pwd = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && pwd != null) {
            viewModel.exportLocalEncryptedBackupToUri(uri, pwd)
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val pwd = pendingImportPassword
        pendingImportPassword = null
        if (uri != null && pwd != null) {
            viewModel.importLocalEncryptedBackupFromUri(uri, pwd)
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPdfToUri(uri)
        }
    }

    val driveResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.handleDriveResolutionResult(activityResult.data)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.LaunchDriveResolution -> {
                    runCatching {
                        driveResolutionLauncher.launch(
                            IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                        )
                    }.onFailure {
                        snackbarHostState.showSnackbar(appStrings.driveAuthError)
                    }
                }
                is UiEvent.SharePdfFile -> {
                    runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            event.file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, appStrings.shareMonthlyPdf))
                    }.onFailure {
                        snackbarHostState.showSnackbar(appStrings.pdfExportError)
                    }
                }
            }
        }
    }

    // Modal editors & dialogs state
    var showShiftEditor by remember { mutableStateOf(false) }
    var editorDate by remember { mutableStateOf(selectedDate) }
    var editorTarget by remember { mutableStateOf<ShiftInstance?>(null) }
    var editorMode by remember { mutableStateOf(EditMode.NEW_SHIFT) }
    var recurringEditPrompt by remember { mutableStateOf<ShiftInstance?>(null) }
    var recurringDeletePrompt by remember { mutableStateOf<ShiftInstance?>(null) }
    var overtimeTarget by remember { mutableStateOf<Pair<LocalDate, ShiftInstance?>?>(null) }
    var allowanceTarget by remember { mutableStateOf<Pair<LocalDate, ShiftInstance?>?>(null) }
    var showQuickDriveSyncDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        EasyPlanningTheme(
            themeMode = prefs.themeMode,
            dynamicColor = prefs.dynamicColor
        ) {
            // Avoid flashing Registration before DataStore finishes initial read
            if (!prefs.isPreferencesLoaded) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@EasyPlanningTheme
            }

            // Registration & Login Gate (Offline Username+Password or Online Google)
            if (!prefs.isLoggedIn) {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { _ ->
                    AuthScreen(
                        prefs = prefs,
                        isBusy = isBusy,
                        onSetLanguage = viewModel::setLanguage,
                        onRegisterOffline = viewModel::registerOffline,
                        onLoginOffline = viewModel::loginOffline,
                        onContinueWithGoogle = {
                            viewModel.signInOrLinkWithGoogle(
                                activityContext = context,
                                isLinkingExistingOffline = false
                            )
                        }
                    )
                }
                return@EasyPlanningTheme
            }

            if (prefs.appLockEnabled && !isUnlocked) {
                AppLockScreen(
                    onVerifyPin = viewModel::verifyPinAndUnlock,
                    onBiometricSuccess = viewModel::unlockApp
                )
                return@EasyPlanningTheme
            }

            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: ROUTE_SCHEDULE

            val navItems = listOf(
                Triple(ROUTE_SCHEDULE, appStrings.navSchedule, Icons.Filled.CalendarMonth to Icons.Outlined.CalendarMonth),
                Triple(ROUTE_STATS, appStrings.navStats, Icons.Filled.BarChart to Icons.Outlined.BarChart),
                Triple(ROUTE_SETTINGS, appStrings.navSettings, Icons.Filled.Settings to Icons.Outlined.Settings)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ) {
                            navItems.forEach { (route, label, icons) ->
                                val selected = currentRoute == route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navController.navigate(route) {
                                                popUpTo(ROUTE_SCHEDULE) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) icons.first else icons.second,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("nav_rail_$route")
                                )
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        bottomBar = {
                            if (!useNavRail) {
                                Column {
                                    HorizontalDivider(
                                        thickness = 0.75.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                    )
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        tonalElevation = 0.dp
                                    ) {
                                        navItems.forEach { (route, label, icons) ->
                                            val selected = currentRoute == route
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    if (!selected) {
                                                        navController.navigate(route) {
                                                            popUpTo(ROUTE_SCHEDULE) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) icons.first else icons.second,
                                                        contentDescription = label
                                                    )
                                                },
                                                label = { Text(label) },
                                                modifier = Modifier.testTag("nav_bar_$route")
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        floatingActionButton = {
                            if (currentRoute == ROUTE_SCHEDULE) {
                                FloatingActionButton(
                                    onClick = {
                                        editorDate = selectedDate
                                        editorTarget = null
                                        editorMode = EditMode.NEW_SHIFT
                                        showShiftEditor = true
                                    },
                                    shape = CircleShape,
                                    containerColor = BrandIndigoEnd,
                                    contentColor = Color.White,
                                    elevation = FloatingActionButtonDefaults.elevation(
                                        defaultElevation = 6.dp,
                                        pressedElevation = 10.dp
                                    ),
                                    modifier = Modifier
                                        .size(58.dp)
                                        .testTag("fab_add_shift")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = appStrings.addShift,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_SCHEDULE,
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxSize()
                            ) {
                                composable(ROUTE_SCHEDULE) {
                                    ScheduleScreen(
                                        selectedDate = selectedDate,
                                        visibleMonth = visibleMonth,
                                        viewMode = viewMode,
                                        monthSummaries = monthSummaries,
                                        activeTimelineDays = activeTimelineDays,
                                        selectedDaySummary = selectedDaySummary,
                                        scrollSignal = scrollSignal,
                                        isDriveConnected = prefs.driveConnected,
                                        showNotificationBanner = !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                                        onRequestNotificationPermission = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        onSelectDate = viewModel::selectDate,
                                        onJumpToDate = viewModel::jumpToDate,
                                        onJumpToToday = viewModel::jumpToToday,
                                        onShiftMonth = viewModel::shiftMonth,
                                        onViewModeChange = viewModel::setScheduleViewMode,
                                        onQuickSync = {
                                            if (prefs.driveConnected) {
                                                showQuickDriveSyncDialog = true
                                            } else {
                                                navController.navigate(ROUTE_SETTINGS) {
                                                    popUpTo(ROUTE_SCHEDULE) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        onAddShift = { targetDate ->
                                            editorDate = targetDate
                                            editorTarget = null
                                            editorMode = EditMode.NEW_SHIFT
                                            showShiftEditor = true
                                        },
                                        onEditShift = { shift ->
                                            editorDate = shift.date
                                            if (shift.isRecurring) {
                                                recurringEditPrompt = shift
                                            } else {
                                                editorTarget = shift
                                                editorMode = EditMode.EDIT_ENTIRE_SERIES
                                                showShiftEditor = true
                                            }
                                        },
                                        onDeleteShift = { shift ->
                                            if (shift.isRecurring) {
                                                recurringDeletePrompt = shift
                                            } else {
                                                viewModel.deleteEntireSeries(shift.seriesId)
                                            }
                                        },
                                        onAddOvertime = { targetDate, shift ->
                                            overtimeTarget = targetDate to shift
                                        },
                                        onDeleteOvertime = viewModel::deleteOvertime,
                                        onAddAllowance = { targetDate, shift ->
                                            allowanceTarget = targetDate to shift
                                        },
                                        onDeleteAllowance = viewModel::deleteAllowance
                                    )
                                }

                                composable(ROUTE_STATS) {
                                    StatsScreen(
                                        stats = statsSummary,
                                        selectedPeriod = statsPeriod,
                                        onSelectPeriod = viewModel::setStatsPeriod,
                                        onShiftPeriod = viewModel::shiftStatsPeriod,
                                        onExportMonthlyPdf = {
                                            val ym = YearMonth.from(statsSummary.startDate)
                                            createPdfLauncher.launch("EasyPlanning_${ym}.pdf")
                                        },
                                        onShareMonthlyPdf = viewModel::generateShareablePdf
                                    )
                                }

                                composable(ROUTE_SETTINGS) {
                                    SettingsSecurityScreen(
                                        prefs = prefs,
                                        onLinkGoogleAccount = {
                                            viewModel.signInOrLinkWithGoogle(
                                                activityContext = context,
                                                isLinkingExistingOffline = true
                                            )
                                        },
                                        onLogout = { viewModel.logout(context) },
                                        onSetLanguage = viewModel::setLanguage,
                                        onSetThemeMode = viewModel::setThemeMode,
                                        onSetDynamicColor = viewModel::setDynamicColor,
                                        onSetDefaultReminder = viewModel::setDefaultReminder,
                                        onSaveBackupPassword = viewModel::saveBackupPassword,
                                        onSetupAppLockPin = viewModel::setupAppLockPin,
                                        onSetAppLockEnabled = viewModel::setAppLockEnabled,
                                        onSetScreenSecurity = viewModel::setScreenSecurity,
                                        onRequestLocalBackupExport = { pwd ->
                                            pendingExportPassword = pwd
                                            createBackupLauncher.launch("EasyPlanning_Backup.epbak")
                                        },
                                        onRequestLocalBackupImport = { pwd ->
                                            pendingImportPassword = pwd
                                            openBackupLauncher.launch(arrayOf("*/*"))
                                        },
                                        onConnectDrive = { viewModel.startDriveAction(DriveActionRequest.Connect) },
                                        onDisconnectDrive = viewModel::disconnectGoogleDrive,
                                        onSyncDriveUpload = { pwd ->
                                            viewModel.startDriveAction(DriveActionRequest.Upload(pwd))
                                        },
                                        onRestoreDriveDownload = { pwd ->
                                            viewModel.startDriveAction(DriveActionRequest.Restore(pwd))
                                        },
                                        onSetDriveAutoSync = viewModel::setDriveAutoSync,
                                        onExecuteSecureReset = viewModel::verifyPasswordAndResetAll
                                    )
                                }
                            }

                            if (isBusy) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                )
                            }
                        }
                    }
                }
            }

            // Modals & Dialogs
            if (showShiftEditor) {
                val initialExtras = remember(editorTarget, editorMode) {
                    viewModel.resolveInitialExtrasForEdit(
                        target = editorTarget,
                        isSingleOccurrenceEdit = editorMode == EditMode.EDIT_SINGLE_OCCURRENCE
                    )
                }
                ShiftEditorSheet(
                    initialDate = editorDate,
                    defaultReminderMinutes = prefs.defaultReminderMinutes,
                    editTarget = editorTarget,
                    editMode = editorMode,
                    initialOvertime = initialExtras.overtime,
                    initialAllowances = initialExtras.allowances,
                    initialApplyExtrasToSeries = initialExtras.applyToSeries,
                    onDismiss = { showShiftEditor = false },
                    onSaveSeries = viewModel::saveShiftSeriesWithExtras,
                    onSaveSingleOccurrence = viewModel::saveSingleOccurrenceWithExtras
                )
            }

            recurringEditPrompt?.let { shift ->
                RecurringScopeDialog(
                    onDismiss = { recurringEditPrompt = null },
                    onOnlyThis = {
                        editorDate = shift.date
                        editorTarget = shift
                        editorMode = EditMode.EDIT_SINGLE_OCCURRENCE
                        showShiftEditor = true
                    },
                    onEntireSeries = {
                        editorDate = shift.date
                        editorTarget = shift
                        editorMode = EditMode.EDIT_ENTIRE_SERIES
                        showShiftEditor = true
                    }
                )
            }

            recurringDeletePrompt?.let { shift ->
                RecurringScopeDialog(
                    onDismiss = { recurringDeletePrompt = null },
                    onOnlyThis = { viewModel.deleteSingleOccurrence(shift) },
                    onEntireSeries = { viewModel.deleteEntireSeries(shift.seriesId) }
                )
            }

            overtimeTarget?.let { (targetDate, shift) ->
                OvertimeEntryDialog(
                    date = targetDate,
                    onDismiss = { overtimeTarget = null },
                    onSave = { type, halfHours, note ->
                        viewModel.addOvertime(
                            date = targetDate,
                            shiftSeriesId = shift?.seriesId,
                            type = type,
                            halfHours = halfHours,
                            notes = note
                        )
                    }
                )
            }

            allowanceTarget?.let { (targetDate, shift) ->
                AllowanceEntryDialog(
                    date = targetDate,
                    onDismiss = { allowanceTarget = null },
                    onSave = { presetKey, label, quantity, note ->
                        viewModel.addAllowance(
                            date = targetDate,
                            shiftSeriesId = shift?.seriesId,
                            presetKey = presetKey,
                            label = label,
                            quantity = quantity,
                            notes = note
                        )
                    }
                )
            }

            if (showQuickDriveSyncDialog) {
                EnterBackupPasswordDialog(
                    title = appStrings.syncNowDrive,
                    requireMinLength = true,
                    onDismiss = { showQuickDriveSyncDialog = false },
                    onConfirm = { pwd ->
                        viewModel.startDriveAction(DriveActionRequest.Upload(pwd))
                    }
                )
            }
        }
    }
}
