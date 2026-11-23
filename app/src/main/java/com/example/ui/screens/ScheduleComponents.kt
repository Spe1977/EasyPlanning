package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DaySummary
import com.example.data.model.OvertimeType
import com.example.data.model.ScheduleViewMode
import com.example.data.model.ShiftInstance
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.BrandBlueStart
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandIndigoEnd
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.ShiftColorPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Top app header inspired by reference screenshots:
 * Brand wordmark + live online/offline status dot, outlined "Cerca Data" pill, and vibrant "Oggi" pill.
 */
@Composable
fun ScheduleTopHeaderBar(
    isOnline: Boolean,
    onSearchDate: () -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isDark = LocalIsDarkTheme.current
    val brandTextColor = if (isDark) MaterialTheme.colorScheme.primary else BrandIndigoEnd
    val statusColor = if (isOnline) BrandEmerald else MaterialTheme.colorScheme.outline

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Brand logo + title + live connection dot
                Image(
                    painter = painterResource(id = R.drawable.ic_easyplanning_fg),
                    contentDescription = strings.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = brandTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = if (isOnline) strings.statusOnline else strings.statusOffline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Outlined "Cerca Data" pill button
                Surface(
                    onClick = onSearchDate,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .testTag("search_date_button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.searchDate,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = brandTextColor,
                            maxLines = 1
                        )
                    }
                }

                // Filled luminous gradient "Oggi" pill button
                Box(
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(BrandBlueStart, BrandIndigoEnd)
                            )
                        )
                        .clickable(onClick = onJumpToToday)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("jump_today_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.today,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )
        }
    }
}

/**
 * Floating Sync button + elevated rounded segmented pill switcher ("Lista" | "Calendario").
 */
@Composable
fun ScheduleModeSwitchBar(
    viewMode: ScheduleViewMode,
    onViewModeChange: (ScheduleViewMode) -> Unit,
    onQuickSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isDark = LocalIsDarkTheme.current
    val activeAccent = if (isDark) MaterialTheme.colorScheme.primary else BrandIndigoEnd

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Floating rounded Sync icon button
        Surface(
            onClick = onQuickSync,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 3.dp,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .size(48.dp)
                .testTag("quick_sync_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = strings.quickSyncTooltip,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Elevated rounded segmented pill ("Lista" | "Calendario")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("schedule_view_switcher")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModePillTab(
                    label = strings.viewDailyList,
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    selected = viewMode == ScheduleViewMode.DAILY_LIST,
                    activeAccent = activeAccent,
                    onClick = { onViewModeChange(ScheduleViewMode.DAILY_LIST) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_daily_list")
                )
                ModePillTab(
                    label = strings.viewMonthlyCalendar,
                    icon = Icons.Filled.CalendarMonth,
                    selected = viewMode == ScheduleViewMode.MONTHLY_CALENDAR,
                    activeAccent = activeAccent,
                    onClick = { onViewModeChange(ScheduleViewMode.MONTHLY_CALENDAR) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_monthly_calendar")
                )
            }
        }
    }
}

