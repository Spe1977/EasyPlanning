package com.example

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import android.graphics.Canvas
import android.graphics.Paint
import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AllowanceDraft
import com.example.data.model.AllowancePreset
import com.example.data.model.AppLanguage
import com.example.data.model.OvertimeDraft
import com.example.data.model.OvertimeType
import com.example.data.model.RecurrenceType
import com.example.data.model.ShiftExtrasCodec
import com.example.data.local.EasyPlanningDatabase
import com.example.domain.PdfExportService
import com.example.i18n.stringsFor
import com.example.notifications.BootRescheduleReceiver
import com.example.notifications.ShiftReminderReceiver
import com.example.notifications.ShiftReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EasyPlanningNotificationsPdfTest {

    private lateinit var app: Application

    private class RecordingPdfSink : PdfExportService.PdfPageSink {
        val pages = mutableListOf<MutableList<String>>()
        var finishedPages = 0

        override fun startPage(width: Int, height: Int, pageNumber: Int): Canvas {
            assertEquals(595, width)
            assertEquals(842, height)
            val lines = mutableListOf<String>()
            pages.add(lines)
            return object : Canvas() {
                override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                    lines.add(text)
                }
            }
        }

        override fun finishPage() {
            finishedPages++
        }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun pdfExportServiceGeneratesMultiPageReportInItalianAndEnglish() {
        val month = YearMonth.of(2026, 10) // 31 days -> forces multi-page A4 table pagination
        val series = listOf(
            ShiftSeriesEntity(
                id = 1L,
                title = "Mattina Operativa Reparto A",
                startDateEpochDay = month.atDay(1).toEpochDay(),
                startMinuteOfDay = 6 * 60,
                endMinuteOfDay = 14 * 60,
                notes = "Nota dettagliata giornaliera molto lunga per verificare il troncamento pulito",
                recurrenceType = RecurrenceType.DAILY.key,
                recurrenceInterval = 1,
                endDateEpochDay = month.atEndOfMonth().toEpochDay(),
                defaultOvertimeJson = ShiftExtrasCodec.encodeOvertime(
                    listOf(OvertimeDraft(OvertimeType.SCHEDULED.key, halfHours = 2, notes = "Prog"))
                ),
                defaultAllowancesJson = ShiftExtrasCodec.encodeAllowances(
                    listOf(AllowanceDraft(AllowancePreset.MEAL_VOUCHER.key, "Buono Pasto", quantity = 1))
                )
            )
        )
        val overtime = listOf(
            OvertimeEntryEntity(dateEpochDay = month.atDay(2).toEpochDay(), overtimeType = OvertimeType.WEEKDAY.key, halfHours = 3),
            OvertimeEntryEntity(dateEpochDay = month.atDay(3).toEpochDay(), overtimeType = OvertimeType.HOLIDAY.key, halfHours = 4),
            OvertimeEntryEntity(dateEpochDay = month.atDay(4).toEpochDay(), overtimeType = OvertimeType.NIGHT.key, halfHours = 2),
            OvertimeEntryEntity(dateEpochDay = month.atDay(5).toEpochDay(), overtimeType = OvertimeType.NIGHT_HOLIDAY.key, halfHours = 5)
        )
        val allowances = listOf(
            AllowanceEntryEntity(dateEpochDay = month.atDay(6).toEpochDay(), presetKey = "CUSTOM", label = "Reperibilità", quantity = 2)
        )

        val itSink = RecordingPdfSink()
        val itPages = PdfExportService.renderMonthlyReport(
            month = month,
            seriesList = series,
            overrides = emptyList(),
            overtimeEntries = overtime,
            allowanceEntries = allowances,
            strings = stringsFor(AppLanguage.ITALIAN),
            sink = itSink
        )
        assertTrue("31-day busy month must paginate across >= 2 A4 pages", itPages >= 2)
        assertEquals(itPages, itSink.finishedPages)
        val itAllText = itSink.pages.flatten()
        assertTrue(itAllText.any { it.contains("Programmato (PROG)") })
        assertTrue(itAllText.any { it.contains("Buono Pasto: 31") })
        assertTrue(itAllText.any { it.contains("Pag. 1") })
        assertTrue(itAllText.any { it.contains("Pag. 2") })
        assertTrue("Long notes must ellipsize cleanly", itAllText.any { it.endsWith("…") })

        val enSink = RecordingPdfSink()
        val enPages = PdfExportService.renderMonthlyReport(
            month = month,
            seriesList = series,
            overrides = emptyList(),
            overtimeEntries = overtime,
            allowanceEntries = allowances,
            strings = stringsFor(AppLanguage.ENGLISH),
            sink = enSink
        )
        assertTrue(enPages >= 2)
        assertTrue(enSink.pages.flatten().any { it.contains("Scheduled (SCH)") })
    }

    @Test
    fun pdfExportServiceHandlesEmptyMonthGracefully() {
        val sink = RecordingPdfSink()
        val pages = PdfExportService.renderMonthlyReport(
            month = YearMonth.of(2026, 2),
            seriesList = emptyList(),
            overrides = emptyList(),
            overtimeEntries = emptyList(),
            allowanceEntries = emptyList(),
            strings = stringsFor(AppLanguage.ITALIAN),
            sink = sink
        )
        assertEquals(1, pages)
        assertEquals(1, sink.finishedPages)
        assertTrue(sink.pages.first().any { it == stringsFor(AppLanguage.ITALIAN).noShiftsInPeriod })
    }

    @Test
    fun shiftReminderSchedulerSchedulesUpcomingSkipsPastDisabledAndOutOfWindowAndCancels() {
        val scheduler = ShiftReminderScheduler(app)
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        val today = LocalDate.now()

        val series = listOf(
            // Valid upcoming shift (in 2 days, 30m reminder)
            ShiftSeriesEntity(
                id = 11L,
                title = "Mattina Prossima",
                startDateEpochDay = today.plusDays(2).toEpochDay(),
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 16 * 60,
                reminderMinutesBefore = 30,
                recurrenceType = RecurrenceType.NONE.key
            ),
            // Past shift (should be skipped)
            ShiftSeriesEntity(
                id = 12L,
                title = "Turno Passato",
                startDateEpochDay = today.minusDays(2).toEpochDay(),
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 16 * 60,
                reminderMinutesBefore = 30,
                recurrenceType = RecurrenceType.NONE.key
            ),
            // Disabled reminder (-1, should be skipped)
            ShiftSeriesEntity(
                id = 13L,
                title = "Senza Promemoria",
                startDateEpochDay = today.plusDays(3).toEpochDay(),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 17 * 60,
                reminderMinutesBefore = -1,
                recurrenceType = RecurrenceType.NONE.key
            ),
            // Beyond 30-day rolling window (should be skipped)
            ShiftSeriesEntity(
                id = 14L,
                title = "Oltre 30 Giorni",
                startDateEpochDay = today.plusDays(45).toEpochDay(),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 17 * 60,
                reminderMinutesBefore = 30,
                recurrenceType = RecurrenceType.NONE.key
            )
        )

        scheduler.rescheduleAll(series, emptyList())
        assertEquals(1, shadowAlarm.scheduledAlarms.size)

        // Rescheduling with empty list must cancel previously scheduled alarms
        scheduler.rescheduleAll(emptyList(), emptyList())
        assertEquals(0, shadowAlarm.scheduledAlarms.size)
    }

    @Test
    fun shiftReminderReceiverRespectsAndroid13PermissionAndPostsMonochromeNotification() {
        val receiver = ShiftReminderReceiver()
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotification = Shadows.shadowOf(notificationManager)

        val intent = Intent(app, ShiftReminderReceiver::class.java).apply {
            putExtra(ShiftReminderScheduler.EXTRA_TITLE, "Mattina")
            putExtra(ShiftReminderScheduler.EXTRA_TIME_RANGE, "06:00 – 14:00")
            putExtra(ShiftReminderScheduler.EXTRA_NOTES, "Reparto Nord")
            putExtra(ShiftReminderScheduler.EXTRA_NOTIFICATION_ID, 4242)
        }

        // Without POST_NOTIFICATIONS on Android 13+, receiver safely does nothing
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        receiver.onReceive(app, intent)
        assertTrue(shadowNotification.allNotifications.isEmpty())

        // With POST_NOTIFICATIONS granted, notification is posted with monochrome vector icon
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        receiver.onReceive(app, intent)
        assertEquals(1, shadowNotification.allNotifications.size)
        val posted = shadowNotification.allNotifications.single()
        assertEquals(ShiftReminderScheduler.CHANNEL_ID, posted.channelId)
        assertEquals(R.drawable.ic_notification_shift, posted.smallIcon.resId)
        assertEquals("Mattina", posted.extras.getString("android.title"))
        assertEquals("06:00 – 14:00 · Reparto Nord", posted.extras.getString("android.text"))
    }

    @After
    fun tearDown() {
        runBlocking {
            EasyPlanningDatabase.getInstance(app).dao().wipeAllTables()
        }
        EasyPlanningDatabase.resetForTesting()
    }

    @Test
    fun bootRescheduleReceiverReschedulesPersistedShiftsAndIgnoresUnrelatedActions() = runBlocking {
        val dao = EasyPlanningDatabase.getInstance(app).dao()
        dao.wipeAllTables()
        val tomorrow = LocalDate.now().plusDays(2)
        dao.insertSeries(
            ShiftSeriesEntity(
                title = "Turno Dopo Riavvio",
                startDateEpochDay = tomorrow.toEpochDay(),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 17 * 60,
                reminderMinutesBefore = 30,
                recurrenceType = RecurrenceType.NONE.key
            )
        )

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        val receiver = BootRescheduleReceiver()

        // Unrelated broadcast action must be ignored safely
        receiver.onReceive(app, Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        assertEquals(0, shadowAlarm.scheduledAlarms.size)

        // ACTION_BOOT_COMPLETED must reload persisted shifts and schedule the upcoming alarm
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        withTimeout(5_000L) {
            while (shadowAlarm.scheduledAlarms.isEmpty()) {
                delay(25)
            }
        }
        assertEquals(1, shadowAlarm.scheduledAlarms.size)
    }
}
