package com.example.sport.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sport.R
import com.example.sport.core.designsystem.component.FormDialog
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.ui.setup.SetupFields
import com.example.sport.core.ui.setup.hasSetupContent
import com.example.sport.core.ui.setup.initialSetupRows
import com.example.sport.core.ui.setup.toParams

/** Profilo in modifica nella scheda esercizio ([setup] null = nuovo profilo). */
internal data class SetupEditorTarget(val setup: SavedSetupUi?)

/** Nessuna regolazione salvata: spiega a cosa serve e invita a compilarla subito. */
@Composable
internal fun SetupEmptyCard(hints: List<String>, onAdd: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SportDimens.SpacingM), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            Text(stringResource(R.string.exercise_setup_empty), style = MaterialTheme.typography.bodyMedium)
            if (hints.isNotEmpty()) {
                Text(
                    stringResource(R.string.exercise_setup_suggested, hints.joinToString(" · ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.exercise_setup_add))
            }
        }
    }
}

/** Un profilo salvato: voci, note e matita per modificarlo senza avviare un allenamento. */
@Composable
internal fun SavedSetupCard(setup: SavedSetupUi, onEdit: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = SportDimens.SpacingM, top = SportDimens.SpacingS, bottom = SportDimens.SpacingM),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = SportDimens.SpacingS),
            )
            Column(
                Modifier.weight(1f).padding(top = SportDimens.SpacingS),
                verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
            ) {
                Text(
                    if (setup.isLatest) stringResource(R.string.exercise_setup_latest, setup.label) else setup.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                setup.params.forEach { param ->
                    Text(
                        stringResource(R.string.exercise_setup_param, param.name, param.value),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                setup.notes?.let { notes ->
                    Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.exercise_setup_edit, setup.label))
            }
        }
    }
}

/**
 * Editor di un profilo di regolazione fuori dall'allenamento: nome del profilo, voci, note.
 * [onDelete] è null per un profilo nuovo.
 */
@Composable
internal fun SetupEditorDialog(
    exerciseName: String,
    setup: SavedSetupUi?,
    hints: List<String>,
    defaultLabel: String,
    onSave: (label: String, params: List<MachineSetupParam>, notes: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val rows = remember(setup?.id) { mutableStateListOf(*initialSetupRows(setup?.params.orEmpty(), hints).toTypedArray()) }
    var label by remember(setup?.id) { mutableStateOf(setup?.label ?: defaultLabel) }
    var notes by remember(setup?.id) { mutableStateOf(setup?.notes.orEmpty()) }
    var confirmDelete by remember(setup?.id) { mutableStateOf(false) }
    FormDialog(
        title = stringResource(if (setup == null) R.string.exercise_setup_new_title else R.string.exercise_setup_edit_title),
        confirmLabel = stringResource(R.string.action_save),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = { onSave(label.ifBlank { defaultLabel }, rows.toParams(), notes) },
        onDismiss = onDismiss,
        confirmEnabled = hasSetupContent(rows, notes),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            Text(exerciseName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.exercise_setup_profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SetupFields(rows = rows, notes = notes, onNotesChange = { notes = it })
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.exercise_setup_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete && onDelete != null && setup != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.exercise_setup_delete_title, setup.label)) },
            text = { Text(stringResource(R.string.exercise_setup_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
