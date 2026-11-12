package com.example.sport.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sport.R
import com.example.sport.core.data.ExerciseTargets
import com.example.sport.core.designsystem.component.FormDialog
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles
import com.example.sport.core.ui.format.Formatters
import java.time.DayOfWeek

@Composable
internal fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    FormDialog(
        title = title,
        confirmLabel = stringResource(R.string.builder_save),
        dismissLabel = stringResource(R.string.builder_cancel),
        confirmEnabled = text.isNotBlank(),
        onConfirm = {
            onConfirm(text)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Copia della scheda; "Settimana di scarico" propone anche il nome adatto finché l'utente non lo cambia. */
@Composable
internal fun DuplicateDialog(
    copyName: String,
    deloadName: String,
    onConfirm: (name: String, deload: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deload by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(copyName) }
    FormDialog(
        title = stringResource(R.string.builder_variant_title),
        confirmLabel = stringResource(R.string.builder_variant_create),
        dismissLabel = stringResource(R.string.builder_cancel),
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onConfirm(name, deload)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.builder_variant_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SportDimens.TouchTargetMin)
                    .toggleable(value = deload, role = Role.Switch) { checked ->
                        if (name == copyName || name == deloadName) name = if (checked) deloadName else copyName
                        deload = checked
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.builder_variant_deload), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.builder_variant_deload_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = deload, onCheckedChange = null)
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.builder_delete_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.builder_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeekdaySheet(
    dayOfWeek: DayOfWeek,
    days: List<BuilderDayUi>,
    currentDayId: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = SportDimens.SpacingL)) {
            SheetTitle(stringResource(R.string.builder_assign_title, Formatters.dayOfWeek(dayOfWeek)))
            days.forEach { day ->
                ChoiceRow(day.name, selected = day.id == currentDayId) {
                    onPick(day.id)
                    onDismiss()
                }
            }
            ChoiceRow(stringResource(R.string.builder_rest), selected = currentDayId == null) {
                onPick(null)
                onDismiss()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToDaySheet(exerciseName: String, days: List<BuilderDayUi>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = SportDimens.SpacingL)) {
            SheetTitle(stringResource(R.string.builder_move_to_title, exerciseName))
            days.forEach { day ->
                ChoiceRow(day.name, selected = false) {
                    onPick(day.id)
                    onDismiss()
                }
            }
        }
    }
}

/** Obiettivi dell'esercizio: serie, intervallo di ripetizioni e recupero, con passi da un tocco. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TargetsSheet(exercise: BuilderExerciseUi, onApply: (ExerciseTargets) -> Unit, onDismiss: () -> Unit) {
    var sets by rememberSaveable { mutableIntStateOf(exercise.sets) }
    var repsMin by rememberSaveable { mutableIntStateOf(exercise.repsMin ?: exercise.repsMax ?: DEFAULT_REPS) }
    var repsMax by rememberSaveable { mutableIntStateOf(exercise.repsMax ?: exercise.repsMin ?: DEFAULT_REPS) }
    var rest by rememberSaveable { mutableStateOf(if (exercise.restIsDefault) null else exercise.restSeconds) }
    val setsLabel = stringResource(R.string.builder_targets_sets)
    val repsMinLabel = stringResource(R.string.builder_targets_reps_min)
    val repsMaxLabel = stringResource(R.string.builder_targets_reps_max)
    val restLabel = stringResource(R.string.builder_targets_rest)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = SportDimens.ScreenPadding)
                .padding(bottom = SportDimens.SpacingL),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge)
            Stepper(setsLabel, sets.toString(), onMinus = { sets-- }, onPlus = { sets++ }, canMinus = sets > ExerciseTargets.MIN_SETS, canPlus = sets < ExerciseTargets.MAX_SETS)
            if (exercise.countsReps) {
                Stepper(
                    repsMinLabel,
                    repsMin.toString(),
                    onMinus = { repsMin-- },
                    onPlus = {
                        repsMin++
                        if (repsMax < repsMin) repsMax = repsMin
                    },
                    canMinus = repsMin > 1,
                    canPlus = repsMin < ExerciseTargets.MAX_REPS,
                )
                Stepper(
                    repsMaxLabel,
                    repsMax.toString(),
                    onMinus = {
                        repsMax--
                        if (repsMin > repsMax) repsMin = repsMax
                    },
                    onPlus = { repsMax++ },
                    canMinus = repsMax > 1,
                    canPlus = repsMax < ExerciseTargets.MAX_REPS,
                )
            }
            val effectiveRest = rest ?: exercise.defaultRestSeconds
            Stepper(
                restLabel,
                Formatters.duration(effectiveRest.toLong()),
                onMinus = { rest = (effectiveRest - REST_STEP).coerceAtLeast(0) },
                onPlus = { rest = (effectiveRest + REST_STEP).coerceAtMost(ExerciseTargets.MAX_REST_SECONDS) },
                canMinus = effectiveRest > 0,
                canPlus = effectiveRest < ExerciseTargets.MAX_REST_SECONDS,
            )
            if (rest != null && rest != exercise.defaultRestSeconds) {
                TextButton(onClick = { rest = null }) {
                    Text(stringResource(R.string.builder_targets_rest_default, Formatters.duration(exercise.defaultRestSeconds.toLong())))
                }
            }
            PrimaryActionButton(
                text = stringResource(R.string.builder_targets_apply),
                onClick = {
                    onApply(
                        ExerciseTargets(
                            sets = sets,
                            repsMin = if (exercise.countsReps) repsMin else null,
                            repsMax = if (exercise.countsReps) repsMax else null,
                            restSeconds = rest?.takeIf { it != exercise.defaultRestSeconds },
                        ),
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(top = SportDimens.SpacingS),
            )
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    canMinus: Boolean,
    canPlus: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = false) { stateDescription = value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus, enabled = canMinus, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.builder_decrease, label))
        }
        Text(
            value,
            style = SportNumberStyles.Row,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = SportDimens.SpacingS),
        )
        FilledTonalIconButton(onClick = onPlus, enabled = canPlus, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.builder_increase, label))
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SportDimens.TouchTargetMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = SportDimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private const val DEFAULT_REPS = 10
private const val REST_STEP = 15
