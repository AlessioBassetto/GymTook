package com.example.sport.feature.workout

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.data.SetMetric
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.input.KeypadKey
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.SetType
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.format.LocalEffortScale
import com.example.sport.core.ui.exercise.SupersetBadge
import com.example.sport.core.ui.media.ExerciseThumbnail
import com.example.sport.core.ui.time.rememberTickingNow
import com.example.sport.core.workout.exactAlarmSettingsIntent
import java.time.Duration
import java.time.Instant

/** Azioni del logger (separate dal ViewModel per poter testare e mostrare in anteprima la UI). */
internal data class WorkoutActions(
    val onMinimize: () -> Unit,
    val onFinishRequest: () -> Unit,
    val onFieldClick: (Long, SetMetric) -> Unit,
    val onKey: (KeypadKey) -> Unit,
    val onNext: () -> Unit,
    val onCloseKeypad: () -> Unit,
    val onToggleComplete: (Long) -> Unit,
    val onSetTypeChange: (Long, SetType) -> Unit,
    val onRemoveSet: (Long) -> Unit,
    val onAddSet: (Long) -> Unit,
    val onOpenSetup: (Long) -> Unit,
    val onToggleRpe: (Long) -> Unit,
    val onRemoveExercise: (Long) -> Unit,
    val onReplaceExercise: (Long) -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onAddExercise: () -> Unit,
    val onDiscardRequest: () -> Unit,
    val onRestAdjust: (Int) -> Unit,
    val onRestSkip: () -> Unit,
    val onRestElapsed: () -> Unit,
    val now: () -> Instant,
    val onGrantExactAlarms: () -> Unit = {},
    val onDismissExactAlarmHint: () -> Unit = {},
)

