package com.example.sport.feature.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.data.CustomExerciseDraft
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes

/** Azioni dell'editor (separate dal ViewModel per i test di interfaccia). */
internal data class EditorActions(
    val onNameChange: (String) -> Unit,
    val onEquipmentChange: (Equipment) -> Unit,
    val onTrackingChange: (ExerciseTrackingType) -> Unit,
    val onTogglePrimary: (Muscle) -> Unit,
    val onToggleSecondary: (Muscle) -> Unit,
    val onMechanicChange: (Mechanic) -> Unit,
    val onUnilateralChange: (Boolean) -> Unit,
    val onRestAdjust: (Int) -> Unit,
    val onAddSetupHint: (String) -> Unit,
    val onRemoveSetupHint: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
fun ExerciseEditorScreen(
    onClose: () -> Unit,
    onSaved: (exerciseId: String, isNew: Boolean) -> Unit,
    viewModel: ExerciseEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExerciseEditorEvent.Saved -> onSaved(event.exerciseId, event.isNew)
            }
        }
    }
    val requestClose = { if (state.isDirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.isDirty) { confirmDiscard = true }

    ExerciseEditorContent(
        state = state,
        actions = EditorActions(
            onNameChange = viewModel::onNameChange,
            onEquipmentChange = viewModel::onEquipmentChange,
            onTrackingChange = viewModel::onTrackingChange,
            onTogglePrimary = viewModel::onTogglePrimary,
            onToggleSecondary = viewModel::onToggleSecondary,
            onMechanicChange = viewModel::onMechanicChange,
            onUnilateralChange = viewModel::onUnilateralChange,
            onRestAdjust = viewModel::onRestAdjust,
            onAddSetupHint = viewModel::onAddSetupHint,
            onRemoveSetupHint = viewModel::onRemoveSetupHint,
            onNotesChange = viewModel::onNotesChange,
            onSave = viewModel::onSave,
            onClose = requestClose,
        ),
    )
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseEditorContent(state: ExerciseEditorUiState, actions: EditorActions) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
        TopAppBar(
            title = { Text(stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit)) },
            navigationIcon = {
                IconButton(onClick = actions.onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_close))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val draft = state.draft
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SportDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingL),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = actions.onNameChange,
                label = { Text(stringResource(R.string.editor_name)) },
                isError = state.nameError,
                supportingText = when {
                    state.nameError -> { { Text(stringResource(R.string.editor_name_error)) } }
                    state.sameNameAs != null -> { { Text(stringResource(R.string.editor_name_exists, state.sameNameAs)) } }
                    else -> null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            EditorSection(stringResource(R.string.editor_equipment)) {
                Equipment.entries.forEach { equipment ->
                    ChoiceChip(stringResource(equipment.labelRes()), selected = draft.equipment == equipment) { actions.onEquipmentChange(equipment) }
                }
            }
            EditorSection(stringResource(R.string.editor_tracking)) {
                ExerciseTrackingType.entries.forEach { tracking ->
                    ChoiceChip(stringResource(tracking.labelRes()), selected = draft.trackingType == tracking) { actions.onTrackingChange(tracking) }
                }
            }
            EditorSection(
                title = stringResource(R.string.editor_primary),
                subtitle = stringResource(if (state.primaryError) R.string.editor_primary_error else R.string.editor_primary_hint),
                subtitleIsError = state.primaryError,
            ) {
                Muscle.entries.forEach { muscle ->
                    val index = draft.primaryMuscles.indexOf(muscle)
                    FilterChip(
                        selected = index >= 0,
                        onClick = { actions.onTogglePrimary(muscle) },
                        label = { Text(stringResource(muscle.labelRes())) },
                        leadingIcon = when (index) {
                            -1 -> null
                            0 -> { { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp)) } }
                            else -> { { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } }
                        },
                        modifier = Modifier.heightIn(min = 40.dp),
                        colors = selectedPrimaryColors(),
                    )
                }
            }
            EditorSection(stringResource(R.string.editor_secondary)) {
                Muscle.entries.filterNot { it in draft.primaryMuscles }.forEach { muscle ->
                    FilterChip(
                        selected = muscle in draft.secondaryMuscles,
                        onClick = { actions.onToggleSecondary(muscle) },
                        label = { Text(stringResource(muscle.labelRes())) },
                        modifier = Modifier.heightIn(min = 40.dp),
                    )
                }
            }
            EditorSection(stringResource(R.string.editor_mechanic)) {
                Mechanic.entries.forEach { mechanic ->
                    ChoiceChip(stringResource(mechanic.labelRes()), selected = draft.mechanic == mechanic) { actions.onMechanicChange(mechanic) }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SportDimens.TouchTargetMin)
                    .toggleable(value = draft.isUnilateral, role = Role.Switch, onValueChange = actions.onUnilateralChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.editor_unilateral), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = draft.isUnilateral, onCheckedChange = null)
            }
            RestStepper(draft.defaultRestSeconds, actions.onRestAdjust)
            SetupHintsEditor(draft.setupParamHints, actions.onAddSetupHint, actions.onRemoveSetupHint)
            OutlinedTextField(
                value = draft.notes,
                onValueChange = actions.onNotesChange,
                label = { Text(stringResource(R.string.editor_notes)) },
                placeholder = { Text(stringResource(R.string.editor_notes_hint)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.editor_save),
            onClick = actions.onSave,
            enabled = !state.isSaving,
            icon = Icons.Filled.Check,
            modifier = Modifier.fillMaxWidth().padding(SportDimens.ScreenPadding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorSection(
    title: String,
    subtitle: String? = null,
    subtitleIsError: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (subtitleIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
        ) { content() }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 40.dp),
        colors = selectedPrimaryColors(),
    )
}

@Composable
private fun selectedPrimaryColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun RestStepper(seconds: Int, onAdjust: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.editor_rest), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onAdjust(-REST_STEP_SECONDS) },
            enabled = seconds > CustomExerciseDraft.MIN_REST_SECONDS,
            modifier = Modifier.size(SportDimens.TouchTargetMin),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.editor_rest_minus))
        }
        Text(
            Formatters.duration(seconds.toLong()),
            style = SportNumberStyles.Row,
            modifier = Modifier.padding(horizontal = SportDimens.SpacingM),
        )
        FilledTonalIconButton(
            onClick = { onAdjust(REST_STEP_SECONDS) },
            enabled = seconds < CustomExerciseDraft.MAX_REST_SECONDS,
            modifier = Modifier.size(SportDimens.TouchTargetMin),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.editor_rest_plus))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupHintsEditor(hints: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var newHint by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(newHint)
        newHint = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
        Text(stringResource(R.string.editor_setup), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.editor_setup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hints.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                hints.forEach { hint ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(hint) },
                        label = { Text(hint) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_setup_remove, hint), modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            OutlinedTextField(
                value = newHint,
                onValueChange = { newHint = it },
                placeholder = { Text(stringResource(R.string.editor_setup_param)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = submit, enabled = newHint.isNotBlank(), modifier = Modifier.heightIn(min = SportDimens.TouchTargetMin)) {
                Text(stringResource(R.string.editor_setup_add))
            }
        }
    }
}

private const val REST_STEP_SECONDS = 15
