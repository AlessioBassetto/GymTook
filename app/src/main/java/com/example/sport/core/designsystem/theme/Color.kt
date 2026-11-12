package com.example.sport.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette "gym-proof": fondo quasi nero (OLED, niente riflessi sotto le luci della palestra),
 * testo quasi bianco e un solo accento lime ad altissimo contrasto per le azioni principali.
 * Rapporti di contrasto (WCAG): testo primario 17:1, lime su fondo 15:1, testo su lime 14:1.
 */
internal object SportPalette {
    val Lime = Color(0xFFD4FF3A)
    val LimeContainer = Color(0xFF2A330D)
    val OnLime = Color(0xFF151A00)
    val OnLimeContainer = Color(0xFFE6FF99)
    val Olive = Color(0xFF4A5C00)

    val Cyan = Color(0xFF5AD2F4)
    val CyanContainer = Color(0xFF0E3440)
    val OnCyan = Color(0xFF00222C)
    val OnCyanContainer = Color(0xFFB8EEFF)

    val Amber = Color(0xFFFFC94A)
    val AmberContainer = Color(0xFF3D2E00)
    val OnAmber = Color(0xFF261A00)
    val OnAmberContainer = Color(0xFFFFE6A6)

    val Coral = Color(0xFFFF6B6B)
    val CoralContainer = Color(0xFF4A1416)
    val OnCoral = Color(0xFF2B0003)
    val OnCoralContainer = Color(0xFFFFDAD8)

    val Ink0 = Color(0xFF0B0C0E)
    val Ink1 = Color(0xFF131518)
    val Ink2 = Color(0xFF1A1D21)
    val Ink3 = Color(0xFF22262B)
    val Ink4 = Color(0xFF2C3137)
    val Ink5 = Color(0xFF363C43)
    val Line = Color(0xFF3D434B)
    val LineSubtle = Color(0xFF2A2F35)
    val TextHigh = Color(0xFFF3F4F6)
    val TextMedium = Color(0xFFAEB4BD)

    val Paper0 = Color(0xFFF7F8F5)
    val Paper1 = Color(0xFFFFFFFF)
    val Paper2 = Color(0xFFF0F2EC)
    val Paper3 = Color(0xFFE8EBE3)
    val Paper4 = Color(0xFFDFE3D9)
    val InkText = Color(0xFF15181B)
    val InkTextMedium = Color(0xFF4A5058)
}

internal val SportDarkColors = darkColorScheme(
    primary = SportPalette.Lime,
    onPrimary = SportPalette.OnLime,
    primaryContainer = SportPalette.LimeContainer,
    onPrimaryContainer = SportPalette.OnLimeContainer,
    inversePrimary = SportPalette.Olive,
    secondary = SportPalette.Cyan,
    onSecondary = SportPalette.OnCyan,
    secondaryContainer = SportPalette.CyanContainer,
    onSecondaryContainer = SportPalette.OnCyanContainer,
    tertiary = SportPalette.Amber,
    onTertiary = SportPalette.OnAmber,
    tertiaryContainer = SportPalette.AmberContainer,
    onTertiaryContainer = SportPalette.OnAmberContainer,
    error = SportPalette.Coral,
    onError = SportPalette.OnCoral,
    errorContainer = SportPalette.CoralContainer,
    onErrorContainer = SportPalette.OnCoralContainer,
    background = SportPalette.Ink0,
    onBackground = SportPalette.TextHigh,
    surface = SportPalette.Ink1,
    onSurface = SportPalette.TextHigh,
    surfaceVariant = SportPalette.Ink3,
    onSurfaceVariant = SportPalette.TextMedium,
    // Nessuna tinta lime sulle superfici elevate: il colore resta riservato alle azioni.
    surfaceTint = SportPalette.Ink1,
    inverseSurface = SportPalette.TextHigh,
    inverseOnSurface = SportPalette.Ink1,
    outline = SportPalette.Line,
    outlineVariant = SportPalette.LineSubtle,
    scrim = Color.Black,
    surfaceBright = SportPalette.Ink4,
    surfaceDim = SportPalette.Ink0,
    surfaceContainerLowest = SportPalette.Ink0,
    surfaceContainerLow = SportPalette.Ink2,
    surfaceContainer = SportPalette.Ink3,
    surfaceContainerHigh = SportPalette.Ink4,
    surfaceContainerHighest = SportPalette.Ink5,
)

/** Variante chiara (outdoor, luce solare): l'accento diventa oliva per mantenere il contrasto. */
internal val SportLightColors = lightColorScheme(
    primary = SportPalette.Olive,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F27A),
    onPrimaryContainer = Color(0xFF151A00),
    inversePrimary = SportPalette.Lime,
    secondary = Color(0xFF00677F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8EAFF),
    onSecondaryContainer = Color(0xFF001F28),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDF9E),
    onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = SportPalette.Paper0,
    onBackground = SportPalette.InkText,
    surface = SportPalette.Paper1,
    onSurface = SportPalette.InkText,
    surfaceVariant = SportPalette.Paper3,
    onSurfaceVariant = SportPalette.InkTextMedium,
    surfaceTint = SportPalette.Paper1,
    inverseSurface = SportPalette.Ink2,
    inverseOnSurface = SportPalette.TextHigh,
    outline = Color(0xFF747B70),
    outlineVariant = Color(0xFFC6CBBE),
    scrim = Color.Black,
    surfaceBright = SportPalette.Paper1,
    surfaceDim = SportPalette.Paper4,
    surfaceContainerLowest = SportPalette.Paper1,
    surfaceContainerLow = SportPalette.Paper2,
    surfaceContainer = SportPalette.Paper3,
    surfaceContainerHigh = SportPalette.Paper4,
    surfaceContainerHighest = Color(0xFFD6DBCF),
)

/** Colori semantici dell'allenamento, non coperti dal ColorScheme Material. */
@Immutable
data class SportExtendedColors(
    /** Riga della serie completata. */
    val completedContainer: Color,
    val onCompletedContainer: Color,
    /** Timer di recupero. */
    val rest: Color,
    val restContainer: Color,
    /** Record personale. */
    val record: Color,
    val recordContainer: Color,
)

internal val DarkExtendedColors = SportExtendedColors(
    completedContainer = SportPalette.LimeContainer,
    onCompletedContainer = SportPalette.OnLimeContainer,
    rest = SportPalette.Cyan,
    restContainer = SportPalette.CyanContainer,
    record = SportPalette.Amber,
    recordContainer = SportPalette.AmberContainer,
)

internal val LightExtendedColors = SportExtendedColors(
    completedContainer = Color(0xFFE3F5A8),
    onCompletedContainer = Color(0xFF151A00),
    rest = Color(0xFF00677F),
    restContainer = Color(0xFFCDEFFC),
    record = Color(0xFF7A5900),
    recordContainer = Color(0xFFFFE6A6),
)

internal val LocalSportExtendedColors = staticCompositionLocalOf { DarkExtendedColors }
