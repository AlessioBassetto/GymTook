package com.example.sport.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sport.R
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.ui.setup.SetupFields
import com.example.sport.core.ui.setup.initialSetupRows
import com.example.sport.core.ui.setup.toParams

/**
 * Editor della regolazione macchinario nel logger. Al primo utilizzo propone i nomi suggeriti dal
 * catalogo (es. "Schienale", "Rullo tibie") con i valori da compilare; dopo, i valori e le note
 * dell'ultima volta, anche se inseriti "a freddo" dalla scheda esercizio.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MachineSetupSheet(
    sheet: SetupSheetUi,
    onDismiss: () -> Unit,
    onSave: (params: List<MachineSetupParam>, notes: String) -> Unit,
    onSaveAsNew: (label: String, params: List<MachineSetupParam>, notes: String) -> Unit,
    onSelectProfile: (Long) -> Unit,
) {
    val rows = remember(sheet.sessionExerciseId) {
        mutableStateListOf(*initialSetupRows(sheet.params, sheet.hints).toTypedArray())
    }
    var notes by remember(sheet.sessionExerciseId) { mutableStateOf(sheet.notes) }
    var creatingProfile by remember(sheet.sessionExerciseId) { mutableStateOf(false) }
    var profileLabel by remember(sheet.sessionExerciseId) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            Text(stringResource(R.string.setup_title, sheet.exerciseName), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.setup_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sheet.profiles.isNotEmpty()) {
                Text(stringResource(R.string.setup_profiles), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                    sheet.profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.id == sheet.currentSetupId,
                            onClick = { onSelectProfile(profile.id) },
                            label = { Text(profile.label) },
                        )
                    }
                }
            }
            SetupFields(rows = rows, notes = notes, onNotesChange = { notes = it })
            val defaultLabel = stringResource(R.string.setup_default_label)
            PrimaryActionButton(text = stringResource(R.string.setup_save), onClick = { onSave(rows.toParams(), notes) })
            if (creatingProfile) {
                OutlinedTextField(
                    value = profileLabel,
                    onValueChange = { profileLabel = it },
                    label = { Text(stringResource(R.string.setup_new_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryActionButton(
                    text = stringResource(R.string.setup_save_as_new),
                    onClick = { onSaveAsNew(profileLabel.ifBlank { defaultLabel }, rows.toParams(), notes) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { creatingProfile = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_save_as_new))
                }
            }
        }
    }
}
