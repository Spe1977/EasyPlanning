package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EasyPlanningDao {

    // Reactive streams
    @Query("SELECT * FROM shift_series ORDER BY startDateEpochDay ASC, startMinuteOfDay ASC")
    fun observeAllSeries(): Flow<List<ShiftSeriesEntity>>

    @Query("SELECT * FROM shift_overrides")
    fun observeAllOverrides(): Flow<List<ShiftOverrideEntity>>

    @Query("SELECT * FROM overtime_entries ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeAllOvertime(): Flow<List<OvertimeEntryEntity>>

    @Query("SELECT * FROM allowance_entries ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeAllAllowances(): Flow<List<AllowanceEntryEntity>>

    // One-shot snapshots for backup / notifications / PDF
    @Query("SELECT * FROM shift_series")
    suspend fun getAllSeriesOnce(): List<ShiftSeriesEntity>

    @Query("SELECT * FROM shift_overrides")
    suspend fun getAllOverridesOnce(): List<ShiftOverrideEntity>

    @Query("SELECT * FROM overtime_entries")
    suspend fun getAllOvertimeOnce(): List<OvertimeEntryEntity>

    @Query("SELECT * FROM allowance_entries")
    suspend fun getAllAllowancesOnce(): List<AllowanceEntryEntity>

    @Query("SELECT * FROM shift_series WHERE id = :seriesId LIMIT 1")
    suspend fun getSeriesById(seriesId: Long): ShiftSeriesEntity?

    // Shift series mutations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: ShiftSeriesEntity): Long

    @Update
    suspend fun updateSeries(series: ShiftSeriesEntity)

    @Query("DELETE FROM shift_series WHERE id = :seriesId")
    suspend fun deleteSeriesById(seriesId: Long)

    @Query("DELETE FROM shift_overrides WHERE seriesId = :seriesId")
    suspend fun deleteOverridesForSeries(seriesId: Long)

    @Query("DELETE FROM overtime_entries WHERE shiftSeriesId = :seriesId")
    suspend fun deleteOvertimeForSeries(seriesId: Long)

    @Query("DELETE FROM allowance_entries WHERE shiftSeriesId = :seriesId")
    suspend fun deleteAllowancesForSeries(seriesId: Long)

    @Transaction
    suspend fun deleteSeriesWithOverrides(seriesId: Long) {
        deleteOverridesForSeries(seriesId)
        deleteOvertimeForSeries(seriesId)
        deleteAllowancesForSeries(seriesId)
        deleteSeriesById(seriesId)
    }

    // Overrides
    @Query("SELECT * FROM shift_overrides WHERE seriesId = :seriesId AND occurrenceEpochDay = :epochDay LIMIT 1")
    suspend fun getOverride(seriesId: Long, epochDay: Long): ShiftOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(overrideEntity: ShiftOverrideEntity): Long

    // Overtime
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOvertime(entry: OvertimeEntryEntity): Long

    @Query("DELETE FROM overtime_entries WHERE id = :id")
    suspend fun deleteOvertimeById(id: Long)

    @Query("DELETE FROM overtime_entries WHERE shiftSeriesId = :seriesId AND dateEpochDay = :epochDay")
    suspend fun deleteOvertimeForShiftOnDate(seriesId: Long, epochDay: Long)

    // Allowances
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllowance(entry: AllowanceEntryEntity): Long

    @Query("DELETE FROM allowance_entries WHERE id = :id")
    suspend fun deleteAllowanceById(id: Long)

    @Query("DELETE FROM allowance_entries WHERE shiftSeriesId = :seriesId AND dateEpochDay = :epochDay")
    suspend fun deleteAllowancesForShiftOnDate(seriesId: Long, epochDay: Long)

    // Atomic full clear & restore
    @Query("DELETE FROM shift_series")
    suspend fun clearAllSeries()

    @Query("DELETE FROM shift_overrides")
    suspend fun clearAllOverrides()

    @Query("DELETE FROM overtime_entries")
    suspend fun clearAllOvertime()

    @Query("DELETE FROM allowance_entries")
    suspend fun clearAllAllowances()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSeries(items: List<ShiftSeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllOverrides(items: List<ShiftOverrideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllOvertime(items: List<OvertimeEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAllowances(items: List<AllowanceEntryEntity>)

    @Transaction
    suspend fun wipeAllTables() {
        clearAllOverrides()
        clearAllOvertime()
        clearAllAllowances()
        clearAllSeries()
    }

    @Transaction
    suspend fun replaceAllData(
        series: List<ShiftSeriesEntity>,
        overrides: List<ShiftOverrideEntity>,
        overtime: List<OvertimeEntryEntity>,
        allowances: List<AllowanceEntryEntity>
    ) {
        wipeAllTables()
        insertAllSeries(series)
        insertAllOverrides(overrides)
        insertAllOvertime(overtime)
        insertAllAllowances(allowances)
    }
}
