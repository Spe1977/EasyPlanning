package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Signature Brand Gradients & Semantic Accents
val BrandBlueStart = Color(0xFF1D4ED8)
val BrandIndigoEnd = Color(0xFF4F46E5)
val BrandBlueBright = Color(0xFF2563EB)
val BrandSkyAccent = Color(0xFF0EA5E9)
val BrandEmerald = Color(0xFF10B981)
val BrandAmber = Color(0xFFF59E0B)
val BrandCoral = Color(0xFFEF4444)

// Auth Screen Atmospheric Gradients
val AuthSkyTopLight = Color(0xFFD2E8FF)
val AuthSkyMidLight = Color(0xFFF2F7FF)
val AuthSkyBottomLight = Color(0xFFC2DAFF)

val AuthSkyTopDark = Color(0xFF0D1B3E)
val AuthSkyMidDark = Color(0xFF0B101D)
val AuthSkyBottomDark = Color(0xFF131D38)

// Signature Luminous Blue/Indigo Palette — Light (Crisp White Cards on Airy Cool Canvas)
val PrimaryLight = Color(0xFF2563EB)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE0EAFF)
val OnPrimaryContainerLight = Color(0xFF0F2566)

val SecondaryLight = Color(0xFF0D9488)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCCFBF1)
val OnSecondaryContainerLight = Color(0xFF063B35)

val TertiaryLight = Color(0xFFEA580C)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFEDD5)
val OnTertiaryContainerLight = Color(0xFF4D1A00)

val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)

val BackgroundLight = Color(0xFFF3F6FB)
val OnBackgroundLight = Color(0xFF111827)
val SurfaceLight = Color(0xFFF3F6FB)
val OnSurfaceLight = Color(0xFF111827)
val SurfaceVariantLight = Color(0xFFE5EAF3)
val OnSurfaceVariantLight = Color(0xFF525E75)
val OutlineLight = Color(0xFF94A3B8)
val OutlineVariantLight = Color(0xFFE2E8F0)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF8FAFE)
val SurfaceContainerLight = Color(0xFFEEF2FA)
val SurfaceContainerHighLight = Color(0xFFE6ECF7)
val SurfaceContainerHighestLight = Color(0xFFDCE4F4)

// Signature Luminous Midnight Palette — Dark (Zero-glare Night-Shift Friendly)
val PrimaryDark = Color(0xFF7AA7FF)
val OnPrimaryDark = Color(0xFF09215E)
val PrimaryContainerDark = Color(0xFF1E3A8A)
val OnPrimaryContainerDark = Color(0xFFDBE7FF)

val SecondaryDark = Color(0xFF5EEAD4)
val OnSecondaryDark = Color(0xFF043731)
val SecondaryContainerDark = Color(0xFF115E59)
val OnSecondaryContainerDark = Color(0xFFCCFBF1)

val TertiaryDark = Color(0xFFFDBA74)
val OnTertiaryDark = Color(0xFF5A2100)
val TertiaryContainerDark = Color(0xFF9A3412)
val OnTertiaryContainerDark = Color(0xFFFFEDD5)

val ErrorDark = Color(0xFFFCA5A5)
val OnErrorDark = Color(0xFF7F1D1D)
val ErrorContainerDark = Color(0xFF991B1B)
val OnErrorContainerDark = Color(0xFFFEE2E2)

val BackgroundDark = Color(0xFF0B101D)
val OnBackgroundDark = Color(0xFFF1F5F9)
val SurfaceDark = Color(0xFF0B101D)
val OnSurfaceDark = Color(0xFFF1F5F9)
val SurfaceVariantDark = Color(0xFF1E293B)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF64748B)
val OutlineVariantDark = Color(0xFF25324D)
val SurfaceContainerLowestDark = Color(0xFF151C2F)
val SurfaceContainerLowDark = Color(0xFF121828)
val SurfaceContainerDark = Color(0xFF192237)
val SurfaceContainerHighDark = Color(0xFF202B44)
val SurfaceContainerHighestDark = Color(0xFF283654)

val EasyPlanningLightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight
)

val EasyPlanningDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark
)

/**
 * 10 curated high-contrast shift colors calibrated for crisp readability on light and dark cards.
 */
data class ShiftColorSwatch(
    val id: Int,
    val nameIt: String,
    val nameEn: String,
    val accent: Color,
    val lightContainer: Color,
    val darkContainer: Color
)

object ShiftColorPalette {
    val swatches: List<ShiftColorSwatch> = listOf(
        ShiftColorSwatch(0, "Cobalto", "Cobalt", Color(0xFF0EA5E9), Color(0xFFE0F2FE), Color(0xFF0C354F)),
        ShiftColorSwatch(1, "Smeraldo", "Emerald", Color(0xFF10B981), Color(0xFFD1FAE5), Color(0xFF083D2D)),
        ShiftColorSwatch(2, "Corallo", "Coral", Color(0xFFF43F5E), Color(0xFFFFE4E6), Color(0xFF571222)),
        ShiftColorSwatch(3, "Ambra", "Amber", Color(0xFFF59E0B), Color(0xFFFEF3C7), Color(0xFF4D3205)),
        ShiftColorSwatch(4, "Viola", "Violet", Color(0xFFA855F7), Color(0xFFF3E8FF), Color(0xFF3A1860)),
        ShiftColorSwatch(5, "Indaco", "Indigo", Color(0xFF4F46E5), Color(0xFFE0E7FF), Color(0xFF1F1E5C)),
        ShiftColorSwatch(6, "Ottanio", "Teal", Color(0xFF14B8A6), Color(0xFFCCFBF1), Color(0xFF083B37)),
        ShiftColorSwatch(7, "Arancio", "Orange", Color(0xFFF97316), Color(0xFFFFEDD5), Color(0xFF522307)),
        ShiftColorSwatch(8, "Lime", "Lime", Color(0xFF84CC16), Color(0xFFECFCCB), Color(0xFF284208)),
        ShiftColorSwatch(9, "Ardesia", "Slate", Color(0xFF64748B), Color(0xFFF1F5F9), Color(0xFF263042))
    )

    fun byId(id: Int): ShiftColorSwatch = swatches.firstOrNull { it.id == id } ?: swatches.first()
}
