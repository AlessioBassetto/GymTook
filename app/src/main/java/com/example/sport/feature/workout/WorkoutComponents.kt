package com.example.sport.feature.workout

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sport.R
import com.example.sport.core.data.SetMetric
import com.example.sport.core.designsystem.component.NumericKeypad
import com.example.sport.core.designsystem.component.NumericValueField
import com.example.sport.core.designsystem.input.KeypadKey
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.SetType
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.format.LocalWeightUnit
import com.example.sport.core.ui.labels.labelRes
import com.example.sport.core.ui.time.rememberIsVisible
import com.example.sport.core.ui.time.rememberTickingNow
import com.example.sport.core.workout.RestTimerState
import java.time.Instant

private val BadgeWidth = 36.dp
private val CheckSize = 52.dp
private const val PREVIOUS_WEIGHT = 1.1f

/** Peso relativo delle colonne: si adattano alla larghezza dello schermo. */
private fun SetMetric.columnWeight(): Float = when (this) {
    SetMetric.WEIGHT, SetMetric.DISTANCE_KM -> 1.25f
    SetMetric.REPS, SetMetric.DURATION_MINUTES -> 1f
    SetMetric.DURATION_SECONDS -> 1.25f
    SetMetric.RPE, SetMetric.RIR -> 0.9f
}

/** Intestazione della colonna; il peso usa l'unità scelta ("kg", "+lb" con la zavorra, "−kg" con l'assistenza). */
@Composable
internal fun SetMetric.columnLabel(type: ExerciseTrackingType): String = when (this) {
    SetMetric.WEIGHT -> {
        val unit = LocalWeightUnit.current.symbol
        when (type) {
            ExerciseTrackingType.BODYWEIGHT_REPS -> stringResource(R.string.workout_col_added, unit)
            ExerciseTrackingType.ASSISTED_BODYWEIGHT -> stringResource(R.string.workout_col_assist, unit)
            else -> unit
        }
    }
    else -> stringResource(columnLabelRes())
}

@StringRes
private fun SetMetric.columnLabelRes(): Int = when (this) {
    SetMetric.WEIGHT -> R.string.workout_field_weight
    SetMetric.REPS -> R.string.workout_col_reps
    SetMetric.RPE -> R.string.workout_col_rpe
    SetMetric.RIR -> R.string.workout_col_rir
    SetMetric.DURATION_SECONDS -> R.string.workout_col_seconds
    SetMetric.DURATION_MINUTES -> R.string.workout_col_minutes
    SetMetric.DISTANCE_KM -> R.string.workout_col_km
}

@StringRes
internal fun SetMetric.fieldLabelRes(): Int = when (this) {
    SetMetric.WEIGHT -> R.string.workout_field_weight
    SetMetric.REPS -> R.string.workout_field_reps
    SetMetric.RPE -> R.string.workout_field_rpe
    SetMetric.RIR -> R.string.workout_field_rir
    SetMetric.DURATION_SECONDS -> R.string.workout_field_seconds
    SetMetric.DURATION_MINUTES -> R.string.workout_field_minutes
    SetMetric.DISTANCE_KM -> R.string.workout_field_km
}

/** Intestazioni delle colonne, allineate alla [SetRow]. */
@Composable
internal fun SetColumnLabels(metrics: List<SetMetric>, trackingType: ExerciseTrackingType) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.SpacingM),
        horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.workout_col_set), style = style, color = color, modifier = Modifier.width(BadgeWidth))
        Text(stringResource(R.string.workout_col_previous), style = style, color = color, modifier = Modifier.weight(PREVIOUS_WEIGHT))
        metrics.forEach { metric ->
            Text(
                metric.columnLabel(trackingType),
                style = style,
                color = color,
                modifier = Modifier.weight(metric.columnWeight()),
            )
        }
        Spacer(Modifier.width(CheckSize))
    }
}

/**
 * Riga serie: [n°/tipo] [precedente] [campi] [✓]. Nel caso tipico (valori pre-compilati) basta
 * un tocco sulla spunta; i campi aprono il tastierino dell'app.
 */
@Composable
internal fun SetRow(
    row: SetRowUi,
    metrics: List<SetMetric>,
    trackingType: ExerciseTrackingType,
    editing: EditingUi?,
    onFieldClick: (SetMetric) -> Unit,
    onToggleComplete: () -> Unit,
    onTypeChange: (SetType) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = SportTheme.extendedColors
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (row.isCompleted) extended.completedContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = SportDimens.TouchTargetMin + SportDimens.SpacingS)
                .padding(horizontal = SportDimens.SpacingM, vertical = SportDimens.SpacingXs),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetBadge(row, onTypeChange, onDelete)
            Text(
                text = row.previous ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(PREVIOUS_WEIGHT),
            )
            metrics.forEach { metric ->
                val isActive = editing?.setId == row.setId && editing.metric == metric
                NumericValueField(
                    value = if (isActive) editing.text else row.values[metric].orEmpty(),
                    placeholder = "–",
                    isActive = isActive,
                    onClick = { onFieldClick(metric) },
                    accessibilityLabel = stringResource(metric.fieldLabelRes()) + " " + metric.columnLabel(trackingType),
                    textStyle = SportNumberStyles.Row,
                    modifier = Modifier.weight(metric.columnWeight()),
                )
            }
            CompleteButton(
                completed = row.isCompleted,
                description = stringResource(
                    if (row.isCompleted) R.string.workout_set_uncomplete else R.string.workout_set_complete,
                    row.position,
                ),
                onClick = {
                    haptics.performHapticFeedback(
                        if (row.isCompleted) HapticFeedbackType.ToggleOff else HapticFeedbackType.Confirm,
                    )
                    onToggleComplete()
                },
            )
        }
    }
}

