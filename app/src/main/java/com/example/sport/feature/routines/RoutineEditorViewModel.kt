package com.example.sport.feature.routines

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.core.data.ExerciseRepository
import com.example.sport.core.data.ExerciseTargets
import com.example.sport.core.data.RoutineBuilderRepository
import com.example.sport.core.data.estimatedMinutes
import com.example.sport.core.data.totalSets
import com.example.sport.core.database.relation.RoutineDayWithExercises
import com.example.sport.core.database.relation.RoutineWithDays
import com.example.sport.core.media.MediaRepository
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.ui.labels.labelRes
import com.example.sport.navigation.RoutineEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/** Posizione di un esercizio dentro la sua superserie: serve a disegnare il collegamento. */
enum class SupersetPosition { NONE, FIRST, MIDDLE, LAST }

@Immutable
data class BuilderExerciseUi(
    /** Id della riga di scheda (routine_exercises). */
    val id: Long,
    val exerciseId: String,
    val name: String,
    @StringRes val muscleLabelRes: Int,
    val thumbnail: Any?,
    val sets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    /** Recupero effettivo: quello impostato o il predefinito dell'esercizio. */
    val restSeconds: Int,
    val restIsDefault: Boolean,
    val defaultRestSeconds: Int,
    val countsReps: Boolean,
    /** Muscolo principale: ripiego della miniatura (mappa anatomica). */
    val muscle: Muscle? = null,
    val supersetPosition: SupersetPosition,
) {
    val targets: ExerciseTargets get() = ExerciseTargets(sets, repsMin, repsMax, if (restIsDefault) null else restSeconds)
}

@Immutable
data class BuilderDayUi(
    val id: Long,
    val name: String,
    val exercises: List<BuilderExerciseUi>,
    val totalSets: Int,
    val estimatedMinutes: Int,
)

@Immutable
data class RoutineEditorUi(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val parentName: String?,
    val days: List<BuilderDayUi>,
    /** Giorno della settimana → giorno di scheda (assente = riposo). */
    val schedule: Map<DayOfWeek, Long>,
)

sealed interface RoutineEditorUiState {
    data object Loading : RoutineEditorUiState
    data object NotFound : RoutineEditorUiState
    data class Content(val routine: RoutineEditorUi, val selectedDayId: Long?) : RoutineEditorUiState {
        val selectedDay: BuilderDayUi? get() = routine.days.firstOrNull { it.id == selectedDayId }
    }
}

