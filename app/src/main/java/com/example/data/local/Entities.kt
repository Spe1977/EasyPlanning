package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Core shift definition. Can be a single shift (recurrenceType = "NONE")
 * or a recurring series (DAILY, WEEKLY, MONTHLY, ROTATING_CYCLE).
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "shift_series")
data class ShiftSeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDateEpochDay: Long,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val colorId: Int = 0,
    val notes: String = "",
    val reminderMinutesBefore: Int = 30,
    val recurrenceType: String = "NONE",
    val recurrenceInterval: Int = 1,
    /** Bitmask for weekly recurrence: Mon=1<<0 .. Sun=1<<6 */
    val daysOfWeekMask: Int = 0,
    /** Rotating cycle parameters: workDays ON, offDays OFF */
    val cycleWorkDays: Int = 4,
    val cycleOffDays: Int = 2,
    val endDateEpochDay: Long? = null,
    val maxOccurrences: Int? = null,
    val defaultOvertimeJson: String = "",
    val defaultAllowancesJson: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Per-date exception for a recurring shift series: either modifies fields for that date
 * or cancels (isDeleted = true) that single occurrence.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "shift_overrides",
    indices = [Index(value = ["seriesId", "occurrenceEpochDay"], unique = true)]
)
data class ShiftOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val occurrenceEpochDay: Long,
    val isDeleted: Boolean = false,
    val title: String? = null,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null,
    val colorId: Int? = null,
    val notes: String? = null,
    val reminderMinutesBefore: Int? = null,
    val customOvertimeJson: String? = null,
    val customAllowancesJson: String? = null
)

/**
 * Overtime entry stored in integer half-hours (1 unit = 0.5h = 30 min)
 * for exact arithmetic without floating-point drift.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "overtime_entries",
    indices = [Index(value = ["dateEpochDay"])]
)
data class OvertimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val shiftSeriesId: Long? = null,
    val overtimeType: String,
    val halfHours: Int,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Allowance / benefit entry (quick presets like Meal Voucher or custom free-text label).
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "allowance_entries",
    indices = [Index(value = ["dateEpochDay"])]
)
data class AllowanceEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val shiftSeriesId: Long? = null,
    val presetKey: String,
    val label: String,
    val quantity: Int = 1,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)