@Composable
fun WorkoutScreen(
    onMinimize: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onAddExercise: (sessionId: Long) -> Unit,
    onReplaceExercise: (sessionId: Long, sessionExerciseId: Long) -> Unit,
    onFinished: (sessionId: Long) -> Unit,
    onDiscarded: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val setupSheet by viewModel.setupSheet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutEvent.Finished -> onFinished(event.sessionId)
                WorkoutEvent.Discarded -> onDiscarded()
                WorkoutEvent.RestFinished -> vibrateRestFinished(context)
            }
        }
    }

    // Al rientro dalle impostazioni "Sveglie e promemoria" l'allarme in corso diventa esatto.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val active = state as? WorkoutUiState.Active
    BackHandler(enabled = active?.editing != null) { viewModel.onCloseKeypad() }

    WorkoutContent(
        state = state,
        actions = WorkoutActions(
            onMinimize = onMinimize,
            onFinishRequest = { confirmFinish = true },
            onFieldClick = viewModel::onFieldClick,
            onKey = viewModel::onKey,
            onNext = viewModel::onNext,
            onCloseKeypad = viewModel::onCloseKeypad,
            onToggleComplete = viewModel::onToggleComplete,
            onSetTypeChange = viewModel::onSetTypeChange,
            onRemoveSet = viewModel::onRemoveSet,
            onAddSet = viewModel::onAddSet,
            onOpenSetup = viewModel::onOpenSetup,
            onToggleRpe = viewModel::onToggleRpe,
            onRemoveExercise = viewModel::onRemoveExercise,
            onReplaceExercise = { id -> active?.let { onReplaceExercise(it.sessionId, id) } },
            onOpenExercise = onOpenExercise,
            onAddExercise = { active?.let { onAddExercise(it.sessionId) } },
            onDiscardRequest = { confirmDiscard = true },
            onRestAdjust = viewModel::onRestAdjust,
            onRestSkip = viewModel::onRestSkip,
            onRestElapsed = viewModel::onRestElapsed,
            now = viewModel::now,
            onGrantExactAlarms = {
                exactAlarmSettingsIntent(context)?.let { intent ->
                    runCatching { context.startActivity(intent) }
                }
            },
            onDismissExactAlarmHint = viewModel::onDismissExactAlarmHint,
        ),
    )

    setupSheet?.let { sheet ->
        val defaultLabel = stringResource(R.string.setup_default_label)
        MachineSetupSheet(
            sheet = sheet,
            onDismiss = viewModel::onCloseSetup,
            onSave = { viewModel.onSaveSetup(it, defaultLabel) },
            onSaveAsNew = viewModel::onSaveSetupAsNew,
            onSelectProfile = viewModel::onSelectSetup,
        )
    }
    if (confirmFinish) {
        ConfirmDialog(
            title = stringResource(R.string.workout_finish_title),
            message = stringResource(R.string.workout_finish_message),
            confirm = stringResource(R.string.workout_finish),
            dismiss = stringResource(R.string.workout_continue),
            onConfirm = {
                confirmFinish = false
                viewModel.onFinish()
            },
            onDismiss = { confirmFinish = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.workout_discard_title),
            message = stringResource(R.string.workout_discard_message),
            confirm = stringResource(R.string.workout_discard),
            dismiss = stringResource(R.string.workout_keep),
            onConfirm = {
                confirmDiscard = false
                viewModel.onDiscard()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** Elemento della lista: ogni riga della card è un item, così il tastierino può portarla in vista. */
private sealed interface LoggerItem {
    val key: String

    data class Header(val exercise: WorkoutExerciseUi) : LoggerItem {
        override val key = "header-${exercise.sessionExerciseId}"
    }

    data class Setup(val exercise: WorkoutExerciseUi) : LoggerItem {
        override val key = "setup-${exercise.sessionExerciseId}"
    }

    data class Labels(val exercise: WorkoutExerciseUi) : LoggerItem {
        override val key = "labels-${exercise.sessionExerciseId}"
    }

    data class SetItem(val exercise: WorkoutExerciseUi, val row: SetRowUi) : LoggerItem {
        override val key = "set-${row.setId}"
    }

    data class Footer(val exercise: WorkoutExerciseUi) : LoggerItem {
        override val key = "footer-${exercise.sessionExerciseId}"
    }

    data object Actions : LoggerItem {
        override val key = "actions"
    }
}

@Composable
internal fun WorkoutContent(state: WorkoutUiState, actions: WorkoutActions) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        when (state) {
            WorkoutUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            WorkoutUiState.NoActiveSession -> {
                MinimalTopBar(onBack = actions.onMinimize)
                EmptyState(
                    icon = Icons.Outlined.FitnessCenter,
                    title = stringResource(R.string.workout_no_session_title),
                    message = stringResource(R.string.workout_no_session_message),
                )
            }
            is WorkoutUiState.Active -> ActiveWorkout(state, actions)
        }
    }
}

@Composable
private fun ColumnScope.ActiveWorkout(state: WorkoutUiState.Active, actions: WorkoutActions) {
    val items = remember(state.exercises) {
        buildList {
            state.exercises.forEach { exercise ->
                add(LoggerItem.Header(exercise))
                if (exercise.showSetup) add(LoggerItem.Setup(exercise))
                add(LoggerItem.Labels(exercise))
                exercise.sets.forEach { add(LoggerItem.SetItem(exercise, it)) }
                add(LoggerItem.Footer(exercise))
            }
            add(LoggerItem.Actions)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.editing?.setId) {
        val setId = state.editing?.setId ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.key == "set-$setId" }
        if (index >= 0) listState.animateScrollToItem((index - 2).coerceAtLeast(0))
    }

    WorkoutTopBar(state, actions)
    Box(Modifier.weight(1f)) {
        if (state.exercises.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.workout_empty_title),
                message = stringResource(R.string.workout_empty_message),
                action = { SecondaryActionButton(stringResource(R.string.workout_add_exercise), actions.onAddExercise, icon = Icons.Filled.Add) },
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is LoggerItem.Header -> ExerciseHeader(item.exercise, actions)
                        is LoggerItem.Setup -> SetupSummary(item.exercise, onClick = { actions.onOpenSetup(item.exercise.sessionExerciseId) })
                        is LoggerItem.Labels -> CardSection { SetColumnLabels(item.exercise.metrics, item.exercise.trackingType) }
                        is LoggerItem.SetItem -> SetRow(
                            row = item.row,
                            metrics = item.exercise.metrics,
                            trackingType = item.exercise.trackingType,
                            editing = state.editing,
                            onFieldClick = { metric -> actions.onFieldClick(item.row.setId, metric) },
                            onToggleComplete = { actions.onToggleComplete(item.row.setId) },
                            onTypeChange = { type -> actions.onSetTypeChange(item.row.setId, type) },
                            onDelete = { actions.onRemoveSet(item.row.setId) },
                        )
                        is LoggerItem.Footer -> ExerciseFooter(onAddSet = { actions.onAddSet(item.exercise.sessionExerciseId) })
                        LoggerItem.Actions -> WorkoutBottomActions(actions)
                    }
                }
            }
        }
    }
    state.rest?.let { rest ->
        RestTimerBar(
            rest = rest,
            now = actions.now,
            onAdjust = actions.onRestAdjust,
            onSkip = actions.onRestSkip,
            onElapsed = actions.onRestElapsed,
            showExactAlarmHint = state.exactAlarmHint,
            onGrantExactAlarms = actions.onGrantExactAlarms,
            onDismissExactAlarmHint = actions.onDismissExactAlarmHint,
        )
    }
    state.editing?.let { editing ->
        KeypadPanel(editing = editing, onKey = actions.onKey, onNext = actions.onNext, onClose = actions.onCloseKeypad)
    }
}

