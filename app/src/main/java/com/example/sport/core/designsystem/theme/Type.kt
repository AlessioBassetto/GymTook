package com.example.sport.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Cifre tabulari: i numeri hanno tutti la stessa larghezza, quindi kg e reps non "ballano". */
private const val TABULAR_NUMBERS = "tnum"

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = TABULAR_NUMBERS,
)

/**
 * Tipografia ad alto contrasto: pesi più marcati e corpi leggermente più grandi dei default
 * Material, perché l'app si legge a un braccio di distanza, spesso sudati e in movimento.
 */
internal val SportTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, -0.25),
    displayMedium = style(45, 52, FontWeight.Bold),
    displaySmall = style(36, 44, FontWeight.Bold),
    headlineLarge = style(32, 40, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.Bold),
    titleMedium = style(18, 24, FontWeight.SemiBold, 0.1),
    titleSmall = style(15, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(17, 24, FontWeight.Normal, 0.2),
    bodyMedium = style(15, 22, FontWeight.Normal, 0.2),
    bodySmall = style(13, 18, FontWeight.Normal, 0.3),
    labelLarge = style(16, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(13, 16, FontWeight.SemiBold, 0.4),
    labelSmall = style(12, 16, FontWeight.Medium, 0.4),
)

/** Stili per i valori numerici del logger (peso, ripetizioni, timer). */
object SportNumberStyles {
    /** Timer di recupero a tutto schermo. */
    val Hero: TextStyle = style(64, 72, FontWeight.Bold, -0.5)

    /** Valore nei campi peso/ripetizioni. */
    val Field: TextStyle = style(26, 32, FontWeight.Bold)

    /** Valori nella riga serie del logger (più colonne affiancate). */
    val Row: TextStyle = style(20, 26, FontWeight.Bold)

    /** Valori secondari (serie precedente, totali). */
    val Compact: TextStyle = style(17, 22, FontWeight.SemiBold)
}