sealed interface RoutineEditorEvent {
    data object Deleted : RoutineEditorEvent
    data class CopyCreated(val routineId: Long, val name: String) : RoutineEditorEvent
}

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val builder: RoutineBuilderRepository,
    exerciseRepository: ExerciseRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val routineId = savedStateHandle.toRoute<RoutineEditorRoute>().routineId
    private val selectedDay = savedStateHandle.getStateFlow<Long?>(KEY_SELECTED_DAY, null)

    private val eventChannel = Channel<RoutineEditorEvent>(Channel.BUFFERED)
    val events: Flow<RoutineEditorEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<RoutineEditorUiState> = combine(
        builder.observeRoutine(routineId),
        builder.observeRoutines(),
        exerciseRepository.observeThumbnailPaths(),
        selectedDay,
    ) { routine, all, thumbnails, selected ->
        if (routine == null) return@combine RoutineEditorUiState.NotFound
        val ui = routine.toUi(thumbnails, parentName = all.firstOrNull { it.routine.id == routine.routine.parentRoutineId }?.routine?.name)
        // Il giorno scelto può essere stato eliminato: si torna al primo.
        val dayId = selected?.takeIf { id -> ui.days.any { it.id == id } } ?: ui.days.firstOrNull()?.id
        RoutineEditorUiState.Content(ui, dayId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineEditorUiState.Loading)

    // ------------------------------------------------------------------ Scheda

    fun onRename(name: String) = launchIfNotBlank(name) { builder.renameRoutine(routineId, it) }

    fun onSetActive() = launch { builder.setActive(routineId) }

    fun onDuplicate(name: String, deload: Boolean) = launchIfNotBlank(name) { cleanName ->
        val copyId = builder.duplicateRoutine(routineId, cleanName, deload)
        eventChannel.send(RoutineEditorEvent.CopyCreated(copyId, cleanName))
    }

    fun onDelete() = launch {
        builder.deleteRoutine(routineId)
        eventChannel.send(RoutineEditorEvent.Deleted)
    }

    fun onAssignWeekday(dayOfWeek: DayOfWeek, dayId: Long?) = launch { builder.assignWeekday(routineId, dayOfWeek, dayId) }

    // ------------------------------------------------------------------ Giorni

    fun onSelectDay(dayId: Long) {
        savedStateHandle[KEY_SELECTED_DAY] = dayId
    }

    fun onAddDay(name: String) = launchIfNotBlank(name) { onSelectDay(builder.addDay(routineId, it)) }

    fun onRenameDay(dayId: Long, name: String) = launchIfNotBlank(name) { builder.renameDay(dayId, it) }

    fun onDuplicateDay(dayId: Long, name: String) = launchIfNotBlank(name) { onSelectDay(builder.duplicateDay(dayId, it)) }

    /** [delta] -1 = un posto prima, +1 = un posto dopo. */
    fun onMoveDay(dayId: Long, delta: Int) = launch {
        val days = (uiState.value as? RoutineEditorUiState.Content)?.routine?.days ?: return@launch
        val from = days.indexOfFirst { it.id == dayId }
        val to = from + delta
        if (from >= 0 && to in days.indices) builder.moveDay(routineId, from, to)
    }

    fun onDeleteDay(dayId: Long) = launch { builder.deleteDay(dayId) }

    // ------------------------------------------------------------------ Esercizi

    fun onUpdateTargets(routineExerciseId: Long, targets: ExerciseTargets) = launch { builder.updateTargets(routineExerciseId, targets) }

    fun onMoveExercise(dayId: Long, fromIndex: Int, toIndex: Int) = launch { builder.moveExercise(dayId, fromIndex, toIndex) }

    fun onMoveExerciseToDay(routineExerciseId: Long, targetDayId: Long) = launch { builder.moveExerciseToDay(routineExerciseId, targetDayId) }

    fun onLinkSuperset(routineExerciseId: Long) = launch { builder.linkWithNext(routineExerciseId) }

    fun onUnlinkSuperset(routineExerciseId: Long) = launch { builder.unlinkSuperset(routineExerciseId) }

    fun onRemoveExercise(routineExerciseId: Long) = launch { builder.removeExercise(routineExerciseId) }

    // ------------------------------------------------------------------ Mappatura

    private fun RoutineWithDays.toUi(thumbnails: Map<String, String>, parentName: String?) = RoutineEditorUi(
        id = routine.id,
        name = routine.name,
        isActive = routine.isActive,
        parentName = parentName,
        days = orderedDays.map { it.toUi(thumbnails) },
        schedule = schedule.associate { it.dayOfWeek to it.routineDayId },
    )

    private fun RoutineDayWithExercises.toUi(thumbnails: Map<String, String>): BuilderDayUi {
        val ordered = orderedExercises
        return BuilderDayUi(
            id = day.id,
            name = day.name,
            exercises = ordered.mapIndexed { index, item ->
                val planned = item.routineExercise
                val exercise = item.exercise
                val group = planned.supersetGroup
                val previousSame = group != null && ordered.getOrNull(index - 1)?.routineExercise?.supersetGroup == group
                val nextSame = group != null && ordered.getOrNull(index + 1)?.routineExercise?.supersetGroup == group
                BuilderExerciseUi(
                    id = planned.id,
                    exerciseId = exercise.id,
                    name = exercise.name,
                    muscleLabelRes = exercise.primaryMuscle.labelRes(),
                    muscle = exercise.primaryMuscle,
                    thumbnail = thumbnails[exercise.id]?.let(mediaRepository::resolve),
                    sets = planned.targetSets,
                    repsMin = planned.targetRepsMin,
                    repsMax = planned.targetRepsMax,
                    restSeconds = planned.restSeconds ?: exercise.defaultRestSeconds,
                    restIsDefault = planned.restSeconds == null,
                    defaultRestSeconds = exercise.defaultRestSeconds,
                    countsReps = exercise.trackingType != ExerciseTrackingType.DURATION &&
                        exercise.trackingType != ExerciseTrackingType.DISTANCE_DURATION &&
                        exercise.trackingType != ExerciseTrackingType.INCLINE_TIME,
                    supersetPosition = when {
                        !previousSame && nextSame -> SupersetPosition.FIRST
                        previousSame && nextSame -> SupersetPosition.MIDDLE
                        previousSame -> SupersetPosition.LAST
                        else -> SupersetPosition.NONE
                    },
                )
            },
            totalSets = totalSets,
            estimatedMinutes = estimatedMinutes(),
        )
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun launchIfNotBlank(text: String, block: suspend (String) -> Unit) {
        val clean = text.trim()
        if (clean.isNotEmpty()) launch { block(clean) }
    }

    private companion object {
        const val KEY_SELECTED_DAY = "selected_day"
    }
}
