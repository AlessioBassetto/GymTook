package com.example.sport.feature.progress

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.data.loadVolumeKg
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.database.relation.WorkoutSessionWithExercises
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.SessionStatus
import com.example.sport.core.model.SetType
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.ui.format.Formatters
import com.example.sport.feature.workout.SetFormat
import com.example.sport.navigation.SessionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Una serie completata: numero (o R/D/C), carico × ripetizioni, sforzo percepito. */
@Immutable
data class SessionSetUi(
    val id: Long,
    val type: SetType,
    /** Numero progressivo fra le serie non di riscaldamento. */
    val number: Int,
    val description: String,
    val rpe: Double?,
    val rir: Int?,
)

@Immutable
data class SessionExerciseUi(
    val id: Long,
    val exerciseId: String,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val sets: List<SessionSetUi>,
    val volumeKg: Double,
)

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState
    data object NotFound : SessionDetailUiState

    data class Content(
        val title: String,
        val date: LocalDate,
        val startTime: LocalTime,
        val endTime: LocalTime?,
        val duration: String,
        val workingSets: Int,
        val volumeKg: Double,
        val exercises: List<SessionExerciseUi>,
        val notes: String?,
    ) : SessionDetailUiState
}

sealed interface SessionDetailEvent {
    data object Deleted : SessionDetailEvent
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val sessionId = savedStateHandle.toRoute<SessionDetailRoute>().sessionId
    private val _uiState = MutableStateFlow<SessionDetailUiState>(SessionDetailUiState.Loading)
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<SessionDetailEvent>(Channel.BUFFERED)
    val events: Flow<SessionDetailEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val session = repository.session(sessionId)
            val unit = settingsRepository.current().weightUnit
            // Solo allenamenti conclusi: quello in corso si apre nel logger.
            _uiState.value = session?.takeIf { it.session.status == SessionStatus.COMPLETED }?.toUi(unit)
                ?: SessionDetailUiState.NotFound
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            eventChannel.send(SessionDetailEvent.Deleted)
        }
    }

    private fun WorkoutSessionWithExercises.toUi(unit: WeightUnit): SessionDetailUiState.Content {
        val zone = repository.zone()
        val exercises = orderedExercises.mapNotNull { item ->
            val done = item.orderedSets.filter { it.isCompleted }
            if (done.isEmpty()) return@mapNotNull null
            var number = 0
            SessionExerciseUi(
                id = item.sessionExercise.id,
                exerciseId = item.exercise.id,
                name = item.exercise.name,
                trackingType = item.exercise.trackingType,
                sets = done.map { set ->
                    if (set.setType != SetType.WARMUP) number++
                    set.toUi(item.exercise.trackingType, number, unit)
                },
                volumeKg = loadVolumeKg(item.exercise.trackingType, done.filter { it.setType != SetType.WARMUP }),
            )
        }
        val working = exercises.flatMap { it.sets }.count { it.type != SetType.WARMUP }
        return SessionDetailUiState.Content(
            title = session.title,
            date = session.startedAt.toLocalDate(zone),
            startTime = session.startedAt.atZone(zone).toLocalTime(),
            endTime = session.endedAt?.atZone(zone)?.toLocalTime(),
            duration = session.endedAt?.let { Formatters.durationHuman(Duration.between(session.startedAt, it)) } ?: "—",
            workingSets = working,
            volumeKg = exercises.sumOf { it.volumeKg },
            exercises = exercises,
            notes = session.notes?.takeIf { it.isNotBlank() },
        )
    }

    private fun ExerciseSetEntity.toUi(type: ExerciseTrackingType, number: Int, unit: WeightUnit) = SessionSetUi(
        id = id,
        type = setType,
        number = number,
        description = describe(type, unit),
        rpe = rpe,
        rir = rir,
    )

    /** "100 kg × 5" per i pesi; per gli altri tipi lo stesso formato della colonna "Precedente". */
    private fun ExerciseSetEntity.describe(type: ExerciseTrackingType, unit: WeightUnit): String {
        val weight = weightKg?.takeIf { it > 0 }
        val repetitions = reps
        return when {
            type != ExerciseTrackingType.WEIGHT_REPS -> SetFormat.previous(type, this, unit) ?: "—"
            weight != null && repetitions != null -> Formatters.setLabel(weight, repetitions, unit)
            // Peso non inserito: "× 8", come le serie a corpo libero senza zavorra.
            repetitions != null -> "× $repetitions"
            else -> weight?.let { Formatters.weightKg(it, unit) } ?: "—"
        }
    }
}
