package com.example.domain

import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftOverrideEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.RecurrenceType
import com.example.data.model.ShiftExtrasCodec
import com.example.data.model.ShiftInstance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure domain recurrence expander supporting single shifts, daily, weekly (any weekday subset),
 * monthly (day-of-month clamped to month length), and rotating N-work / M-off shift cycles,
 * with per-occurrence overrides & deletions.
 */
object RecurrenceEngine {

    fun weekdayBit(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    fun expandRange(
        seriesList: List<ShiftSeriesEntity>,
        overrides: List<ShiftOverrideEntity>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<ShiftInstance> {
        if (rangeEnd.isBefore(rangeStart)) return emptyList()

        val overrideLookup: Map<Pair<Long, Long>, ShiftOverrideEntity> =
            overrides.associateBy { it.seriesId to it.occurrenceEpochDay }

        val result = mutableListOf<ShiftInstance>()
        for (series in seriesList) {
            val dates = expandDatesForSeries(series, rangeStart, rangeEnd)
            if (dates.isEmpty()) continue
            val isRecurring = RecurrenceType.fromKey(series.recurrenceType) != RecurrenceType.NONE
            // Decode series-level JSON defaults ONCE per series instead of per occurrence
            val seriesDefaultOt = ShiftExtrasCodec.decodeOvertime(series.defaultOvertimeJson)
            val seriesDefaultAll = ShiftExtrasCodec.decodeAllowances(series.defaultAllowancesJson)
            for (date in dates) {
                val epochDay = date.toEpochDay()
                val ov = overrideLookup[series.id to epochDay]
                if (ov?.isDeleted == true) continue

                val inheritedOt = if (ov?.customOvertimeJson != null) {
                    ShiftExtrasCodec.decodeOvertime(ov.customOvertimeJson)
                } else {
                    seriesDefaultOt
                }
                val inheritedAll = if (ov?.customAllowancesJson != null) {
                    ShiftExtrasCodec.decodeAllowances(ov.customAllowancesJson)
                } else {
                    seriesDefaultAll
                }

                result += ShiftInstance(
                    seriesId = series.id,
                    date = date,
                    title = ov?.title ?: series.title,
                    startMinuteOfDay = ov?.startMinuteOfDay ?: series.startMinuteOfDay,
                    endMinuteOfDay = ov?.endMinuteOfDay ?: series.endMinuteOfDay,
                    colorId = ov?.colorId ?: series.colorId,
                    notes = ov?.notes ?: series.notes,
                    reminderMinutesBefore = ov?.reminderMinutesBefore ?: series.reminderMinutesBefore,
                    isRecurring = isRecurring,
                    isModifiedOverride = ov != null,
                    sourceSeries = series,
                    inheritedOvertime = inheritedOt,
                    inheritedAllowances = inheritedAll
                )
            }
        }
        return result.sortedWith(compareBy<ShiftInstance> { it.date }.thenBy { it.startMinuteOfDay })
    }

    /**
     * Combines direct overtime rows in [rangeStart..rangeEnd] with series-inherited recurring overtime
     * from active (non-deleted) shift occurrences in the same range.
     * Virtual inherited entries carry negative IDs so UI/repository can distinguish them cleanly.
     */
    fun effectiveOvertime(
        expandedShifts: List<ShiftInstance>,
        directEntries: List<OvertimeEntryEntity>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<OvertimeEntryEntity> {
        val startEpoch = rangeStart.toEpochDay()
        val endEpoch = rangeEnd.toEpochDay()
        val result = ArrayList<OvertimeEntryEntity>(directEntries.size + expandedShifts.size)
        directEntries.forEach { entry ->
            if (entry.dateEpochDay in startEpoch..endEpoch) {
                result += entry
            }
        }
        var virtualCounter = 1L
        expandedShifts.forEach { shift ->
            val epochDay = shift.date.toEpochDay()
            if (epochDay in startEpoch..endEpoch && shift.inheritedOvertime.isNotEmpty()) {
                shift.inheritedOvertime.forEach { draft ->
                    if (draft.halfHours > 0) {
                        result += OvertimeEntryEntity(
                            id = -virtualCounter++,
                            dateEpochDay = epochDay,
                            shiftSeriesId = shift.seriesId,
                            overtimeType = draft.overtimeType,
                            halfHours = draft.halfHours,
                            notes = draft.notes
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * Combines direct allowance rows in [rangeStart..rangeEnd] with series-inherited recurring allowances
     * from active (non-deleted) shift occurrences in the same range.
     */
    fun effectiveAllowances(
        expandedShifts: List<ShiftInstance>,
        directEntries: List<AllowanceEntryEntity>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<AllowanceEntryEntity> {
        val startEpoch = rangeStart.toEpochDay()
        val endEpoch = rangeEnd.toEpochDay()
        val result = ArrayList<AllowanceEntryEntity>(directEntries.size + expandedShifts.size)
        directEntries.forEach { entry ->
            if (entry.dateEpochDay in startEpoch..endEpoch) {
                result += entry
            }
        }
        var virtualCounter = 1L
        expandedShifts.forEach { shift ->
            val epochDay = shift.date.toEpochDay()
            if (epochDay in startEpoch..endEpoch && shift.inheritedAllowances.isNotEmpty()) {
                shift.inheritedAllowances.forEach { draft ->
                    if (draft.label.isNotBlank() && draft.quantity > 0) {
                        result += AllowanceEntryEntity(
                            id = -virtualCounter++,
                            dateEpochDay = epochDay,
                            shiftSeriesId = shift.seriesId,
                            presetKey = draft.presetKey,
                            label = draft.label,
                            quantity = draft.quantity,
                            notes = draft.notes
                        )
                    }
                }
            }
        }
        return result
    }

    private fun expandDatesForSeries(
        series: ShiftSeriesEntity,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<LocalDate> {
        val startDate = LocalDate.ofEpochDay(series.startDateEpochDay)
        if (startDate.isAfter(rangeEnd)) return emptyList()

        val seriesEnd = series.endDateEpochDay?.let { LocalDate.ofEpochDay(it) }
        val effectiveEnd = if (seriesEnd != null && seriesEnd.isBefore(rangeEnd)) seriesEnd else rangeEnd
        if (effectiveEnd.isBefore(startDate) || effectiveEnd.isBefore(rangeStart)) return emptyList()

        val type = RecurrenceType.fromKey(series.recurrenceType)
        val interval = series.recurrenceInterval.coerceAtLeast(1)
        val maxOcc = series.maxOccurrences?.takeIf { it > 0 }

        return when (type) {
            RecurrenceType.NONE -> {
                if (!startDate.isBefore(rangeStart) && !startDate.isAfter(effectiveEnd)) {
                    listOf(startDate)
                } else {
                    emptyList()
                }
            }

            RecurrenceType.DAILY -> {
                val list = mutableListOf<LocalDate>()
                val daysBeforeRange = ChronoUnit.DAYS.between(startDate, rangeStart).coerceAtLeast(0)
                val firstStep = if (daysBeforeRange == 0L) 0L else ((daysBeforeRange + interval - 1) / interval)
                var step = firstStep
                while (true) {
                    if (maxOcc != null && step >= maxOcc) break
                    val candidate = startDate.plusDays(step * interval)
                    if (candidate.isAfter(effectiveEnd)) break
                    if (!candidate.isBefore(rangeStart)) list += candidate
                    step++
                }
                list
            }

            RecurrenceType.WEEKLY -> {
                val mask = if (series.daysOfWeekMask == 0) {
                    weekdayBit(startDate.dayOfWeek)
                } else {
                    series.daysOfWeekMask
                }
                val startWeekMonday = startDate.with(DayOfWeek.MONDAY)
                val list = mutableListOf<LocalDate>()
                if (maxOcc == null) {
                    // O(1) jump straight to rangeStart while preserving week-interval alignment
                    var cursor = if (rangeStart.isAfter(startDate)) rangeStart else startDate
                    while (!cursor.isAfter(effectiveEnd)) {
                        val weeksBetween = ChronoUnit.WEEKS.between(startWeekMonday, cursor.with(DayOfWeek.MONDAY))
                        val matchesWeek = weeksBetween >= 0 && (weeksBetween % interval.toLong() == 0L)
                        if (matchesWeek && (mask and weekdayBit(cursor.dayOfWeek)) != 0) {
                            list += cursor
                        }
                        cursor = cursor.plusDays(1)
                    }
                } else {
                    var count = 0
                    var cursor = startDate
                    while (!cursor.isAfter(effectiveEnd)) {
                        val weeksBetween = ChronoUnit.WEEKS.between(startWeekMonday, cursor.with(DayOfWeek.MONDAY))
                        val matchesWeek = weeksBetween >= 0 && (weeksBetween % interval.toLong() == 0L)
                        val matchesDay = (mask and weekdayBit(cursor.dayOfWeek)) != 0
                        if (matchesWeek && matchesDay) {
                            count++
                            if (count > maxOcc) break
                            if (!cursor.isBefore(rangeStart)) {
                                list += cursor
                            }
                        }
                        cursor = cursor.plusDays(1)
                    }
                }
                list
            }

            RecurrenceType.MONTHLY -> {
                val list = mutableListOf<LocalDate>()
                val anchorDay = startDate.dayOfMonth
                val startYm = YearMonth.from(startDate)
                val monthsBeforeRange = if (maxOcc == null && rangeStart.isAfter(startDate)) {
                    ChronoUnit.MONTHS.between(startYm, YearMonth.from(rangeStart)).coerceAtLeast(0)
                } else {
                    0L
                }
                var step = if (monthsBeforeRange > 0L) (monthsBeforeRange / interval) else 0L
                while (true) {
                    if (maxOcc != null && step >= maxOcc) break
                    val ym = startYm.plusMonths(step * interval)
                    val day = anchorDay.coerceAtMost(ym.lengthOfMonth())
                    val candidate = ym.atDay(day)
                    if (candidate.isAfter(effectiveEnd)) break
                    if (!candidate.isBefore(rangeStart)) list += candidate
                    step++
                }
                list
            }

            RecurrenceType.ROTATING_CYCLE -> {
                val workDays = series.cycleWorkDays.coerceAtLeast(1)
                val offDays = series.cycleOffDays.coerceAtLeast(0)
                val cycleLen = (workDays + offDays).toLong()
                val list = mutableListOf<LocalDate>()

                if (maxOcc == null) {
                    var cursor = if (rangeStart.isAfter(startDate)) rangeStart else startDate
                    while (!cursor.isAfter(effectiveEnd)) {
                        val offset = ChronoUnit.DAYS.between(startDate, cursor)
                        val dayInCycle = (offset % cycleLen).toInt()
                        if (dayInCycle < workDays) {
                            list += cursor
                        }
                        cursor = cursor.plusDays(1)
                    }
                } else {
                    var count = 0
                    var cursor = startDate
                    while (!cursor.isAfter(effectiveEnd) && count < maxOcc) {
                        val offset = ChronoUnit.DAYS.between(startDate, cursor)
                        val dayInCycle = (offset % cycleLen).toInt()
                        if (dayInCycle < workDays) {
                            count++
                            if (!cursor.isBefore(rangeStart)) {
                                list += cursor
                            }
                        }
                        cursor = cursor.plusDays(1)
                    }
                }
                list
            }
        }
    }
}