@Composable
private fun WorkoutTopBar(state: WorkoutUiState.Active, actions: WorkoutActions) {
    val now by rememberTickingNow(periodMillis = 1_000, now = actions.now)
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = SportDimens.ScreenPadding, top = SportDimens.SpacingS, bottom = SportDimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onMinimize, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.workout_minimize))
        }
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(
                    R.string.workout_progress,
                    Formatters.duration(Duration.between(state.startedAt, now).seconds),
                    state.completedSets,
                    state.totalSets,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Button(
            onClick = actions.onFinishRequest,
            modifier = Modifier.height(SportDimens.TouchTargetMin),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(stringResource(R.string.workout_finish), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun MinimalTopBar(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

private val CardTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val CardBottom = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

@Composable
private fun CardSection(shape: Shape? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape ?: RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

@Composable
private fun ExerciseHeader(exercise: WorkoutExerciseUi, actions: WorkoutActions) {
    var menuOpen by remember { mutableStateOf(false) }
    CardSection(shape = CardTop) {
        Row(
            modifier = Modifier.padding(start = SportDimens.SpacingM, top = SportDimens.SpacingM, bottom = SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            ExerciseThumbnail(model = exercise.thumbnail, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                exercise.supersetLabel?.let { SupersetBadge(stringResource(R.string.workout_superset, it)) }
                val details = buildList {
                    add(stringResource(exercise.muscleLabelRes))
                    add(stringResource(R.string.workout_rest_chip, exercise.restLabel))
                    if (exercise.wasReplaced) add(stringResource(R.string.workout_replaced))
                }
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.workout_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuItem(stringResource(R.string.workout_open_details)) {
                        menuOpen = false
                        actions.onOpenExercise(exercise.exerciseId)
                    }
                    MenuItem(stringResource(R.string.workout_replace)) {
                        menuOpen = false
                        actions.onReplaceExercise(exercise.sessionExerciseId)
                    }
                    MenuItem(stringResource(R.string.workout_setup)) {
                        menuOpen = false
                        actions.onOpenSetup(exercise.sessionExerciseId)
                    }
                    MenuItem(stringResource(effortMenuLabel(exercise.showRpe, LocalEffortScale.current))) {
                        menuOpen = false
                        actions.onToggleRpe(exercise.sessionExerciseId)
                    }
                    MenuItem(stringResource(R.string.workout_remove_exercise), isDestructive = true) {
                        menuOpen = false
                        actions.onRemoveExercise(exercise.sessionExerciseId)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
    )
}

/** Riassunto della regolazione: un tocco apre l'editor. */
@Composable
private fun SetupSummary(exercise: WorkoutExerciseUi, onClick: () -> Unit) {
    CardSection {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.SpacingM, vertical = SportDimens.SpacingXs),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(SportDimens.SpacingM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.workout_setup),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val params = exercise.setup.params
                    Text(
                        text = if (params.isNotEmpty()) {
                            params.joinToString(" · ") { "${it.name} ${it.value}" }
                        } else {
                            listOf(stringResource(R.string.setup_empty)).plus(exercise.setup.hints).joinToString(" · ")
                        },
                        style = if (params.isNotEmpty()) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = if (params.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseFooter(onAddSet: () -> Unit) {
    Column {
        CardSection(shape = CardBottom) {
            TextButton(
                onClick = onAddSet,
                modifier = Modifier.fillMaxWidth().height(SportDimens.TouchTargetMin),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(SportDimens.SpacingXs))
                Text(stringResource(R.string.workout_add_set), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(SportDimens.SpacingL))
    }
}

@Composable
private fun WorkoutBottomActions(actions: WorkoutActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SportDimens.SpacingS),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        SecondaryActionButton(
            text = stringResource(R.string.workout_add_exercise),
            onClick = actions.onAddExercise,
            icon = Icons.Filled.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = actions.onDiscardRequest, modifier = Modifier.fillMaxWidth().height(SportDimens.TouchTargetMin)) {
            Text(stringResource(R.string.workout_discard), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

/** Voce del menu che mostra o nasconde la colonna dello sforzo, nella scala scelta. */
@StringRes
private fun effortMenuLabel(shown: Boolean, scale: EffortScale): Int = when (scale) {
    EffortScale.RPE -> if (shown) R.string.workout_hide_rpe else R.string.workout_show_rpe
    EffortScale.RIR -> if (shown) R.string.workout_hide_rir else R.string.workout_show_rir
}
