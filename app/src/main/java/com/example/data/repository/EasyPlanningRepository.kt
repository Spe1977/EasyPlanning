package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.EasyPlanningDao
import com.example.data.local.EasyPlanningDatabase
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.PreferencesStore
import com.example.data.local.ShiftOverrideEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AllowanceDraft
import com.example.data.model.AppLanguage
import com.example.data.model.OvertimeDraft
import com.example.data.model.RecurrenceType
import com.example.data.model.ShiftExtrasCodec
import com.example.data.model.ShiftInstance
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.domain.PdfExportService
import com.example.i18n.AppStrings
import com.example.notifications.ShiftReminderScheduler
import com.example.sync.BackupRepository
import com.example.sync.GoogleDriveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth

class EasyPlanningRepository(private val context: Context) {

    private val database: EasyPlanningDatabase = EasyPlanningDatabase.getInstance(context)
    private val dao: EasyPlanningDao = database.dao()
    val preferencesStore: PreferencesStore = PreferencesStore(context)
    val backupRepository: BackupRepository = BackupRepository(dao)
    val driveSyncManager: GoogleDriveSyncManager = GoogleDriveSyncManager(context)
    private val reminderScheduler: ShiftReminderScheduler = ShiftReminderScheduler(context)

    val allSeries: Flow<List<ShiftSeriesEntity>> = dao.observeAllSeries()
    val allOverrides: Flow<List<ShiftOverrideEntity>> = dao.observeAllOverrides()
    val allOvertime: Flow<List<OvertimeEntryEntity>> = dao.observeAllOvertime()
    val allAllowances: Flow<List<AllowanceEntryEntity>> = dao.observeAllAllowances()
    val userPreferences: Flow<UserPreferences> = preferencesStore.preferencesFlow

    suspend fun saveShiftSeriesWithExtras(
        series: ShiftSeriesEntity,
        applyExtrasToSeries: Boolean,
        overtimeDrafts: List<OvertimeDraft>,
        allowanceDrafts: List<AllowanceDraft>
    ) {
        database.withTransaction {
            val previousSeries = if (series.id != 0L) dao.getSeriesById(series.id) else null
            val isRecurring = RecurrenceType.fromKey(series.recurrenceType) != RecurrenceType.NONE
            val storeOnSeries = isRecurring && applyExtrasToSeries
            val normalizedSeries = series.copy(
                defaultOvertimeJson = if (storeOnSeries) ShiftExtrasCodec.encodeOvertime(overtimeDrafts) else "",
                defaultAllowancesJson = if (storeOnSeries) ShiftExtrasCodec.encodeAllowances(allowanceDrafts) else ""
            )
            val savedSeriesId = if (normalizedSeries.id == 0L) {
                dao.insertSeries(normalizedSeries)
            } else {
                dao.updateSeries(normalizedSeries)
                normalizedSeries.id
            }

            // If a shift's start date moved, remove old direct extras left behind on the previous start date
            if (previousSeries != null && previousSeries.startDateEpochDay != normalizedSeries.startDateEpochDay) {
                dao.deleteOvertimeForShiftOnDate(savedSeriesId, previousSeries.startDateEpochDay)
                dao.deleteAllowancesForShiftOnDate(savedSeriesId, previousSeries.startDateEpochDay)
            }

            if (storeOnSeries) {
                // Clean up prior direct start-date extras when migrating to series-level defaults
                if (previousSeries != null && previousSeries.defaultOvertimeJson.isBlank()) {
                    dao.deleteOvertimeForShiftOnDate(savedSeriesId, normalizedSeries.startDateEpochDay)
                }
                if (previousSeries != null && previousSeries.defaultAllowancesJson.isBlank()) {
                    dao.deleteAllowancesForShiftOnDate(savedSeriesId, normalizedSeries.startDateEpochDay)
                }
            } else {
                // Replace shift-linked direct extras on the start date
                dao.deleteOvertimeForShiftOnDate(savedSeriesId, normalizedSeries.startDateEpochDay)
                dao.deleteAllowancesForShiftOnDate(savedSeriesId, normalizedSeries.startDateEpochDay)

                overtimeDrafts.filter { it.halfHours > 0 }.forEach { draft ->
                    dao.insertOvertime(
                        OvertimeEntryEntity(
                            dateEpochDay = normalizedSeries.startDateEpochDay,
                            shiftSeriesId = savedSeriesId,
                            overtimeType = draft.overtimeType,
                            halfHours = draft.halfHours,
                            notes = draft.notes.trim()
                        )
                    )
                }
                allowanceDrafts.filter { it.label.isNotBlank() && it.quantity > 0 }.forEach { draft ->
                    dao.insertAllowance(
                        AllowanceEntryEntity(
                            dateEpochDay = normalizedSeries.startDateEpochDay,
                            shiftSeriesId = savedSeriesId,
                            presetKey = draft.presetKey,
                            label = draft.label.trim(),
                            quantity = draft.quantity,
                            notes = draft.notes.trim()
                        )
                    )
                }
            }
        }
        refreshReminders()
    }

