package com.example.sport.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.sport.R
import com.example.sport.core.data.SetValues
import com.example.sport.core.designsystem.component.FormDialog
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.format.LocalWeightUnit
import kotlin.math.roundToInt

/** Conferma generica di un'eliminazione nello storico (serie, record, allenamento). */
@Composable
internal fun DestructiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "Azzera storico" di un solo esercizio: dice con precisione cosa sparisce e cosa resta. */
@Composable
internal fun ResetHistoryDialog(exerciseName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DestructiveConfirmDialog(
        title = stringResource(R.string.history_reset_title, exerciseName),
        message = stringResource(R.string.history_reset_message),
        confirmLabel = stringResource(R.string.history_reset_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Serie di un allenamento concluso da correggere: valori salvati (kg, secondi, metri). */
internal data class SetEditTarget(
    val setId: Long,
    val trackingType: ExerciseTrackingType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    /** Etichetta attuale ("100 kg × 5"), usata nel titolo e nella conferma di eliminazione. */
    val description: String,
)

/** Testi dei campi → valori da salvare; null se manca un dato o un numero non è valido. */
internal fun parseSetValues(
    type: ExerciseTrackingType,
    unit: WeightUnit,
    weight: String,
    reps: String,
    seconds: String,
    kilometers: String,
    minutes: String,
): SetValues? {
    fun number(text: String): Double? = Formatters.parseDecimal(text)?.takeIf { it >= 0 }
    fun whole(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }
    fun isBlankOr(text: String, parsed: Any?) = text.isBlank() || parsed != null
    val values = when (type) {
        ExerciseTrackingType.WEIGHT_REPS,
        ExerciseTrackingType.BODYWEIGHT_REPS,
        ExerciseTrackingType.ASSISTED_BODYWEIGHT,
        -> {
            val typedWeight = number(weight)
            if (!isBlankOr(weight, typedWeight)) return null
            SetValues(weightKg = typedWeight?.let(unit::toKg), reps = whole(reps) ?: return null)
        }
        ExerciseTrackingType.DURATION -> SetValues(durationSeconds = whole(seconds) ?: return null)
        ExerciseTrackingType.DISTANCE_DURATION -> {
            val typedKm = number(kilometers)
            val typedMinutes = whole(minutes)
            if (!isBlankOr(kilometers, typedKm) || !isBlankOr(minutes, typedMinutes)) return null
            SetValues(distanceMeters = typedKm?.let { it * 1000 }, durationSeconds = typedMinutes?.let { it * 60 })
        }
    }
    return values.takeIf { it.isValid }
}

/**
 * Correzione di una serie già registrata: gli stessi campi del logger per quel tipo di esercizio,
 * nell'unità scelta dall'utente. "Elimina serie" chiede conferma a parte.
 */
@Composable
internal fun SetEditDialog(
    target: SetEditTarget,
    onSave: (SetValues) -> Unit,
    onDeleteRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val unit = LocalWeightUnit.current
    var weight by remember(target.setId) { mutableStateOf(Formatters.editable(target.weightKg?.let(unit::fromKg)?.let(::roundForEditing))) }
    var reps by remember(target.setId) { mutableStateOf(target.reps?.toString().orEmpty()) }
    var seconds by remember(target.setId) { mutableStateOf(target.durationSeconds?.toString().orEmpty()) }
    var kilometers by remember(target.setId) { mutableStateOf(Formatters.editable(target.distanceMeters?.let { it / 1000 })) }
    var minutes by remember(target.setId) { mutableStateOf(target.durationSeconds?.let { (it / 60.0).roundToInt().toString() }.orEmpty()) }
    val values = parseSetValues(target.trackingType, unit, weight, reps, seconds, kilometers, minutes)

    FormDialog(
        title = stringResource(R.string.set_edit_title),
        confirmLabel = stringResource(R.string.action_save),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = { values?.let(onSave) },
        onDismiss = onDismiss,
        confirmEnabled = values != null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            Text(
                stringResource(R.string.set_edit_current, target.description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (target.trackingType) {
                ExerciseTrackingType.WEIGHT_REPS,
                ExerciseTrackingType.BODYWEIGHT_REPS,
                ExerciseTrackingType.ASSISTED_BODYWEIGHT,
                -> {
                    NumberField(weight, { weight = it }, stringResource(R.string.set_edit_weight, unit.symbol), decimal = true)
                    NumberField(reps, { reps = it }, stringResource(R.string.set_edit_reps), decimal = false)
                }
                ExerciseTrackingType.DURATION ->
                    NumberField(seconds, { seconds = it }, stringResource(R.string.set_edit_seconds), decimal = false)
                ExerciseTrackingType.DISTANCE_DURATION -> {
                    NumberField(kilometers, { kilometers = it }, stringResource(R.string.set_edit_kilometers), decimal = true)
                    NumberField(minutes, { minutes = it }, stringResource(R.string.set_edit_minutes), decimal = false)
                }
            }
            TextButton(onClick = onDeleteRequest) {
                Text(stringResource(R.string.set_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Due decimali bastano per ripresentare un carico convertito in libbre senza code di cifre. */
private fun roundForEditing(value: Double): Double = (value * 100).roundToInt() / 100.0

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, decimal: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
