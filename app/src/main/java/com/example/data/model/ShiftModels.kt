package com.example.data.model

import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftOverrideEntity
import com.example.data.local.ShiftSeriesEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@JsonClass(generateAdapter = true)
data class OvertimeDraft(
    val overtimeType: String = OvertimeType.WEEKDAY.key,
    val halfHours: Int = 2,
    val notes: String = ""
)

@JsonClass(generateAdapter = true)
data class AllowanceDraft(
    val presetKey: String = AllowancePreset.MEAL_VOUCHER.key,
    val label: String,
    val quantity: Int = 1,
    val notes: String = ""
)

object ShiftExtrasCodec {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val overtimeListAdapter = moshi.adapter<List<OvertimeDraft>>(
        Types.newParameterizedType(List::class.java, OvertimeDraft::class.java)
    )
    private val allowanceListAdapter = moshi.adapter<List<AllowanceDraft>>(
        Types.newParameterizedType(List::class.java, AllowanceDraft::class.java)
    )

    fun encodeOvertime(items: List<OvertimeDraft>): String =
        if (items.isEmpty()) "" else overtimeListAdapter.toJson(items)

    fun decodeOvertime(json: String?): List<OvertimeDraft> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { overtimeListAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    fun encodeAllowances(items: List<AllowanceDraft>): String =
        if (items.isEmpty()) "" else allowanceListAdapter.toJson(items)

    fun decodeAllowances(json: String?): List<AllowanceDraft> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { allowanceListAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }
}

/**
 * A materialized shift occurrence on a specific calendar date.
 */
data class ShiftInstance(
    val seriesId: Long,
    val date: LocalDate,
    val title: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val colorId: Int,
    val notes: String,
    val reminderMinutesBefore: Int,
    val isRecurring: Boolean,
    val isModifiedOverride: Boolean,
    val sourceSeries: ShiftSeriesEntity,
    val inheritedOvertime: List<OvertimeDraft> = emptyList(),
    val inheritedAllowances: List<AllowanceDraft> = emptyList()
) {
    val startTime: LocalTime
        get() = LocalTime.of((startMinuteOfDay / 60).coerceIn(0, 23), (startMinuteOfDay % 60).coerceIn(0, 59))

    val endTime: LocalTime
        get() = LocalTime.of((endMinuteOfDay / 60).coerceIn(0, 23), (endMinuteOfDay % 60).coerceIn(0, 59))

    val isOvernight: Boolean
        get() = endMinuteOfDay <= startMinuteOfDay

    val durationMinutes: Int
        get() = if (endMinuteOfDay > startMinuteOfDay) {
            endMinuteOfDay - startMinuteOfDay
        } else {
            (24 * 60 - startMinuteOfDay) + endMinuteOfDay
        }

    fun formattedTimeRange(): String =
        "${startTime.format(TIME_FORMATTER)} – ${endTime.format(TIME_FORMATTER)}"

    companion object {
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class DaySummary(
    val date: LocalDate,
    val shifts: List<ShiftInstance>,
    val overtimeEntries: List<OvertimeEntryEntity>,
    val allowanceEntries: List<AllowanceEntryEntity>
) {
    val totalRegularMinutes: Int get() = shifts.sumOf { it.durationMinutes }
    val totalOvertimeHalfHours: Int get() = overtimeEntries.sumOf { it.halfHours }
    val totalAllowancesQuantity: Int get() = allowanceEntries.sumOf { it.quantity }
    val hasActivity: Boolean get() = shifts.isNotEmpty() || overtimeEntries.isNotEmpty() || allowanceEntries.isNotEmpty()
}

data class ShiftDistributionItem(
    val title: String,
    val colorId: Int,
    val count: Int,
    val totalMinutes: Int
)

data class AllowanceAggregate(
    val label: String,
    val totalQuantity: Int
)

data class StatsSummary(
    val period: StatsPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val workedDays: Int,
    val totalRegularMinutes: Int,
    val totalOvertimeHalfHours: Int,
    val overtimeByTypeHalfHours: Map<OvertimeType, Int>,
    val shiftDistribution: List<ShiftDistributionItem>,
    val allowanceAggregates: List<AllowanceAggregate>,
    val totalAllowancesCount: Int
)

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val appName: String = "EasyPlanning",
    val shiftSeries: List<ShiftSeriesEntity>,
    val shiftOverrides: List<ShiftOverrideEntity>,
    val overtimeEntries: List<OvertimeEntryEntity>,
    val allowanceEntries: List<AllowanceEntryEntity>
)

data class UserPreferences(
    val isPreferencesLoaded: Boolean = false,
    val hasRegisteredAccount: Boolean = false,
    val isLoggedIn: Boolean = false,
    val authProvider: AuthProvider = AuthProvider.NONE,
    val username: String? = null,
    val googleDisplayName: String? = null,
    val googleEmail: String? = null,
    val language: AppLanguage = AppLanguage.ITALIAN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultReminderMinutes: Int = ReminderOption.MIN_30.minutesBefore,
    val appLockEnabled: Boolean = false,
    val screenSecurityEnabled: Boolean = false,
    val hasBackupPassword: Boolean = false,
    val hasAppLockPin: Boolean = false,
    val driveConnected: Boolean = false,
    val driveAccountEmail: String? = null,
    val driveAutoSync: Boolean = false,
    val lastLocalBackupMillis: Long? = null,
    val lastDriveSyncMillis: Long? = null
)
