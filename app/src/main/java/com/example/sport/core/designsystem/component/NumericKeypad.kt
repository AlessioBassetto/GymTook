package com.example.sport.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.sport.R
import com.example.sport.core.designsystem.input.KeypadKey
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportTheme

/**
 * Tastierino numerico dell'app: tasti da 64dp, risposta aptica, incremento rapido ±step.
 *
 * ```
 * [ 1 ][ 2 ][ 3 ][ ⌫  ]
 * [ 4 ][ 5 ][ 6 ][ −2,5]
 * [ 7 ][ 8 ][ 9 ][ +2,5]
 * [ , ][ 0 ][  Avanti  ]
 * ```
 */
@Composable
fun NumericKeypad(
    onKey: (KeypadKey) -> Unit,
    onDone: () -> Unit,
    stepLabel: String,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = true,
    doneLabel: String = stringResource(R.string.keypad_next),
) {
    val haptics = LocalHapticFeedback.current
    val press: (KeypadKey) -> Unit = { key ->
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onKey(key)
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(SportDimens.SpacingS),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        KeypadRow {
            DigitKey(1, press)
            DigitKey(2, press)
            DigitKey(3, press)
            KeypadButton(
                onClick = { press(KeypadKey.Backspace) },
                description = stringResource(R.string.keypad_backspace),
                icon = Icons.AutoMirrored.Outlined.Backspace,
            )
        }
        KeypadRow {
            DigitKey(4, press)
            DigitKey(5, press)
            DigitKey(6, press)
            KeypadButton(
                onClick = { press(KeypadKey.Decrement) },
                description = stringResource(R.string.keypad_decrement, stepLabel),
                label = "−$stepLabel",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        KeypadRow {
            DigitKey(7, press)
            DigitKey(8, press)
            DigitKey(9, press)
            KeypadButton(
                onClick = { press(KeypadKey.Increment) },
                description = stringResource(R.string.keypad_increment, stepLabel),
                label = "+$stepLabel",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        KeypadRow {
            if (allowDecimal) {
                KeypadButton(
                    onClick = { press(KeypadKey.DecimalSeparator) },
                    description = stringResource(R.string.keypad_decimal),
                    label = ",",
                )
            } else {
                Box(Modifier.weight(1f))
            }
            DigitKey(0, press)
            KeypadButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onDone()
                },
                description = doneLabel,
                label = doneLabel,
                weight = 2f,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
        content = content,
    )
}

@Composable
private fun RowScope.DigitKey(digit: Int, onPress: (KeypadKey) -> Unit) {
    KeypadButton(
        onClick = { onPress(KeypadKey.Digit(digit)) },
        description = digit.toString(),
        label = digit.toString(),
    )
}

@Composable
private fun RowScope.KeypadButton(
    onClick: () -> Unit,
    description: String,
    label: String? = null,
    icon: ImageVector? = null,
    weight: Float = 1f,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(weight)
            .height(SportDimens.TouchTargetPrimary)
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(icon, contentDescription = null)
                label != null -> Text(text = label, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0C0E, widthDp = 360)
@Composable
private fun NumericKeypadPreview() {
    SportTheme {
        NumericKeypad(onKey = {}, onDone = {}, stepLabel = "2,5")
    }
}
