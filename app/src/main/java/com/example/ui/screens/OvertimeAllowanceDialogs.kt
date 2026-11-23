package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AllowancePreset
import com.example.data.model.OvertimeType
import com.example.i18n.LocalAppStrings
import java.time.LocalDate

@Composable
fun OvertimeEntryDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (OvertimeType, Int, String) -> Unit
) {
    val strings = LocalAppStrings.current
    val haptic = LocalHapticFeedback.current
    var selectedType by remember { mutableStateOf(OvertimeType.WEEKDAY) }
    var halfHours by remember { mutableIntStateOf(2) } // Default 1.0h (2 * 0.5h)
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.overtimeDialogTitle, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = strings.formatShortDate(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(text = strings.overtimeTypeLabel, style = MaterialTheme.typography.titleSmall)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OvertimeType.entries.chunked(2).forEach { rowTypes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTypes.forEach { type ->
                                val selected = type == selectedType
                                val accent = Color(type.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedType = type }
                                        .testTag("overtime_type_${type.key}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = type.abbr(strings.language),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = accent
                                        )
                                        Text(
                                            text = type.label(strings.language),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Exact 0.5h Stepper
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = strings.overtimeDurationLabel, style = MaterialTheme.typography.titleSmall)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (halfHours > 1) {
                                        halfHours--
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                modifier = Modifier.testTag("overtime_minus_half")
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "-0.5h")
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = strings.formatHalfHours(halfHours),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = strings.overtimeStepHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (halfHours < 48) {
                                        halfHours++
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                modifier = Modifier.testTag("overtime_plus_half")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "+0.5h")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.optionalNote) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedType, halfHours, note)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_overtime_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllowanceEntryDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (presetKey: String, label: String, quantity: Int, note: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedPreset by remember { mutableStateOf(AllowancePreset.MEAL_VOUCHER) }
    var customLabel by remember { mutableStateOf(AllowancePreset.MEAL_VOUCHER.label(strings.language)) }
    var quantity by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.allowanceDialogTitle, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = strings.formatShortDate(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = strings.quickPresetsLabel, style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AllowancePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                customLabel = if (preset == AllowancePreset.CUSTOM) "" else preset.label(strings.language)
                            },
                            label = { Text(preset.label(strings.language)) },
                            modifier = Modifier.testTag("allowance_preset_${preset.key}")
                        )
                    }
                }

                OutlinedTextField(
                    value = customLabel,
                    onValueChange = {
                        customLabel = it
                        if (selectedPreset != AllowancePreset.CUSTOM &&
                            it != selectedPreset.label(strings.language)
                        ) {
                            selectedPreset = AllowancePreset.CUSTOM
                        }
                    },
                    label = { Text(strings.customFieldLabel) },
                    placeholder = { Text(strings.customFieldPlaceholder) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("allowance_label_input")
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = strings.quantityLabel, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(onClick = { if (quantity > 1) quantity-- }) {
                                Icon(Icons.Filled.Remove, contentDescription = "-")
                            }
                            Text(
                                text = "$quantity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            FilledTonalIconButton(onClick = { if (quantity < 99) quantity++ }) {
                                Icon(Icons.Filled.Add, contentDescription = "+")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.optionalNote) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customLabel.isNotBlank()) {
                        onSave(selectedPreset.key, customLabel.trim(), quantity, note)
                        onDismiss()
                    }
                },
                enabled = customLabel.isNotBlank(),
                modifier = Modifier.testTag("save_allowance_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun RecurringScopeDialog(
    onDismiss: () -> Unit,
    onOnlyThis: () -> Unit,
    onEntireSeries: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.recurringActionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.recurringActionSubtitle)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        onOnlyThis()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scope_only_this")
                ) {
                    Text(strings.onlyThisOccurrence)
                }
                Button(
                    onClick = {
                        onEntireSeries()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scope_entire_series")
                ) {
                    Text(strings.entireSeries)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
