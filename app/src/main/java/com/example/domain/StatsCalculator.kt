package com.example.domain

import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftOverrideEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AllowanceAggregate
import com.example.data.model.OvertimeType
import com.example.data.model.ShiftDistributionItem
import com.example.data.model.ShiftInstance
import com.example.data.model.StatsPeriod
import com.example.data.model.StatsSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

object StatsCalculator {

    fun periodBounds(anchorDate: LocalDate, period: StatsPeriod): Pair<LocalDate, LocalDate> {
        return when (period) {
            StatsPeriod.WEEK -> {
                val start = anchorDate.with(DayOfWeek.MONDAY)
                start to start.plusDays(6)
            }
            StatsPeriod.MONTH -> {
                val ym = YearMonth.from(anchorDate)
                ym.atDay(1) to ym.atEndOfMonth()
            }
            StatsPeriod.YEAR -> {
                LocalDate.of(anchorDate.year, 1, 1) to LocalDate.of(anchorDate.year, 12, 31)
            }
        }
    }

    fun calculate(
        seriesList: List<ShiftSeriesEntity>,
        overrides: List<ShiftOverrideEntity>,
        overtimeEntries: List<OvertimeEntryEntity>,
        allowanceEntries: List<AllowanceEntryEntity>,
        anchorDate: LocalDate,
        period: StatsPeriod
    ): StatsSummary {
        val (startDate, endDate) = periodBounds(anchorDate, period)
        val shifts = RecurrenceEngine.expandRange(seriesList, overrides, startDate, endDate)
        val periodOvertime = RecurrenceEngine.effectiveOvertime(shifts, overtimeEntries, startDate, endDate)
        val periodAllowances = RecurrenceEngine.effectiveAllowances(shifts, allowanceEntries, startDate, endDate)
        return calculateFromExpanded(
            period = period,
            startDate = startDate,
            endDate = endDate,
            shifts = shifts,
            periodOvertime = periodOvertime,
            periodAllowances = periodAllowances
        )
    }

    fun calculateFromExpanded(
        period: StatsPeriod,
        startDate: LocalDate,
        endDate: LocalDate,
        shifts: List<ShiftInstance>,
        periodOvertime: List<OvertimeEntryEntity>,
        periodAllowances: List<AllowanceEntryEntity>
    ): StatsSummary {
        val workedDays = shifts.mapTo(HashSet()) { it.date }.size
        val totalRegularMinutes = shifts.sumOf { it.durationMinutes }

        // Single-pass overtime aggregation across all categories
        val overtimeMap = LinkedHashMap<OvertimeType, Int>(OvertimeType.entries.size).apply {
            OvertimeType.entries.forEach { put(it, 0) }
        }
        var totalOvertimeHalfHours = 0
        periodOvertime.forEach { entry ->
            val type = OvertimeType.fromKey(entry.overtimeType)
            overtimeMap[type] = (overtimeMap[type] ?: 0) + entry.halfHours
            totalOvertimeHalfHours += entry.halfHours
        }

        val shiftDistribution = shifts
            .groupBy { it.title to it.colorId }
            .map { (key, instances) ->
                ShiftDistributionItem(
                    title = key.first,
                    colorId = key.second,
                    count = instances.size,
                    totalMinutes = instances.sumOf { it.durationMinutes }
                )
            }
            .sortedByDescending { it.count }

        val allowanceAggregates = periodAllowances
            .groupBy { it.label.trim() }
            .map { (label, entries) ->
                AllowanceAggregate(
                    label = label,
                    totalQuantity = entries.sumOf { it.quantity }
                )
            }
            .sortedByDescending { it.totalQuantity }

        return StatsSummary(
            period = period,
            startDate = startDate,
            endDate = endDate,
            workedDays = workedDays,
            totalRegularMinutes = totalRegularMinutes,
            totalOvertimeHalfHours = totalOvertimeHalfHours,
            overtimeByTypeHalfHours = overtimeMap,
            shiftDistribution = shiftDistribution,
            allowanceAggregates = allowanceAggregates,
            totalAllowancesCount = periodAllowances.sumOf { it.quantity }
        )
    }
}
