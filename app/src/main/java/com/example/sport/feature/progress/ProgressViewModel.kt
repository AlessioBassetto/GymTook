package com.example.sport.feature.progress

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.data.progress.ExerciseRecords
import com.example.sport.core.data.progress.ExerciseTrend
import com.example.sport.core.data.progress.WeightTrendPoint
import com.example.sport.core.data.progress.weeklyChange
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.database.relation.SessionSummary
import com.example.sport.core.ui.format.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Riquadro del peso corporeo nella panoramica: ultima pesata, media e mini-grafico. */
@Immutable
data class WeightSummaryUi(
    val latestKg: Double,
    val latestDate: LocalDate,
    val averageKg: Double?,
    val weeklyChange: Double?,
    /** Ultimi [ProgressViewModel.OVERVIEW_WEIGHT_DAYS] giorni, per il mini-grafico. */
    val points: List<WeightTrendPoint>,
)

/** Andamento di un esercizio: 1RM stimato dell'ultima sessione e variazione dalla prima. */
@Immutable
data class ExerciseTrendUi(
    val exerciseId: String,
    val name: String,
    val sessionCount: Int,
    val lastDate: LocalDate,
    val latestOneRepMaxKg: Double?,
    val oneRepMaxChangeKg: Double?,
)

/** Record di sempre di un esercizio. */
@Immutable
data class ExerciseRecordUi(
    val exerciseId: String,
    val name: String,
    val heaviestWeightKg: Double,
    val heaviestReps: Int,
    val oneRepMaxKg: Double?,
    val bestVolumeKg: Double,
    /** Giorno del record più recente fra i tre. */
    val latestDate: LocalDate,
)

@Immutable
data class SessionHistoryUi(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val duration: String,
    val sets: Int,
    val volumeKg: Double,
    /** Stima in kcal; null se non calcolabile (nessuna pesata). */
    val caloriesBurned: Int? = null,
)

/**
 * Totali calorici dello storico. Si calcolano dalle righe degli allenamenti: eliminare un
 * allenamento, correggerne le serie o azzerare un esercizio li aggiorna da soli.
 */
@Immutable
data class CalorieTotalsUi(
    val weekKcal: Int,
    val monthKcal: Int,
    /** false se nessun allenamento ha ancora una stima (manca una pesata). */
    val hasEstimates: Boolean,
)

sealed interface ProgressUiState {
    data object Loading : ProgressUiState

    data class Content(
        val bodyWeight: WeightSummaryUi?,
        val trends: List<ExerciseTrendUi>,
        val records: List<ExerciseRecordUi>,
        val sessions: List<SessionHistoryUi>,
        val calorieTotals: CalorieTotalsUi? = null,
    ) : ProgressUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: ProgressRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val entryEditor = BodyWeightEntryEditor(repository, settingsRepository, viewModelScope)

    /** Conferma delle pesate salvate dal foglio. */
    val weightSaved: Flow<LocalDate> = entryEditor.saved

    /** La data è letta a ogni sottoscrizione (come in "Oggi"): tornando in primo piano il periodo è aggiornato. */
    private val overviewWeightTrend = flow { emit(repository.today()) }
        .flatMapLatest { today -> repository.observeBodyWeightTrend(today.minusDays(OVERVIEW_WEIGHT_DAYS - 1L)) }

    val uiState: StateFlow<ProgressUiState> = combine(
        repository.observeLatestBodyWeight(),
        overviewWeightTrend,
        repository.observeExerciseOverview(),
        repository.observeSessionHistory(),
    ) { latest, weightPoints, overview, sessions ->
        val zone = repository.zone()
        ProgressUiState.Content(
            bodyWeight = latest?.let { weightSummary(it, weightPoints) },
            trends = overview.trends.map { it.toUi(zone) },
            records = overview.records.map { it.toUi(zone) },
            sessions = sessions.map { it.toUi(zone) },
            calorieTotals = calorieTotals(sessions, repository.today(), zone),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState.Loading)

    fun onWeighIn() = entryEditor.open()

    private val sessionDeletedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Conferma dell'eliminazione di un allenamento dallo storico. */
    val sessionDeleted: Flow<Unit> = sessionDeletedChannel.receiveAsFlow()

    /** Elimina un allenamento concluso: record, volumi e grafici si ricalcolano dal database. */
    fun onDeleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            sessionDeletedChannel.send(Unit)
        }
    }

    private fun weightSummary(latest: BodyWeightEntryEntity, points: List<WeightTrendPoint>) = WeightSummaryUi(
        latestKg = latest.weightKg,
        latestDate = latest.date,
        averageKg = points.lastOrNull()?.takeIf { it.date == latest.date }?.averageKg,
        weeklyChange = weeklyChange(points),
        points = points,
    )

    private fun ExerciseTrend.toUi(zone: ZoneId) = ExerciseTrendUi(
        exerciseId = exerciseId,
        name = exerciseName,
        sessionCount = sessionCount,
        lastDate = lastPerformedAt.toLocalDate(zone),
        latestOneRepMaxKg = latestOneRepMax,
        oneRepMaxChangeKg = oneRepMaxChange,
    )

    private fun ExerciseRecords.toUi(zone: ZoneId) = ExerciseRecordUi(
        exerciseId = exerciseId,
        name = exerciseName,
        heaviestWeightKg = heaviestSet.valueKg,
        heaviestReps = heaviestSet.reps ?: 0,
        oneRepMaxKg = bestOneRepMax?.valueKg,
        bestVolumeKg = bestSessionVolume.valueKg,
        latestDate = latestAchievedAt.toLocalDate(zone),
    )

    private fun SessionSummary.toUi(zone: ZoneId) = SessionHistoryUi(
        id = id,
        title = title,
        date = startedAt.toLocalDate(zone),
        duration = endedAt?.let { Formatters.durationHuman(Duration.between(startedAt, it)) } ?: "—",
        sets = workingSetCount,
        volumeKg = totalVolumeKg,
        caloriesBurned = caloriesBurned,
    )

    companion object {
        /** Il mini-grafico della panoramica copre circa tre mesi. */
        const val OVERVIEW_WEIGHT_DAYS = 90

        /** Righe mostrate per "Esercizi" e "Record" prima di "Mostra tutti". */
        const val PREVIEW_ROWS = 5
    }
}

/**
 * Calorie della settimana in corso (da lunedì) e degli ultimi 30 giorni, oggi compreso.
 * Gli allenamenti senza stima non contano. null se lo storico è vuoto.
 */
internal fun calorieTotals(sessions: List<SessionSummary>, today: LocalDate, zone: ZoneId): CalorieTotalsUi? {
    if (sessions.isEmpty()) return null
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monthStart = today.minusDays(TOTALS_DAYS - 1L)
    fun sumFrom(start: LocalDate) = sessions
        .filter { it.startedAt.toLocalDate(zone) in start..today }
        .sumOf { it.caloriesBurned ?: 0 }
    return CalorieTotalsUi(
        weekKcal = sumFrom(weekStart),
        monthKcal = sumFrom(monthStart),
        hasEstimates = sessions.any { it.caloriesBurned != null },
    )
}

private const val TOTALS_DAYS = 30
