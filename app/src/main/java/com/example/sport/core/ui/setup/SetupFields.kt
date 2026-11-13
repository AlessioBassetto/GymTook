package com.example.sport.core.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sport.R
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.normalized

/** Voce di regolazione in modifica ("Schienale" → "3"). */
@Immutable
data class SetupRowState(val name: String, val value: String)

/**
 * Righe iniziali dell'editor: i valori salvati; al primo utilizzo i nomi suggeriti dal catalogo
 * (es. "Schienale", "Rullo tibie") da compilare; altrimenti una riga vuota.
 */
fun initialSetupRows(params: List<MachineSetupParam>, hints: List<String>): List<SetupRowState> = when {
    params.isNotEmpty() -> params.map { SetupRowState(it.name, it.value) }
    hints.isNotEmpty() -> hints.map { SetupRowState(it, "") }
    else -> listOf(SetupRowState("", ""))
}

fun List<SetupRowState>.toParams(): List<MachineSetupParam> = map { MachineSetupParam(it.name, it.value) }

/** C'è qualcosa da salvare: almeno una voce con nome e valore, oppure una nota. */
fun hasSetupContent(rows: List<SetupRowState>, notes: String): Boolean =
    rows.toParams().normalized().isNotEmpty() || notes.isNotBlank()

/**
 * Campi della regolazione di un macchinario, condivisi dal logger e dalla scheda esercizio: voci
 * "nome / valore" e note libere. Tastiera di sistema: i valori possono essere lettere ("B") o
 * testo ("foro 2", "40 kg").
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
