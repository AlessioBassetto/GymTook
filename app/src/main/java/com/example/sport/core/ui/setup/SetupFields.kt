package com.example.sport.core.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sport.R
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.TreadmillSettings
import com.example.sport.core.model.normalized

/** Voce di regolazione in modifica ("Schienale" → "3"). */
@Immutable
data class SetupRowState(val name: String, val value: String)

/**
 * Righe iniziali dell'editor: i valori salvati; al primo utilizzo i nomi suggeriti dal catalogo
 * (es. "Schienale", "Rullo tibie") da compilare; altrimenti una riga vuota. Le voci fisse del tapis
 * roulant (Pendenza, Velocità) ci sono sempre quando il catalogo le suggerisce, anche se un profilo
 * salvato prima non le aveva.
 */
fun initialSetupRows(params: List<MachineSetupParam>, hints: List<String>): List<SetupRowState> = when {
    params.isNotEmpty() -> {
        val missingTreadmill = hints.filter { hint ->
            TreadmillSettings.isTreadmillParam(hint) && params.none { it.name.trim().equals(hint, ignoreCase = true) }
        }
        missingTreadmill.map { SetupRowState(it, "") } + params.map { SetupRowState(it.name, it.value) }
    }
    hints.isNotEmpty() -> hints.map { SetupRowState(it, "") }
    else -> listOf(SetupRowState("", ""))
}

/**
 * Righe → voci da salvare. Pendenza e velocità del tapis si salvano nel formato italiano ("6,5")
 * e dentro i limiti; un valore non numerico vale come vuoto (la voce non viene salvata).
 */
fun List<SetupRowState>.toParams(): List<MachineSetupParam> = map { row ->
    val value = if (TreadmillSettings.isTreadmillParam(row.name)) treadmillValue(row)?.let(TreadmillSettings::format).orEmpty() else row.value
    MachineSetupParam(row.name, value)
}

/** C'è qualcosa da salvare: almeno una voce con nome e valore, oppure una nota. */
fun hasSetupContent(rows: List<SetupRowState>, notes: String): Boolean =
    rows.toParams().normalized().isNotEmpty() || notes.isNotBlank()

/**
 * Campi della regolazione di un macchinario, condivisi dal logger e dalla scheda esercizio: voci
 * "nome / valore" e note libere. Tastiera di sistema: i valori possono essere lettere ("B") o
 * testo ("foro 2", "40 kg"). Le voci del tapis roulant (Pendenza %, Velocità km/h) hanno un campo
 * numerico con i pulsanti di incremento rapido e non si possono rinominare né togliere.
 */
@Composable
fun SetupFields(
    rows: SnapshotStateList<SetupRowState>,
    notes: String,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
        rows.forEachIndexed { index, row ->
            if (TreadmillSettings.isTreadmillParam(row.name)) {
                TreadmillSettingRow(row = row, onChange = { rows[index] = it })
                return@forEachIndexed
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { rows[index] = row.copy(name = it) },
                    label = { Text(stringResource(R.string.setup_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                OutlinedTextField(
                    value = row.value,
                    onValueChange = { rows[index] = row.copy(value = it) },
                    label = { Text(stringResource(R.string.setup_value)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { rows.removeAt(index) }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.setup_remove_row))
                }
            }
        }
        TextButton(onClick = { rows.add(SetupRowState("", "")) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.setup_add_row))
        }
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text(stringResource(R.string.setup_notes)) },
            placeholder = { Text(stringResource(R.string.setup_notes_placeholder)) },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Regole di una voce del tapis: passi rapidi e limiti. */
private data class TreadmillField(val labelRes: Int, val unit: String, val steps: List<Double>, val min: Double, val max: Double)

private fun treadmillField(name: String): TreadmillField =
    if (name.trim().equals(TreadmillSettings.INCLINE, ignoreCase = true)) {
        TreadmillField(R.string.treadmill_setup_incline, "%", listOf(TreadmillSettings.INCLINE_STEP), TreadmillSettings.MIN_INCLINE, TreadmillSettings.MAX_INCLINE)
    } else {
        TreadmillField(
            R.string.treadmill_setup_speed,
            "km/h",
            listOf(TreadmillSettings.SPEED_FINE_STEP, TreadmillSettings.SPEED_COARSE_STEP),
            TreadmillSettings.MIN_SPEED,
            TreadmillSettings.MAX_SPEED,
        )
    }

/** Valore numerico valido della voce del tapis (null se vuoto, non numerico o fuori scala). */
private fun treadmillValue(row: SetupRowState): Double? {
    val field = treadmillField(row.name)
    return TreadmillSettings.parse(row.value)?.takeIf { it in field.min..field.max }
}

/**
 * Pendenza o velocità del tapis: − passi | valore | + passi. I pulsanti partono dal valore scritto
 * (o da zero) e restano nei limiti; il campo accetta virgola o punto.
 */
@Composable
private fun TreadmillSettingRow(row: SetupRowState, onChange: (SetupRowState) -> Unit) {
    val field = treadmillField(row.name)
    val label = stringResource(field.labelRes)
    val invalid = row.value.isNotBlank() && treadmillValue(row) == null
    fun stepBy(delta: Double) {
        val current = TreadmillSettings.parse(row.value)?.coerceIn(field.min, field.max) ?: field.min
        val next = Math.round((current + delta) * 10) / 10.0
        onChange(row.copy(value = TreadmillSettings.format(next.coerceIn(field.min, field.max))))
    }
    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = row.value,
            onValueChange = { typed -> onChange(row.copy(value = typed.filter { it.isDigit() || it == ',' || it == '.' }.take(5))) },
            singleLine = true,
            isError = invalid,
            suffix = { Text(field.unit) },
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
        // Passi rapidi sotto il campo, a tutta larghezza: anche nei dialoghi stretti restano leggibili.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
        ) {
            (field.steps.asReversed().map { -it } + field.steps).forEach { delta ->
                StepButton(label, delta, ::stepBy, Modifier.weight(1f))
            }
        }
        if (invalid) {
            Text(
                stringResource(R.string.treadmill_setup_out_of_range, TreadmillSettings.format(field.min), TreadmillSettings.format(field.max)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun StepButton(label: String, delta: Double, onStep: (Double) -> Unit, modifier: Modifier = Modifier) {
    val text = (if (delta < 0) "−" else "+") + TreadmillSettings.format(kotlin.math.abs(delta))
    val description = stringResource(R.string.treadmill_setup_step, text, label)
    FilledTonalButton(
        onClick = { onStep(delta) },
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = modifier
            .heightIn(min = SportDimens.TouchTargetMin)
            .semantics { contentDescription = description },
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
