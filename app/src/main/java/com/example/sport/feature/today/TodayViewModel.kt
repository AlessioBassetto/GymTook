package com.example.sport.feature.today

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sport.core.data.ExerciseRepository
import com.example.sport.core.data.RoutineRepository
import com.example.sport.core.data.TodayPlan
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.data.estimatedMinutes
import com.example.sport.core.media.MediaRepository
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PlannedExerciseUi(
    val exerciseId: String,
    val name: String,
    @StringRes val muscleLabelRes: Int,
    @StringRes val equipmentLabelRes: Int,
    val sets: Int,
    val reps: String,
    val rest: String,
    val thumbnail: Any?,
)

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class NoRoutine(val dateLabel: String) : TodayUiState

    data class Rest(
        val dateLabel: String,
        val routineName: String,
        val nextDayName: String?,
        val nextDateLabel: String?,
    ) : TodayUiState

    data class Workout(
        val dateLabel: String,
        val routineName: String,
        val routineDayId: Long,
        val dayName: String,
        val exercises: List<PlannedExerciseUi>,
        val totalSets: Int,
        val estimatedMinutes: Int,
    ) : TodayUiState
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    routineRepository: RoutineRepository,
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(
        routineRepository.observeTodayPlan(),
        exerciseRepository.observeThumbnailPaths(),
    ) { plan, thumbnails -> plan.toUiState(thumbnails) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    /** True se c'è una sessione aperta: il pulsante principale diventa "Riprendi allenamento". */
    val hasActiveSession: StateFlow<Boolean> = workoutRepository.observeActiveSessionHeader()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val openWorkoutChannel = Channel<Unit>(Channel.CONFLATED)
    val openWorkout: Flow<Unit> = openWorkoutChannel.receiveAsFlow()

    fun startWorkout(routineDayId: Long) {
        viewModelScope.launch {
            workoutRepository.startFromRoutineDay(routineDayId)
            openWorkoutChannel.send(Unit)
        }
    }

    fun startFreeWorkout(title: String) {
        viewModelScope.launch {
            workoutRepository.startFree(title)
            openWorkoutChannel.send(Unit)
        }
    }

    private fun TodayPlan.toUiState(thumbnails: Map<String, String>): TodayUiState {
        val dateLabel = Formatters.longDate(date)
        return when (this) {
            is TodayPlan.NoActiveRoutine -> TodayUiState.NoRoutine(dateLabel)
            is TodayPlan.RestDay -> TodayUiState.Rest(
                dateLabel = dateLabel,
                routineName = routineName,
                nextDayName = next?.dayName,
                nextDateLabel = next?.let { Formatters.longDate(it.date) },
            )
            is TodayPlan.Workout -> {
                val exercises = day.orderedExercises.map { item ->
                    val planned = item.routineExercise
                    val exercise = item.exercise
                    PlannedExerciseUi(
                        exerciseId = exercise.id,
                        name = exercise.name,
                        muscleLabelRes = exercise.primaryMuscle.labelRes(),
                        equipmentLabelRes = exercise.equipment.labelRes(),
                        sets = planned.targetSets,
                        reps = Formatters.repRange(planned.targetRepsMin, planned.targetRepsMax),
                        rest = Formatters.duration((planned.restSeconds ?: exercise.defaultRestSeconds).toLong()),
                        thumbnail = thumbnails[exercise.id]?.let(mediaRepository::resolve),
                    )
                }
                TodayUiState.Workout(
                    dateLabel = dateLabel,
                    routineName = routineName,
                    routineDayId = day.day.id,
                    dayName = day.day.name,
                    exercises = exercises,
                    totalSets = exercises.sumOf { it.sets },
                    estimatedMinutes = day.estimatedMinutes(),
                )
            }
        }
    }
}
