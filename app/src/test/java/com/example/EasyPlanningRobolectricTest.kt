package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.EasyPlanningDatabase
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AllowanceDraft
import com.example.data.model.AllowancePreset
import com.example.data.model.AppLanguage
import com.example.data.model.AuthProvider
import com.example.data.model.DaySummary
import com.example.data.model.OvertimeDraft
import com.example.data.model.OvertimeType
import com.example.data.model.RecurrenceType
import com.example.data.model.ScheduleViewMode
import com.example.data.model.ShiftExtrasCodec
import com.example.data.model.StatsPeriod
import com.example.data.model.UserPreferences
import com.example.data.repository.EasyPlanningRepository
import com.example.domain.RecurrenceEngine
import com.example.domain.StatsCalculator
import com.example.i18n.LocalAppStrings
import com.example.i18n.stringsFor
import com.example.security.CryptoManager
import com.example.sync.BackupRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EditMode
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SettingsSecurityScreen
import com.example.ui.screens.ShiftEditorSheet
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.EasyPlanningTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class EasyPlanningRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roomMigration1To2PreservesV1DataAndSupportsV2Columns() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create a genuine Room v1 SQLite schema and seed existing rows
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `shift_series` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `startDateEpochDay` INTEGER NOT NULL,
                    `startMinuteOfDay` INTEGER NOT NULL,
                    `endMinuteOfDay` INTEGER NOT NULL,
                    `colorId` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `reminderMinutesBefore` INTEGER NOT NULL,
                    `recurrenceType` TEXT NOT NULL,
                    `recurrenceInterval` INTEGER NOT NULL,
                    `daysOfWeekMask` INTEGER NOT NULL,
                    `cycleWorkDays` INTEGER NOT NULL,
                    `cycleOffDays` INTEGER NOT NULL,
                    `endDateEpochDay` INTEGER,
                    `maxOccurrences` INTEGER,
                    `createdAtMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `shift_overrides` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `seriesId` INTEGER NOT NULL,
                    `occurrenceEpochDay` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    `title` TEXT,
                    `startMinuteOfDay` INTEGER,
                    `endMinuteOfDay` INTEGER,
                    `colorId` INTEGER,
                    `notes` TEXT,
                    `reminderMinutesBefore` INTEGER
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shift_overrides_seriesId_occurrenceEpochDay` ON `shift_overrides` (`seriesId`, `occurrenceEpochDay`)")
            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `overtime_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `shiftSeriesId` INTEGER,
                    `overtimeType` TEXT NOT NULL,
                    `halfHours` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_overtime_entries_dateEpochDay` ON `overtime_entries` (`dateEpochDay`)")
            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `allowance_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `shiftSeriesId` INTEGER,
                    `presetKey` TEXT NOT NULL,
                    `label` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_allowance_entries_dateEpochDay` ON `allowance_entries` (`dateEpochDay`)")

            val day = LocalDate.of(2026, 6, 1).toEpochDay()
            sqlite.execSQL(
                "INSERT INTO shift_series (id, title, startDateEpochDay, startMinuteOfDay, endMinuteOfDay, colorId, notes, reminderMinutesBefore, recurrenceType, recurrenceInterval, daysOfWeekMask, cycleWorkDays, cycleOffDays, endDateEpochDay, maxOccurrences, createdAtMillis) VALUES (1, 'Mattina Legacy', $day, 360, 840, 0, 'v1 note', 30, 'DAILY', 1, 0, 4, 2, NULL, 5, 1700000000000)"
            )
            sqlite.execSQL(
                "INSERT INTO shift_overrides (id, seriesId, occurrenceEpochDay, isDeleted, title) VALUES (1, 1, ${day + 1}, 0, 'Mattina Modificata')"
            )
            sqlite.version = 1
        } finally {
            sqlite.close()
        }

        // Open with Room v2 + MIGRATION_1_2: validates schema strictly against Entities.kt
        val migratedDb = Room.databaseBuilder(context, EasyPlanningDatabase::class.java, dbName)
            .addMigrations(EasyPlanningDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = migratedDb.dao()
            val allSeries = dao.getAllSeriesOnce()
            assertEquals(1, allSeries.size)
            assertEquals("Mattina Legacy", allSeries[0].title)
            assertEquals("", allSeries[0].defaultOvertimeJson)
            assertEquals("", allSeries[0].defaultAllowancesJson)

            val allOverrides = dao.getAllOverridesOnce()
            assertEquals(1, allOverrides.size)
            assertEquals("Mattina Modificata", allOverrides[0].title)
            assertNull(allOverrides[0].customOvertimeJson)
            assertNull(allOverrides[0].customAllowancesJson)

            // Verify writing new v2 columns works seamlessly
            val v2Id = dao.insertSeries(
                ShiftSeriesEntity(
                    title = "Pomeriggio v2",
                    startDateEpochDay = LocalDate.of(2026, 6, 10).toEpochDay(),
                    startMinuteOfDay = 14 * 60,
                    endMinuteOfDay = 22 * 60,
                    recurrenceType = RecurrenceType.DAILY.key,
                    maxOccurrences = 3,
                    defaultOvertimeJson = ShiftExtrasCodec.encodeOvertime(
                        listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2, notes = "Prog"))
                    ),
                    defaultAllowancesJson = ShiftExtrasCodec.encodeAllowances(
                        listOf(AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", 1))
                    )
                )
            )
            val inserted = dao.getSeriesById(v2Id)
            assertNotNull(inserted)
            assertEquals(1, ShiftExtrasCodec.decodeOvertime(inserted!!.defaultOvertimeJson).size)
            assertEquals(1, ShiftExtrasCodec.decodeAllowances(inserted.defaultAllowancesJson).size)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun repositoryRecurringExtrasMigrationSingleEditAndInheritedChipRemoval() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EasyPlanningRepository(context)
        repo.secureWipeAllData()

        val start = LocalDate.of(2026, 7, 1)
        val progDraft = OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2, notes = "Pianificato")
        val mealDraft = AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", quantity = 1)

        // 1. Create recurring shift with applyExtrasToSeries = false (only start date gets direct extras)
        repo.saveShiftSeriesWithExtras(
            series = ShiftSeriesEntity(
                title = "Mattina",
                startDateEpochDay = start.toEpochDay(),
                startMinuteOfDay = 6 * 60,
                endMinuteOfDay = 14 * 60,
                recurrenceType = RecurrenceType.DAILY.key,
                maxOccurrences = 4
            ),
            applyExtrasToSeries = false,
            overtimeDrafts = listOf(progDraft),
            allowanceDrafts = listOf(mealDraft)
        )

        val db = EasyPlanningDatabase.getInstance(context)
        val dao = db.dao()
        val series1 = dao.getAllSeriesOnce().single()

        var shifts = RecurrenceEngine.expandRange(listOf(series1), dao.getAllOverridesOnce(), start, start.plusDays(10))
        var effOt = RecurrenceEngine.effectiveOvertime(shifts, dao.getAllOvertimeOnce(), start, start.plusDays(10))
        var effAll = RecurrenceEngine.effectiveAllowances(shifts, dao.getAllAllowancesOnce(), start, start.plusDays(10))
        assertEquals(4, shifts.size)
        assertEquals(1, effOt.size)
        assertEquals(1, effAll.size)

        // 2. Edit same series and flip applyExtrasToSeries = true -> MUST migrate without duplicating Day 1!
        repo.saveShiftSeriesWithExtras(
            series = series1,
            applyExtrasToSeries = true,
            overtimeDrafts = listOf(progDraft),
            allowanceDrafts = listOf(mealDraft)
        )
        val updatedSeries = dao.getAllSeriesOnce().single()
        shifts = RecurrenceEngine.expandRange(listOf(updatedSeries), dao.getAllOverridesOnce(), start, start.plusDays(10))
        effOt = RecurrenceEngine.effectiveOvertime(shifts, dao.getAllOvertimeOnce(), start, start.plusDays(10))
        effAll = RecurrenceEngine.effectiveAllowances(shifts, dao.getAllAllowancesOnce(), start, start.plusDays(10))
        assertEquals(4, effOt.size) // Exactly 1 per day, no duplicate on Day 1
        assertEquals(4, effAll.size)

        // 3. Remove inherited Buono Pasto chip ONLY on Day 2
        val day2Allowance = effAll.first { it.dateEpochDay == start.plusDays(1).toEpochDay() }
        assertTrue(day2Allowance.id < 0L)
        repo.removeAllowanceEntry(day2Allowance)

        shifts = RecurrenceEngine.expandRange(listOf(updatedSeries), dao.getAllOverridesOnce(), start, start.plusDays(10))
        effAll = RecurrenceEngine.effectiveAllowances(shifts, dao.getAllAllowancesOnce(), start, start.plusDays(10))
        assertEquals(3, effAll.size)
        assertTrue(effAll.none { it.dateEpochDay == start.plusDays(1).toEpochDay() })

        // 4. Edit Day 3 as single occurrence adding extra WEEKDAY overtime -> no duplication of inherited PROG
        val day3Shift = shifts.first { it.date == start.plusDays(2) }
        repo.saveSingleOccurrenceWithExtras(
            original = day3Shift,
            title = "Mattina Extra",
            startMinuteOfDay = 6 * 60,
            endMinuteOfDay = 14 * 60,
            colorId = 1,
            notes = "Modificato",
            reminderMinutesBefore = 30,
            overtimeDrafts = listOf(progDraft, OvertimeDraft(OvertimeType.WEEKDAY.key, halfHours = 1)),
            allowanceDrafts = listOf(mealDraft)
        )

        shifts = RecurrenceEngine.expandRange(listOf(updatedSeries), dao.getAllOverridesOnce(), start, start.plusDays(10))
        effOt = RecurrenceEngine.effectiveOvertime(shifts, dao.getAllOvertimeOnce(), start, start.plusDays(10))
        val day3Ot = effOt.filter { it.dateEpochDay == start.plusDays(2).toEpochDay() }
        assertEquals(2, day3Ot.size) // 1 PROG + 1 WEEKDAY
        assertEquals(3, day3Ot.sumOf { it.halfHours }) // 1.5h total on Day 3
        assertEquals(5, effOt.size) // Day 1 (1) + Day 2 (1) + Day 3 (2) + Day 4 (1)

        // 5. Move a non-recurring shift to a new date -> old date direct extras must be cleaned up
        val singleDate1 = LocalDate.of(2026, 7, 15)
        val singleDate2 = LocalDate.of(2026, 7, 18)
        repo.saveShiftSeriesWithExtras(
            series = ShiftSeriesEntity(
                title = "Singolo",
                startDateEpochDay = singleDate1.toEpochDay(),
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 16 * 60,
                recurrenceType = RecurrenceType.NONE.key
            ),
            applyExtrasToSeries = false,
            overtimeDrafts = listOf(progDraft),
            allowanceDrafts = listOf(mealDraft)
        )
        val singleSeries = dao.getAllSeriesOnce().first { it.title == "Singolo" }
        repo.saveShiftSeriesWithExtras(
            series = singleSeries.copy(startDateEpochDay = singleDate2.toEpochDay()),
            applyExtrasToSeries = false,
            overtimeDrafts = listOf(progDraft),
            allowanceDrafts = listOf(mealDraft)
        )
        val directOt = dao.getAllOvertimeOnce().filter { it.shiftSeriesId == singleSeries.id }
        assertEquals(1, directOt.size)
        assertEquals(singleDate2.toEpochDay(), directOt.single().dateEpochDay)

        repo.secureWipeAllData()
    }

    @Test
    fun encryptedBackupRoundTripAndLegacyV1PayloadBackwardCompatibility() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EasyPlanningDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.dao()
            val backupRepo = BackupRepository(dao)

            // Seed v2 data with SCHEDULED overtime & recurring allowances
            dao.insertSeries(
                ShiftSeriesEntity(
                    id = 10L,
                    title = "Mattina",
                    startDateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
                    startMinuteOfDay = 6 * 60,
                    endMinuteOfDay = 14 * 60,
                    recurrenceType = RecurrenceType.DAILY.key,
                    maxOccurrences = 3,
                    defaultOvertimeJson = ShiftExtrasCodec.encodeOvertime(
                        listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2))
                    ),
                    defaultAllowancesJson = ShiftExtrasCodec.encodeAllowances(
                        listOf(AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", 1))
                    )
                )
            )

            val encryptedV2 = backupRepo.createEncryptedBackupBytes("Sicura2026!".toCharArray())
            dao.wipeAllTables()
            assertTrue(dao.getAllSeriesOnce().isEmpty())

            backupRepo.restoreFromEncryptedBytes(encryptedV2, "Sicura2026!".toCharArray())
            val restoredV2 = dao.getAllSeriesOnce().single()
            assertEquals("Mattina", restoredV2.title)
            assertEquals(1, ShiftExtrasCodec.decodeOvertime(restoredV2.defaultOvertimeJson).size)
            assertEquals(1, ShiftExtrasCodec.decodeAllowances(restoredV2.defaultAllowancesJson).size)

            // Now verify restoring a legacy v1 JSON backup that omits v2 fields
            val legacyV1Json = """
                {
                  "schemaVersion": 1,
                  "exportedAtMillis": 1700000000000,
                  "appName": "EasyPlanning",
                  "shiftSeries": [
                    {
                      "id": 5,
                      "title": "Turno Legacy",
                      "startDateEpochDay": 20600,
                      "startMinuteOfDay": 480,
                      "endMinuteOfDay": 960,
                      "colorId": 1,
                      "notes": "vecchio backup",
                      "reminderMinutesBefore": 30,
                      "recurrenceType": "NONE",
                      "recurrenceInterval": 1,
                      "daysOfWeekMask": 0,
                      "cycleWorkDays": 4,
                      "cycleOffDays": 2,
                      "createdAtMillis": 1700000000000
                    }
                  ],
                  "shiftOverrides": [],
                  "overtimeEntries": [
                    {
                      "id": 1,
                      "dateEpochDay": 20600,
                      "overtimeType": "WEEKDAY",
                      "halfHours": 2,
                      "notes": "",
                      "createdAtMillis": 1700000000000
                    }
                  ],
                  "allowanceEntries": []
                }
            """.trimIndent()

            val encryptedLegacy = CryptoManager.encryptBackup(
                legacyV1Json.toByteArray(Charsets.UTF_8),
                "LegacyPass2026!".toCharArray()
            )
            backupRepo.restoreFromEncryptedBytes(encryptedLegacy, "LegacyPass2026!".toCharArray())
            val legacySeries = dao.getAllSeriesOnce().single()
            assertEquals("Turno Legacy", legacySeries.title)
            assertEquals("", legacySeries.defaultOvertimeJson)
            assertEquals("", legacySeries.defaultAllowancesJson)
            assertEquals(1, dao.getAllOvertimeOnce().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun monthlyPdfDataPipelineAggregatesAll5OvertimeTypesInItAndEn() {
        val month = YearMonth.of(2026, 9)
        val day1 = month.atDay(1)
        val endDate = month.atEndOfMonth()
        val series = listOf(
            ShiftSeriesEntity(
                id = 1L,
                title = "Mattina",
                startDateEpochDay = day1.toEpochDay(),
                startMinuteOfDay = 6 * 60,
                endMinuteOfDay = 14 * 60,
                recurrenceType = RecurrenceType.DAILY.key,
                maxOccurrences = 10,
                defaultOvertimeJson = ShiftExtrasCodec.encodeOvertime(
                    listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 3, notes = "Prog"))
                ),
                defaultAllowancesJson = ShiftExtrasCodec.encodeAllowances(
                    listOf(AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", 1))
                )
            )
        )
        val directOvertime = listOf(
            OvertimeEntryEntity(id = 1L, dateEpochDay = month.atDay(2).toEpochDay(), overtimeType = OvertimeType.WEEKDAY.key, halfHours = 2),
            OvertimeEntryEntity(id = 2L, dateEpochDay = month.atDay(3).toEpochDay(), overtimeType = OvertimeType.HOLIDAY.key, halfHours = 3),
            OvertimeEntryEntity(id = 3L, dateEpochDay = month.atDay(4).toEpochDay(), overtimeType = OvertimeType.NIGHT.key, halfHours = 2),
            OvertimeEntryEntity(id = 4L, dateEpochDay = month.atDay(5).toEpochDay(), overtimeType = OvertimeType.NIGHT_HOLIDAY.key, halfHours = 4)
        )

        val stats = StatsCalculator.calculate(
            seriesList = series,
            overrides = emptyList(),
            overtimeEntries = directOvertime,
            allowanceEntries = emptyList(),
            anchorDate = day1,
            period = StatsPeriod.MONTH
        )
        val expanded = RecurrenceEngine.expandRange(series, emptyList(), day1, endDate)
        val effOt = RecurrenceEngine.effectiveOvertime(expanded, directOvertime, day1, endDate)
        val effAll = RecurrenceEngine.effectiveAllowances(expanded, emptyList(), day1, endDate)

        assertEquals(10, stats.workedDays)
        assertEquals(30, stats.overtimeByTypeHalfHours[OvertimeType.SCHEDULED]) // 10 * 1.5h
        assertEquals(41, stats.totalOvertimeHalfHours) // 30 + 2 + 3 + 2 + 4
        assertEquals(14, effOt.size)
        assertEquals(10, effAll.size)

        // Verify IT & EN formatting lines used by PdfExportService
        val itStrings = stringsFor(AppLanguage.ITALIAN)
        val enStrings = stringsFor(AppLanguage.ENGLISH)
        assertEquals("1,5h", itStrings.formatHalfHours(3))
        assertEquals("1.5h", enStrings.formatHalfHours(3))
        assertEquals("15h", itStrings.formatHalfHours(30))
        assertEquals(2, OvertimeType.entries.chunked(3).size)
    }

    @Test
    fun authScreenValidatesAndSubmitsOfflineRegistration() {
        var registeredUser: String? = null
        var registeredPwd: String? = null

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    AuthScreen(
                        prefs = UserPreferences(isPreferencesLoaded = true, hasRegisteredAccount = false, isLoggedIn = false),
                        isBusy = false,
                        onSetLanguage = {},
                        onRegisterOffline = { u, p ->
                            registeredUser = u
                            registeredPwd = p
                        },
                        onLoginOffline = { _, _, cb -> cb(false) },
                        onContinueWithGoogle = {}
                    )
                }
            }
        }

        // Submit empty -> should show validation error
        composeRule.onNodeWithTag("auth_submit_offline_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_error_text").assertIsDisplayed()
        assertNull(registeredUser)

        // Enter valid offline credentials
        composeRule.onNodeWithTag("auth_username_input").performScrollTo().performTextInput("marco.rossi")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("Sicura2026!")
        composeRule.onNodeWithTag("auth_confirm_password_input").performScrollTo().performTextInput("Sicura2026!")
        composeRule.onNodeWithTag("auth_submit_offline_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("marco.rossi", registeredUser)
        assertEquals("Sicura2026!", registeredPwd)
    }

    @Test
    fun shiftEditorInlineScheduledOvertimeAndMealVoucherFlowWorks() {
        var savedSeries: ShiftSeriesEntity? = null
        var savedApplyFlag: Boolean? = null
        var savedOt: List<OvertimeDraft> = emptyList()
        var savedAll: List<AllowanceDraft> = emptyList()

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    ShiftEditorSheet(
                        initialDate = LocalDate.of(2026, 11, 23),
                        defaultReminderMinutes = 30,
                        editTarget = null,
                        editMode = EditMode.NEW_SHIFT,
                        onDismiss = {},
                        onSaveSeries = { series, applyFlag, ot, al ->
                            savedSeries = series
                            savedApplyFlag = applyFlag
                            savedOt = ot
                            savedAll = al
                        },
                        onSaveSingleOccurrence = { _, _, _, _, _, _, _, _, _ -> }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shift_title_input").performScrollTo().performTextInput("Mattina")
        composeRule.onNodeWithTag("recurrence_DAILY").performScrollTo().performClick()

        // Add inline Programmato (SCHEDULED) overtime
        composeRule.onNodeWithTag("inline_toggle_overtime_composer").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("inline_ot_type_SCHEDULED").performScrollTo().performClick()
        composeRule.onNodeWithTag("inline_ot_plus").performScrollTo().performClick() // 1.0h -> 1.5h (3 half-hours)
        composeRule.onNodeWithTag("inline_confirm_add_overtime").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Toggle Buono Pasto preset
        composeRule.onNodeWithTag("inline_preset_MEAL_VOUCHER").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Recurring propagation toggle should be visible and ON by default
        composeRule.onNodeWithTag("apply_extras_to_series_switch").performScrollTo().assertIsOn()

        // Save shift
        composeRule.onNodeWithTag("save_shift_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedSeries)
        assertEquals("Mattina", savedSeries?.title)
        assertEquals(RecurrenceType.DAILY.key, savedSeries?.recurrenceType)
        assertEquals(true, savedApplyFlag)
        assertEquals(1, savedOt.size)
        assertEquals(OvertimeType.SCHEDULED.key, savedOt.single().overtimeType)
        assertEquals(3, savedOt.single().halfHours)
        assertEquals(1, savedAll.size)
        assertEquals(AllowancePreset.MEAL_VOUCHER.key, savedAll.single().presetKey)
        assertEquals("Buono Pasto", savedAll.single().label)
    }

    @Test
    fun scheduleTimelineAndStatsScreensRenderScheduledOvertimeAndAllowances() {
        val today = LocalDate.of(2026, 11, 23)
        val series = ShiftSeriesEntity(
            id = 1L,
            title = "Mattina",
            startDateEpochDay = today.toEpochDay(),
            startMinuteOfDay = 6 * 60,
            endMinuteOfDay = 14 * 60,
            colorId = 0,
            recurrenceType = RecurrenceType.DAILY.key,
            maxOccurrences = 3,
            defaultOvertimeJson = ShiftExtrasCodec.encodeOvertime(
                listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2))
            ),
            defaultAllowancesJson = ShiftExtrasCodec.encodeAllowances(
                listOf(AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", 1))
            )
        )
        val expanded = RecurrenceEngine.expandRange(listOf(series), emptyList(), today, today.plusDays(5))
        val effOt = RecurrenceEngine.effectiveOvertime(expanded, emptyList(), today, today.plusDays(5))
        val effAll = RecurrenceEngine.effectiveAllowances(expanded, emptyList(), today, today.plusDays(5))
        val daySummary = DaySummary(
            date = today,
            shifts = expanded.filter { it.date == today },
            overtimeEntries = effOt.filter { it.dateEpochDay == today.toEpochDay() },
            allowanceEntries = effAll.filter { it.dateEpochDay == today.toEpochDay() }
        )
        val stats = StatsCalculator.calculate(
            seriesList = listOf(series),
            overrides = emptyList(),
            overtimeEntries = emptyList(),
            allowanceEntries = emptyList(),
            anchorDate = today,
            period = StatsPeriod.MONTH
        )

        var currentTab = ScheduleViewMode.DAILY_LIST
        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    ScheduleScreen(
                        selectedDate = today,
                        visibleMonth = YearMonth.from(today),
                        viewMode = currentTab,
                        monthSummaries = mapOf(today to daySummary),
                        activeTimelineDays = listOf(daySummary),
                        selectedDaySummary = daySummary,
                        scrollSignal = today to 0L,
                        isDriveConnected = false,
                        showNotificationBanner = false,
                        onRequestNotificationPermission = {},
                        onSelectDate = {},
                        onJumpToDate = {},
                        onJumpToToday = {},
                        onShiftMonth = {},
                        onViewModeChange = { currentTab = it },
                        onQuickSync = {},
                        onAddShift = {},
                        onEditShift = {},
                        onDeleteShift = {},
                        onAddOvertime = { _, _ -> },
                        onDeleteOvertime = {},
                        onAddAllowance = { _, _ -> },
                        onDeleteAllowance = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline_day_card_$today").assertIsDisplayed()
        composeRule.onNodeWithText("Mattina").assertIsDisplayed()
        composeRule.onNodeWithText("PROG 1h").assertIsDisplayed()
        composeRule.onNodeWithText("Buono Pasto").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_monthly_calendar").performClick()
        composeRule.waitForIdle()
        assertEquals(ScheduleViewMode.MONTHLY_CALENDAR, currentTab)

        // Verify StatsSummary reflects 3 days, 3.0h PROG, 3 Meal Vouchers
        assertEquals(3, stats.workedDays)
        assertEquals(6, stats.overtimeByTypeHalfHours[OvertimeType.SCHEDULED])
        assertEquals(3, stats.totalAllowancesCount)
    }

    @Test
    fun statsScreenDisplaysAll5OvertimeCategoriesIncludingProgrammato() {
        val today = LocalDate.of(2026, 11, 23)
        val series = ShiftSeriesEntity(
            id = 2L,
            title = "Mattina",
            startDateEpochDay = today.toEpochDay(),
            startMinuteOfDay = 6 * 60,
            endMinuteOfDay = 14 * 60,
            recurrenceType = RecurrenceType.NONE.key
        )
        val stats = StatsCalculator.calculate(
            seriesList = listOf(series),
            overrides = emptyList(),
            overtimeEntries = listOf(
                OvertimeEntryEntity(dateEpochDay = today.toEpochDay(), overtimeType = OvertimeType.SCHEDULED.key, halfHours = 4),
                OvertimeEntryEntity(dateEpochDay = today.toEpochDay(), overtimeType = OvertimeType.WEEKDAY.key, halfHours = 2)
            ),
            allowanceEntries = listOf(
                AllowanceEntryEntity(dateEpochDay = today.toEpochDay(), presetKey = "MEAL_VOUCHER", label = "Buono Pasto", quantity = 1)
            ),
            anchorDate = today,
            period = StatsPeriod.MONTH
        )

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    StatsScreen(
                        stats = stats,
                        selectedPeriod = StatsPeriod.MONTH,
                        onSelectPeriod = {},
                        onShiftPeriod = {},
                        onExportMonthlyPdf = {},
                        onShareMonthlyPdf = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stats_lazy_column").performScrollToNode(hasText("Programmato (PROG)"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("PROG").assertIsDisplayed()
        composeRule.onNodeWithText("Programmato (PROG)").assertIsDisplayed()
        composeRule.onNodeWithText("FER").assertIsDisplayed()
        composeRule.onNodeWithText("FEST").assertIsDisplayed()
        composeRule.onNodeWithText("NOTT").assertIsDisplayed()
        composeRule.onNodeWithText("N-FEST").assertIsDisplayed()
    }

    @Test
    fun settingsSecurityScreenAllowsLanguageSwitchAndLogout() {
        var selectedLanguage: AppLanguage? = null
        var loggedOut = false

        composeRule.setContent {
            val strings = stringsFor(AppLanguage.ITALIAN)
            CompositionLocalProvider(LocalAppStrings provides strings) {
                EasyPlanningTheme {
                    SettingsSecurityScreen(
                        prefs = UserPreferences(
                            isPreferencesLoaded = true,
                            hasRegisteredAccount = true,
                            isLoggedIn = true,
                            authProvider = AuthProvider.OFFLINE_LOCAL,
                            username = "marco.rossi",
                            hasBackupPassword = true
                        ),
                        onLinkGoogleAccount = {},
                        onLogout = { loggedOut = true },
                        onSetLanguage = { selectedLanguage = it },
                        onSetThemeMode = {},
                        onSetDynamicColor = {},
                        onSetDefaultReminder = {},
                        onSaveBackupPassword = {},
                        onSetupAppLockPin = {},
                        onSetAppLockEnabled = {},
                        onSetScreenSecurity = {},
                        onRequestLocalBackupExport = {},
                        onRequestLocalBackupImport = {},
                        onConnectDrive = {},
                        onDisconnectDrive = {},
                        onSyncDriveUpload = {},
                        onRestoreDriveDownload = {},
                        onSetDriveAutoSync = {},
                        onExecuteSecureReset = { _, cb -> cb(true) }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("marco.rossi").assertIsDisplayed()
        composeRule.onNodeWithTag("logout_button").performClick()
        composeRule.waitForIdle()
        assertTrue(loggedOut)

        composeRule.onNodeWithTag("settings_lazy_column").performScrollToNode(hasTestTag("lang_en_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lang_en_button").performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.ENGLISH, selectedLanguage)
    }

    @Test
    fun backupRepositoryRejectsMalformedOutOfBoundsPayloadWithoutWipingDb() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EasyPlanningDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.dao()
            val backupRepo = BackupRepository(dao)
            dao.insertSeries(
                ShiftSeriesEntity(
                    id = 1L,
                    title = "Esistente",
                    startDateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
                    startMinuteOfDay = 8 * 60,
                    endMinuteOfDay = 16 * 60
                )
            )

            val malformedJson = """
                {
                  "schemaVersion": 2,
                  "exportedAtMillis": 1700000000000,
                  "appName": "EasyPlanning",
                  "shiftSeries": [
                    {
                      "id": 99,
                      "title": "Corrotto",
                      "startDateEpochDay": 20600,
                      "startMinuteOfDay": 2000,
                      "endMinuteOfDay": 960,
                      "colorId": 1,
                      "notes": "",
                      "reminderMinutesBefore": 30,
                      "recurrenceType": "NONE",
                      "recurrenceInterval": 1,
                      "daysOfWeekMask": 0,
                      "cycleWorkDays": 4,
                      "cycleOffDays": 2,
                      "createdAtMillis": 1700000000000
                    }
                  ],
                  "shiftOverrides": [],
                  "overtimeEntries": [],
                  "allowanceEntries": []
                }
            """.trimIndent()
            val encrypted = CryptoManager.encryptBackup(
                malformedJson.toByteArray(Charsets.UTF_8),
                "Sicura2026!".toCharArray()
            )

            var threw = false
            try {
                backupRepo.restoreFromEncryptedBytes(encrypted, "Sicura2026!".toCharArray())
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw)
            // Existing data must remain untouched when validation rejects the payload
            assertEquals("Esistente", dao.getAllSeriesOnce().single().title)
        } finally {
            db.close()
        }
    }

    @Test
    fun repositoryClearingExtrasWhileMigratingToSeriesRemovesOldDirectEntries() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EasyPlanningRepository(context)
        repo.secureWipeAllData()

        val start = LocalDate.of(2026, 10, 1)
        val progDraft = OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2, notes = "Prog")
        val mealDraft = AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", quantity = 1)

        repo.saveShiftSeriesWithExtras(
            series = ShiftSeriesEntity(
                title = "Mattina",
                startDateEpochDay = start.toEpochDay(),
                startMinuteOfDay = 6 * 60,
                endMinuteOfDay = 14 * 60,
                recurrenceType = RecurrenceType.DAILY.key,
                maxOccurrences = 3
            ),
            applyExtrasToSeries = false,
            overtimeDrafts = listOf(progDraft),
            allowanceDrafts = listOf(mealDraft)
        )

        val dao = EasyPlanningDatabase.getInstance(context).dao()
        val series = dao.getAllSeriesOnce().single()
        assertEquals(1, dao.getAllOvertimeOnce().size)
        assertEquals(1, dao.getAllAllowancesOnce().size)

        // Flip to applyExtrasToSeries = true while clearing both lists -> old direct Day-1 entries must be cleaned up
        repo.saveShiftSeriesWithExtras(
            series = series,
            applyExtrasToSeries = true,
            overtimeDrafts = emptyList(),
            allowanceDrafts = emptyList()
        )
        assertTrue(dao.getAllOvertimeOnce().isEmpty())
        assertTrue(dao.getAllAllowancesOnce().isEmpty())

        repo.secureWipeAllData()
    }
}
