package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.model.DaySummary
import com.example.data.model.ScheduleViewMode
import com.example.data.model.ShiftInstance
import com.example.i18n.LocalAppStrings
import java.time.LocalDate
import java.time.YearMonth

private sealed class TimelineListItem {
    data class MonthHeader(val yearMonth: YearMonth) : TimelineListItem()
    data class DayItem(val summary: DaySummary) : TimelineListItem()
}

@Composable
fun ScheduleScreen(
    selectedDate: LocalDate,
    visibleMonth: YearMonth,
    viewMode: ScheduleViewMode,
    monthSummaries: Map<LocalDate, DaySummary>,
    activeTimelineDays: List<DaySummary>,
    selectedDaySummary: DaySummary,
    scrollSignal: Pair<LocalDate, Long>,
    isDriveConnected: Boolean,
    showNotificationBanner: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onJumpToToday: () -> Unit,
    onShiftMonth: (Long) -> Unit,
    onViewModeChange: (ScheduleViewMode) -> Unit,
    onQuickSync: () -> Unit,
    onAddShift: (LocalDate) -> Unit,
    onEditShift: (ShiftInstance) -> Unit,
    onDeleteShift: (ShiftInstance) -> Unit,
    onAddOvertime: (LocalDate, ShiftInstance?) -> Unit,
    onDeleteOvertime: (OvertimeEntryEntity) -> Unit,
    onAddAllowance: (LocalDate, ShiftInstance?) -> Unit,
    onDeleteAllowance: (AllowanceEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showSearchDateDialog by remember { mutableStateOf(false) }
    var detailSheetDate by remember { mutableStateOf<LocalDate?>(null) }

    // Build flattened list with month headers for the continuous -24/+36m Lista view
    val timelineItems = remember(activeTimelineDays) {
        val result = ArrayList<TimelineListItem>(activeTimelineDays.size + 12)
        var lastMonth: YearMonth? = null
        activeTimelineDays.forEach { day ->
            val ym = YearMonth.from(day.date)
            if (ym != lastMonth) {
                result.add(TimelineListItem.MonthHeader(ym))
                lastMonth = ym
            }
            result.add(TimelineListItem.DayItem(day))
        }
        result
    }

    val listState = rememberLazyListState()
    var hasDoneInitialScroll by remember { mutableStateOf(false) }

    // Initial auto-scroll to Today (or nearest upcoming scheduled day) once data loads
    LaunchedEffect(timelineItems.size) {
        if (!hasDoneInitialScroll && timelineItems.isNotEmpty()) {
            val today = LocalDate.now()
            val targetIdx = findTargetScrollIndex(timelineItems, today)
            if (targetIdx >= 0) {
                listState.scrollToItem(targetIdx)
            }
            hasDoneInitialScroll = true
        }
    }

    var lastScrollSeq by remember { mutableStateOf(0L) }
    var lastScrollExact by remember { mutableStateOf(false) }

    // Scroll smoothly whenever user taps "Oggi" or picks a date in "Cerca Data",
    // re-aligning automatically if expanded timeline bounds finish computing asynchronously.
    LaunchedEffect(scrollSignal.second, viewMode, timelineItems) {
        val seq = scrollSignal.second
        if (seq > 0L && viewMode == ScheduleViewMode.DAILY_LIST && timelineItems.isNotEmpty()) {
            val targetDate = scrollSignal.first
            val exactIdx = timelineItems.indexOfFirst {
                it is TimelineListItem.DayItem && it.summary.date == targetDate
            }
            val shouldScroll = seq != lastScrollSeq || (!lastScrollExact && exactIdx >= 0)
            if (shouldScroll) {
                val targetIdx = if (exactIdx >= 0) exactIdx else findTargetScrollIndex(timelineItems, targetDate)
                if (targetIdx >= 0) {
                    listState.animateScrollToItem(targetIdx)
                }
                lastScrollSeq = seq
                lastScrollExact = exactIdx >= 0
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Brand Header + Cerca Data + Oggi
        ScheduleTopHeaderBar(
            isOnline = isDriveConnected,
            onSearchDate = { showSearchDateDialog = true },
            onJumpToToday = onJumpToToday
        )

        // Floating Sync + Lista | Calendario Switcher
        ScheduleModeSwitchBar(
            viewMode = viewMode,
            onViewModeChange = onViewModeChange,
            onQuickSync = onQuickSync
        )

        // Optional Notification Permission Banner (Android 13+)
        AnimatedVisibility(visible = showNotificationBanner) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = strings.notificationPermissionTitle,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.notificationPermissionTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = strings.notificationPermissionDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    FilledTonalButton(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.testTag("enable_notifications_button")
                    ) {
                        Text(strings.enableNotifications)
                    }
                }
            }
        }

        // Main Body: Continuous Lista Timeline OR Redesigned Monthly Calendar
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "schedule_body_mode",
            modifier = Modifier.weight(1f)
        ) { mode ->
            when (mode) {
                ScheduleViewMode.DAILY_LIST -> {
                    if (timelineItems.isEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                EmptyDayScheduleCard(onAddShift = { onAddShift(selectedDate) })
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("timeline_lazy_column"),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = timelineItems,
                                key = { item ->
                                    when (item) {
                                        is TimelineListItem.MonthHeader -> "month_${item.yearMonth}"
                                        is TimelineListItem.DayItem -> "day_${item.summary.date}"
                                    }
                                }
                            ) { item ->
                                when (item) {
                                    is TimelineListItem.MonthHeader -> {
                                        TimelineMonthHeader(yearMonth = item.yearMonth)
                                    }
                                    is TimelineListItem.DayItem -> {
                                        TimelineShiftDayCard(
                                            daySummary = item.summary,
                                            isHighlighted = item.summary.date == selectedDate,
                                            onCardClick = {
                                                onSelectDate(item.summary.date)
                                                detailSheetDate = item.summary.date
                                            },
                                            onEditShift = onEditShift,
                                            onDeleteShift = onDeleteShift
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ScheduleViewMode.MONTHLY_CALENDAR -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            RedesignedMonthlyCalendarCard(
                                visibleMonth = visibleMonth,
                                selectedDate = selectedDate,
                                summaries = monthSummaries,
                                onSelectDate = onSelectDate,
                                onShiftMonth = onShiftMonth,
                                onJumpToToday = onJumpToToday
                            )
                        }

                        // Selected day summary right underneath the calendar
                        item {
                            val hasContent = selectedDaySummary.shifts.isNotEmpty() ||
                                selectedDaySummary.overtimeEntries.isNotEmpty() ||
                                selectedDaySummary.allowanceEntries.isNotEmpty()

                            if (hasContent) {
                                TimelineShiftDayCard(
                                    daySummary = selectedDaySummary,
                                    isHighlighted = true,
                                    onCardClick = { detailSheetDate = selectedDaySummary.date },
                                    onEditShift = onEditShift,
                                    onDeleteShift = onDeleteShift
                                )
                            } else {
                                CalendarEmptySelectedDayCard(
                                    date = selectedDaySummary.date,
                                    onAddShift = { onAddShift(selectedDaySummary.date) },
                                    onAddOvertime = { onAddOvertime(selectedDaySummary.date, null) },
                                    onAddAllowance = { onAddAllowance(selectedDaySummary.date, null) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Search Date Picker ("Cerca Data") — supports jumping to ANY date
    if (showSearchDateDialog) {
        SearchDatePickerDialog(
            initialDate = selectedDate,
            onDismiss = { showSearchDateDialog = false },
            onDateSelected = { picked ->
                onJumpToDate(picked)
                // If in Lista mode and picked date has no scheduled items yet, open Day Detail Sheet so user can add immediately
                if (viewMode == ScheduleViewMode.DAILY_LIST && activeTimelineDays.none { it.date == picked }) {
                    detailSheetDate = picked
                }
            }
        )
    }

    // Live-updated Day Detail Bottom Sheet
    detailSheetDate?.let { sheetDate ->
        val liveSummary = remember(sheetDate, monthSummaries, activeTimelineDays) {
            monthSummaries[sheetDate]
                ?: activeTimelineDays.firstOrNull { it.date == sheetDate }
                ?: DaySummary(sheetDate, emptyList(), emptyList(), emptyList())
        }
        DayDetailBottomSheet(
            daySummary = liveSummary,
            onDismiss = { detailSheetDate = null },
            onAddShift = onAddShift,
            onEditShift = onEditShift,
            onDeleteShift = onDeleteShift,
            onAddOvertime = onAddOvertime,
            onDeleteOvertime = onDeleteOvertime,
            onAddAllowance = onAddAllowance,
            onDeleteAllowance = onDeleteAllowance
        )
    }
}

private fun findTargetScrollIndex(items: List<TimelineListItem>, targetDate: LocalDate): Int {
    val exactIdx = items.indexOfFirst { it is TimelineListItem.DayItem && it.summary.date == targetDate }
    if (exactIdx >= 0) return exactIdx
    val upcomingIdx = items.indexOfFirst { it is TimelineListItem.DayItem && !it.summary.date.isBefore(targetDate) }
    if (upcomingIdx >= 0) return upcomingIdx
    return items.indexOfLast { it is TimelineListItem.DayItem }.coerceAtLeast(0)
}

@Composable
private fun TimelineMonthHeader(yearMonth: YearMonth) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Text(
                text = strings.formatMonthYear(yearMonth).uppercase(strings.javaLocale),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CalendarEmptySelectedDayCard(
    date: LocalDate,
    onAddShift: () -> Unit,
    onAddOvertime: () -> Unit,
    onAddAllowance: () -> Unit
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 3.dp,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.formatFullDate(date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.noShiftsTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAddShift,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("calendar_empty_add_shift")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.addShift, maxLines = 1)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onAddOvertime,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_overtime_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.MoreTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(strings.addOvertime, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onAddAllowance,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_allowance_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(strings.addAllowance, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EmptyDayScheduleCard(onAddShift: () -> Unit) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_schedule),
                contentDescription = strings.noShiftsTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Text(
                text = strings.noShiftsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = strings.noShiftsSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddShift,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_state_add_shift_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.addFirstShift, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
