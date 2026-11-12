package com.example.sport.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles

/** Azione principale dello schermo: 64dp di altezza, larghezza piena, conferma aptica. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(SportDimens.TouchTargetPrimary),
        shape = MaterialTheme.shapes.large,
    ) {
        ButtonContent(text, icon, MaterialTheme.typography.titleMedium)
    }
}

/** Azione secondaria: 56dp, tonale. */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = SportDimens.TouchTargetMin),
        shape = MaterialTheme.shapes.large,
    ) {
        ButtonContent(text, icon, MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, style: androidx.compose.ui.text.TextStyle) {
    if (icon != null) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(SportDimens.SpacingS))
    }
    Text(text = text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Campo numerico del logger (peso, ripetizioni, RPE): mostra il valore in grande con cifre tabulari.
 * Non apre la tastiera di sistema: al tocco il chiamante mostra [NumericKeypad].
 */
@Composable
fun NumericValueField(
    value: String,
    placeholder: String,
    isActive: Boolean,
    onClick: () -> Unit,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    textStyle: androidx.compose.ui.text.TextStyle = SportNumberStyles.Field,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = SportDimens.TouchTargetMin)
            .semantics {
                contentDescription = "$accessibilityLabel: ${value.ifEmpty { placeholder }}"
                role = Role.Button
            },
        shape = MaterialTheme.shapes.medium,
        color = if (isActive) colors.primaryContainer else colors.surfaceContainerHigh,
        border = if (isActive) BorderStroke(SportDimens.ActiveBorderWidth, colors.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SportDimens.SpacingM),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value.ifEmpty { placeholder },
                style = textStyle,
                color = if (value.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
            )
            if (unit != null && value.isNotEmpty()) {
                Spacer(Modifier.width(SportDimens.SpacingXs))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Stato vuoto: spiega cosa manca e, se possibile, offre l'azione che lo risolve. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(SportDimens.SpacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(SportDimens.SpacingS))
            action()
        }
    }
}

/** Titolo di sezione con azione opzionale a destra. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = SportDimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Valore + etichetta compatti (riepiloghi, statistiche). */
@Composable
fun StatValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(value, style = SportNumberStyles.Compact, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
