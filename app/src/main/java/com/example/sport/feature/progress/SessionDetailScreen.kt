package com.example.sport.feature.progress

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
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
)

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SessionDetailEvent.Deleted -> onBack()
            }
        }
    }
    SessionDetailContent(state, SessionDetailActions(onBack, onOpenExercise, viewModel::onDelete))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDetailContent(state: SessionDetailUiState, actions: SessionDetailActions) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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
            is SessionDetailUiState.Content -> SessionDetailBody(state, actions)
        }
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
private fun SessionDetailBody(state: SessionDetailUiState.Content, actions: SessionDetailActions) {
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
                }
            }
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
        items(state.exercises, key = { it.id }) { exercise ->
            ExerciseBlock(exercise, onOpen = { actions.onOpenExercise(exercise.exerciseId) })
        }
    }
}

@Composable
private fun ExerciseBlock(exercise: SessionExerciseUi, onOpen: () -> Unit) {
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
                exercise.sets.forEach { set -> SetLine(set) }
            }
        }
    }
}

@Composable
private fun SetLine(set: SessionSetUi) {
    val badge = when (set.type) {
        SetType.WARMUP -> stringResource(R.string.set_badge_warmup)
        SetType.DROP -> stringResource(R.string.set_badge_drop)
        SetType.FAILURE -> stringResource(R.string.set_badge_failure)
        SetType.WORKING -> set.number.toString()
    }
    val isWarmup = set.type == SetType.WARMUP
    Row(verticalAlignment = Alignment.CenterVertically) {
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
