package com.example.sport.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

internal val SportShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Tema dell'app. Scuro di default (non segue il sistema): è il contesto d'uso reale in palestra
 * e riduce i consumi su OLED. Colori dinamici disattivati per un'identità e un contrasto costanti.
 */
@Composable
fun SportTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSportExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) SportDarkColors else SportLightColors,
            typography = SportTypography,
            shapes = SportShapes,
            content = content,
        )
    }
}

object SportTheme {
    val extendedColors: SportExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSportExtendedColors.current
}
