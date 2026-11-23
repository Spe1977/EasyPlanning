package com.example

import android.app.Application
import android.os.Looper
import android.util.Base64
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EasyPlanningDatabase
import com.example.data.local.PreferencesStore
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AllowanceDraft
import com.example.data.model.AllowancePreset
import com.example.data.model.AppLanguage
import com.example.data.model.AuthProvider
import com.example.data.model.OvertimeDraft
import com.example.data.model.OvertimeType
import com.example.data.model.RecurrenceType
import com.example.data.model.ShiftExtrasCodec
import com.example.data.model.StatsPeriod
import com.example.data.model.ThemeMode
import com.example.security.CryptoManager
import com.example.ui.EasyPlanningViewModel
import com.example.ui.UiEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EasyPlanningAuthViewModelTest {

    private lateinit var app: Application
    private var vmStore: ViewModelStore? = null

    private fun createViewModel(): EasyPlanningViewModel {
        val store = ViewModelStore().also { vmStore = it }
        return ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[EasyPlanningViewModel::class.java]
    }

    private fun wipeLocalDatabase() {
        runBlocking {
            EasyPlanningDatabase.getInstance(app).dao().wipeAllTables()
        }
        EasyPlanningDatabase.resetForTesting()
        val dbFile = app.getDatabasePath("easyplanning_local.db")
        app.deleteDatabase("easyplanning_local.db")
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            java.io.File(dbFile.path + suffix).delete()
        }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val testKey = SecretKeySpec(ByteArray(32) { (it + 17).toByte() }, "AES")
        CryptoManager.keyProviderOverride = { testKey }
        CryptoManager.resetKeystoreCacheForTesting()
        wipeLocalDatabase()
    }

    @After
    fun tearDown() {
        vmStore?.clear()
        vmStore = null
        wipeLocalDatabase()
        CryptoManager.keyProviderOverride = null
        CryptoManager.resetKeystoreCacheForTesting()
    }

    private suspend fun awaitMainCondition(timeoutMs: Long = 10_000L, predicate: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(25)
            }
        }
    }

    @Test
    fun cryptoManagerKeystoreRoundTripVerifierAndCorruptedPayloadRejection() {
        val secret = "marco.rossi@easyplanning.it"
        val encrypted = CryptoManager.encryptWithKeystore(secret)
        assertEquals(secret, CryptoManager.decryptWithKeystore(encrypted))

        // Protected verifier round-trip + CharArray zeroization
        val originalSecret = "PinSegreto2026!".toCharArray()
        val verifier = CryptoManager.createProtectedVerifier(originalSecret)
        assertTrue(originalSecret.all { it == '\u0000' })

        val validCandidate = "PinSegreto2026!".toCharArray()
        assertTrue(CryptoManager.verifyProtectedSecret(validCandidate, verifier))
        assertTrue(validCandidate.all { it == '\u0000' })

        val wrongCandidate = "Errata2026!".toCharArray()
        assertFalse(CryptoManager.verifyProtectedSecret(wrongCandidate, verifier))
        assertTrue(wrongCandidate.all { it == '\u0000' })

        // Malformed verifier format returns false cleanly
        val badFormatVerifier = CryptoManager.encryptWithKeystore("invalid:format")
        val checkPwd = "PinSegreto2026!".toCharArray()
        assertFalse(CryptoManager.verifyProtectedSecret(checkPwd, badFormatVerifier))
        assertTrue(checkPwd.all { it == '\u0000' })

        // Corrupted/truncated keystore payloads & out-of-range IV lengths must be rejected
        assertThrows(IllegalArgumentException::class.java) {
            CryptoManager.decryptWithKeystore(Base64.encodeToString(byteArrayOf(12, 1, 2, 3), Base64.NO_WRAP))
        }
        val badIvLen = ByteArray(40).apply { this[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) {
            CryptoManager.decryptWithKeystore(Base64.encodeToString(badIvLen, Base64.NO_WRAP))
        }
        val oversizedIv = ByteArray(30).apply { this[0] = 25 }
        assertThrows(IllegalArgumentException::class.java) {
            CryptoManager.decryptWithKeystore(Base64.encodeToString(oversizedIv, Base64.NO_WRAP))
        }
    }

    @Test
    fun preferencesStoreOfflineAuthHybridUpgradePinBackupPasswordAndResetPreservesLocaleTheme() = runBlocking {
        withTimeout(15_000L) {
            val store = PreferencesStore(app)
            store.clearAllUserPreferencesPreserveLocaleAndTheme()

            // 1. Register offline account
            store.registerOfflineAccount("  Marco.Rossi  ", "Sicura2026!".toCharArray())
            var prefs = store.preferencesFlow.first()
            assertTrue(prefs.hasRegisteredAccount)
            assertTrue(prefs.isLoggedIn)
            assertEquals(AuthProvider.OFFLINE_LOCAL, prefs.authProvider)
            assertEquals("Marco.Rossi", prefs.username)
            assertTrue(prefs.hasBackupPassword)

            // 2. Non-destructive logout keeps profile and username
            store.logout()
            prefs = store.preferencesFlow.first()
            assertFalse(prefs.isLoggedIn)
            assertTrue(prefs.hasRegisteredAccount)
            assertEquals("Marco.Rossi", prefs.username)

            // 3. Offline login verification (wrong password / wrong username / case-insensitive valid)
            assertFalse(store.verifyOfflineLogin("marco.rossi", "Sbagliata!".toCharArray()))
            assertFalse(store.verifyOfflineLogin("altro.utente", "Sicura2026!".toCharArray()))
            assertTrue(store.verifyOfflineLogin("marco.rossi", "Sicura2026!".toCharArray()))
            assertTrue(store.preferencesFlow.first().isLoggedIn)

            // 4. Link Google account over offline profile -> upgrades to HYBRID cleanly
            store.signInOrLinkGoogleAccount("marco@example.com", "Marco Rossi")
            prefs = store.preferencesFlow.first()
            assertEquals(AuthProvider.HYBRID, prefs.authProvider)
            assertEquals("marco@example.com", prefs.googleEmail)
            assertEquals("Marco Rossi", prefs.googleDisplayName)
            assertEquals("Marco.Rossi", prefs.username)

            // 5. App Lock PIN & Backup Password verification
            store.saveAppLockPin("2580".toCharArray())
            assertTrue(store.preferencesFlow.first().appLockEnabled)
            assertTrue(store.preferencesFlow.first().hasAppLockPin)
            assertFalse(store.verifyAppLockPin("9999".toCharArray()))
            assertTrue(store.verifyAppLockPin("2580".toCharArray()))

            store.saveBackupPassword("NuovaChiave2026!".toCharArray())
            assertFalse(store.verifyBackupPassword("Sicura2026!".toCharArray()))
            assertTrue(store.verifyBackupPassword("NuovaChiave2026!".toCharArray()))

            // 6. Theme / Locale / Reminder / Screen Security + reset preserves locale & theme
            store.setLanguage(AppLanguage.ENGLISH)
            store.setThemeMode(ThemeMode.DARK)
            store.setDynamicColor(true)
            store.setDefaultReminder(60)
            store.setScreenSecurity(true)

            prefs = store.preferencesFlow.first()
            assertEquals(AppLanguage.ENGLISH, prefs.language)
            assertEquals(ThemeMode.DARK, prefs.themeMode)
            assertTrue(prefs.dynamicColor)
            assertEquals(60, prefs.defaultReminderMinutes)
            assertTrue(prefs.screenSecurityEnabled)

            store.clearAllUserPreferencesPreserveLocaleAndTheme()
            val afterReset = store.preferencesFlow.first()
            assertFalse(afterReset.hasRegisteredAccount)
            assertFalse(afterReset.isLoggedIn)
            assertFalse(afterReset.hasAppLockPin)
            assertFalse(afterReset.hasBackupPassword)
            assertNull(afterReset.username)
            assertEquals(AppLanguage.ENGLISH, afterReset.language)
            assertEquals(ThemeMode.DARK, afterReset.themeMode)
            assertTrue(afterReset.dynamicColor)
        }
    }

    @Test
    fun viewModelJumpToDateExpandsTimelineBoundsAndUpdatesReactiveFlows() = runBlocking {
        withTimeout(15_000L) {
            val vm = createViewModel()
            val collector = launch(Dispatchers.Default) {
                launch { vm.activeTimelineDays.collect {} }
                launch { vm.monthDaySummaries.collect {} }
                launch { vm.statsSummary.collect {} }
            }
            try {
                val today = LocalDate.now()
                val farFuture = today.plusMonths(60)
                val farPast = today.minusMonths(48)

                vm.saveShiftSeriesWithExtras(
                    series = ShiftSeriesEntity(
                        title = "Turno Futuro Lontano",
                        startDateEpochDay = farFuture.toEpochDay(),
                        startMinuteOfDay = 8 * 60,
                        endMinuteOfDay = 16 * 60,
                        recurrenceType = RecurrenceType.NONE.key
                    ),
                    applyExtrasToSeries = false,
                    overtimeDrafts = emptyList(),
                    allowanceDrafts = emptyList()
                )
                vm.saveShiftSeriesWithExtras(
                    series = ShiftSeriesEntity(
                        title = "Turno Passato Lontano",
                        startDateEpochDay = farPast.toEpochDay(),
                        startMinuteOfDay = 6 * 60,
                        endMinuteOfDay = 14 * 60,
                        recurrenceType = RecurrenceType.NONE.key
                    ),
                    applyExtrasToSeries = false,
                    overtimeDrafts = emptyList(),
                    allowanceDrafts = emptyList()
                )

                vm.jumpToDate(farFuture)
                assertEquals(farFuture, vm.selectedDate.value)
                assertEquals(YearMonth.from(farFuture), vm.visibleMonth.value)
                assertEquals(1L, vm.scrollSignal.value.second)
                awaitMainCondition { vm.activeTimelineDays.value.any { it.date == farFuture } }

                vm.jumpToDate(farPast)
                assertEquals(2L, vm.scrollSignal.value.second)
                awaitMainCondition { vm.activeTimelineDays.value.any { it.date == farPast } }
                assertTrue(vm.activeTimelineDays.value.any { it.date == farFuture })

                vm.jumpToToday()
                assertEquals(today, vm.selectedDate.value)
                val currentMonth = vm.visibleMonth.value
                vm.shiftMonth(1)
                assertEquals(currentMonth.plusMonths(1), vm.visibleMonth.value)
                vm.setStatsPeriod(StatsPeriod.WEEK)
                vm.shiftStatsPeriod(1)
            } finally {
                collector.cancel()
            }
        }
    }

    @Test
    fun viewModelResolveInitialExtrasForEditAndDeleteFlows() = runBlocking {
        withTimeout(15_000L) {
            val vm = createViewModel()
            val collector = launch(Dispatchers.Default) { vm.activeTimelineDays.collect {} }
            try {
                val start = LocalDate.now().plusDays(1)
                val progDraft = OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2, notes = "Prog")
                val mealDraft = AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", 1)

                vm.saveShiftSeriesWithExtras(
                    series = ShiftSeriesEntity(
                        title = "Mattina Serie",
                        startDateEpochDay = start.toEpochDay(),
                        startMinuteOfDay = 6 * 60,
                        endMinuteOfDay = 14 * 60,
                        recurrenceType = RecurrenceType.DAILY.key,
                        maxOccurrences = 3
                    ),
                    applyExtrasToSeries = true,
                    overtimeDrafts = listOf(progDraft),
                    allowanceDrafts = listOf(mealDraft)
                )

                awaitMainCondition {
                    vm.activeTimelineDays.value.any { it.date == start.plusDays(1) && it.shifts.isNotEmpty() }
                }
                val day2Shifts = vm.activeTimelineDays.value.first { it.date == start.plusDays(1) }.shifts
                assertEquals("Unexpected shifts on day2: ${day2Shifts.map { it.title to it.seriesId }}", 1, day2Shifts.size)
                val day2Shift = day2Shifts.single()

                vm.addOvertime(start.plusDays(1), day2Shift.seriesId, OvertimeType.WEEKDAY, 1, "Urgenza")
                awaitMainCondition {
                    vm.activeTimelineDays.value.firstOrNull { it.date == start.plusDays(1) }
                        ?.overtimeEntries?.size == 2
                }

                val seriesExtras = vm.resolveInitialExtrasForEdit(day2Shift, isSingleOccurrenceEdit = false)
                assertTrue(seriesExtras.applyToSeries)
                assertEquals(1, seriesExtras.overtime.size)
                assertEquals(OvertimeType.SCHEDULED.key, seriesExtras.overtime.single().overtimeType)
                assertEquals(1, seriesExtras.allowances.size)

                val singleExtras = vm.resolveInitialExtrasForEdit(day2Shift, isSingleOccurrenceEdit = true)
                assertFalse(singleExtras.applyToSeries)
                assertEquals(2, singleExtras.overtime.size)
                assertEquals(1, singleExtras.allowances.size)

                vm.deleteSingleOccurrence(day2Shift)
                awaitMainCondition {
                    vm.activeTimelineDays.value.none { it.date == start.plusDays(1) && it.shifts.isNotEmpty() }
                }
                assertEquals(2, vm.activeTimelineDays.value.count { it.shifts.isNotEmpty() })

                vm.deleteEntireSeries(day2Shift.seriesId)
                awaitMainCondition { vm.activeTimelineDays.value.none { it.shifts.isNotEmpty() } }
            } finally {
                collector.cancel()
            }
        }
    }

    @Test
    fun viewModelLocalBackupExportRestoreAndProtectedReset() = runBlocking {
        withTimeout(20_000L) {
            val vm = createViewModel()
            val dao = EasyPlanningDatabase.getInstance(app).dao()
            val capturedEvents = CopyOnWriteArrayList<UiEvent>()
            val eventCollector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                vm.events.collect { capturedEvents.add(it) }
            }
            try {
                // 1. Register offline account (sets backup password) and save a shift
                vm.registerOffline("luca.bianchi", "MasterPass2026!")
                awaitMainCondition { vm.preferences.value.hasBackupPassword && vm.isUnlocked.value }

                val shiftDate = LocalDate.of(2026, 11, 25)
                vm.saveShiftSeriesWithExtras(
                    series = ShiftSeriesEntity(
                        title = "Turno Backup",
                        startDateEpochDay = shiftDate.toEpochDay(),
                        startMinuteOfDay = 7 * 60,
                        endMinuteOfDay = 15 * 60,
                        defaultOvertimeJson = ShiftExtrasCodec.encodeOvertime(
                            listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, 2))
                        )
                    ),
                    applyExtrasToSeries = true,
                    overtimeDrafts = listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, 2)),
                    allowanceDrafts = emptyList()
                )
                awaitMainCondition { dao.getAllSeriesOnce().any { it.title == "Turno Backup" } }

                // 2. Export local encrypted backup
                val exportedStream = ByteArrayOutputStream()
                val eventsBeforeExport = capturedEvents.size
                vm.exportLocalEncryptedBackup(exportedStream, "MasterPass2026!")
                awaitMainCondition {
                    exportedStream.size() > 0 && !vm.isBusy.value && capturedEvents.size > eventsBeforeExport
                }
                val backupBytes = exportedStream.toByteArray()
                assertTrue(backupBytes.size > 40)

                // 3. Protected reset with WRONG password must fail and keep DB intact
                val wrongResetResult = AtomicReference<Boolean?>(null)
                vm.verifyPasswordAndResetAll("PasswordErrata!") { wrongResetResult.set(it) }
                awaitMainCondition { wrongResetResult.get() != null }
                assertEquals(false, wrongResetResult.get())
                assertEquals(1, dao.getAllSeriesOnce().size)

                // 4. Protected reset with VALID password wipes DB and clears account preferences
                val validResetResult = AtomicReference<Boolean?>(null)
                vm.verifyPasswordAndResetAll("MasterPass2026!") { validResetResult.set(it) }
                awaitMainCondition { validResetResult.get() != null }
                assertEquals(true, validResetResult.get())
                assertTrue(dao.getAllSeriesOnce().isEmpty())
                awaitMainCondition { !vm.preferences.value.hasRegisteredAccount }

                // 5. Restore with wrong password leaves DB empty
                val eventsBeforeWrongImport = capturedEvents.size
                vm.importLocalEncryptedBackup(backupBytes, "PasswordErrata!")
                awaitMainCondition { !vm.isBusy.value && capturedEvents.size > eventsBeforeWrongImport }
                assertTrue(dao.getAllSeriesOnce().isEmpty())

                // 6. Restore with valid password recovers shift cleanly
                val eventsBeforeOkImport = capturedEvents.size
                vm.importLocalEncryptedBackup(backupBytes, "MasterPass2026!")
                awaitMainCondition {
                    dao.getAllSeriesOnce().any { it.title == "Turno Backup" } &&
                        capturedEvents.size > eventsBeforeOkImport
                }
                assertEquals("Turno Backup", dao.getAllSeriesOnce().single().title)
            } finally {
                eventCollector.cancel()
            }
        }
    }

    @Test
    fun viewModelAuthLockUnlockAndAllSettingsPersistEndToEnd() = runBlocking {
        withTimeout(20_000L) {
            PreferencesStore(app).clearAllUserPreferencesPreserveLocaleAndTheme()
            val vm = createViewModel()

            // 1. Register offline, logout, verify wrong vs valid offline login
            vm.registerOffline("sara.verdi", "ChiaveSicura2026!")
            awaitMainCondition { vm.preferences.value.isLoggedIn && vm.isUnlocked.value }

            vm.logout(app)
            awaitMainCondition { !vm.preferences.value.isLoggedIn && !vm.isUnlocked.value }

            val wrongLogin = AtomicReference<Boolean?>(null)
            vm.loginOffline("sara.verdi", "Sbagliata2026!") { wrongLogin.set(it) }
            awaitMainCondition { wrongLogin.get() != null }
            assertEquals(false, wrongLogin.get())
            assertFalse(vm.isUnlocked.value)

            val okLogin = AtomicReference<Boolean?>(null)
            vm.loginOffline("SARA.VERDI", "ChiaveSicura2026!") { okLogin.set(it) }
            awaitMainCondition { okLogin.get() != null }
            assertEquals(true, okLogin.get())
            assertTrue(vm.isUnlocked.value)

            // 2. App Lock PIN setup, wrong/right PIN verification, disabling lock
            vm.setupAppLockPin("4321")
            awaitMainCondition { vm.preferences.value.appLockEnabled && vm.preferences.value.hasAppLockPin }

            val wrongPin = AtomicReference<Boolean?>(null)
            vm.verifyPinAndUnlock("0000") { wrongPin.set(it) }
            awaitMainCondition { wrongPin.get() != null }
            assertEquals(false, wrongPin.get())

            val okPin = AtomicReference<Boolean?>(null)
            vm.verifyPinAndUnlock("4321") { okPin.set(it) }
            awaitMainCondition { okPin.get() != null }
            assertEquals(true, okPin.get())

            vm.setAppLockEnabled(false)
            awaitMainCondition { !vm.preferences.value.appLockEnabled }

            // 3. All app settings persist cleanly via ViewModel
            vm.setLanguage(AppLanguage.ENGLISH)
            vm.setThemeMode(ThemeMode.DARK)
            vm.setDynamicColor(true)
            vm.setDefaultReminder(120)
            vm.setScreenSecurity(true)
            vm.setDriveAutoSync(true)
            awaitMainCondition {
                val p = vm.preferences.value
                p.language == AppLanguage.ENGLISH &&
                    p.themeMode == ThemeMode.DARK &&
                    p.dynamicColor &&
                    p.defaultReminderMinutes == 120 &&
                    p.screenSecurityEnabled &&
                    p.driveAutoSync
            }

            // 4. Disconnect Drive clears connection state
            vm.disconnectGoogleDrive()
            awaitMainCondition { !vm.preferences.value.driveConnected && vm.preferences.value.driveAccountEmail == null }

            // 5. Reset without backup password (e.g., Google-only account) succeeds with null password
            PreferencesStore(app).clearAllUserPreferencesPreserveLocaleAndTheme()
            awaitMainCondition { !vm.preferences.value.hasBackupPassword }
            val resetNoPwd = AtomicReference<Boolean?>(null)
            vm.verifyPasswordAndResetAll(null) { resetNoPwd.set(it) }
            awaitMainCondition { resetNoPwd.get() != null }
            assertEquals(true, resetNoPwd.get())
        }
    }

    @Test
    fun viewModelDeleteInheritedOvertimeAndDirectAllowanceSuppressesOnlyTargetDay() = runBlocking {
        withTimeout(15_000L) {
            val vm = createViewModel()
            val collector = launch(Dispatchers.Default) { vm.activeTimelineDays.collect {} }
            try {
                val start = LocalDate.now().plusDays(10)
                vm.saveShiftSeriesWithExtras(
                    series = ShiftSeriesEntity(
                        title = "Notte Serie",
                        startDateEpochDay = start.toEpochDay(),
                        startMinuteOfDay = 22 * 60,
                        endMinuteOfDay = 6 * 60,
                        recurrenceType = RecurrenceType.DAILY.key,
                        maxOccurrences = 3
                    ),
                    applyExtrasToSeries = true,
                    overtimeDrafts = listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2, notes = "Prog")),
                    allowanceDrafts = emptyList()
                )
                awaitMainCondition {
                    vm.activeTimelineDays.value.count { it.date in start..start.plusDays(2) } == 3
                }

                // Add direct allowance on Day 1
                val day1Shift = vm.activeTimelineDays.value.first { it.date == start }.shifts.single()
                vm.addAllowance(start, day1Shift.seriesId, AllowancePreset.CUSTOM.key, "Reperibilità", 1, "Diretta")
                awaitMainCondition {
                    vm.activeTimelineDays.value.first { it.date == start }.allowanceEntries.size == 1
                }

                // Delete inherited virtual overtime (id < 0) ONLY on Day 2
                val day2VirtualOt = vm.activeTimelineDays.value
                    .first { it.date == start.plusDays(1) }
                    .overtimeEntries
                    .single()
                assertTrue(day2VirtualOt.id < 0L)
                vm.deleteOvertime(day2VirtualOt)

                // Delete direct allowance (id > 0) on Day 1
                val day1DirectAll = vm.activeTimelineDays.value
                    .first { it.date == start }
                    .allowanceEntries
                    .single()
                assertTrue(day1DirectAll.id > 0L)
                vm.deleteAllowance(day1DirectAll)

                awaitMainCondition {
                    val d1 = vm.activeTimelineDays.value.firstOrNull { it.date == start }
                    val d2 = vm.activeTimelineDays.value.firstOrNull { it.date == start.plusDays(1) }
                    val d3 = vm.activeTimelineDays.value.firstOrNull { it.date == start.plusDays(2) }
                    d1 != null && d2 != null && d3 != null &&
                        d1.allowanceEntries.isEmpty() &&
                        d2.overtimeEntries.isEmpty() &&
                        d1.overtimeEntries.size == 1 &&
                        d3.overtimeEntries.size == 1
                }
            } finally {
                collector.cancel()
            }
        }
    }

    @Test
    fun backupRepositorySanitizesFieldsFiltersInvalidExtrasAndRejectsBadOverrideBounds() = runBlocking {
        withTimeout(15_000L) {
            val dao = EasyPlanningDatabase.getInstance(app).dao()
            val backupRepo = com.example.sync.BackupRepository(dao)

            // 1. Valid schema v1 with blank title, 0 interval/workDays, negative offDays, and invalid overtime/allowances
            val dirtyValidJson = """
                {
                  "schemaVersion": 1,
                  "exportedAtMillis": 1700000000000,
                  "appName": "EasyPlanning",
                  "shiftSeries": [
                    {
                      "id": 1,
                      "title": "   ",
                      "startDateEpochDay": 20600,
                      "startMinuteOfDay": 480,
                      "endMinuteOfDay": 960,
                      "colorId": 0,
                      "notes": "",
                      "reminderMinutesBefore": 30,
                      "recurrenceType": "ROTATING_CYCLE",
                      "recurrenceInterval": 0,
                      "daysOfWeekMask": 0,
                      "cycleWorkDays": 0,
                      "cycleOffDays": -3,
                      "maxOccurrences": 0,
                      "createdAtMillis": 1700000000000
                    }
                  ],
                  "shiftOverrides": [],
                  "overtimeEntries": [
                    {"id": 1, "dateEpochDay": 20600, "overtimeType": "SCHEDULED", "halfHours": 0, "notes": "", "createdAtMillis": 1},
                    {"id": 2, "dateEpochDay": 20600, "overtimeType": "SCHEDULED", "halfHours": 49, "notes": "", "createdAtMillis": 2},
                    {"id": 3, "dateEpochDay": 20600, "overtimeType": "SCHEDULED", "halfHours": 2, "notes": "Valido", "createdAtMillis": 3}
                  ],
                  "allowanceEntries": [
                    {"id": 1, "dateEpochDay": 20600, "presetKey": "CUSTOM", "label": "   ", "quantity": 1, "notes": "", "createdAtMillis": 1},
                    {"id": 2, "dateEpochDay": 20600, "presetKey": "CUSTOM", "label": "Troppi", "quantity": 1000, "notes": "", "createdAtMillis": 2},
                    {"id": 3, "dateEpochDay": 20600, "presetKey": "MEAL_VOUCHER", "label": "  Buono Pasto  ", "quantity": 1, "notes": "", "createdAtMillis": 3}
                  ]
                }
            """.trimIndent()
            val encDirty = CryptoManager.encryptBackup(dirtyValidJson.toByteArray(Charsets.UTF_8), "Sicura2026!".toCharArray())
            backupRepo.restoreFromEncryptedBytes(encDirty, "Sicura2026!".toCharArray())

            val sanitizedSeries = dao.getAllSeriesOnce().single()
            assertEquals("Turno", sanitizedSeries.title)
            assertEquals(1, sanitizedSeries.recurrenceInterval)
            assertEquals(1, sanitizedSeries.cycleWorkDays)
            assertEquals(0, sanitizedSeries.cycleOffDays)
            assertNull(sanitizedSeries.maxOccurrences)
            assertEquals(1, dao.getAllOvertimeOnce().size)
            assertEquals("Valido", dao.getAllOvertimeOnce().single().notes)
            assertEquals(1, dao.getAllAllowancesOnce().size)
            assertEquals("Buono Pasto", dao.getAllAllowancesOnce().single().label)

            // 2. Bad override time bounds (> 1439) must be rejected WITHOUT wiping existing DB
            val badOverrideJson = """
                {
                  "schemaVersion": 1,
                  "exportedAtMillis": 1700000000000,
                  "appName": "EasyPlanning",
                  "shiftSeries": [],
                  "shiftOverrides": [
                    {"id": 1, "seriesId": 1, "occurrenceEpochDay": 20600, "isDeleted": false, "startMinuteOfDay": 1500}
                  ],
                  "overtimeEntries": [],
                  "allowanceEntries": []
                }
            """.trimIndent()
            val encBadOv = CryptoManager.encryptBackup(badOverrideJson.toByteArray(Charsets.UTF_8), "Sicura2026!".toCharArray())
            var threw = false
            try {
                backupRepo.restoreFromEncryptedBytes(encBadOv, "Sicura2026!".toCharArray())
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw)
            // Previously restored sanitized series remains intact
            assertEquals("Turno", dao.getAllSeriesOnce().single().title)
        }
    }
}
