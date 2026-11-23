package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AllowanceDraft
import com.example.data.model.AllowancePreset
import com.example.data.model.AppLanguage
import com.example.data.model.DaySummary
import com.example.data.model.OvertimeDraft
import com.example.data.model.OvertimeType
import com.example.data.model.RecurrenceType
import com.example.data.model.ScheduleViewMode
import com.example.domain.RecurrenceEngine
import com.example.i18n.LocalAppStrings
import com.example.i18n.stringsFor
import com.example.ui.screens.AllowanceEntryDialog
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.EditMode
import com.example.ui.screens.MultiStepResetDialog
import com.example.ui.screens.OvertimeEntryDialog
import com.example.ui.screens.RecurringScopeDialog
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SetBackupPasswordDialog
import com.example.ui.screens.SetupPinDialog
import com.example.ui.screens.ShiftEditorSheet
import com.example.ui.theme.EasyPlanningTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EasyPlanningUiDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h915dp-mdpi")
    fun scheduleCalendarNavigationSearchDateAndDayDetailBottomSheetWork() {
        val anchor = LocalDate.of(2026, 11, 23)
        val series = ShiftSeriesEntity(
            id = 5L,
            title = "Pomeriggio",
            startDateEpochDay = anchor.toEpochDay(),
            startMinuteOfDay = 13 * 60,
            endMinuteOfDay = 19 * 60,
            colorId = 1,
            notes = "Consegna chiavi",
            recurrenceType = RecurrenceType.NONE.key
        )
        val shift = RecurrenceEngine.expandRange(listOf(series), emptyList(), anchor, anchor).single()
        val ot = OvertimeEntryEntity(id = 1L, dateEpochDay = anchor.toEpochDay(), overtimeType = OvertimeType.SCHEDULED.key, halfHours = 2)
        val al = AllowanceEntryEntity(id = 1L, dateEpochDay = anchor.toEpochDay(), presetKey = "MEAL_VOUCHER", label = "Buono Pasto", quantity = 1)
        val summary = DaySummary(anchor, listOf(shift), listOf(ot), listOf(al))

        var shiftedDelta = 0L
        var jumpedToToday = false
        var jumpedDate: LocalDate? = null
        var selectedCellDate: LocalDate? = null
        var addedOtFromSheet = false
        var addedAllFromSheet = false

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    ScheduleScreen(
                        selectedDate = anchor,
                        visibleMonth = YearMonth.from(anchor),
                        viewMode = ScheduleViewMode.MONTHLY_CALENDAR,
                        monthSummaries = mapOf(anchor to summary),
                        activeTimelineDays = listOf(summary),
                        selectedDaySummary = summary,
                        scrollSignal = anchor to 0L,
                        isDriveConnected = true,
                        showNotificationBanner = false,
                        onRequestNotificationPermission = {},
                        onSelectDate = { selectedCellDate = it },
                        onJumpToDate = { jumpedDate = it },
                        onJumpToToday = { jumpedToToday = true },
                        onShiftMonth = { shiftedDelta += it },
                        onViewModeChange = {},
                        onQuickSync = {},
                        onAddShift = {},
                        onEditShift = {},
                        onDeleteShift = {},
                        onAddOvertime = { _, _ -> addedOtFromSheet = true },
                        onDeleteOvertime = {},
                        onAddAllowance = { _, _ -> addedAllFromSheet = true },
                        onDeleteAllowance = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Navigate months & tap "Oggi" link on calendar banner
        composeRule.onNodeWithTag("next_month_button").performClick()
        composeRule.onNodeWithTag("prev_month_button").performClick()
        composeRule.onNodeWithTag("calendar_today_link").performClick()
        composeRule.waitForIdle()
        assertEquals(0L, shiftedDelta)
        assertTrue(jumpedToToday)

        // Tap a day cell in the calendar grid
        val targetCell = LocalDate.of(2026, 11, 24)
        composeRule.onNodeWithTag("calendar_cell_$targetCell").performClick()
        composeRule.waitForIdle()
        assertEquals(targetCell, selectedCellDate)

        // Open Search Date ("Cerca Data") dialog and confirm
        composeRule.onNodeWithTag("search_date_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_search_date_button").performClick()
        composeRule.waitForIdle()
        assertEquals(anchor, jumpedDate)

        // Tap selected day card to open DayDetailBottomSheet and verify quick actions
        composeRule.onNodeWithTag("timeline_day_card_$anchor").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Consegna chiavi").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_add_overtime_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("sheet_add_allowance_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(addedOtFromSheet)
        assertTrue(addedAllFromSheet)
    }

    @Test
    fun overtimeEntryDialogSelectsScheduledAndHalfHourStepper() {
        var savedType: OvertimeType? = null
        var savedHalfHours = 0

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    OvertimeEntryDialog(
                        date = LocalDate.of(2026, 11, 23),
                        onDismiss = {},
                        onSave = { type, half, _ ->
                            savedType = type
                            savedHalfHours = half
                        }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overtime_type_SCHEDULED").performScrollTo().performClick()
        composeRule.onNodeWithTag("overtime_plus_half").performScrollTo().performClick() // 1.0h -> 1.5h
        composeRule.onNodeWithTag("overtime_plus_half").performScrollTo().performClick() // 1.5h -> 2.0h (4 half-hours)
        composeRule.onNodeWithTag("save_overtime_button").performClick()
        composeRule.waitForIdle()

        assertEquals(OvertimeType.SCHEDULED, savedType)
        assertEquals(4, savedHalfHours)
    }

    @Test
    fun allowanceEntryDialogValidatesCustomLabelAndSubmits() {
        var savedPreset: String? = null
        var savedLabel: String? = null

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    AllowanceEntryDialog(
                        date = LocalDate.of(2026, 11, 23),
                        onDismiss = {},
                        onSave = { preset, label, _, _ ->
                            savedPreset = preset
                            savedLabel = label
                        }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("allowance_preset_CUSTOM").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_allowance_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("allowance_label_input").performScrollTo().performTextInput("Indennità Trasferta")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_allowance_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertEquals(AllowancePreset.CUSTOM.key, savedPreset)
        assertEquals("Indennità Trasferta", savedLabel)
    }

    @Test
    fun recurringScopeDialogTriggersBothChoiceCallbacks() {
        var onlyThisClicked = false
        var entireSeriesClicked = false

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    RecurringScopeDialog(
                        onDismiss = {},
                        onOnlyThis = { onlyThisClicked = true },
                        onEntireSeries = { entireSeriesClicked = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("scope_only_this").performClick()
        composeRule.onNodeWithTag("scope_entire_series").performClick()
        composeRule.waitForIdle()
        assertTrue(onlyThisClicked)
        assertTrue(entireSeriesClicked)
    }

    @Test
    fun shiftEditorSheetEditEntireSeriesPreservesCreatedAtAndExtras() {
        val originalCreatedAt = 1_700_123_456_000L
        val date = LocalDate.of(2026, 11, 23)
        val existingSeries = ShiftSeriesEntity(
            id = 99L,
            title = "Mattina Originale",
            startDateEpochDay = date.toEpochDay(),
            startMinuteOfDay = 6 * 60,
            endMinuteOfDay = 14 * 60,
            colorId = 2,
            recurrenceType = RecurrenceType.DAILY.key,
            maxOccurrences = 5,
            createdAtMillis = originalCreatedAt
        )
        val instance = RecurrenceEngine.expandRange(listOf(existingSeries), emptyList(), date, date).single()
        var savedSeries: ShiftSeriesEntity? = null

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    ShiftEditorSheet(
                        initialDate = date,
                        defaultReminderMinutes = 30,
                        editTarget = instance,
                        editMode = EditMode.EDIT_ENTIRE_SERIES,
                        initialOvertime = listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, 2)),
                        initialAllowances = listOf(AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", 1)),
                        initialApplyExtrasToSeries = true,
                        onDismiss = {},
                        onSaveSeries = { s, _, _, _ -> savedSeries = s },
                        onSaveSingleOccurrence = { _, _, _, _, _, _, _, _, _ -> }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shift_title_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("shift_title_input").performScrollTo().performTextInput("Mattina Aggiornata")
        composeRule.onNodeWithTag("save_shift_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedSeries)
        assertEquals(99L, savedSeries?.id)
        assertEquals("Mattina Aggiornata", savedSeries?.title)
        assertEquals(originalCreatedAt, savedSeries?.createdAtMillis)
    }

    @Test
    fun shiftEditorSheetEditSingleOccurrencePassesUpdatedTitleAndExtras() {
        val date = LocalDate.of(2026, 11, 23)
        val existingSeries = ShiftSeriesEntity(
            id = 100L,
            title = "Mattina Originale",
            startDateEpochDay = date.toEpochDay(),
            startMinuteOfDay = 6 * 60,
            endMinuteOfDay = 14 * 60,
            colorId = 2,
            recurrenceType = RecurrenceType.DAILY.key,
            maxOccurrences = 5
        )
        val instance = RecurrenceEngine.expandRange(listOf(existingSeries), emptyList(), date, date).single()
        var savedSingleTitle: String? = null
        var savedSingleOtCount = 0

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    ShiftEditorSheet(
                        initialDate = date,
                        defaultReminderMinutes = 30,
                        editTarget = instance,
                        editMode = EditMode.EDIT_SINGLE_OCCURRENCE,
                        initialOvertime = listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, 2)),
                        initialAllowances = emptyList(),
                        initialApplyExtrasToSeries = false,
                        onDismiss = {},
                        onSaveSeries = { _, _, _, _ -> },
                        onSaveSingleOccurrence = { _, title, _, _, _, _, _, ot, _ ->
                            savedSingleTitle = title
                            savedSingleOtCount = ot.size
                        }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shift_title_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("shift_title_input").performScrollTo().performTextInput("Solo Oggi")
        composeRule.onNodeWithTag("save_shift_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("Solo Oggi", savedSingleTitle)
        assertEquals(1, savedSingleOtCount)
    }

    @Test
    fun appLockScreenEnforcesMinLengthWrongPinErrorAndUnlocks() {
        var unlocked = false
        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    AppLockScreen(
                        onVerifyPin = { pin, cb ->
                            val ok = pin == "2580"
                            if (ok) unlocked = true
                            cb(ok)
                        },
                        onBiometricSuccess = { unlocked = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_lock_pin_input").performTextInput("123")
        composeRule.onNodeWithTag("app_lock_unlock_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("app_lock_pin_input").performTextInput("9")
        composeRule.onNodeWithTag("app_lock_unlock_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(false, unlocked)

        composeRule.onNodeWithTag("app_lock_pin_input").performTextInput("2580")
        composeRule.onNodeWithTag("app_lock_unlock_button").performClick()
        composeRule.waitForIdle()
        assertTrue(unlocked)
    }

    @Test
    fun multiStepResetDialogEnforcesAllThreeConfirmationSteps() {
        var resetExecuted = false
        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    MultiStepResetDialog(
                        hasBackupPassword = true,
                        onDismiss = {},
                        onExecuteReset = { pwd, cb ->
                            val ok = pwd == "Sicura2026!"
                            if (ok) resetExecuted = true
                            cb(ok)
                        }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Step 1 -> proceed
        composeRule.onNodeWithTag("reset_confirm_step_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        // Step 2 -> disabled until checkbox + "RESET"
        composeRule.onNodeWithTag("reset_confirm_step_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("reset_ack_checkbox").performClick()
        composeRule.onNodeWithTag("reset_confirm_step_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("reset_type_input").performTextInput("RESET")
        composeRule.onNodeWithTag("reset_confirm_step_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        // Step 3 -> wrong password fails, valid password succeeds
        composeRule.onNodeWithTag("reset_password_input").performTextInput("Errata")
        composeRule.onNodeWithTag("reset_confirm_step_button").performClick()
        composeRule.waitForIdle()
        assertEquals(false, resetExecuted)

        composeRule.onNodeWithTag("reset_password_input").performTextClearance()
        composeRule.onNodeWithTag("reset_password_input").performTextInput("Sicura2026!")
        composeRule.onNodeWithTag("reset_confirm_step_button").performClick()
        composeRule.waitForIdle()
        assertTrue(resetExecuted)
    }

    @Test
    fun setBackupPasswordDialogValidatesLengthAndMatching() {
        var savedBackupPwd: String? = null
        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    SetBackupPasswordDialog(
                        onDismiss = {},
                        onConfirm = { savedBackupPwd = it }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("set_backup_password_input").performTextInput("short")
        composeRule.onNodeWithTag("confirm_backup_password_input").performTextInput("short")
        composeRule.onNodeWithTag("save_backup_password_button").performClick()
        composeRule.waitForIdle()
        assertEquals(null, savedBackupPwd)

        composeRule.onNodeWithTag("set_backup_password_input").performTextClearance()
        composeRule.onNodeWithTag("confirm_backup_password_input").performTextClearance()
        composeRule.onNodeWithTag("set_backup_password_input").performTextInput("ChiaveLunga2026!")
        composeRule.onNodeWithTag("confirm_backup_password_input").performTextInput("ChiaveLunga2026!")
        composeRule.onNodeWithTag("save_backup_password_button").performClick()
        composeRule.waitForIdle()
        assertEquals("ChiaveLunga2026!", savedBackupPwd)
    }

    @Test
    fun setupPinDialogRejectsEmptyPin() {
        var savedPin: String? = null
        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    SetupPinDialog(
                        onDismiss = {},
                        onConfirm = { savedPin = it }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_app_pin_button").performClick()
        composeRule.waitForIdle()
        assertEquals(null, savedPin)
    }
}