    suspend fun saveSingleOccurrenceWithExtras(
        original: ShiftInstance,
        title: String,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        colorId: Int,
        notes: String,
        reminderMinutesBefore: Int,
        overtimeDrafts: List<OvertimeDraft>,
        allowanceDrafts: List<AllowanceDraft>
    ) {
        val epochDay = original.date.toEpochDay()
        database.withTransaction {
            val existingOverride = dao.getOverride(original.seriesId, epochDay)
            // Suppress inherited series defaults on this specific date since all effective extras are persisted directly
            dao.upsertOverride(
                ShiftOverrideEntity(
                    id = existingOverride?.id ?: 0L,
                    seriesId = original.seriesId,
                    occurrenceEpochDay = epochDay,
                    isDeleted = false,
                    title = title,
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    colorId = colorId,
                    notes = notes,
                    reminderMinutesBefore = reminderMinutesBefore,
                    customOvertimeJson = "[]",
                    customAllowancesJson = "[]"
                )
            )

            dao.deleteOvertimeForShiftOnDate(original.seriesId, epochDay)
            dao.deleteAllowancesForShiftOnDate(original.seriesId, epochDay)

            overtimeDrafts.filter { it.halfHours > 0 }.forEach { draft ->
                dao.insertOvertime(
                    OvertimeEntryEntity(
                        dateEpochDay = epochDay,
                        shiftSeriesId = original.seriesId,
                        overtimeType = draft.overtimeType,
                        halfHours = draft.halfHours,
                        notes = draft.notes.trim()
                    )
                )
            }
            allowanceDrafts.filter { it.label.isNotBlank() && it.quantity > 0 }.forEach { draft ->
                dao.insertAllowance(
                    AllowanceEntryEntity(
                        dateEpochDay = epochDay,
                        shiftSeriesId = original.seriesId,
                        presetKey = draft.presetKey,
                        label = draft.label.trim(),
                        quantity = draft.quantity,
                        notes = draft.notes.trim()
                    )
                )
            }
        }
        refreshReminders()
    }

    suspend fun deleteSingleOccurrence(seriesId: Long, date: LocalDate) {
        val epochDay = date.toEpochDay()
        database.withTransaction {
            val existing = dao.getOverride(seriesId, epochDay)
            dao.upsertOverride(
                ShiftOverrideEntity(
                    id = existing?.id ?: 0L,
                    seriesId = seriesId,
                    occurrenceEpochDay = epochDay,
                    isDeleted = true
                )
            )
            dao.deleteOvertimeForShiftOnDate(seriesId, epochDay)
            dao.deleteAllowancesForShiftOnDate(seriesId, epochDay)
        }
        refreshReminders()
    }

    suspend fun deleteEntireSeries(seriesId: Long) {
        dao.deleteSeriesWithOverrides(seriesId)
        refreshReminders()
    }

    suspend fun addOvertime(entry: OvertimeEntryEntity) {
        dao.insertOvertime(entry)
    }

    suspend fun removeOvertimeEntry(entry: OvertimeEntryEntity) {
        if (entry.id > 0L) {
            dao.deleteOvertimeById(entry.id)
            return
        }
        val seriesId = entry.shiftSeriesId ?: return
        val series = dao.getSeriesById(seriesId) ?: return
        val existingOv = dao.getOverride(seriesId, entry.dateEpochDay)
        val currentList = if (existingOv?.customOvertimeJson != null) {
            ShiftExtrasCodec.decodeOvertime(existingOv.customOvertimeJson)
        } else {
            ShiftExtrasCodec.decodeOvertime(series.defaultOvertimeJson)
        }.toMutableList()

        val idx = currentList.indexOfFirst {
            it.overtimeType == entry.overtimeType &&
                it.halfHours == entry.halfHours &&
                it.notes == entry.notes
        }
        if (idx >= 0) {
            currentList.removeAt(idx)
            val updatedJson = if (currentList.isEmpty()) "[]" else ShiftExtrasCodec.encodeOvertime(currentList)
            dao.upsertOverride(
                (existingOv ?: ShiftOverrideEntity(seriesId = seriesId, occurrenceEpochDay = entry.dateEpochDay))
                    .copy(customOvertimeJson = updatedJson)
            )
        }
    }

    suspend fun addAllowance(entry: AllowanceEntryEntity) {
        dao.insertAllowance(entry)
    }

