package com.example.domain

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftOverrideEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.OvertimeType
import com.example.data.model.StatsPeriod
import com.example.i18n.AppStrings
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Native Android PdfDocument generator producing a crisp, printable multi-page A4
 * monthly summary of shifts, regular hours, 0.5h overtime by category, and allowances.
 */
object PdfExportService {

    private const val PAGE_WIDTH = 595   // A4 @ 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    internal interface PdfPageSink {
        fun startPage(width: Int, height: Int, pageNumber: Int): Canvas
        fun finishPage()
    }

    fun generateMonthlyReport(
        month: YearMonth,
        seriesList: List<ShiftSeriesEntity>,
        overrides: List<ShiftOverrideEntity>,
        overtimeEntries: List<OvertimeEntryEntity>,
        allowanceEntries: List<AllowanceEntryEntity>,
        strings: AppStrings,
        outputStream: OutputStream
    ) {
        val pdf = PdfDocument()
        try {
            var currentPage: PdfDocument.Page? = null
            val sink = object : PdfPageSink {
                override fun startPage(width: Int, height: Int, pageNumber: Int): Canvas {
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
                    currentPage = page
                    return page.canvas
                }

                override fun finishPage() {
                    currentPage?.let { pdf.finishPage(it) }
                    currentPage = null
                }
            }
            renderMonthlyReport(
                month = month,
                seriesList = seriesList,
                overrides = overrides,
                overtimeEntries = overtimeEntries,
                allowanceEntries = allowanceEntries,
                strings = strings,
                sink = sink
            )
            pdf.writeTo(outputStream)
        } finally {
            pdf.close()
        }
    }

    internal fun renderMonthlyReport(
        month: YearMonth,
        seriesList: List<ShiftSeriesEntity>,
        overrides: List<ShiftOverrideEntity>,
        overtimeEntries: List<OvertimeEntryEntity>,
        allowanceEntries: List<AllowanceEntryEntity>,
        strings: AppStrings,
        sink: PdfPageSink
    ): Int {
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()
        val expandedShifts = RecurrenceEngine.expandRange(seriesList, overrides, startDate, endDate)
        val periodOvertime = RecurrenceEngine.effectiveOvertime(expandedShifts, overtimeEntries, startDate, endDate)
        val periodAllowances = RecurrenceEngine.effectiveAllowances(expandedShifts, allowanceEntries, startDate, endDate)
        val stats = StatsCalculator.calculateFromExpanded(
            period = StatsPeriod.MONTH,
            startDate = startDate,
            endDate = endDate,
            shifts = expandedShifts,
            periodOvertime = periodOvertime,
            periodAllowances = periodAllowances
        )
        val shiftsByDate = expandedShifts.groupBy { it.date }
        val overtimeByDate = periodOvertime.groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }
        val allowancesByDate = periodAllowances.groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }

        var pageNum = 1
        var canvas = sink.startPage(PAGE_WIDTH, PAGE_HEIGHT, pageNum)
        var y = drawHeaderAndSummary(canvas, month, stats, strings)

            y = drawTableHeader(canvas, y, strings)
            val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E2433")
                textSize = 9.2f
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#5C6478")
                textSize = 8.6f
            }
            val stripePaint = Paint().apply { color = Color.parseColor("#F4F6FB") }
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#DCE2F0")
                strokeWidth = 0.8f
            }
            val dayFmt = DateTimeFormatter.ofPattern("dd EEE", strings.javaLocale)

            val activeDays = (1..month.lengthOfMonth())
                .map { month.atDay(it) }
                .filter { d ->
                    !shiftsByDate[d].isNullOrEmpty() ||
                        !overtimeByDate[d].isNullOrEmpty() ||
                        !allowancesByDate[d].isNullOrEmpty()
                }

            activeDays.forEachIndexed { idx, date ->
                val dayShifts = shiftsByDate[date].orEmpty()
                val dayOt = overtimeByDate[date].orEmpty()
                val dayAll = allowancesByDate[date].orEmpty()

                val shiftSummary = if (dayShifts.isEmpty()) "—" else dayShifts.joinToString("; ") {
                    "${it.title} (${it.formattedTimeRange()})"
                }
                val otSummary = if (dayOt.isEmpty()) "—" else dayOt.joinToString(", ") {
                    val t = OvertimeType.fromKey(it.overtimeType)
                    "${t.abbr(strings.language)} ${strings.formatHalfHours(it.halfHours)}"
                }
                val allSummary = if (dayAll.isEmpty()) "—" else dayAll.joinToString(", ") {
                    if (it.quantity > 1) "${it.label} x${it.quantity}" else it.label
                }
                val notes = buildList {
                    dayShifts.mapNotNull { it.notes.takeIf(String::isNotBlank) }.forEach(::add)
                    dayOt.mapNotNull { it.notes.takeIf(String::isNotBlank) }.forEach(::add)
                    dayAll.mapNotNull { it.notes.takeIf(String::isNotBlank) }.forEach(::add)
                }.joinToString(" · ").ifBlank { "—" }

                val rowHeight = 22f
                if (y + rowHeight > PAGE_HEIGHT - MARGIN) {
                    drawFooter(canvas, pageNum, strings)
                    sink.finishPage()
                    pageNum++
                    canvas = sink.startPage(PAGE_WIDTH, PAGE_HEIGHT, pageNum)
                    y = MARGIN
                    y = drawTableHeader(canvas, y, strings)
                }

                if (idx % 2 == 1) {
                    canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, stripePaint)
                }

                val textY = y + 14.5f
                canvas.drawText(date.format(dayFmt), MARGIN + 4f, textY, rowPaint)
                canvas.drawText(ellipsize(shiftSummary, 27), MARGIN + 60f, textY, rowPaint)
                canvas.drawText(ellipsize(otSummary, 18), MARGIN + 224f, textY, rowPaint)
                canvas.drawText(ellipsize(allSummary, 20), MARGIN + 336f, textY, rowPaint)
                canvas.drawText(ellipsize(notes, 18), MARGIN + 448f, textY, mutedPaint)

                canvas.drawLine(MARGIN, y + rowHeight, PAGE_WIDTH - MARGIN, y + rowHeight, dividerPaint)
                y += rowHeight
            }

            if (activeDays.isEmpty()) {
                canvas.drawText(strings.noShiftsInPeriod, MARGIN + 8f, y + 20f, mutedPaint)
            }

            drawFooter(canvas, pageNum, strings)
            sink.finishPage()
            return pageNum
        }

    private fun drawHeaderAndSummary(
        canvas: Canvas,
        month: YearMonth,
        stats: com.example.data.model.StatsSummary,
        strings: AppStrings
    ): Float {
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E3C84") }
        canvas.drawRoundRect(
            RectF(MARGIN, MARGIN, PAGE_WIDTH - MARGIN, MARGIN + 68f),
            12f,
            12f,
            bannerPaint
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D5E1FF")
            textSize = 11f
        }
        canvas.drawText("${strings.appName} — ${strings.pdfReportTitle}", MARGIN + 16f, MARGIN + 28f, titlePaint)
        canvas.drawText(
            "${strings.formatMonthYear(month)}  ·  ${strings.pdfGeneratedOn} ${strings.formatShortDate(LocalDate.now())}",
            MARGIN + 16f,
            MARGIN + 50f,
            subtitlePaint
        )

        var y = MARGIN + 82f
        val boxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F0F4FC") }
        val boxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D0DAF0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val kpiLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B5568")
            textSize = 9f
        }
        val kpiValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#151B28")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val boxGap = 10f
        val boxW = (PAGE_WIDTH - MARGIN * 2 - boxGap * 3) / 4f
        val kpis = listOf(
            strings.workedDaysKpi to "${stats.workedDays}",
            strings.regularHoursKpi to strings.formatHoursFromMinutes(stats.totalRegularMinutes),
            strings.totalOvertimeKpi to strings.formatHalfHours(stats.totalOvertimeHalfHours),
            strings.totalAllowancesKpi to "${stats.totalAllowancesCount}"
        )
        kpis.forEachIndexed { i, (label, value) ->
            val left = MARGIN + i * (boxW + boxGap)
            val rect = RectF(left, y, left + boxW, y + 48f)
            canvas.drawRoundRect(rect, 8f, 8f, boxBg)
            canvas.drawRoundRect(rect, 8f, 8f, boxStroke)
            canvas.drawText(label, left + 10f, y + 18f, kpiLabelPaint)
            canvas.drawText(value, left + 10f, y + 38f, kpiValPaint)
        }
        y += 60f

        // Overtime breakdown strip
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3C84")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A3142")
            textSize = 9.5f
        }
        canvas.drawText(strings.overtimeBreakdownTitle, MARGIN, y, sectionPaint)
        y += 14f
        OvertimeType.entries.chunked(3).forEach { rowTypes ->
            val otLine = rowTypes.joinToString("   |   ") { type ->
                val half = stats.overtimeByTypeHalfHours[type] ?: 0
                "${type.label(strings.language)} (${type.abbr(strings.language)}): ${strings.formatHalfHours(half)}"
            }
            canvas.drawText(otLine, MARGIN, y, detailPaint)
            y += 14f
        }
        y += 4f

        if (stats.allowanceAggregates.isNotEmpty()) {
            canvas.drawText(strings.allowancesSummaryTitle, MARGIN, y, sectionPaint)
            y += 14f
            val allLine = stats.allowanceAggregates.joinToString("   ·   ") {
                "${it.label}: ${it.totalQuantity}"
            }
            canvas.drawText(ellipsize(allLine, 92), MARGIN, y, detailPaint)
            y += 18f
        }

        return y + 4f
    }

    private fun drawTableHeader(canvas: Canvas, y: Float, strings: AppStrings): Float {
        val headerBg = Paint().apply { color = Color.parseColor("#E3E8F6") }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A233A")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, headerBg)
        val ty = y + 14.5f
        canvas.drawText(strings.pdfColDate, MARGIN + 4f, ty, headerText)
        canvas.drawText(strings.pdfColShift, MARGIN + 60f, ty, headerText)
        canvas.drawText(strings.pdfColOvertime, MARGIN + 224f, ty, headerText)
        canvas.drawText(strings.pdfColAllowances, MARGIN + 336f, ty, headerText)
        canvas.drawText(strings.pdfColNotes, MARGIN + 448f, ty, headerText)
        return y + 22f
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int, strings: AppStrings) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#757E94")
            textSize = 8.5f
        }
        canvas.drawText("${strings.appName} · Privacy-First Shift Manager", MARGIN, PAGE_HEIGHT - 16f, footerPaint)
        canvas.drawText("Pag. $pageNum", PAGE_WIDTH - MARGIN - 36f, PAGE_HEIGHT - 16f, footerPaint)
    }

    private fun ellipsize(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars - 1) + "…"
}