@Composable
private fun ModePillTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    activeAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
        shadowElevation = if (selected) 3.dp else 0.dp,
        modifier = modifier.fillMaxHeight()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * High-craft timeline day card modeled after screenshot 18-28-579:
 * Thick rounded left color bar, left date block (LUN / 23 / NOV 26) + vertical hairline divider,
 * center bold shift title + circular recurrence badge + time range + compact translucent overtime/allowance/note chips,
 * right Edit & coral Delete icons. Tapping anywhere opens the Day Detail Bottom Sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineShiftDayCard(
    daySummary: DaySummary,
    isHighlighted: Boolean = false,
    onCardClick: () -> Unit,
    onEditShift: (ShiftInstance) -> Unit,
    onDeleteShift: (ShiftInstance) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isDark = LocalIsDarkTheme.current
    val today = remember { LocalDate.now() }
    val isToday = daySummary.date == today
    val primaryShift = daySummary.shifts.firstOrNull()
    val accentColor = when {
        primaryShift != null -> ShiftColorPalette.byId(primaryShift.colorId).accent
        daySummary.overtimeEntries.isNotEmpty() -> Color(OvertimeType.fromKey(daySummary.overtimeEntries.first().overtimeType).colorHex)
        else -> MaterialTheme.colorScheme.secondary
    }
    val dateAccentColor = if (isDark) MaterialTheme.colorScheme.primary else BrandIndigoEnd
    val borderColor = if (isHighlighted || isToday) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (isHighlighted) 6.dp else 3.dp,
        border = BorderStroke(if (isHighlighted || isToday) 1.25.dp else 0.75.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("timeline_day_card_${daySummary.date}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thick rounded left vertical color bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            // Left Date Column (LUN / 23 / NOV 26)
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .padding(vertical = 14.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = strings.shortWeekday(daySummary.date.dayOfWeek).uppercase(strings.javaLocale),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = dateAccentColor,
                    letterSpacing = 0.6.sp
                )
                Text(
                    text = "${daySummary.date.dayOfMonth}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.formatCompactMonthYear(daySummary.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (isToday) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = strings.today.uppercase(strings.javaLocale),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Crisp hairline vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            )

            // Center Content: Shift Title, Recurrence Circle, Time Range & Compact Badges
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (primaryShift != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = primaryShift.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (primaryShift.isRecurring) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Repeat,
                                    contentDescription = strings.recurringBadge,
                                    tint = dateAccentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = primaryShift.formattedTimeRange(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    // Additional shifts on the same day (if > 1)
                    if (daySummary.shifts.size > 1) {
                        daySummary.shifts.drop(1).forEach { extra ->
                            val extraColor = ShiftColorPalette.byId(extra.colorId).accent
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(extraColor)
                                )
                                Text(
                                    text = "${extra.title} · ${extra.formattedTimeRange()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (daySummary.overtimeEntries.isNotEmpty()) strings.overtimeLabel else strings.allowancesLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Compact translucent badges for Overtime, Allowances & Notes
                val hasNotes = daySummary.shifts.any { it.notes.isNotBlank() }
                if (daySummary.overtimeEntries.isNotEmpty() || daySummary.allowanceEntries.isNotEmpty() || hasNotes) {
                    Spacer(modifier = Modifier.height(2.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        daySummary.overtimeEntries.take(2).forEach { ot ->
                            val otType = OvertimeType.fromKey(ot.overtimeType)
                            val otColor = Color(otType.colorHex)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = otColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${otType.abbr(strings.language)} ${strings.formatHalfHours(ot.halfHours)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = otColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (daySummary.overtimeEntries.size > 2) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "+${daySummary.overtimeEntries.size - 2}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        daySummary.allowanceEntries.take(2).forEach { al ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                            ) {
                                Text(
                                    text = if (al.quantity > 1) "${al.label} ×${al.quantity}" else al.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (hasNotes) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Notes,
                                        contentDescription = strings.notesLabel,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = strings.notesLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Edit & Delete action icons (min 48dp touch targets)
            if (primaryShift != null) {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onEditShift(primaryShift) },
                        modifier = Modifier.testTag("edit_shift_${primaryShift.seriesId}_${primaryShift.date}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = strings.editShift,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeleteShift(primaryShift) },
                        modifier = Modifier.testTag("delete_shift_${primaryShift.seriesId}_${primaryShift.date}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = strings.deleteShift,
                            tint = BrandCoral,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Elevated Monthly Calendar Card modeled after screenshot 18-31-461:
 * Luminous Royal Blue -> Indigo gradient header banner with month/year & underlined "Oggi" link,
 * crisp white elevated calendar body with hairline grid dividers, muted previous/next month dates,
 * and vivid multi-color shift dots.
 */
@Composable
fun RedesignedMonthlyCalendarCard(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    summaries: Map<LocalDate, DaySummary>,
    onSelectDate: (LocalDate) -> Unit,
    onShiftMonth: (Long) -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val today = remember { LocalDate.now() }
    val firstDayOfMonth = visibleMonth.atDay(1)
    val leadingDays = firstDayOfMonth.dayOfWeek.value - 1 // Mon=0..Sun=6
    val totalDaysInMonth = visibleMonth.lengthOfMonth()
    val totalCells = ((leadingDays + totalDaysInMonth + 6) / 7) * 7
    val gridStartDate = firstDayOfMonth.minusDays(leadingDays.toLong())
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 6.dp,
        border = BorderStroke(0.75.dp, hairline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Vibrant Royal-Blue -> Indigo Gradient Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(BrandBlueStart, BrandIndigoEnd)
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { onShiftMonth(-1) },
                        modifier = Modifier.testTag("prev_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous month",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = strings.formatMonthYear(visibleMonth),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.today,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.92f),
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onJumpToToday)
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .testTag("calendar_today_link")
                        )
                    }

                    IconButton(
                        onClick = { onShiftMonth(1) },
                        modifier = Modifier.testTag("next_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next month",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Weekday header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                DayOfWeek.entries.forEach { dow ->
                    Text(
                        text = strings.shortWeekday(dow).lowercase(strings.javaLocale),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(thickness = 0.75.dp, color = hairline)

            // Calendar Grid Rows
            val rows = totalCells / 7
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    for (col in 0 until 7) {
                        val cellDate = gridStartDate.plusDays((row * 7 + col).toLong())
                        val isCurrentMonth = YearMonth.from(cellDate) == visibleMonth
                        val isSelected = cellDate == selectedDate
                        val isToday = cellDate == today
                        val summary = summaries[cellDate]
                        val shifts = summary?.shifts.orEmpty()
                        val hasOvertime = (summary?.totalOvertimeHalfHours ?: 0) > 0

                        val cellBg = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            !isCurrentMonth -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .background(cellBg)
                                .border(width = 0.4.dp, color = hairline.copy(alpha = 0.65f))
                                .clickable { onSelectDate(cellDate) }
                                .testTag("calendar_cell_${cellDate}"),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            // Top royal-blue highlight bar on Today or Selected cell
                            if (isToday || isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp)
                                        .background(if (isSelected) BrandBlueStart else BrandIndigoEnd.copy(alpha = 0.55f))
                                        .align(Alignment.TopCenter)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) BrandBlueStart else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${cellDate.dayOfMonth}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isSelected -> Color.White
                                            !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            isToday -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }

                                // Shift colored dots + overtime indicator
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.height(8.dp)
                                ) {
                                    shifts.take(3).forEach { s ->
                                        val swatch = ShiftColorPalette.byId(s.colorId)
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCurrentMonth) swatch.accent
                                                    else swatch.accent.copy(alpha = 0.45f)
                                                )
                                        )
                                    }
                                    if (hasOvertime) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