    suspend fun removeAllowanceEntry(entry: AllowanceEntryEntity) {
        if (entry.id > 0L) {
            dao.deleteAllowanceById(entry.id)
            return
        }
        val seriesId = entry.shiftSeriesId ?: return
        val series = dao.getSeriesById(seriesId) ?: return
        val existingOv = dao.getOverride(seriesId, entry.dateEpochDay)
        val currentList = if (existingOv?.customAllowancesJson != null) {
            ShiftExtrasCodec.decodeAllowances(existingOv.customAllowancesJson)
        } else {
            ShiftExtrasCodec.decodeAllowances(series.defaultAllowancesJson)
        }.toMutableList()

        val idx = currentList.indexOfFirst {
            it.presetKey == entry.presetKey &&
                it.label == entry.label &&
                it.quantity == entry.quantity &&
                it.notes == entry.notes
        }
        if (idx >= 0) {
            currentList.removeAt(idx)
            val updatedJson = if (currentList.isEmpty()) "[]" else ShiftExtrasCodec.encodeAllowances(currentList)
            dao.upsertOverride(
                (existingOv ?: ShiftOverrideEntity(seriesId = seriesId, occurrenceEpochDay = entry.dateEpochDay))
                    .copy(customAllowancesJson = updatedJson)
            )
        }
    }

    suspend fun refreshReminders() = withContext(Dispatchers.IO) {
        reminderScheduler.rescheduleAll(
            seriesList = dao.getAllSeriesOnce(),
            overrides = dao.getAllOverridesOnce()
        )
    }

    suspend fun exportMonthlyPdf(
        month: YearMonth,
        strings: AppStrings,
        outputStream: OutputStream
    ) = withContext(Dispatchers.IO) {
        PdfExportService.generateMonthlyReport(
            month = month,
            seriesList = dao.getAllSeriesOnce(),
            overrides = dao.getAllOverridesOnce(),
            overtimeEntries = dao.getAllOvertimeOnce(),
            allowanceEntries = dao.getAllAllowancesOnce(),
            strings = strings,
            outputStream = outputStream
        )
    }

    suspend fun createEncryptedBackup(password: CharArray): ByteArray {
        val bytes = backupRepository.createEncryptedBackupBytes(password)
        preferencesStore.markLocalBackupNow()
        return bytes
    }

    suspend fun restoreEncryptedBackup(bytes: ByteArray, password: CharArray) {
        backupRepository.restoreFromEncryptedBytes(bytes, password)
        refreshReminders()
    }

    suspend fun secureWipeAllData() = withContext(Dispatchers.IO) {
        reminderScheduler.cancelAllScheduled()
        dao.wipeAllTables()
        preferencesStore.clearAllUserPreferencesPreserveLocaleAndTheme()
    }

    // Auth & Profile shortcuts
    suspend fun registerOfflineAccount(username: String, password: CharArray) =
        preferencesStore.registerOfflineAccount(username, password)
    suspend fun verifyOfflineLogin(username: String, password: CharArray) =
        preferencesStore.verifyOfflineLogin(username, password)
    suspend fun signInOrLinkGoogleAccount(email: String, displayName: String?) =
        preferencesStore.signInOrLinkGoogleAccount(email, displayName)
    suspend fun logout() = preferencesStore.logout()

    // Preference shortcuts
    suspend fun setLanguage(language: AppLanguage) = preferencesStore.setLanguage(language)
    suspend fun setThemeMode(mode: ThemeMode) = preferencesStore.setThemeMode(mode)
    suspend fun setDynamicColor(enabled: Boolean) = preferencesStore.setDynamicColor(enabled)
    suspend fun setDefaultReminder(minutes: Int) = preferencesStore.setDefaultReminder(minutes)
    suspend fun setScreenSecurity(enabled: Boolean) = preferencesStore.setScreenSecurity(enabled)
    suspend fun setAppLockEnabled(enabled: Boolean) = preferencesStore.setAppLockEnabled(enabled)
    suspend fun saveBackupPassword(password: CharArray) = preferencesStore.saveBackupPassword(password)
    suspend fun verifyBackupPassword(candidate: CharArray) = preferencesStore.verifyBackupPassword(candidate)
    suspend fun saveAppLockPin(pin: CharArray) = preferencesStore.saveAppLockPin(pin)
    suspend fun verifyAppLockPin(candidate: CharArray) = preferencesStore.verifyAppLockPin(candidate)
    suspend fun setDriveConnection(connected: Boolean, email: String?) =
        preferencesStore.setDriveConnection(connected, email)
    suspend fun setDriveAutoSync(enabled: Boolean) = preferencesStore.setDriveAutoSync(enabled)
    suspend fun markDriveSyncNow() = preferencesStore.markDriveSyncNow()
}
