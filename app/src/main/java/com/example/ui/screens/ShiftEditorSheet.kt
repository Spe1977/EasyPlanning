package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AllowanceDraft
import com.example.data.model.AppLanguage
import com.example.data.model.OvertimeDraft
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderOption
import com.example.data.model.ShiftInstance
import com.example.domain.RecurrenceEngine
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.ShiftColorPalette
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class EditMode {
    NEW_SHIFT,
    EDIT_SINGLE_OCCURRENCE,
    EDIT_ENTIRE_SERIES
}

private enum class EndCondition { NEVER, UNTIL_DATE, AFTER_COUNT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShiftEditorSheet(
    initialDate: LocalDate,
    defaultReminderMinutes: Int,
    editTarget: ShiftInstance?,
    editMode: EditMode,
    initialOvertime: List<OvertimeDraft> = emptyList(),
    initialAllowances: List<AllowanceDraft> = emptyList(),
    initialApplyExtrasToSeries: Boolean = true,
    onDismiss: () -> Unit,
    onSaveSeries: (
        series: ShiftSeriesEntity,
        applyExtrasToSeries: Boolean,
        overtimeDrafts: List<OvertimeDraft>,
        allowanceDrafts: List<AllowanceDraft>
    ) -> Unit,
    onSaveSingleOccurrence: (
        original: ShiftInstance,
        title: String,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        colorId: Int,
        notes: String,
        reminderMinutesBefore: Int,
        overtimeDrafts: List<OvertimeDraft>,
        allowanceDrafts: List<AllowanceDraft>
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val baseSeries = editTarget?.sourceSeries
    val editorKey = remember(editTarget?.seriesId, editTarget?.date, editMode, initialDate) {
        "${editTarget?.seriesId}_${editTarget?.date}_${editMode}_$initialDate"
    }

    var title by remember(editorKey) {
        mutableStateOf(
            when (editMode) {
                EditMode.NEW_SHIFT -> ""
                EditMode.EDIT_SINGLE_OCCURRENCE -> editTarget?.title.orEmpty()
                EditMode.EDIT_ENTIRE_SERIES -> baseSeries?.title.orEmpty()
            }
        )
    }
    var date by remember(editorKey) {
        mutableStateOf(
            when (editMode) {
                EditMode.NEW_SHIFT -> initialDate
                EditMode.EDIT_SINGLE_OCCURRENCE -> editTarget?.date ?: initialDate
                EditMode.EDIT_ENTIRE_SERIES -> baseSeries?.let { LocalDate.ofEpochDay(it.startDateEpochDay) } ?: initialDate
            }
        )
    }
    var startMinute by remember(editorKey) {
        mutableIntStateOf(
            when (editMode) {
                EditMode.NEW_SHIFT -> 8 * 60
                EditMode.EDIT_SINGLE_OCCURRENCE -> editTarget?.startMinuteOfDay ?: (8 * 60)
                EditMode.EDIT_ENTIRE_SERIES -> baseSeries?.startMinuteOfDay ?: (8 * 60)
            }
        )
    }
    var endMinute by remember(editorKey) {
        mutableIntStateOf(
            when (editMode) {
                EditMode.NEW_SHIFT -> 16 * 60
                EditMode.EDIT_SINGLE_OCCURRENCE -> editTarget?.endMinuteOfDay ?: (16 * 60)
                EditMode.EDIT_ENTIRE_SERIES -> baseSeries?.endMinuteOfDay ?: (16 * 60)
            }
        )
    }
    var colorId by remember(editorKey) {
        mutableIntStateOf(
            when (editMode) {
                EditMode.NEW_SHIFT -> 0
                EditMode.EDIT_SINGLE_OCCURRENCE -> editTarget?.colorId ?: 0
                EditMode.EDIT_ENTIRE_SERIES -> baseSeries?.colorId ?: 0
            }
        )
    }
    var notes by remember(editorKey) {
        mutableStateOf(
            when (editMode) {
                EditMode.NEW_SHIFT -> ""
                EditMode.EDIT_SINGLE_OCCURRENCE -> editTarget?.notes.orEmpty()
                EditMode.EDIT_ENTIRE_SERIES -> baseSeries?.notes.orEmpty()
            }
        )
    }
    var reminderMinutes by remember(editorKey) {
        mutableIntStateOf(
            when (editMode) {
                EditMode.NEW_SHIFT -> defaultReminderMinutes
                EditMode.EDIT_SINGLE_OCCURRENCE -> editTarget?.reminderMinutesBefore ?: defaultReminderMinutes
                EditMode.EDIT_ENTIRE_SERIES -> baseSeries?.reminderMinutesBefore ?: defaultReminderMinutes
            }
        )
    }
    var recurrenceType by remember(editorKey) {
        mutableStateOf(
            if (editMode == EditMode.EDIT_ENTIRE_SERIES && baseSeries != null) {
                RecurrenceType.fromKey(baseSeries.recurrenceType)
            } else {
                RecurrenceType.NONE
            }
        )
    }
    var recurrenceInterval by remember(editorKey) {
        mutableIntStateOf(baseSeries?.recurrenceInterval?.coerceAtLeast(1) ?: 1)
    }
    var weekdaysMask by remember(editorKey) {
        mutableIntStateOf(
            baseSeries?.daysOfWeekMask?.takeIf { it != 0 }
                ?: RecurrenceEngine.weekdayBit(date.dayOfWeek)
        )
    }
    var workDays by remember(editorKey) { mutableIntStateOf(baseSeries?.cycleWorkDays ?: 4) }
    var offDays by remember(editorKey) { mutableIntStateOf(baseSeries?.cycleOffDays ?: 2) }
    var endCondition by remember(editorKey) {
        mutableStateOf(
            when {
                baseSeries?.endDateEpochDay != null -> EndCondition.UNTIL_DATE
                baseSeries?.maxOccurrences != null -> EndCondition.AFTER_COUNT
                else -> EndCondition.NEVER
            }
        )
    }
    var endDate by remember(editorKey) {
        mutableStateOf(
            baseSeries?.endDateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: date.plusMonths(3)
        )
    }
    var maxOccurrences by remember(editorKey) { mutableIntStateOf(baseSeries?.maxOccurrences ?: 20) }

    // Inline Overtime & Allowances state (works for both create and edit)
    var overtimeDrafts by remember(editorKey, initialOvertime) { mutableStateOf(initialOvertime) }
    var allowanceDrafts by remember(editorKey, initialAllowances) { mutableStateOf(initialAllowances) }
    var applyExtrasToSeries by remember(editorKey, initialApplyExtrasToSeries) {
        mutableStateOf(initialApplyExtrasToSeries)
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val durationMin = if (endMinute > startMinute) endMinute - startMinute else (24 * 60 - startMinute) + endMinute
    val isOvernight = endMinute <= startMinute

    val quickShiftPresets = remember(strings.language) {
        if (strings.language == AppLanguage.ITALIAN) {
            listOf(
                Triple("Mattina", 6 * 60, 14 * 60),
                Triple("Pomeriggio", 14 * 60, 22 * 60),
                Triple("Notte", 22 * 60, 6 * 60),
                Triple("Giornaliero", 8 * 60, 17 * 60)
            )
        } else {
            listOf(
                Triple("Morning", 6 * 60, 14 * 60),
                Triple("Afternoon", 14 * 60, 22 * 60),
                Triple("Night", 22 * 60, 6 * 60),
                Triple("Full Day", 8 * 60, 17 * 60)
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (editMode == EditMode.NEW_SHIFT) strings.newShiftTitle else strings.editShiftTitle,
                style = MaterialTheme.typography.headlineSmall
            )

            // Quick title & schedule presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickShiftPresets.forEachIndexed { idx, (label, sMin, eMin) ->
                    FilterChip(
                        selected = title == label,
                        onClick = {
                            title = label
                            startMinute = sMin
                            endMinute = eMin
                            colorId = idx
                        },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(strings.shiftNameLabel) },
                placeholder = { Text(strings.shiftNamePlaceholder) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shift_title_input")
            )

            // Date & Times row
            if (editMode != EditMode.EDIT_SINGLE_OCCURRENCE) {
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${strings.dateLabel}: ${strings.formatShortDate(date)}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_time_button")
                ) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${strings.startTimeLabel}: ${LocalTime.of(startMinute / 60, startMinute % 60).format(timeFmt)}")
                }
                OutlinedButton(
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("end_time_button")
                ) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${strings.endTimeLabel}: ${LocalTime.of(endMinute / 60, endMinute % 60).format(timeFmt)}")
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.durationLabel}: ${strings.formatHoursFromMinutes(durationMin)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (isOvernight) {
                        Text(
                            text = strings.overnightBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // 10 Shift Colors Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = strings.colorLabel, style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShiftColorPalette.swatches.forEach { swatch ->
                        val selected = swatch.id == colorId
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(swatch.accent)
                                .clickable { colorId = swatch.id }
                                .testTag("color_swatch_${swatch.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = swatch.nameEn,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reminder lead time chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = strings.reminderLabel, style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReminderOption.entries.forEach { opt ->
                        FilterChip(
                            selected = reminderMinutes == opt.minutesBefore,
                            onClick = { reminderMinutes = opt.minutesBefore },
                            label = { Text(strings.reminderText(opt)) }
                        )
                    }
                }
            }

            // Recurrence Builder (only for new shifts or full-series edits)
            if (editMode != EditMode.EDIT_SINGLE_OCCURRENCE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = strings.recurrenceLabel, style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val types = listOf(
                            RecurrenceType.NONE to strings.recurrenceNone,
                            RecurrenceType.DAILY to strings.recurrenceDaily,
                            RecurrenceType.WEEKLY to strings.recurrenceWeekly,
                            RecurrenceType.MONTHLY to strings.recurrenceMonthly,
                            RecurrenceType.ROTATING_CYCLE to strings.recurrenceRotating
                        )
                        types.forEach { (type, label) ->
                            FilterChip(
                                selected = recurrenceType == type,
                                onClick = { recurrenceType = type },
                                label = { Text(label) },
                                modifier = Modifier.testTag("recurrence_${type.key}")
                            )
                        }
                    }

                    when (recurrenceType) {
                        RecurrenceType.NONE -> Unit
                        RecurrenceType.DAILY -> {
                            NumericStepperRow(
                                label = strings.repeatEveryDays,
                                value = recurrenceInterval,
                                min = 1,
                                max = 365,
                                onChange = { recurrenceInterval = it }
                            )
                        }
                        RecurrenceType.WEEKLY -> {
                            NumericStepperRow(
                                label = strings.repeatEveryWeeks,
                                value = recurrenceInterval,
                                min = 1,
                                max = 52,
                                onChange = { recurrenceInterval = it }
                            )
                            Text(text = strings.selectWeekdays, style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DayOfWeek.entries.forEach { dow ->
                                    val bit = RecurrenceEngine.weekdayBit(dow)
                                    val selected = (weekdaysMask and bit) != 0
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                val toggled = weekdaysMask xor bit
                                                if (toggled != 0) weekdaysMask = toggled
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = strings.shortWeekday(dow).take(2),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        RecurrenceType.MONTHLY -> {
                            NumericStepperRow(
                                label = strings.repeatEveryMonths,
                                value = recurrenceInterval,
                                min = 1,
                                max = 12,
                                onChange = { recurrenceInterval = it }
                            )
                        }
                        RecurrenceType.ROTATING_CYCLE -> {
                            NumericStepperRow(
                                label = strings.workDaysCount,
                                value = workDays,
                                min = 1,
                                max = 30,
                                onChange = { workDays = it }
                            )
                            NumericStepperRow(
                                label = strings.offDaysCount,
                                value = offDays,
                                min = 0,
                                max = 30,
                                onChange = { offDays = it }
                            )
                        }
                    }

                    if (recurrenceType != RecurrenceType.NONE) {
                        Text(text = strings.endConditionLabel, style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = endCondition == EndCondition.NEVER,
                                onClick = { endCondition = EndCondition.NEVER },
                                label = { Text(strings.endNever) }
                            )
                            FilterChip(
                                selected = endCondition == EndCondition.UNTIL_DATE,
                                onClick = { endCondition = EndCondition.UNTIL_DATE },
                                label = { Text(strings.endOnDate) }
                            )
                            FilterChip(
                                selected = endCondition == EndCondition.AFTER_COUNT,
                                onClick = { endCondition = EndCondition.AFTER_COUNT },
                                label = { Text(strings.endAfterCount) }
                            )
                        }

                        when (endCondition) {
                            EndCondition.NEVER -> Unit
                            EndCondition.UNTIL_DATE -> {
                                OutlinedButton(
                                    onClick = { showEndDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${strings.endOnDate}: ${strings.formatShortDate(endDate)}")
                                }
                            }
                            EndCondition.AFTER_COUNT -> {
                                NumericStepperRow(
                                    label = strings.occurrencesCount,
                                    value = maxOccurrences,
                                    min = 1,
                                    max = 500,
                                    onChange = { maxOccurrences = it }
                                )
                            }
                        }
                    }
                }
            }

            // Inline Overtime (including Programmato / PROG) & Allowances Builder
            ShiftExtrasInlineSection(
                overtimeDrafts = overtimeDrafts,
                allowanceDrafts = allowanceDrafts,
                showRecurringToggle = editMode != EditMode.EDIT_SINGLE_OCCURRENCE && recurrenceType != RecurrenceType.NONE,
                applyExtrasToSeries = applyExtrasToSeries,
                onApplyExtrasToSeriesChange = { applyExtrasToSeries = it },
                onOvertimeChange = { overtimeDrafts = it },
                onAllowancesChange = { allowanceDrafts = it }
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(strings.notesLabel) },
                placeholder = { Text(strings.notesPlaceholder) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shift_notes_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(strings.cancel)
                }
                Button(
                    onClick = {
                        val finalTitle = title.trim().ifBlank {
                            if (strings.language == AppLanguage.ITALIAN) "Turno" else "Shift"
                        }
                        if (editMode == EditMode.EDIT_SINGLE_OCCURRENCE && editTarget != null) {
                            onSaveSingleOccurrence(
                                editTarget,
                                finalTitle,
                                startMinute,
                                endMinute,
                                colorId,
                                notes.trim(),
                                reminderMinutes,
                                overtimeDrafts,
                                allowanceDrafts
                            )
                        } else {
                            onSaveSeries(
                                ShiftSeriesEntity(
                                    id = if (editMode == EditMode.EDIT_ENTIRE_SERIES) baseSeries?.id ?: 0L else 0L,
                                    title = finalTitle,
                                    startDateEpochDay = date.toEpochDay(),
                                    startMinuteOfDay = startMinute,
                                    endMinuteOfDay = endMinute,
                                    colorId = colorId,
                                    notes = notes.trim(),
                                    reminderMinutesBefore = reminderMinutes,
                                    recurrenceType = recurrenceType.key,
                                    recurrenceInterval = recurrenceInterval,
                                    daysOfWeekMask = weekdaysMask,
                                    cycleWorkDays = workDays,
                                    cycleOffDays = offDays,
                                    endDateEpochDay = if (recurrenceType != RecurrenceType.NONE && endCondition == EndCondition.UNTIL_DATE) {
                                        endDate.toEpochDay()
                                    } else null,
                                    maxOccurrences = if (recurrenceType != RecurrenceType.NONE && endCondition == EndCondition.AFTER_COUNT) {
                                        maxOccurrences
                                    } else null,
                                    createdAtMillis = if (editMode == EditMode.EDIT_ENTIRE_SERIES) {
                                        baseSeries?.createdAtMillis ?: System.currentTimeMillis()
                                    } else {
                                        System.currentTimeMillis()
                                    }
                                ),
                                applyExtrasToSeries,
                                overtimeDrafts,
                                allowanceDrafts
                            )
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_shift_button")
                ) {
                    Text(strings.save)
                }
            }
        }
    }

    if (showStartDatePicker) {
        SimpleDatePickerDialog(
            initialDate = date,
            onDismiss = { showStartDatePicker = false },
            onConfirm = { picked ->
                date = picked
                if (endDate.isBefore(picked)) endDate = picked.plusMonths(1)
                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        SimpleDatePickerDialog(
            initialDate = endDate,
            onDismiss = { showEndDatePicker = false },
            onConfirm = { picked ->
                endDate = if (picked.isBefore(date)) date else picked
                showEndDatePicker = false
            }
        )
    }

    if (showStartTimePicker) {
        SimpleTimePickerDialog(
            initialMinuteOfDay = startMinute,
            title = strings.startTimeLabel,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { picked ->
                startMinute = picked
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        SimpleTimePickerDialog(
            initialMinuteOfDay = endMinute,
            title = strings.endTimeLabel,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { picked ->
                endMinute = picked
                showEndTimePicker = false
            }
        )
    }
}

@Composable
private fun NumericStepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (value > min) onChange(value - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = { if (value < max) onChange(value + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val strings = LocalAppStrings.current
    val initialMillis = remember(initialDate) {
        initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                val picked = if (millis != null) {
                    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                } else initialDate
                onConfirm(picked)
            }) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTimePickerDialog(
    initialMinuteOfDay: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val strings = LocalAppStrings.current
    val state = rememberTimePickerState(
        initialHour = (initialMinuteOfDay / 60).coerceIn(0, 23),
        initialMinute = (initialMinuteOfDay % 60).coerceIn(0, 59),
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
