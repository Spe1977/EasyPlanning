package com.example

import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftOverrideEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.OvertimeType
import com.example.data.model.RecurrenceType
import com.example.data.model.StatsPeriod
import com.example.domain.RecurrenceEngine
import com.example.domain.StatsCalculator
import com.example.security.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class EasyPlanningCoreTest {

    @Test
    fun rotatingCycleExpandsCorrectlyWithSingleOverrideAndDeletion() {
        val start = LocalDate.of(2026, 3, 1)
        val series = ShiftSeriesEntity(
            id = 10L,
            title = "Notte",
            startDateEpochDay = start.toEpochDay(),
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 6 * 60,
            colorId = 4,
            recurrenceType = RecurrenceType.ROTATING_CYCLE.key,
            cycleWorkDays = 4,
            cycleOffDays = 2
        )
        val overrides = listOf(
            // Delete day 2
            ShiftOverrideEntity(seriesId = 10L, occurrenceEpochDay = start.plusDays(1).toEpochDay(), isDeleted = true),
            // Modify day 3 title
            ShiftOverrideEntity(seriesId = 10L, occurrenceEpochDay = start.plusDays(2).toEpochDay(), title = "Notte Speciale")
        )

        // 12 days -> two 6-day cycles -> 4 work + 4 work = 8 raw occurrences minus 1 deleted = 7
        val expanded = RecurrenceEngine.expandRange(
            seriesList = listOf(series),
            overrides = overrides,
            rangeStart = start,
            rangeEnd = start.plusDays(11)
        )
        assertEquals(7, expanded.size)
        assertTrue(expanded.none { it.date == start.plusDays(1) })
        val modified = expanded.first { it.date == start.plusDays(2) }
        assertEquals("Notte Speciale", modified.title)
        assertEquals(8 * 60, modified.durationMinutes)
        assertTrue(modified.isOvernight)
    }

    @Test
    fun weeklyAndMonthlyRecurrenceClampDatesProperly() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val monthlySeries = ShiftSeriesEntity(
            id = 1L,
            title = "Chiusura Mese",
            startDateEpochDay = jan31.toEpochDay(),
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            recurrenceType = RecurrenceType.MONTHLY.key,
            recurrenceInterval = 1,
            maxOccurrences = 3
        )
        val monthly = RecurrenceEngine.expandRange(
            listOf(monthlySeries),
            emptyList(),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
            monthly.map { it.date }
        )

        val mon = LocalDate.of(2026, 4, 6)
        val mask = RecurrenceEngine.weekdayBit(DayOfWeek.MONDAY) or RecurrenceEngine.weekdayBit(DayOfWeek.WEDNESDAY)
        val weeklySeries = ShiftSeriesEntity(
            id = 2L,
            title = "Mattina",
            startDateEpochDay = mon.toEpochDay(),
            startMinuteOfDay = 6 * 60,
            endMinuteOfDay = 14 * 60,
            recurrenceType = RecurrenceType.WEEKLY.key,
            recurrenceInterval = 1,
            daysOfWeekMask = mask
        )
        val weekly = RecurrenceEngine.expandRange(
            listOf(weeklySeries),
            emptyList(),
            mon,
            mon.plusDays(13)
        )
        assertEquals(4, weekly.size)
    }

    @Test
    fun statsCalculatorAggregatesOvertimeHalfHoursAndAllowances() {
        val day1 = LocalDate.of(2026, 5, 4)
        val day2 = LocalDate.of(2026, 5, 5)
        val series = listOf(
            ShiftSeriesEntity(
                id = 1L,
                title = "Mattina",
                startDateEpochDay = day1.toEpochDay(),
                startMinuteOfDay = 6 * 60,
                endMinuteOfDay = 14 * 60,
                recurrenceType = RecurrenceType.DAILY.key,
                maxOccurrences = 2
            )
        )
        val overtime = listOf(
            OvertimeEntryEntity(dateEpochDay = day1.toEpochDay(), overtimeType = OvertimeType.WEEKDAY.key, halfHours = 3),
            OvertimeEntryEntity(dateEpochDay = day2.toEpochDay(), overtimeType = OvertimeType.NIGHT_HOLIDAY.key, halfHours = 5)
        )
        val allowances = listOf(
            AllowanceEntryEntity(dateEpochDay = day1.toEpochDay(), presetKey = "MEAL_VOUCHER", label = "Buono Pasto", quantity = 1),
            AllowanceEntryEntity(dateEpochDay = day2.toEpochDay(), presetKey = "MEAL_VOUCHER", label = "Buono Pasto", quantity = 1),
            AllowanceEntryEntity(dateEpochDay = day2.toEpochDay(), presetKey = "CUSTOM", label = "Reperibilità", quantity = 2)
        )

        val stats = StatsCalculator.calculate(
            seriesList = series,
            overrides = emptyList(),
            overtimeEntries = overtime,
            allowanceEntries = allowances,
            anchorDate = day1,
            period = StatsPeriod.MONTH
        )

        assertEquals(2, stats.workedDays)
        assertEquals(16 * 60, stats.totalRegularMinutes)
        assertEquals(8, stats.totalOvertimeHalfHours) // 4.0h total
        assertEquals(3, stats.overtimeByTypeHalfHours[OvertimeType.WEEKDAY])
        assertEquals(5, stats.overtimeByTypeHalfHours[OvertimeType.NIGHT_HOLIDAY])
        assertEquals(4, stats.totalAllowancesCount)
    }

    @Test
    fun backupAesGcmEncryptionRoundTripAndTamperRejection() {
        val payload = """{"schemaVersion":1,"appName":"EasyPlanning","note":"Turno segreto"}""".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoManager.encryptBackup(payload, "SuperSicura2026!".toCharArray())
        val decrypted = CryptoManager.decryptBackup(encrypted, "SuperSicura2026!".toCharArray())
        assertArrayEquals(payload, decrypted)

        // Wrong password must fail GCM authentication
        assertThrows(Exception::class.java) {
            CryptoManager.decryptBackup(encrypted, "PasswordSbagliata".toCharArray())
        }

        // Tampering with even 1 byte must fail GCM authentication
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0x01).toByte()
        assertThrows(Exception::class.java) {
            CryptoManager.decryptBackup(tampered, "SuperSicura2026!".toCharArray())
        }
    }

    @Test
    fun passwordStrengthAndAuthProviderMappingWork() {
        assertTrue(com.example.ui.screens.calculatePasswordStrength("abc") < 0.45f)
        assertTrue(com.example.ui.screens.calculatePasswordStrength("Turni2026!Sicuri") >= 0.85f)
        assertEquals(
            com.example.data.model.AuthProvider.HYBRID,
            com.example.data.model.AuthProvider.fromKey("hybrid")
        )
        assertEquals(
            com.example.data.model.AuthProvider.OFFLINE_LOCAL,
            com.example.data.model.AuthProvider.fromKey("offline_local")
        )
    }

    @Test
    fun fiveYearTimelineWindowExpandsAccurately() {
        val anchor = LocalDate.of(2026, 11, 23)
        val rangeStart = anchor.minusMonths(24)
        val rangeEnd = anchor.plusMonths(36)
        val rotatingSeries = ShiftSeriesEntity(
            id = 42L,
            title = "Mattina",
            startDateEpochDay = rangeStart.toEpochDay(),
            startMinuteOfDay = 7 * 60,
            endMinuteOfDay = 13 * 60,
            colorId = 2,
            recurrenceType = RecurrenceType.ROTATING_CYCLE.key,
            cycleWorkDays = 4,
            cycleOffDays = 2
        )
        val expanded = RecurrenceEngine.expandRange(
            seriesList = listOf(rotatingSeries),
            overrides = emptyList(),
            rangeStart = rangeStart,
            rangeEnd = rangeEnd
        )
        // 5 years (~1826 days) with 4-on/2-off cycle produces ~1217 occurrences cleanly
        assertTrue(expanded.size in 1200..1230)
        assertEquals(rangeStart, expanded.first().date)
        assertTrue(!expanded.last().date.isAfter(rangeEnd))
    }

    @Test
    fun scheduledOvertimeAndRecurringExtrasAggregateAndRespectOverrides() {
        val start = LocalDate.of(2026, 6, 1)
        val defaultOt = com.example.data.model.ShiftExtrasCodec.encodeOvertime(
            listOf(com.example.data.model.OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2, notes = "Pianificato"))
        )
        val defaultAll = com.example.data.model.ShiftExtrasCodec.encodeAllowances(
            listOf(com.example.data.model.AllowanceDraft("MEAL_VOUCHER", "Buono Pasto", quantity = 1))
        )
        val morningSeries = ShiftSeriesEntity(
            id = 77L,
            title = "Mattina",
            startDateEpochDay = start.toEpochDay(),
            startMinuteOfDay = 6 * 60,
            endMinuteOfDay = 14 * 60,
            recurrenceType = RecurrenceType.DAILY.key,
            maxOccurrences = 4,
            defaultOvertimeJson = defaultOt,
            defaultAllowancesJson = defaultAll
        )
        // Day 2 canceled (no shift, no meal voucher, no scheduled overtime), Day 3 overrides allowances to empty
        val overrides = listOf(
            ShiftOverrideEntity(seriesId = 77L, occurrenceEpochDay = start.plusDays(1).toEpochDay(), isDeleted = true),
            ShiftOverrideEntity(seriesId = 77L, occurrenceEpochDay = start.plusDays(2).toEpochDay(), customAllowancesJson = "[]")
        )

        val stats = StatsCalculator.calculate(
            seriesList = listOf(morningSeries),
            overrides = overrides,
            overtimeEntries = emptyList(),
            allowanceEntries = emptyList(),
            anchorDate = start,
            period = StatsPeriod.MONTH
        )

        // 4 planned days minus 1 deleted = 3 worked days
        assertEquals(3, stats.workedDays)
        // 3 active days * 2 half-hours (1.0h each) = 6 half-hours of SCHEDULED (PROG) overtime
        assertEquals(6, stats.totalOvertimeHalfHours)
        assertEquals(6, stats.overtimeByTypeHalfHours[OvertimeType.SCHEDULED])
        assertEquals("PROG", OvertimeType.SCHEDULED.abbr(com.example.data.model.AppLanguage.ITALIAN))
        assertEquals("SCH", OvertimeType.SCHEDULED.abbr(com.example.data.model.AppLanguage.ENGLISH))
        // 3 active days minus Day 3 override ("[]") = 2 Meal Vouchers
        assertEquals(2, stats.totalAllowancesCount)
    }

    @Test
    fun weeklyAndMonthlyFastForwardMatchesLongRangeWithoutCountLimit() {
        // Weekly every 2 weeks (Mon + Thu), starting 3+ years before the query range
        val weeklyStart = LocalDate.of(2023, 1, 2) // Monday
        val weeklyMask = RecurrenceEngine.weekdayBit(DayOfWeek.MONDAY) or
            RecurrenceEngine.weekdayBit(DayOfWeek.THURSDAY)
        val weeklySeries = ShiftSeriesEntity(
            id = 81L,
            title = "Bisettimanale",
            startDateEpochDay = weeklyStart.toEpochDay(),
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 16 * 60,
            recurrenceType = RecurrenceType.WEEKLY.key,
            recurrenceInterval = 2,
            daysOfWeekMask = weeklyMask,
            maxOccurrences = null
        )
        val rangeStart = LocalDate.of(2026, 6, 1) // Monday; weeksBetween(2023-01-02, 2026-06-01) = 178 (even -> active week)
        val rangeEnd = LocalDate.of(2026, 6, 21)
        val weeklyResult = RecurrenceEngine.expandRange(listOf(weeklySeries), emptyList(), rangeStart, rangeEnd)
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 18)
            ),
            weeklyResult.map { it.date }
        )

        // Monthly every 2 months from Jan 31, 2023 -> Jan, Mar, May, Jul, Sep, Nov every year
        val monthlySeries = ShiftSeriesEntity(
            id = 82L,
            title = "Bimestrale",
            startDateEpochDay = LocalDate.of(2023, 1, 31).toEpochDay(),
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            recurrenceType = RecurrenceType.MONTHLY.key,
            recurrenceInterval = 2,
            maxOccurrences = null
        )
        val monthlyResult = RecurrenceEngine.expandRange(
            listOf(monthlySeries),
            emptyList(),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 9, 30)
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 9, 30) // clamped from 31 to 30
            ),
            monthlyResult.map { it.date }
        )
    }

    @Test
    fun cryptoManagerZerosPasswordCharArrayEvenOnFailure() {
        val encryptPwd = "PasswordSegreta2026!".toCharArray()
        val encrypted = CryptoManager.encryptBackup("hello".toByteArray(), encryptPwd)
        assertTrue(encryptPwd.all { it == '\u0000' })

        val wrongPwd = "Sbagliata2026!".toCharArray()
        assertThrows(Exception::class.java) {
            CryptoManager.decryptBackup(encrypted, wrongPwd)
        }
        assertTrue(wrongPwd.all { it == '\u0000' })

        val badBlobPwd = "Qualsiasi2026!".toCharArray()
        assertThrows(IllegalArgumentException::class.java) {
            CryptoManager.decryptBackup(byteArrayOf(1, 2, 3), badBlobPwd)
        }
        assertTrue(badBlobPwd.all { it == '\u0000' })
    }

    @Test
    fun overnightShiftAcrossYearBoundaryMultipleShiftsSameDayAndUntilDate() {
        val dec31 = LocalDate.of(2026, 12, 31)
        val jan2 = LocalDate.of(2027, 1, 2)
        val nightSeries = ShiftSeriesEntity(
            id = 91L,
            title = "Notte Capodanno",
            startDateEpochDay = dec31.toEpochDay(),
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 6 * 60,
            recurrenceType = RecurrenceType.DAILY.key,
            recurrenceInterval = 1,
            endDateEpochDay = jan2.toEpochDay()
        )
        val morningExtra = ShiftSeriesEntity(
            id = 92L,
            title = "Mattina Extra",
            startDateEpochDay = LocalDate.of(2027, 1, 1).toEpochDay(),
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 12 * 60,
            recurrenceType = RecurrenceType.NONE.key
        )

        val expanded = RecurrenceEngine.expandRange(
            seriesList = listOf(nightSeries, morningExtra),
            overrides = emptyList(),
            rangeStart = LocalDate.of(2026, 12, 30),
            rangeEnd = LocalDate.of(2027, 1, 5)
        )
        // Dec 31 (night), Jan 1 (morning + night), Jan 2 (night) — stops strictly at UNTIL_DATE Jan 2
        assertEquals(4, expanded.size)
        assertTrue(expanded.none { it.date.isAfter(jan2) })
        val jan1Shifts = expanded.filter { it.date == LocalDate.of(2027, 1, 1) }
        assertEquals(2, jan1Shifts.size)
        // Sorted chronologically by startMinuteOfDay
        assertEquals("Mattina Extra", jan1Shifts[0].title)
        assertEquals("Notte Capodanno", jan1Shifts[1].title)
        assertTrue(jan1Shifts[1].isOvernight)
        assertEquals(8 * 60, jan1Shifts[1].durationMinutes)
    }

    @Test
    fun statsCalculatorHandlesWeekYearAndEmptyPeriods() {
        val anchor = LocalDate.of(2026, 6, 10) // Wednesday of week Jun 8..Jun 14
        val series = listOf(
            ShiftSeriesEntity(
                id = 101L,
                title = "Mattina",
                startDateEpochDay = LocalDate.of(2026, 6, 8).toEpochDay(),
                startMinuteOfDay = 6 * 60,
                endMinuteOfDay = 14 * 60,
                recurrenceType = RecurrenceType.DAILY.key,
                maxOccurrences = 3 // Jun 8, 9, 10
            ),
            ShiftSeriesEntity(
                id = 102L,
                title = "Pomeriggio",
                startDateEpochDay = LocalDate.of(2026, 11, 2).toEpochDay(),
                startMinuteOfDay = 14 * 60,
                endMinuteOfDay = 20 * 60,
                recurrenceType = RecurrenceType.NONE.key
            )
        )

        val weekStats = StatsCalculator.calculate(
            seriesList = series,
            overrides = emptyList(),
            overtimeEntries = emptyList(),
            allowanceEntries = emptyList(),
            anchorDate = anchor,
            period = StatsPeriod.WEEK
        )
        assertEquals(LocalDate.of(2026, 6, 8), weekStats.startDate)
        assertEquals(LocalDate.of(2026, 6, 14), weekStats.endDate)
        assertEquals(3, weekStats.workedDays)
        assertEquals(24 * 60, weekStats.totalRegularMinutes)

        val yearStats = StatsCalculator.calculate(
            seriesList = series,
            overrides = emptyList(),
            overtimeEntries = emptyList(),
            allowanceEntries = emptyList(),
            anchorDate = anchor,
            period = StatsPeriod.YEAR
        )
        assertEquals(LocalDate.of(2026, 1, 1), yearStats.startDate)
        assertEquals(LocalDate.of(2026, 12, 31), yearStats.endDate)
        assertEquals(4, yearStats.workedDays)
        assertEquals(30 * 60, yearStats.totalRegularMinutes)
        assertEquals("Mattina", yearStats.shiftDistribution.first().title)

        val emptyStats = StatsCalculator.calculate(
            seriesList = series,
            overrides = emptyList(),
            overtimeEntries = emptyList(),
            allowanceEntries = emptyList(),
            anchorDate = LocalDate.of(2025, 1, 15),
            period = StatsPeriod.MONTH
        )
        assertEquals(0, emptyStats.workedDays)
        assertEquals(0, emptyStats.totalRegularMinutes)
        assertEquals(0, emptyStats.totalOvertimeHalfHours)
        assertEquals(0, emptyStats.totalAllowancesCount)
    }

    @Test
    fun recurrenceEngineHandlesLeapYearZeroMaskOffDaysInvalidRangeAndFullDayShift() {
        // 1. Invalid range (rangeEnd < rangeStart) returns empty safely
        val jan31Leap = LocalDate.of(2028, 1, 31) // 2028 is a leap year (Feb has 29 days)
        val monthlyLeap = ShiftSeriesEntity(
            id = 201L,
            title = "Mensile Bisestile",
            startDateEpochDay = jan31Leap.toEpochDay(),
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 8 * 60, // Full 24h shift (start == end)
            recurrenceType = RecurrenceType.MONTHLY.key,
            recurrenceInterval = 1,
            maxOccurrences = 3
        )
        assertTrue(
            RecurrenceEngine.expandRange(
                listOf(monthlyLeap),
                emptyList(),
                LocalDate.of(2028, 5, 10),
                LocalDate.of(2028, 5, 1)
            ).isEmpty()
        )
        val leapExpanded = RecurrenceEngine.expandRange(
            listOf(monthlyLeap),
            emptyList(),
            LocalDate.of(2028, 1, 1),
            LocalDate.of(2028, 4, 30)
        )
        assertEquals(
            listOf(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31)),
            leapExpanded.map { it.date }
        )
        assertTrue(leapExpanded.first().isOvernight)
        assertEquals(24 * 60, leapExpanded.first().durationMinutes)

        // 2. Weekly with daysOfWeekMask = 0 falls back to startDate's day of week & honors maxOccurrences
        val wed = LocalDate.of(2026, 7, 1) // Wednesday
        val weeklyFallback = ShiftSeriesEntity(
            id = 202L,
            title = "Settimanale Fallback",
            startDateEpochDay = wed.toEpochDay(),
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            recurrenceType = RecurrenceType.WEEKLY.key,
            recurrenceInterval = 2,
            daysOfWeekMask = 0,
            maxOccurrences = 3
        )
        val weeklyResult = RecurrenceEngine.expandRange(
            listOf(weeklyFallback),
            emptyList(),
            wed,
            wed.plusWeeks(10)
        )
        assertEquals(
            listOf(wed, wed.plusWeeks(2), wed.plusWeeks(4)),
            weeklyResult.map { it.date }
        )

        // 3. Rotating cycle with offDays = 0 and maxOccurrences behaves continuously up to limit
        val cycleSeries = ShiftSeriesEntity(
            id = 203L,
            title = "Ciclo Continuo",
            startDateEpochDay = wed.toEpochDay(),
            startMinuteOfDay = 6 * 60,
            endMinuteOfDay = 12 * 60,
            recurrenceType = RecurrenceType.ROTATING_CYCLE.key,
            cycleWorkDays = 3,
            cycleOffDays = 0,
            maxOccurrences = 5
        )
        val cycleResult = RecurrenceEngine.expandRange(
            listOf(cycleSeries),
            emptyList(),
            wed,
            wed.plusDays(20)
        )
        assertEquals(5, cycleResult.size)
        assertEquals(wed.plusDays(4), cycleResult.last().date)
    }

    @Test
    fun shiftExtrasCodecHandlesMalformedBlankAndRoundTripJsonSafely() {
        assertTrue(com.example.data.model.ShiftExtrasCodec.decodeOvertime(null).isEmpty())
        assertTrue(com.example.data.model.ShiftExtrasCodec.decodeOvertime("   ").isEmpty())
        assertTrue(com.example.data.model.ShiftExtrasCodec.decodeOvertime("{broken json[").isEmpty())
        assertTrue(com.example.data.model.ShiftExtrasCodec.decodeAllowances(null).isEmpty())
        assertTrue(com.example.data.model.ShiftExtrasCodec.decodeAllowances("").isEmpty())
        assertTrue(com.example.data.model.ShiftExtrasCodec.decodeAllowances("not-json").isEmpty())
        assertEquals("", com.example.data.model.ShiftExtrasCodec.encodeOvertime(emptyList()))
        assertEquals("", com.example.data.model.ShiftExtrasCodec.encodeAllowances(emptyList()))

        val ot = listOf(com.example.data.model.OvertimeDraft(OvertimeType.SCHEDULED.key, 3, "Nota"))
        val al = listOf(com.example.data.model.AllowanceDraft("CUSTOM", "Reperibilità", 2, "Weekend"))
        assertEquals(ot, com.example.data.model.ShiftExtrasCodec.decodeOvertime(com.example.data.model.ShiftExtrasCodec.encodeOvertime(ot)))
        assertEquals(al, com.example.data.model.ShiftExtrasCodec.decodeAllowances(com.example.data.model.ShiftExtrasCodec.encodeAllowances(al)))
    }
}
