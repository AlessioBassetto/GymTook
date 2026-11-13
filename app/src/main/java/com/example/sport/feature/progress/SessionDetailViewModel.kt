package com.example.sport.feature.progress

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.R
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.data.SetValues
import com.example.sport.core.data.loadVolumeKg
import com.example.sport.core.database.dao.HistoryRemoval
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    /** Valori salvati (kg, secondi, metri): servono a correggere la serie a posteriori. */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
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
    /** Allenamento eliminato dall'utente. */
    data object Deleted : SessionDetailEvent

    /** Eliminata l'ultima serie: l'allenamento è rimasto vuoto ed è uscito dallo storico. */
    data object Emptied : SessionDetailEvent

    data class Message(@StringRes val messageRes: Int) : SessionDetailEvent
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val sessionId = savedStateHandle.toRoute<SessionDetailRoute>().sessionId

    /** Lettura "viva": dopo una correzione o un'eliminazione serie, volume e totali si aggiornano. */
    val uiState: StateFlow<SessionDetailUiState> = combine(
        repository.observeSession(sessionId),
        settingsRepository.settings,
    ) { session, settings ->
        // Solo allenamenti conclusi: quello in corso si apre nel logger.
        session?.takeIf { it.session.status == SessionStatus.COMPLETED }?.toUi(settings.weightUnit)
            ?: SessionDetailUiState.NotFound
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState.Loading)

    private val eventChannel = Channel<SessionDetailEvent>(Channel.BUFFERED)
    val events: Flow<SessionDetailEvent> = eventChannel.receiveAsFlow()

    fun onDelete() {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            eventChannel.send(SessionDetailEvent.Deleted)
        }
    }

    /** Correzione di una serie registrata male: record, volumi e grafici si ricalcolano dal database. */
    fun onUpdateSet(setId: Long, values: SetValues) {
        if (!values.isValid) return
        viewModelScope.launch {
            repository.updateSet(setId, values)
            eventChannel.send(SessionDetailEvent.Message(R.string.set_updated))
        }
    }

    fun onDeleteSet(setId: Long) {
        viewModelScope.launch {
            when (repository.deleteSet(setId)) {
                HistoryRemoval.SESSION -> eventChannel.send(SessionDetailEvent.Emptied)
                HistoryRemoval.SET, HistoryRemoval.EXERCISE -> eventChannel.send(SessionDetailEvent.Message(R.string.set_deleted))
                HistoryRemoval.NOTHING -> Unit
            }
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
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
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
