package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.UserPreferences
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.AuthSkyBottomDark
import com.example.ui.theme.AuthSkyBottomLight
import com.example.ui.theme.AuthSkyMidDark
import com.example.ui.theme.AuthSkyMidLight
import com.example.ui.theme.AuthSkyTopDark
import com.example.ui.theme.AuthSkyTopLight
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandBlueBright
import com.example.ui.theme.BrandBlueStart
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandIndigoEnd
import com.example.ui.theme.BrandSkyAccent
import com.example.ui.theme.LocalIsDarkTheme

private enum class AuthTab { REGISTER, LOGIN }

@Composable
fun AuthScreen(
    prefs: UserPreferences,
    isBusy: Boolean,
    onSetLanguage: (AppLanguage) -> Unit,
    onRegisterOffline: (username: String, password: String) -> Unit,
    onLoginOffline: (username: String, password: String, onResult: (Boolean) -> Unit) -> Unit,
    onContinueWithGoogle: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isDark = LocalIsDarkTheme.current

    var activeTab by remember(prefs.hasRegisteredAccount) {
        mutableStateOf(if (prefs.hasRegisteredAccount) AuthTab.LOGIN else AuthTab.REGISTER)
    }
    var username by remember(prefs.username) { mutableStateOf(prefs.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val strengthScore = remember(password) { calculatePasswordStrength(password) }
    val bgColors = if (isDark) {
        listOf(AuthSkyTopDark, AuthSkyMidDark, AuthSkyBottomDark)
    } else {
        listOf(AuthSkyTopLight, AuthSkyMidLight, AuthSkyBottomLight)
    }
    val titleAccent = if (isDark) MaterialTheme.colorScheme.primary else BrandBlueBright

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = bgColors))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top-right sleek IT / EN pill toggle
            Row(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        LanguagePillButton(
                            label = "IT",
                            selected = prefs.language == AppLanguage.ITALIAN,
                            onClick = { onSetLanguage(AppLanguage.ITALIAN) },
                            testTag = "auth_lang_it"
                        )
                        LanguagePillButton(
                            label = "EN",
                            selected = prefs.language == AppLanguage.ENGLISH,
                            onClick = { onSetLanguage(AppLanguage.ENGLISH) },
                            testTag = "auth_lang_en"
                        )
                    }
                }
            }

            // Centered Brand Hero inspired by screenshot 28-16-503
            Column(
                modifier = Modifier.widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_easyplanning_fg),
                        contentDescription = strings.appName,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.appName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.authAppTagline,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (activeTab == AuthTab.LOGIN) strings.authLoginTitle else strings.authRegisterTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleAccent,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (activeTab == AuthTab.LOGIN) strings.authLoginSubtitle else strings.authRegisterSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Floating Pure-White Elevated Auth Card
            Surface(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 10.dp,
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (activeTab == AuthTab.LOGIN && prefs.hasRegisteredAccount && prefs.username.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.noOfflineAccountHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandSkyAccent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedLabelColor = BrandBlueBright,
                        focusedLeadingIconColor = BrandBlueBright,
                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = { Text(strings.usernameLabel) },
                        placeholder = { Text(strings.usernamePlaceholder) },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_username_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text(strings.passwordLabel) },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )

                    AnimatedVisibility(visible = activeTab == AuthTab.REGISTER && password.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val (label, color) = when {
                                strengthScore < 0.45f -> strings.passwordStrengthWeak to MaterialTheme.colorScheme.error
                                strengthScore < 0.78f -> strings.passwordStrengthMedium to BrandAmber
                                else -> strings.passwordStrengthStrong to BrandEmerald
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                            LinearProgressIndicator(
                                progress = { strengthScore },
                                color = color,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }

                    AnimatedVisibility(visible = activeTab == AuthTab.REGISTER) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    errorMessage = null
                                },
                                label = { Text(strings.confirmPasswordLabel) },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_confirm_password_input")
                            )

                            Text(
                                text = strings.unifiedPasswordHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("auth_error_text")
                        )
                    }

                    // Primary Luminous Gradient CTA Button with Right Arrow
                    val submitAction = {
                        if (activeTab == AuthTab.REGISTER) {
                            when {
                                username.trim().length < 3 -> errorMessage = strings.usernameMinLengthError
                                password.length < 8 -> errorMessage = strings.passwordMinLengthError
                                password != confirmPassword -> errorMessage = strings.passwordMismatchError
                                else -> onRegisterOffline(username.trim(), password)
                            }
                        } else {
                            if (username.trim().isEmpty() || password.isEmpty()) {
                                errorMessage = strings.invalidCredentialsError
                            } else {
                                onLoginOffline(username.trim(), password) { ok ->
                                    if (!ok) errorMessage = strings.invalidCredentialsError
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (isBusy) {
                                        listOf(BrandBlueStart.copy(alpha = 0.55f), BrandIndigoEnd.copy(alpha = 0.55f))
                                    } else {
                                        listOf(BrandBlueStart, BrandIndigoEnd)
                                    }
                                )
                            )
                            .clickable(enabled = !isBusy, onClick = submitAction)
                            .testTag("auth_submit_offline_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (activeTab == AuthTab.REGISTER) {
                                    strings.createOfflineAccount.uppercase(strings.javaLocale)
                                } else {
                                    strings.authTabLogin.uppercase(strings.javaLocale)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // "oppure" hairline divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = if (prefs.language == AppLanguage.ITALIAN) "oppure" else "or",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Crisp Outlined Pill Button with Multi-Color Vector Google 'G' Logo
                    OutlinedButton(
                        onClick = onContinueWithGoogle,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_google_button")
                    ) {
                        GoogleGVectorIcon(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (activeTab == AuthTab.REGISTER) strings.continueWithGoogle else strings.signInWithGoogle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Clean bottom toggle link (Non hai un account? Registrati / Hai già un account? Accedi)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeTab == AuthTab.LOGIN) strings.noAccountPrompt else strings.haveAccountPrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeTab == AuthTab.LOGIN) strings.authTabRegister else strings.authTabLogin,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    activeTab = if (activeTab == AuthTab.LOGIN) AuthTab.REGISTER else AuthTab.LOGIN
                                    errorMessage = null
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("auth_toggle_mode_button")
                        )
                    }
                }
            }

            // Subtle Zero-Server Privacy Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.78f),
                modifier = Modifier.widthIn(max = 440.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = titleAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.authPrivacyBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LanguagePillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) BrandBlueBright else Color.Transparent,
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Crisp custom Compose Canvas rendering of the multi-color Google 'G' emblem.
 */
@Composable
private fun GoogleGVectorIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.2f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        // Red top-left arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -145f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue right arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -10f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue horizontal crossbar of 'G'
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.50f, size.height * 0.50f),
            end = Offset(size.width - inset * 0.6f, size.height * 0.50f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}

internal fun calculatePasswordStrength(password: String): Float {
    if (password.isEmpty()) return 0f
    var score = 0f
    if (password.length >= 8) score += 0.35f
    if (password.length >= 12) score += 0.15f
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score += 0.2f
    if (password.any { it.isDigit() }) score += 0.15f
    if (password.any { !it.isLetterOrDigit() }) score += 0.15f
    return score.coerceIn(0.15f, 1f)
}
