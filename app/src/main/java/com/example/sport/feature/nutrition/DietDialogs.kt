package com.example.sport.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.sport.R
import com.example.sport.core.data.DietTargets
import com.example.sport.core.data.MealItemInput
import com.example.sport.core.data.caloriesFromMacros
import com.example.sport.core.designsystem.component.FormDialog
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.MealType
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import java.time.LocalTime
import kotlin.math.roundToInt

/** Nome di un piano (nuovo, rinomina, copia). */
@Composable
internal fun PlanNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    FormDialog(
        title = title,
        confirmLabel = confirmLabel,
        dismissLabel = stringResource(R.string.diet_cancel),
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onConfirm(name.trim())
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.diet_plan_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Obiettivi giornalieri: calorie e grammi dei tre macronutrienti, tutti facoltativi. */
@Composable
internal fun TargetsDialog(targets: DietTargets, onConfirm: (DietTargets) -> Unit, onDismiss: () -> Unit) {
    var kcal by rememberSaveable { mutableStateOf(targets.caloriesKcal?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(Formatters.editable(targets.proteinG)) }
    var carbs by rememberSaveable { mutableStateOf(Formatters.editable(targets.carbsG)) }
    var fat by rememberSaveable { mutableStateOf(Formatters.editable(targets.fatG)) }
    val fields = listOf(kcal, protein, carbs, fat)
    val valid = fields.all(::isValidNumber)
    FormDialog(
        title = stringResource(R.string.diet_targets),
        confirmLabel = stringResource(R.string.diet_save),
        dismissLabel = stringResource(R.string.diet_cancel),
        confirmEnabled = valid,
        onConfirm = {
            onConfirm(
                DietTargets(
                    caloriesKcal = Formatters.parseDecimal(kcal)?.roundToInt(),
                    proteinG = Formatters.parseDecimal(protein),
                    carbsG = Formatters.parseDecimal(carbs),
                    fatG = Formatters.parseDecimal(fat),
                ),
            )
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        NumberField(kcal, { kcal = it }, stringResource(R.string.diet_kcal_label), integer = true)
        NumberField(protein, { protein = it }, stringResource(R.string.diet_protein_label))
        NumberField(carbs, { carbs = it }, stringResource(R.string.diet_carbs_label))
        NumberField(fat, { fat = it }, stringResource(R.string.diet_fat_label))
        MacroCaloriesHint(protein, carbs, fat, onUse = { kcal = it })
    }
}

/** Tipo del pasto, nome personalizzato (facoltativo) e orario indicativo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealDialog(
    title: String,
    initialType: MealType,
    initialName: String?,
    initialTime: LocalTime?,
    onConfirm: (MealType, String, LocalTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(initialType) }
    var name by rememberSaveable { mutableStateOf(initialName.orEmpty()) }
    var time by rememberSaveable { mutableStateOf(initialTime?.let(Formatters::time).orEmpty()) }
    val parsedTime = parseTime(time)
    val timeValid = time.isBlank() || parsedTime != null
    FormDialog(
        title = title,
        confirmLabel = stringResource(R.string.diet_save),
        dismissLabel = stringResource(R.string.diet_cancel),
        confirmEnabled = timeValid,
        onConfirm = {
            onConfirm(type, name, parsedTime)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.diet_meal_type), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            MealType.entries.forEach { option ->
                FilterChip(
                    selected = option == type,
                    onClick = { type = option },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.diet_meal_name)) },
            placeholder = { Text(stringResource(type.labelRes())) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = time,
            onValueChange = { time = it },
            label = { Text(stringResource(R.string.diet_meal_time)) },
            isError = !timeValid,
            supportingText = if (!timeValid) {
                { Text(stringResource(R.string.diet_meal_time_error)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Alimento: nome, quantità con unità e valori nutrizionali della porzione. Senza calorie ma con i
 * macro, le calorie si calcolano (4/4/9 kcal per grammo).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealItemDialog(
    title: String,
    initial: MealItemInput?,
    onConfirm: (MealItemInput) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(Formatters.editable(initial?.quantity)) }
    var unit by rememberSaveable { mutableStateOf(initial?.unit ?: "g") }
    var kcal by rememberSaveable { mutableStateOf(Formatters.editable(initial?.caloriesKcal)) }
    var protein by rememberSaveable { mutableStateOf(Formatters.editable(initial?.proteinG)) }
    var carbs by rememberSaveable { mutableStateOf(Formatters.editable(initial?.carbsG)) }
    var fat by rememberSaveable { mutableStateOf(Formatters.editable(initial?.fatG)) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    val valid = name.isNotBlank() && listOf(quantity, kcal, protein, carbs, fat).all(::isValidNumber)
    FormDialog(
        title = title,
        confirmLabel = stringResource(R.string.diet_save),
        dismissLabel = stringResource(R.string.diet_cancel),
        confirmEnabled = valid,
        onConfirm = {
            onConfirm(
                MealItemInput(
                    name = name,
                    quantity = Formatters.parseDecimal(quantity),
                    unit = unit,
                    caloriesKcal = Formatters.parseDecimal(kcal),
                    proteinG = Formatters.parseDecimal(protein),
                    carbsG = Formatters.parseDecimal(carbs),
                    fatG = Formatters.parseDecimal(fat),
                    notes = notes,
                ),
            )
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.diet_item_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            NumberField(quantity, { quantity = it }, stringResource(R.string.diet_item_quantity), modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text(stringResource(R.string.diet_item_unit)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            COMMON_UNITS.forEach { option ->
                FilterChip(selected = unit == option, onClick = { unit = option }, label = { Text(option) })
            }
        }
        NumberField(kcal, { kcal = it }, stringResource(R.string.diet_kcal_label))
        Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            NumberField(protein, { protein = it }, stringResource(R.string.diet_protein_short), modifier = Modifier.weight(1f))
            NumberField(carbs, { carbs = it }, stringResource(R.string.diet_carbs_short), modifier = Modifier.weight(1f))
            NumberField(fat, { fat = it }, stringResource(R.string.diet_fat_short), modifier = Modifier.weight(1f))
        }
        MacroCaloriesHint(protein, carbs, fat, onUse = { kcal = it })
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.diet_item_notes)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (onDelete != null) {
            TextButton(onClick = {
                onDelete()
                onDismiss()
            }) {
                Text(stringResource(R.string.diet_item_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Note del piano (integrazione, idratazione, indicazioni del nutrizionista). */
@Composable
internal fun NotesDialog(initial: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var notes by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    FormDialog(
        title = stringResource(R.string.diet_notes),
        confirmLabel = stringResource(R.string.diet_save),
        dismissLabel = stringResource(R.string.diet_cancel),
        onConfirm = {
            onConfirm(notes)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = { Text(stringResource(R.string.diet_notes_empty)) },
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    integer: Boolean = false,
) {
    val valid = isValidNumber(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = !valid,
        supportingText = if (!valid) {
            { Text(stringResource(R.string.diet_number_error)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
    )
}

/** "Dai macro: 520 kcal" con il pulsante per copiarlo nel campo calorie. */
@Composable
private fun MacroCaloriesHint(protein: String, carbs: String, fat: String, onUse: (String) -> Unit) {
    val computed = caloriesFromMacros(
        Formatters.parseDecimal(protein),
        Formatters.parseDecimal(carbs),
        Formatters.parseDecimal(fat),
    )?.roundToInt() ?: return
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            stringResource(R.string.diet_macros_kcal, computed.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onUse(computed.toString()) }) { Text(stringResource(R.string.diet_use_value)) }
    }
}

/** Campo vuoto o numero non negativo (virgola o punto). */
private fun isValidNumber(text: String): Boolean =
    text.isBlank() || Formatters.parseDecimal(text)?.let { it >= 0 } == true

/** "7:30", "07.30", "1930", "13" → orario; altro → null. */
internal fun parseTime(text: String): LocalTime? {
    val digits = text.trim()
    if (digits.isEmpty()) return null
    val match = TIME_PATTERN.matchEntire(digits) ?: return null
    val (hourText, minuteText) = match.destructured
    val hour = hourText.toInt()
    val minute = minuteText.ifEmpty { "0" }.toInt()
    return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}

private val TIME_PATTERN = Regex("""^(\d{1,2})(?:[:.]?(\d{2}))?$""")

private val COMMON_UNITS = listOf("g", "ml", "pz", "cucchiaio")
