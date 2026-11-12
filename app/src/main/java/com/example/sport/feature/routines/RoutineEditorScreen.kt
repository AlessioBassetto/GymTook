package com.example.sport.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.data.ExerciseTargets
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.exercise.SupersetBadge
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.media.ExerciseThumbnail
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.DayOfWeek

/** Azioni del builder (separate dal ViewModel per i test di interfaccia). */
internal data class BuilderActions(
    val onBack: () -> Unit,
    val onRename: (String) -> Unit,
    val onSetActive: () -> Unit,
    val onDuplicate: (name: String, deload: Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onAssignWeekday: (DayOfWeek, Long?) -> Unit,
    val onSelectDay: (Long) -> Unit,
    val onAddDay: (String) -> Unit,
    val onRenameDay: (Long, String) -> Unit,
    val onDuplicateDay: (Long, String) -> Unit,
    val onMoveDay: (Long, Int) -> Unit,
    val onDeleteDay: (Long) -> Unit,
    val onAddExercise: (dayId: Long) -> Unit,
    val onReplaceExercise: (routineExerciseId: Long) -> Unit,
    val onOpenExercise: (exerciseId: String) -> Unit,
    val onUpdateTargets: (Long, ExerciseTargets) -> Unit,
    val onMoveExercise: (dayId: Long, from: Int, to: Int) -> Unit,
    val onMoveExerciseToDay: (routineExerciseId: Long, dayId: Long) -> Unit,
    val onLinkSuperset: (Long) -> Unit,
    val onUnlinkSuperset: (Long) -> Unit,
    val onRemoveExercise: (Long) -> Unit,
)

/** Finestra o pannello aperto sopra il builder. */
private sealed interface BuilderOverlay {
    data object RenameRoutine : BuilderOverlay
    data object Duplicate : BuilderOverlay
    data object DeleteRoutine : BuilderOverlay
    data object AddDay : BuilderOverlay
    data class RenameDay(val day: BuilderDayUi) : BuilderOverlay
    data class DuplicateDay(val day: BuilderDayUi) : BuilderOverlay
    data class DeleteDay(val day: BuilderDayUi) : BuilderOverlay
    data class Weekday(val dayOfWeek: DayOfWeek) : BuilderOverlay
    data class Targets(val exercise: BuilderExerciseUi) : BuilderOverlay
    data class MoveToDay(val exercise: BuilderExerciseUi) : BuilderOverlay
}

@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    onAddExercise: (dayId: Long) -> Unit,
    onReplaceExercise: (routineExerciseId: Long) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    onOpenRoutine: (routineId: Long) -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RoutineEditorEvent.Deleted -> onBack()
                is RoutineEditorEvent.CopyCreated -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.builder_copy_created, event.name),
                        actionLabel = context.getString(R.string.builder_open),
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenRoutine(event.routineId)
                }
            }
        }
    }
    RoutineEditorContent(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = BuilderActions(
            onBack = onBack,
            onRename = viewModel::onRename,
            onSetActive = viewModel::onSetActive,
            onDuplicate = viewModel::onDuplicate,
            onDelete = viewModel::onDelete,
            onAssignWeekday = viewModel::onAssignWeekday,
            onSelectDay = viewModel::onSelectDay,
            onAddDay = viewModel::onAddDay,
            onRenameDay = viewModel::onRenameDay,
            onDuplicateDay = viewModel::onDuplicateDay,
            onMoveDay = viewModel::onMoveDay,
            onDeleteDay = viewModel::onDeleteDay,
            onAddExercise = onAddExercise,
            onReplaceExercise = onReplaceExercise,
            onOpenExercise = onOpenExercise,
            onUpdateTargets = viewModel::onUpdateTargets,
            onMoveExercise = viewModel::onMoveExercise,
            onMoveExerciseToDay = viewModel::onMoveExerciseToDay,
            onLinkSuperset = viewModel::onLinkSuperset,
            onUnlinkSuperset = viewModel::onUnlinkSuperset,
            onRemoveExercise = viewModel::onRemoveExercise,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineEditorContent(
    state: RoutineEditorUiState,
    actions: BuilderActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var overlay by remember { mutableStateOf<BuilderOverlay?>(null) }
    val content = state as? RoutineEditorUiState.Content
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(content?.routine?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = { overlay = BuilderOverlay.RenameRoutine }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.builder_rename))
                        }
                        RoutineMenu(content.routine, onOverlay = { overlay = it }, onSetActive = actions.onSetActive)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            when (state) {
                RoutineEditorUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                RoutineEditorUiState.NotFound -> EmptyState(icon = Icons.Outlined.CalendarMonth, title = stringResource(R.string.builder_not_found))
                is RoutineEditorUiState.Content -> BuilderBody(state, actions, onOverlay = { overlay = it })
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }
    if (content != null) {
        overlay?.let { current ->
            BuilderOverlayHost(current, content, actions, onDismiss = { overlay = null })
        }
    }
}

