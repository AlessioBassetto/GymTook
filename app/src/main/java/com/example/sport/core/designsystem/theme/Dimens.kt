package com.example.sport.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Misure del design system. Le aree di tocco superano il minimo Android (48dp): in palestra si
 * tocca con dita sudate, guanti o di fretta tra una serie e l'altra.
 */
object SportDimens {
    /** Area minima per qualsiasi elemento toccabile. */
    val TouchTargetMin = 56.dp

    /** Pulsanti d'azione principali e tasti del tastierino. */
    val TouchTargetPrimary = 64.dp

    val ScreenPadding = 16.dp
    val SpacingXs = 4.dp
    val SpacingS = 8.dp
    val SpacingM = 12.dp
    val SpacingL = 16.dp
    val SpacingXl = 24.dp
    val SpacingXxl = 32.dp

    val CardPadding = 16.dp
    val ThumbnailSize = 56.dp
    val ActiveBorderWidth = 2.dp
}
