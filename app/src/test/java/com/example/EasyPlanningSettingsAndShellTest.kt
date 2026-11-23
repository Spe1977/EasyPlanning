package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EasyPlanningDatabase
import com.example.data.local.PreferencesStore
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AppLanguage
import com.example.data.model.AuthProvider
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderOption
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.domain.RecurrenceEngine
import com.example.i18n.LocalAppStrings
import com.example.i18n.stringsFor
import com.example.security.CryptoManager
import com.example.ui.EasyPlanningApp
import com.example.ui.EasyPlanningViewModel
import com.example.ui.ROUTE_SETTINGS
import com.example.ui.ROUTE_STATS
import com.example.ui.screens.EditMode
import com.example.ui.screens.EnterBackupPasswordDialog
import com.example.ui.screens.SettingsSecurityScreen
import com.example.ui.screens.ShiftEditorSheet
import com.example.ui.theme.EasyPlanningTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EasyPlanningSettingsAndShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private var vmStore: ViewModelStore? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val testKey = SecretKeySpec(ByteArray(32) { (it + 31).toByte() }, "AES")
        CryptoManager.keyProviderOverride = { testKey }
        CryptoManager.resetKeystoreCacheForTesting()
        runBlocking {
            EasyPlanningDatabase.getInstance(app).dao().wipeAllTables()
            PreferencesStore(app).clearAllUserPreferencesPreserveLocaleAndTheme()
        }
        EasyPlanningDatabase.resetForTesting()
    }

    @After
    fun tearDown() {
        vmStore?.clear()
        vmStore = null
        runBlocking {
            EasyPlanningDatabase.getInstance(app).dao().wipeAllTables()
        }
        EasyPlanningDatabase.resetForTesting()
        CryptoManager.keyProviderOverride = null
        CryptoManager.resetKeystoreCacheForTesting()
    }

    private fun createViewModel(): EasyPlanningViewModel {
        val store = ViewModelStore().also { vmStore = it }
        return ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[EasyPlanningViewModel::class.java]
    }

    @Test
    fun shiftEditorSheetPresetsWeeklyRotatingEndConditionsColorsRemindersAndBlankTitle() {
        val monday = LocalDate.of(2026, 11, 23) // Monday
        var savedSeries: ShiftSeriesEntity? = null

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    ShiftEditorSheet(
                        initialDate = monday,
                        defaultReminderMinutes = ReminderOption.MIN_30.minutesBefore,
                        editTarget = null,
                        editMode = EditMode.NEW_SHIFT,
                        onDismiss = {},
                        onSaveSeries = { series, _, _, _ -> savedSeries = series },
                        onSaveSingleOccurrence = { _, _, _, _, _, _, _, _, _ -> }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // 1. Quick preset "Notte" -> sets 22:00-06:00 and displays overnight badge
        composeRule.onNodeWithText("Notte").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(stringsFor(AppLanguage.ITALIAN).overnightBadge).assertIsDisplayed()

        // 2. Pick color swatch #5 & reminder "1 ora prima"
        composeRule.onNodeWithTag("color_swatch_5").performScrollTo().performClick()
        composeRule.onNodeWithText("1 ora prima").performScrollTo().performClick()

        // 3. Configure WEEKLY -> toggle Thursday ON, verify Monday cannot be turned off if it's the only selected day
        composeRule.onNodeWithTag("recurrence_WEEKLY").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Gi").performScrollTo().performClick()

        // 4. Switch to ROTATING_CYCLE -> increment workDays (4->5) and offDays (2->3), set AFTER_COUNT end condition
        composeRule.onNodeWithTag("recurrence_ROTATING_CYCLE").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Increase")[0].performScrollTo().performClick()
        composeRule.onAllNodesWithContentDescription("Increase")[1].performScrollTo().performClick()
        composeRule.onNodeWithText(stringsFor(AppLanguage.ITALIAN).endAfterCount).performScrollTo().performClick()
        composeRule.waitForIdle()

        // 5. Clear title -> fallback to "Turno" on save
        composeRule.onNodeWithTag("shift_title_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("save_shift_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedSeries)
        assertEquals("Turno", savedSeries?.title)
        assertEquals(22 * 60, savedSeries?.startMinuteOfDay)
        assertEquals(6 * 60, savedSeries?.endMinuteOfDay)
        assertEquals(5, savedSeries?.colorId)
        assertEquals(60, savedSeries?.reminderMinutesBefore)
        assertEquals(RecurrenceType.ROTATING_CYCLE.key, savedSeries?.recurrenceType)
        assertEquals(5, savedSeries?.cycleWorkDays)
        assertEquals(3, savedSeries?.cycleOffDays)
        assertEquals(20, savedSeries?.maxOccurrences)
        val expectedMask = RecurrenceEngine.weekdayBit(DayOfWeek.MONDAY) or RecurrenceEngine.weekdayBit(DayOfWeek.THURSDAY)
        assertEquals(expectedMask, savedSeries?.daysOfWeekMask)
    }

    @Test
    fun settingsSecurityScreenCoversThemeDynamicColorReminderSwitchesAndDriveStates() {
        var selectedTheme: ThemeMode? = null
        var dynamicEnabled: Boolean? = null
        var reminderMinutes: Int? = null
        var screenSecEnabled: Boolean? = null
        var autoSyncEnabled: Boolean? = null
        var disconnectedDrive = false

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    SettingsSecurityScreen(
                        prefs = UserPreferences(
                            isPreferencesLoaded = true,
                            hasRegisteredAccount = true,
                            isLoggedIn = true,
                            authProvider = AuthProvider.HYBRID,
                            username = "marco.rossi",
                            googleEmail = "marco@example.com",
                            googleDisplayName = "Marco Rossi",
                            hasBackupPassword = true,
                            hasAppLockPin = false,
                            appLockEnabled = false,
                            driveConnected = true,
                            driveAccountEmail = "marco@example.com",
                            driveAutoSync = false
                        ),
                        onLinkGoogleAccount = {},
                        onLogout = {},
                        onSetLanguage = {},
                        onSetThemeMode = { selectedTheme = it },
                        onSetDynamicColor = { dynamicEnabled = it },
                        onSetDefaultReminder = { reminderMinutes = it },
                        onSaveBackupPassword = {},
                        onSetupAppLockPin = {},
                        onSetAppLockEnabled = {},
                        onSetScreenSecurity = { screenSecEnabled = it },
                        onRequestLocalBackupExport = {},
                        onRequestLocalBackupImport = {},
                        onConnectDrive = {},
                        onDisconnectDrive = { disconnectedDrive = true },
                        onSyncDriveUpload = {},
                        onRestoreDriveDownload = {},
                        onSetDriveAutoSync = { autoSyncEnabled = it },
                        onExecuteSecureReset = { _, cb -> cb(true) }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Theme, Dynamic Color, Default Reminder
        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("theme_dark_button"))
        composeRule.onNodeWithTag("theme_dark_button").performClick()
        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("dynamic_color_switch"))
        composeRule.onNodeWithTag("dynamic_color_switch").performClick()
        val oneHourLabel = stringsFor(AppLanguage.ITALIAN).reminderText(ReminderOption.HOUR_1)
        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasText(oneHourLabel))
        composeRule.onNodeWithText(oneHourLabel).performClick()

        // Screen security toggle
        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("screen_security_switch"))
        composeRule.onNodeWithTag("screen_security_switch").performClick()

        // Connected Drive controls: auto-sync switch & disconnect button
        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("drive_auto_sync_switch"))
        composeRule.onNodeWithTag("drive_auto_sync_switch").performClick()
        composeRule.onNodeWithText(stringsFor(AppLanguage.ITALIAN).disconnectGoogleDrive).performClick()

        // Toggling App Lock ON without existing PIN must open SetupPinDialog
        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("app_lock_switch"))
        composeRule.onNodeWithTag("app_lock_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_app_pin_button").assertIsDisplayed()

        assertEquals(ThemeMode.DARK, selectedTheme)
        assertEquals(true, dynamicEnabled)
        assertEquals(60, reminderMinutes)
        assertEquals(true, screenSecEnabled)
        assertEquals(true, autoSyncEnabled)
        assertTrue(disconnectedDrive)
    }

    @Test
    fun enterBackupPasswordDialogEnforcesMinLengthOnlyWhenRequired() {
        var confirmedPwd: String? = null
        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    EnterBackupPasswordDialog(
                        title = strings.localBackupExport,
                        requireMinLength = true,
                        onDismiss = {},
                        onConfirm = { confirmedPwd = it }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // Blank and <8 chars rejected when requireMinLength = true
        composeRule.onNodeWithTag("confirm_enter_password_button").performClick()
        assertNull(confirmedPwd)
        composeRule.onNodeWithTag("enter_backup_password_input").performTextInput("1234567")
        composeRule.onNodeWithTag("confirm_enter_password_button").performClick()
        assertNull(confirmedPwd)

        // >= 8 chars accepted
        composeRule.onNodeWithTag("enter_backup_password_input").performTextClearance()
        composeRule.onNodeWithTag("enter_backup_password_input").performTextInput("Sicura2026!")
        composeRule.onNodeWithTag("confirm_enter_password_button").performClick()
        composeRule.waitForIdle()
        assertEquals("Sicura2026!", confirmedPwd)
    }

    @Test
    fun easyPlanningAppEnforcesAuthLockGateAndAdaptiveNavigationBar() {
        val store = PreferencesStore(app)
        runBlocking {
            store.registerOfflineAccount("elena.neri", "Chiave2026!".toCharArray())
            store.saveAppLockPin("1234".toCharArray())
        }
        val vm = createViewModel()

        composeRule.setContent {
            EasyPlanningApp(viewModel = vm)
        }

        // Wait until DataStore loads preferences -> App Lock gate must block main navigation
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.preferences.value.isPreferencesLoaded
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_lock_pin_input").assertIsDisplayed()

        // Unlock with valid PIN -> compact bottom NavigationBar becomes active
        composeRule.onNodeWithTag("app_lock_pin_input").performTextInput("1234")
        composeRule.onNodeWithTag("app_lock_unlock_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.isUnlocked.value
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("fab_add_shift").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_bar_$ROUTE_STATS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stats_lazy_column").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_bar_$ROUTE_SETTINGS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_column").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h600dp-mdpi")
    fun easyPlanningAppUsesNavigationRailOnExpandedTabletScreen() {
        val store = PreferencesStore(app)
        runBlocking {
            store.registerOfflineAccount("paolo.blu", "Chiave2026!".toCharArray())
        }
        val vm = createViewModel()

        composeRule.setContent {
            EasyPlanningApp(viewModel = vm)
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            vm.preferences.value.isPreferencesLoaded
        }
        composeRule.waitForIdle()

        // Expanded screen (>= 600dp) must show NavigationRail instead of bottom NavigationBar
        composeRule.onNodeWithTag("nav_rail_$ROUTE_STATS").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stats_lazy_column").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_rail_$ROUTE_SETTINGS").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_column").assertIsDisplayed()
    }
}