@Composable
private fun RoutineMenu(routine: RoutineEditorUi, onOverlay: (BuilderOverlay) -> Unit, onSetActive: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.builder_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!routine.isActive) {
                MenuItem(stringResource(R.string.routines_set_active)) {
                    expanded = false
                    onSetActive()
                }
            }
            MenuItem(stringResource(R.string.builder_duplicate)) {
                expanded = false
                onOverlay(BuilderOverlay.Duplicate)
            }
            MenuItem(stringResource(R.string.builder_delete), destructive = true) {
                expanded = false
                onOverlay(BuilderOverlay.DeleteRoutine)
            }
        }
    }
}

@Composable
private fun BuilderBody(state: RoutineEditorUiState.Content, actions: BuilderActions, onOverlay: (BuilderOverlay) -> Unit) {
    val routine = state.routine
    val day = state.selectedDay
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // Copia locale durante il trascinamento: il database si aggiorna solo al rilascio.
    var exercises by remember(day?.id, day?.exercises) { mutableStateOf(day?.exercises.orEmpty()) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = exercises.indexOfFirst { exerciseKey(it.id) == from.key }
        val toIndex = exercises.indexOfFirst { exerciseKey(it.id) == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            exercises = exercises.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(start = SportDimens.ScreenPadding, end = SportDimens.ScreenPadding, bottom = 96.dp),
        // Righe vicine: la barra delle superserie resta quasi continua tra un esercizio e l'altro.
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
    ) {
        item(key = "status") { RoutineStatus(routine, actions.onSetActive) }
        item(key = "week") { WeekSection(routine, onWeekdayClick = { onOverlay(BuilderOverlay.Weekday(it)) }) }
        item(key = "days") {
            DayChips(routine.days, state.selectedDayId, actions.onSelectDay, onAddDay = { onOverlay(BuilderOverlay.AddDay) })
        }
        if (day == null) {
            item(key = "no-days") {
                EmptyState(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.builder_no_days_title),
                    message = stringResource(R.string.builder_no_days_message),
                    action = { SecondaryActionButton(stringResource(R.string.builder_add_day_description), { onOverlay(BuilderOverlay.AddDay) }, icon = Icons.Filled.Add) },
                )
            }
            return@LazyColumn
        }
        item(key = "day-header-${day.id}") {
            val index = routine.days.indexOfFirst { it.id == day.id }
            DayHeader(day, isFirst = index == 0, isLast = index == routine.days.lastIndex, actions = actions, onOverlay = onOverlay)
        }
        if (exercises.isEmpty()) {
            item(key = "empty-day") {
                EmptyState(
                    icon = Icons.Outlined.FitnessCenter,
                    title = stringResource(R.string.builder_empty_day_title),
                    message = stringResource(R.string.builder_empty_day_message),
                )
            }
        }
        items(exercises, key = { exerciseKey(it.id) }) { exercise ->
            ReorderableItem(reorderState, key = exerciseKey(exercise.id)) { isDragging ->
                val index = exercises.indexOf(exercise)
                BuilderExerciseRow(
                    exercise = exercise,
                    isDragging = isDragging,
                    canMoveUp = index > 0,
                    canMoveDown = index < exercises.lastIndex,
                    hasOtherDays = routine.days.size > 1,
                    handleModifier = Modifier.draggableHandle(
                        onDragStarted = {
                            dragStartIndex = exercises.indexOfFirst { it.id == exercise.id }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragStopped = {
                            val end = exercises.indexOfFirst { it.id == exercise.id }
                            if (dragStartIndex >= 0 && end >= 0 && end != dragStartIndex) actions.onMoveExercise(day.id, dragStartIndex, end)
                            dragStartIndex = -1
                        },
                    ),
                    onClick = { onOverlay(BuilderOverlay.Targets(exercise)) },
                    onMove = { delta -> actions.onMoveExercise(day.id, index, index + delta) },
                    actions = actions,
                    onOverlay = onOverlay,
                )
            }
        }
        item(key = "add-exercise") {
            SecondaryActionButton(
                text = stringResource(R.string.builder_add_exercise),
                onClick = { actions.onAddExercise(day.id) },
                icon = Icons.Filled.Add,
                modifier = Modifier.fillMaxWidth().padding(top = SportDimens.SpacingS),
            )
        }
    }
}

private fun exerciseKey(id: Long) = "exercise-$id"

private val SUPERSET_BAR_WIDTH = 6.dp

@Composable
private fun RoutineStatus(routine: RoutineEditorUi, onSetActive: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
        if (routine.isActive) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {
                Text(
                    stringResource(R.string.routines_active_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = SportDimens.SpacingS, vertical = SportDimens.SpacingXs),
                )
            }
        } else {
            TextButton(onClick = onSetActive, modifier = Modifier.heightIn(min = SportDimens.TouchTargetMin)) {
                Text(stringResource(R.string.routines_set_active))
            }
        }
        routine.parentName?.let {
            Text(
                stringResource(R.string.routines_variant_of, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Sette giorni con il giorno di scheda assegnato: un tocco per cambiarlo. */
@Composable
private fun WeekSection(routine: RoutineEditorUi, onWeekdayClick: (DayOfWeek) -> Unit) {
    val dayNames = routine.days.associate { it.id to it.name }
    val rest = stringResource(R.string.builder_rest)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            Text(stringResource(R.string.builder_week), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.builder_week_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { dayOfWeek ->
                    val assigned = routine.schedule[dayOfWeek]?.let(dayNames::get)
                    val description = stringResource(R.string.builder_weekday_description, Formatters.dayOfWeek(dayOfWeek), assigned ?: rest)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = SportDimens.TouchTargetPrimary)
                            .clickable { onWeekdayClick(dayOfWeek) }
                            .semantics(mergeDescendants = true) { contentDescription = description }
                            .padding(vertical = SportDimens.SpacingXs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
                    ) {
                        Surface(shape = CircleShape, color = if (assigned != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    Formatters.dayOfWeekInitial(dayOfWeek),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (assigned != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Fino a due righe centrate: "Upper A" diventa "Upper" / "A" invece di toccare il vicino.
                        Text(
                            assigned ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (assigned != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChips(days: List<BuilderDayUi>, selectedDayId: Long?, onSelect: (Long) -> Unit, onAddDay: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
        Text(
            stringResource(R.string.builder_days),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = SportDimens.SpacingS),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            days.forEach { day ->
                FilterChip(
                    selected = day.id == selectedDayId,
                    onClick = { onSelect(day.id) },
                    label = { Text(day.name) },
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
            FilterChip(
                selected = false,
                onClick = onAddDay,
                label = { Text(stringResource(R.string.builder_add_day)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.heightIn(min = 40.dp),
            )
        }
    }
}

@Composable
private fun DayHeader(
    day: BuilderDayUi,
    isFirst: Boolean,
    isLast: Boolean,
    actions: BuilderActions,
    onOverlay: (BuilderOverlay) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = SportDimens.SpacingS), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(day.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.today_workout_summary, day.exercises.size, day.totalSets, day.estimatedMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.builder_day_menu))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MenuItem(stringResource(R.string.builder_rename_day)) {
                    expanded = false
                    onOverlay(BuilderOverlay.RenameDay(day))
                }
                MenuItem(stringResource(R.string.builder_duplicate_day)) {
                    expanded = false
                    onOverlay(BuilderOverlay.DuplicateDay(day))
                }
                if (!isFirst) {
                    MenuItem(stringResource(R.string.builder_move_day_left)) {
                        expanded = false
                        actions.onMoveDay(day.id, -1)
                    }
                }
                if (!isLast) {
                    MenuItem(stringResource(R.string.builder_move_day_right)) {
                        expanded = false
                        actions.onMoveDay(day.id, +1)
                    }
                }
                MenuItem(stringResource(R.string.builder_delete_day), destructive = true) {
                    expanded = false
                    onOverlay(BuilderOverlay.DeleteDay(day))
                }
            }
        }
    }
}

@Composable
private fun BuilderExerciseRow(
    exercise: BuilderExerciseUi,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    hasOtherDays: Boolean,
    handleModifier: Modifier,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    actions: BuilderActions,
    onOverlay: (BuilderOverlay) -> Unit,
) {
    val moveUp = stringResource(R.string.builder_move_up)
    val moveDown = stringResource(R.string.builder_move_down)
    val supersetColor = MaterialTheme.colorScheme.tertiary
    val position = exercise.supersetPosition
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = Modifier.semantics {
            // Alternativa accessibile al trascinamento (TalkBack, Switch Access).
            customActions = buildList {
                if (canMoveUp) add(CustomAccessibilityAction(moveUp) { onMove(-1); true })
                if (canMoveDown) add(CustomAccessibilityAction(moveDown) { onMove(+1); true })
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                // Barra laterale che unisce visivamente gli esercizi della stessa superserie:
                // parte a metà del primo e finisce a metà dell'ultimo.
                .drawBehind {
                    if (position == SupersetPosition.NONE) return@drawBehind
                    val top = if (position == SupersetPosition.FIRST) size.height / 2 else 0f
                    val bottom = if (position == SupersetPosition.LAST) size.height / 2 else size.height
                    drawRect(supersetColor, topLeft = Offset(0f, top), size = Size(SUPERSET_BAR_WIDTH.toPx(), bottom - top))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(SUPERSET_BAR_WIDTH))
            IconButton(onClick = {}, modifier = handleModifier.size(SportDimens.TouchTargetMin)) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = stringResource(R.string.builder_drag_handle, exercise.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExerciseThumbnail(model = exercise.thumbnail, size = 48.dp)
            Spacer(Modifier.width(SportDimens.SpacingM))
            Column(Modifier.weight(1f).padding(vertical = SportDimens.SpacingS)) {
                if (exercise.supersetPosition == SupersetPosition.FIRST) {
                    SupersetBadge(stringResource(R.string.builder_superset), Modifier.padding(bottom = 2.dp))
                }
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(targetSummary(exercise), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ExerciseMenu(exercise, hasOtherDays, canMoveDown, actions, onOverlay)
        }
    }
}

@Composable
private fun targetSummary(exercise: BuilderExerciseUi): String {
    val sets = if (exercise.countsReps && (exercise.repsMin != null || exercise.repsMax != null)) {
        stringResource(R.string.plan_target, exercise.sets, Formatters.repRange(exercise.repsMin, exercise.repsMax))
    } else {
        stringResource(R.string.builder_sets_only, exercise.sets)
    }
    return sets + " · " + stringResource(R.string.plan_rest, Formatters.duration(exercise.restSeconds.toLong()))
}

@Composable
private fun ExerciseMenu(
    exercise: BuilderExerciseUi,
    hasOtherDays: Boolean,
    hasNext: Boolean,
    actions: BuilderActions,
    onOverlay: (BuilderOverlay) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.builder_exercise_menu, exercise.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem(stringResource(R.string.builder_edit_targets)) {
                expanded = false
                onOverlay(BuilderOverlay.Targets(exercise))
            }
            MenuItem(stringResource(R.string.workout_open_details)) {
                expanded = false
                actions.onOpenExercise(exercise.exerciseId)
            }
            MenuItem(stringResource(R.string.builder_replace)) {
                expanded = false
                actions.onReplaceExercise(exercise.id)
            }
            val linkedWithNext = exercise.supersetPosition == SupersetPosition.FIRST || exercise.supersetPosition == SupersetPosition.MIDDLE
            if (hasNext && !linkedWithNext) {
                MenuItem(stringResource(R.string.builder_link_superset)) {
                    expanded = false
                    actions.onLinkSuperset(exercise.id)
                }
            }
            if (exercise.supersetPosition != SupersetPosition.NONE) {
                MenuItem(stringResource(R.string.builder_unlink_superset)) {
                    expanded = false
                    actions.onUnlinkSuperset(exercise.id)
                }
            }
            if (hasOtherDays) {
                MenuItem(stringResource(R.string.builder_move_to_day)) {
                    expanded = false
                    onOverlay(BuilderOverlay.MoveToDay(exercise))
                }
            }
            MenuItem(stringResource(R.string.builder_remove), destructive = true) {
                expanded = false
                actions.onRemoveExercise(exercise.id)
            }
        }
    }
}

@Composable
internal fun MenuItem(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
    )
}

@Composable
private fun BuilderOverlayHost(
    overlay: BuilderOverlay,
    content: RoutineEditorUiState.Content,
    actions: BuilderActions,
    onDismiss: () -> Unit,
) {
    val routine = content.routine
    when (overlay) {
        BuilderOverlay.RenameRoutine -> TextInputDialog(
            title = stringResource(R.string.builder_rename),
            label = stringResource(R.string.routines_name_label),
            initial = routine.name,
            onConfirm = actions.onRename,
            onDismiss = onDismiss,
        )
        BuilderOverlay.Duplicate -> DuplicateDialog(
            copyName = stringResource(R.string.builder_copy_suffix, routine.name),
            deloadName = stringResource(R.string.builder_deload_suffix, routine.name),
            onConfirm = actions.onDuplicate,
            onDismiss = onDismiss,
        )
        BuilderOverlay.DeleteRoutine -> ConfirmDialog(
            title = stringResource(R.string.builder_delete_title, routine.name),
            message = stringResource(R.string.builder_delete_message),
            onConfirm = actions.onDelete,
            onDismiss = onDismiss,
        )
        BuilderOverlay.AddDay -> TextInputDialog(
            title = stringResource(R.string.builder_new_day_title),
            label = stringResource(R.string.builder_day_name_label),
            initial = stringResource(R.string.builder_day_default, ('A' + routine.days.size).toString()),
            onConfirm = actions.onAddDay,
            onDismiss = onDismiss,
        )
        is BuilderOverlay.RenameDay -> TextInputDialog(
            title = stringResource(R.string.builder_rename_day),
            label = stringResource(R.string.builder_day_name_label),
            initial = overlay.day.name,
            onConfirm = { actions.onRenameDay(overlay.day.id, it) },
            onDismiss = onDismiss,
        )
        is BuilderOverlay.DuplicateDay -> TextInputDialog(
            title = stringResource(R.string.builder_duplicate_day),
            label = stringResource(R.string.builder_day_name_label),
            initial = stringResource(R.string.builder_copy_suffix, overlay.day.name),
            onConfirm = { actions.onDuplicateDay(overlay.day.id, it) },
            onDismiss = onDismiss,
        )
        is BuilderOverlay.DeleteDay -> ConfirmDialog(
            title = stringResource(R.string.builder_delete_title, overlay.day.name),
            message = stringResource(R.string.builder_delete_day_message),
            onConfirm = { actions.onDeleteDay(overlay.day.id) },
            onDismiss = onDismiss,
        )
        is BuilderOverlay.Weekday -> WeekdaySheet(
            dayOfWeek = overlay.dayOfWeek,
            days = routine.days,
            currentDayId = routine.schedule[overlay.dayOfWeek],
            onPick = { actions.onAssignWeekday(overlay.dayOfWeek, it) },
            onDismiss = onDismiss,
        )
        is BuilderOverlay.Targets -> TargetsSheet(
            exercise = overlay.exercise,
            onApply = { actions.onUpdateTargets(overlay.exercise.id, it) },
            onDismiss = onDismiss,
        )
        is BuilderOverlay.MoveToDay -> MoveToDaySheet(
            exerciseName = overlay.exercise.name,
            days = routine.days.filterNot { it.id == content.selectedDayId },
            onPick = { actions.onMoveExerciseToDay(overlay.exercise.id, it) },
            onDismiss = onDismiss,
        )
    }
}
