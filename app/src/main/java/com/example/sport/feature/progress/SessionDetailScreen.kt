package com.example.sport.feature.progress

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.data.SetValues
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.StatValue
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.SetType
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.format.LocalWeightUnit

@Immutable
data class SessionDetailActions(
    val onBack: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onDelete: () -> Unit,
    val onUpdateSet: (setId: Long, values: SetValues) -> Unit = { _, _ -> },
    val onDeleteSet: (setId: Long) -> Unit = {},
)

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SessionDetailEvent.Deleted -> onBack()
                SessionDetailEvent.Emptied -> {
                    Toast.makeText(context, R.string.session_emptied, Toast.LENGTH_LONG).show()
                    onBack()
                }
                is SessionDetailEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
            }
        }
    }
    SessionDetailContent(
        state = state,
        actions = SessionDetailActions(
            onBack = onBack,
            onOpenExercise = onOpenExercise,
            onDelete = viewModel::onDelete,
            onUpdateSet = viewModel::onUpdateSet,
            onDeleteSet = viewModel::onDeleteSet,
        ),
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDetailContent(
    state: SessionDetailUiState,
    actions: SessionDetailActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetEditTarget?>(null) }
    var deletingSet by remember { mutableStateOf<SetEditTarget?>(null) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text((state as? SessionDetailUiState.Content)?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                if (state is SessionDetailUiState.Content) SessionMenu(onDelete = { confirmDelete = true })
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        when (state) {
            SessionDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            SessionDetailUiState.NotFound -> EmptyState(icon = Icons.Outlined.EventBusy, title = stringResource(R.string.session_not_found))
            is SessionDetailUiState.Content -> SessionDetailBody(state, actions, onEditSet = { editingSet = it })
        }
    }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        editingSet?.let { target ->
            SetEditDialog(
                target = target,
                onSave = { values ->
                    editingSet = null
                    actions.onUpdateSet(target.setId, values)
                },
                onDeleteRequest = {
                    editingSet = null
                    deletingSet = target
                },
                onDismiss = { editingSet = null },
            )
        }
    }
    deletingSet?.let { target ->
        DestructiveConfirmDialog(
            title = stringResource(R.string.set_delete_title, target.description),
            message = stringResource(R.string.set_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deletingSet = null
                actions.onDeleteSet(target.setId)
            },
            onDismiss = { deletingSet = null },
        )
    }
    val content = state as? SessionDetailUiState.Content
    if (confirmDelete && content != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.session_delete_title, Formatters.shortDate(content.date))) },
            text = { Text(stringResource(R.string.session_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) {
                    Text(stringResource(R.string.session_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.session_cancel)) }
            },
        )
    }
}

@Composable
private fun SessionMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.session_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun SessionDetailBody(
    state: SessionDetailUiState.Content,
    actions: SessionDetailActions,
    onEditSet: (SetEditTarget) -> Unit,
) {
    val unit = LocalWeightUnit.current
    LazyColumn(
        modifier = Modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(start = SportDimens.ScreenPadding, end = SportDimens.ScreenPadding, bottom = SportDimens.SpacingXl),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        item {
            Column {
                Text(Formatters.longDate(state.date), style = MaterialTheme.typography.titleMedium)
                val start = Formatters.time(state.startTime)
                Text(
                    state.endTime?.let { stringResource(R.string.session_time_range, start, Formatters.time(it)) } ?: start,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(SportDimens.CardPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatValue(state.duration, stringResource(R.string.progress_duration))
                    StatValue(state.workingSets.toString(), stringResource(R.string.progress_sets))
                    StatValue(Formatters.weightKg(state.volumeKg, unit), stringResource(R.string.progress_volume))
                    StatValue(state.caloriesBurned?.let(Formatters::kcal) ?: "—", stringResource(R.string.progress_calories))
                }
            }
        }
        item {
            val weight = state.caloriesBodyWeightKg
            Text(
                if (weight != null) {
                    stringResource(R.string.calories_estimated_with, Formatters.bodyWeightKg(weight, unit))
                } else {
                    stringResource(R.string.calories_missing_weight)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.notes?.let { notes ->
            item { Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.exercises.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.session_no_sets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.exercises.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.session_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.exercises, key = { it.id }) { exercise ->
            ExerciseBlock(exercise, onOpen = { actions.onOpenExercise(exercise.exerciseId) }, onEditSet = onEditSet)
        }
    }
}

@Composable
private fun ExerciseBlock(exercise: SessionExerciseUi, onOpen: () -> Unit, onEditSet: (SetEditTarget) -> Unit) {
    val unit = LocalWeightUnit.current
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .heightIn(min = SportDimens.TouchTargetMin)
                    .padding(start = SportDimens.CardPadding, end = SportDimens.SpacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (exercise.volumeKg > 0) {
                        Text(
                            stringResource(R.string.session_exercise_volume, Formatters.wholeKg(exercise.volumeKg, unit)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.padding(start = SportDimens.CardPadding, end = SportDimens.CardPadding, bottom = SportDimens.SpacingM),
                verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
            ) {
                exercise.sets.forEach { set ->
                    SetLine(
                        set = set,
                        onClick = {
                            onEditSet(
                                SetEditTarget(
                                    setId = set.id,
                                    trackingType = exercise.trackingType,
                                    weightKg = set.weightKg,
                                    reps = set.reps,
                                    durationSeconds = set.durationSeconds,
                                    distanceMeters = set.distanceMeters,
                                    description = set.description,
                                    inclinePercent = set.inclinePercent,
                                    speedKmh = set.speedKmh,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetLine(set: SessionSetUi, onClick: () -> Unit) {
    val badge = when (set.type) {
        SetType.WARMUP -> stringResource(R.string.set_badge_warmup)
        SetType.DROP -> stringResource(R.string.set_badge_drop)
        SetType.FAILURE -> stringResource(R.string.set_badge_failure)
        SetType.WORKING -> set.number.toString()
    }
    val isWarmup = set.type == SetType.WARMUP
    // Tutta la riga è il bersaglio del tocco: apre la correzione della serie.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.set_edit_title), onClick = onClick)
            .heightIn(min = SportDimens.TouchTargetMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (isWarmup) MaterialTheme.colorScheme.surfaceContainerHighest else SportTheme.extendedColors.completedContainer,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else SportTheme.extendedColors.onCompletedContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(Modifier.width(SportDimens.SpacingM))
        Text(
            set.description,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val effort = set.rpe?.let { stringResource(R.string.session_set_rpe, Formatters.number(it)) }
            ?: set.rir?.let { "RIR $it" }
        if (effort != null) {
            Text(effort, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
