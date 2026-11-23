package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalAppStrings

@Composable
fun SetBackupPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.backupPasswordLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(strings.passwordLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_backup_password_input")
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text(strings.confirmPasswordLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_backup_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.length < 8 -> error = strings.passwordMinLengthError
                        password != confirm -> error = strings.passwordMismatchError
                        else -> {
                            onConfirm(password)
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("save_backup_password_button")
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
fun EnterBackupPasswordDialog(
    title: String,
    requireMinLength: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text(strings.passwordLabel) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { msg -> { Text(msg) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("enter_backup_password_input")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (requireMinLength && password.length < 8) {
                        error = strings.passwordMinLengthError
                    } else if (password.isBlank()) {
                        error = strings.passwordMinLengthError
                    } else {
                        onConfirm(password)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_enter_password_button")
            ) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun SetupPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.setupPinTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.setupPinSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 8) { pin = it; error = null } },
                    label = { Text(strings.enterPinPrompt) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 8) { confirmPin = it; error = null } },
                    label = { Text(strings.confirmPinLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length < 4 -> error = strings.pinTooShortError
                        pin != confirmPin -> error = strings.pinMismatchError
                        else -> {
                            onConfirm(pin)
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("save_app_pin_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

/**
 * Multi-step secure data reset confirmation:
 * Step 1: Warning of permanent erasure
 * Step 2: Explicit acknowledgement checkbox + typing "RESET"
 * Step 3 (if backup password is set): Backup encryption password verification
 */
@Composable
fun MultiStepResetDialog(
    hasBackupPassword: Boolean,
    onDismiss: () -> Unit,
    onExecuteReset: (password: String?, onResult: (Boolean) -> Unit) -> Unit
) {
    val strings = LocalAppStrings.current
    var step by remember { mutableIntStateOf(1) }
    var acknowledged by remember { mutableStateOf(false) }
    var typedReset by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val totalSteps = if (hasBackupPassword) 3 else 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${strings.resetStep1Title} ($step/$totalSteps)",
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    1 -> {
                        Text(
                            text = strings.resetStep1Body,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    2 -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = acknowledged,
                                onCheckedChange = { acknowledged = it },
                                modifier = Modifier.testTag("reset_ack_checkbox")
                            )
                            Text(
                                text = strings.resetStep2Check,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedTextField(
                            value = typedReset,
                            onValueChange = { typedReset = it },
                            label = { Text(strings.resetStep2TypeHint) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_type_input")
                        )
                    }
                    3 -> {
                        Text(
                            text = strings.resetStep3PasswordHint,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; passwordError = null },
                            label = { Text(strings.passwordLabel) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            isError = passwordError != null,
                            supportingText = passwordError?.let { msg -> { Text(msg) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_password_input")
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isFinalStep = step == totalSteps
            val canAdvance = when (step) {
                1 -> true
                2 -> acknowledged && typedReset.trim().equals("RESET", ignoreCase = true)
                3 -> password.isNotBlank()
                else -> false
            }
            Button(
                onClick = {
                    if (!isFinalStep) {
                        step++
                    } else {
                        onExecuteReset(if (hasBackupPassword) password else null) { ok ->
                            if (ok) {
                                onDismiss()
                            } else {
                                passwordError = strings.wrongPasswordError
                            }
                        }
                    }
                },
                enabled = canAdvance,
                colors = if (isFinalStep) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.testTag("reset_confirm_step_button")
            ) {
                Text(if (isFinalStep) strings.resetConfirmButton else strings.resetProceedButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
