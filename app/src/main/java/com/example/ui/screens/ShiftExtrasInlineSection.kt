package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AllowanceDraft
import com.example.data.model.AllowancePreset
import com.example.data.model.OvertimeDraft
import com.example.data.model.OvertimeType
import com.example.i18n.LocalAppStrings

/**
 * Inline Overtime (including 5th SCHEDULED / Programmato type) & Allowances composer
 * embedded directly inside ShiftEditorSheet, with optional recurring-series propagation toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftExtrasInlineSection(
    overtimeDrafts: List<OvertimeDraft>,
    allowanceDrafts: List<AllowanceDraft>,
    showRecurringToggle: Boolean,
    applyExtrasToSeries: Boolean,
    onApplyExtrasToSeriesChange: (Boolean) -> Unit,
    onOvertimeChange: (List<OvertimeDraft>) -> Unit,
    onAllowancesChange: (List<AllowanceDraft>) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val haptic = LocalHapticFeedback.current

    var showOvertimeComposer by remember { mutableStateOf(false) }
    var draftOtType by remember { mutableStateOf(OvertimeType.SCHEDULED) }
    var draftOtHalfHours by remember { mutableIntStateOf(2) }
    var draftOtNote by remember { mutableStateOf("") }

    var showCustomAllowanceRow by remember { mutableStateOf(false) }
    var customAllowanceLabel by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Inline Overtime Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(0.85.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = strings.shiftExtrasOvertimeSection,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = { showOvertimeComposer = !showOvertimeComposer },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("inline_toggle_overtime_composer")
                    ) {
                        Icon(
                            imageVector = if (showOvertimeComposer) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (showOvertimeComposer) strings.cancel else strings.addOvertime,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Active overtime pills
                if (overtimeDrafts.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        overtimeDrafts.forEachIndexed { idx, draft ->
                            val type = OvertimeType.fromKey(draft.overtimeType)
                            val accent = Color(type.colorHex)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accent.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "${type.abbr(strings.language)} · ${strings.formatHalfHours(draft.halfHours)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
                                    )
                                    if (draft.notes.isNotBlank()) {
                                        Text(
                                            text = "(${draft.notes})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onOvertimeChange(overtimeDrafts.toMutableList().apply { removeAt(idx) })
                                        },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("inline_remove_overtime_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = strings.delete,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Collapsible inline overtime composer
                AnimatedVisibility(visible = showOvertimeComposer) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                        // All 5 Overtime Types (including Programmato / PROG)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OvertimeType.entries.forEach { type ->
                                val selected = draftOtType == type
                                val accent = Color(type.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        width = if (selected) 1.6.dp else 1.dp,
                                        color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { draftOtType = type }
                                        .testTag("inline_ot_type_${type.key}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                        Text(
                                            text = "${type.label(strings.language)} (${type.abbr(strings.language)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Exact 0.5h Stepper
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = strings.overtimeDurationLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (draftOtHalfHours > 1) {
                                                draftOtHalfHours--
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        modifier = Modifier.testTag("inline_ot_minus")
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "-0.5h")
                                    }
                                    Text(
                                        text = strings.formatHalfHours(draftOtHalfHours),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (draftOtHalfHours < 48) {
                                                draftOtHalfHours++
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        modifier = Modifier.testTag("inline_ot_plus")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "+0.5h")
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = draftOtNote,
                                onValueChange = { draftOtNote = it },
                                label = { Text(strings.optionalNote) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    onOvertimeChange(
                                        overtimeDrafts + OvertimeDraft(
                                            overtimeType = draftOtType.key,
                                            halfHours = draftOtHalfHours,
                                            notes = draftOtNote.trim()
                                        )
                                    )
                                    draftOtNote = ""
                                    showOvertimeComposer = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("inline_confirm_add_overtime")
                            ) {
                                Text(strings.save, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Inline Allowances & Benefits Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(0.85.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CardGiftcard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = strings.shiftExtrasAllowancesSection,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { showCustomAllowanceRow = !showCustomAllowanceRow },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("inline_toggle_custom_allowance")
                    ) {
                        Icon(
                            imageVector = if (showCustomAllowanceRow) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (showCustomAllowanceRow) strings.cancel else AllowancePreset.CUSTOM.label(strings.language),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Quick 1-tap preset chips (e.g. Buono Pasto, Reperibilità, Indennità Turno...)
                val quickPresets = remember {
                    AllowancePreset.entries.filter { it != AllowancePreset.CUSTOM }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresets.forEach { preset ->
                        val activeIdx = allowanceDrafts.indexOfFirst { it.presetKey == preset.key }
                        val isSelected = activeIdx >= 0
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val updated = allowanceDrafts.toMutableList()
                                if (isSelected) {
                                    updated.removeAt(activeIdx)
                                } else {
                                    updated.add(
                                        AllowanceDraft(
                                            presetKey = preset.key,
                                            label = preset.label(strings.language),
                                            quantity = 1,
                                            notes = ""
                                        )
                                    )
                                }
                                onAllowancesChange(updated)
                            },
                            label = { Text(preset.label(strings.language)) },
                            modifier = Modifier.testTag("inline_preset_${preset.key}")
                        )
                    }
                }

                // Custom allowance quick input
                AnimatedVisibility(visible = showCustomAllowanceRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customAllowanceLabel,
                            onValueChange = { customAllowanceLabel = it },
                            label = { Text(strings.customFieldLabel) },
                            placeholder = { Text(strings.customFieldPlaceholder) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inline_custom_allowance_input")
                        )
                        FilledTonalButton(
                            onClick = {
                                val cleaned = customAllowanceLabel.trim()
                                if (cleaned.isNotEmpty()) {
                                    onAllowancesChange(
                                        allowanceDrafts + AllowanceDraft(
                                            presetKey = AllowancePreset.CUSTOM.key,
                                            label = cleaned,
                                            quantity = 1,
                                            notes = ""
                                        )
                                    )
                                    customAllowanceLabel = ""
                                    showCustomAllowanceRow = false
                                }
                            },
                            enabled = customAllowanceLabel.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("inline_confirm_custom_allowance")
                        ) {
                            Text(strings.save, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Active selected allowances with quantity +/- steppers
                if (allowanceDrafts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allowanceDrafts.forEachIndexed { idx, draft ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = draft.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val updated = allowanceDrafts.toMutableList()
                                                if (draft.quantity > 1) {
                                                    updated[idx] = draft.copy(quantity = draft.quantity - 1)
                                                } else {
                                                    updated.removeAt(idx)
                                                }
                                                onAllowancesChange(updated)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Remove,
                                                contentDescription = "-",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Text(
                                            text = "×${draft.quantity}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                val updated = allowanceDrafts.toMutableList()
                                                if (draft.quantity < 99) {
                                                    updated[idx] = draft.copy(quantity = draft.quantity + 1)
                                                    onAllowancesChange(updated)
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "+",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
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

        // 3. Recurring Propagation Switch (shown when editing/creating a recurring shift)
        AnimatedVisibility(visible = showRecurringToggle) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.applyExtrasToSeriesTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (applyExtrasToSeries) {
                                strings.applyExtrasToSeriesOnDesc
                            } else {
                                strings.applyExtrasToSeriesOffDesc
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Switch(
                        checked = applyExtrasToSeries,
                        onCheckedChange = onApplyExtrasToSeriesChange,
                        modifier = Modifier.testTag("apply_extras_to_series_switch")
                    )
                }
            }
        }
    }
}