@Composable
private fun SetBadge(row: SetRowUi, onTypeChange: (SetType) -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val label = when (row.type) {
        SetType.WARMUP -> stringResource(R.string.set_badge_warmup)
        SetType.DROP -> stringResource(R.string.set_badge_drop)
        SetType.FAILURE -> stringResource(R.string.set_badge_failure)
        SetType.WORKING -> row.number.toString()
    }
    val description = stringResource(R.string.workout_set_options, row.position)
    Box {
        Surface(
            onClick = { menuOpen = true },
            shape = MaterialTheme.shapes.small,
            color = if (row.type == SetType.WORKING) colors.surfaceContainerHighest else colors.secondaryContainer,
            modifier = Modifier
                .size(width = BadgeWidth, height = SportDimens.TouchTargetMin)
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (row.isRecord) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = stringResource(R.string.workout_record),
                        tint = SportTheme.extendedColors.record,
                    )
                } else {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            SetType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes())) },
                    onClick = {
                        menuOpen = false
                        onTypeChange(type)
                    },
                    leadingIcon = if (type == row.type) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_set_delete), color = colors.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun CompleteButton(completed: Boolean, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (completed) colors.primary else colors.surfaceContainerHighest,
        contentColor = if (completed) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier.size(CheckSize).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

/** Tastierino ancorato in basso, con il campo in modifica nel titolo. */
@Composable
internal fun KeypadPanel(
    editing: EditingUi,
    onKey: (KeypadKey) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = SportDimens.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.workout_keypad_title,
                        stringResource(editing.metric.fieldLabelRes()),
                        editing.setPosition,
                        editing.exerciseName,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.workout_keypad_close))
                }
            }
            NumericKeypad(
                onKey = onKey,
                onDone = onNext,
                stepLabel = editing.stepLabel,
                allowDecimal = editing.allowDecimal,
            )
        }
    }
}

/**
 * Pannello del recupero: tempo residuo grande, ±15 s e "Salta". Quando il tempo si azzera chiama
 * [onElapsed] una sola volta, ma solo con il logger visibile (avviso aptico). A schermo spento o con
 * l'app in background la fine la segnala l'allarme con notifica e vibrazione: Android ignora le
 * vibrazioni delle app in background, quindi chiudere il recupero da qui lo renderebbe muto.
 */
@Composable
internal fun RestTimerBar(
    rest: RestTimerState,
    now: () -> Instant,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    onElapsed: () -> Unit,
    showExactAlarmHint: Boolean = false,
    onGrantExactAlarms: () -> Unit = {},
    onDismissExactAlarmHint: () -> Unit = {},
) {
    val extended = SportTheme.extendedColors
    val visible = rememberIsVisible()
    val currentNow by rememberTickingNow(periodMillis = 250, now = now)
    val remaining = rest.remainingSeconds(currentNow)
    LaunchedEffect(rest.endsAt, remaining == 0L, visible) {
        if (remaining == 0L && visible) onElapsed()
    }
    Surface(color = extended.restContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rest_title), style = MaterialTheme.typography.labelMedium, color = extended.rest)
                    Text(Formatters.duration(remaining), style = MaterialTheme.typography.displaySmall, color = extended.rest)
                    Text(
                        stringResource(R.string.rest_next, rest.nextExercise),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RestButton(R.string.rest_minus) { onAdjust(-15) }
                Spacer(Modifier.width(SportDimens.SpacingXs))
                RestButton(R.string.rest_plus) { onAdjust(15) }
                Spacer(Modifier.width(SportDimens.SpacingXs))
                TextButton(onClick = onSkip, modifier = Modifier.defaultMinSize(minHeight = SportDimens.TouchTargetMin)) {
                    Text(stringResource(R.string.rest_skip), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(SportDimens.SpacingXs))
            LinearProgressIndicator(
                progress = { if (rest.totalSeconds > 0) remaining.toFloat() / rest.totalSeconds else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = extended.rest,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            if (showExactAlarmHint) {
                ExactAlarmHint(onGrant = onGrantExactAlarms, onDismiss = onDismissExactAlarmHint)
            }
        }
    }
}

/** Invito a concedere le sveglie esatte: compare nel pannello del recupero, dove serve. */
@Composable
private fun ExactAlarmHint(onGrant: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = SportDimens.SpacingS), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.rest_exact_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = SportDimens.TouchTargetMin)) {
            Text(stringResource(R.string.rest_exact_dismiss), style = MaterialTheme.typography.labelLarge)
        }
        FilledTonalButton(onClick = onGrant, modifier = Modifier.defaultMinSize(minHeight = SportDimens.TouchTargetMin)) {
            Text(stringResource(R.string.rest_exact_grant), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RowScope.RestButton(@StringRes label: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = SportDimens.TouchTargetMin),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = SportDimens.SpacingM),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

/** Vibrazione ben percepibile a fine recupero (più forte del feedback aptico dei tasti). */
internal fun vibrateRestFinished(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1))
}
