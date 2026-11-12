package com.example.sport.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sport.R
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.MachineSetupParam

private data class SetupRow(val name: String, val value: String)

/**
 * Editor della regolazione macchinario. Al primo utilizzo propone i nomi suggeriti dal catalogo
 * (es. "Schienale", "Rullo tibie") con i valori da compilare; dopo, i valori dell'ultima volta.
 * Qui si usa la tastiera di sistema: i valori possono essere lettere ("B") o testo ("foro 2").
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MachineSetupSheet(
    sheet: SetupSheetUi,
    onDismiss: () -> Unit,
    onSave: (List<MachineSetupParam>) -> Unit,
    onSaveAsNew: (label: String, params: List<MachineSetupParam>) -> Unit,
    onSelectProfile: (Long) -> Unit,
) {
    val rows = remember(sheet.sessionExerciseId) {
        val initial = when {
            sheet.params.isNotEmpty() -> sheet.params.map { SetupRow(it.name, it.value) }
            sheet.hints.isNotEmpty() -> sheet.hints.map { SetupRow(it, "") }
            else -> listOf(SetupRow("", ""))
        }
        mutableStateListOf(*initial.toTypedArray())
    }
    var creatingProfile by remember(sheet.sessionExerciseId) { mutableStateOf(false) }
    var profileLabel by remember(sheet.sessionExerciseId) { mutableStateOf("") }
    val params = { rows.map { MachineSetupParam(it.name, it.value) } }

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
            TextButton(onClick = { rows.add(SetupRow("", "")) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.setup_add_row))
            }
            val defaultLabel = stringResource(R.string.setup_default_label)
            PrimaryActionButton(text = stringResource(R.string.setup_save), onClick = { onSave(params()) })
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
                    onClick = { onSaveAsNew(profileLabel.ifBlank { defaultLabel }, params()) },
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
