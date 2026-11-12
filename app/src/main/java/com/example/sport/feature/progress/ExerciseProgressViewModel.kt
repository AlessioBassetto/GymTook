package com.example.sport.feature.progress

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.R
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.data.progress.PersonalRecord
import com.example.sport.core.data.progress.RecordKind
import com.example.sport.core.database.relation.ExerciseProgressPoint
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.navigation.ExerciseProgressRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Grandezza mostrata nel grafico di un esercizio (un valore per sessione). */
enum class ProgressMetric(@StringRes val labelRes: Int) {
    ONE_REP_MAX(R.string.metric_one_rep_max),
    MAX_WEIGHT(R.string.metric_max_weight),
    VOLUME(R.string.metric_volume),
    REPS(R.string.metric_reps),
    ;

    /** Valore della sessione; null quando la grandezza non ha senso (es. 1RM senza carico). */
    fun valueOf(point: ExerciseProgressPoint): Double? = when (this) {
        ONE_REP_MAX -> point.bestEstimatedOneRepMaxKg
        MAX_WEIGHT -> point.maxWeightKg.takeIf { it > 0 }
        VOLUME -> point.totalVolumeKg.takeIf { it > 0 }
        REPS -> point.totalReps.toDouble()
    }

    companion object {
        /**
         * Grandezze sensate per il tipo di esercizio: a corpo libero il carico è la sola zavorra
         * (niente 1RM né volume), con l'assistenza il "carico" è un aiuto e conta solo ripetere di più.
         */
        fun availableFor(type: ExerciseTrackingType): List<ProgressMetric> = when (type) {
            ExerciseTrackingType.WEIGHT_REPS -> listOf(ONE_REP_MAX, MAX_WEIGHT, VOLUME, REPS)
            ExerciseTrackingType.BODYWEIGHT_REPS -> listOf(REPS, MAX_WEIGHT)
            ExerciseTrackingType.ASSISTED_BODYWEIGHT -> listOf(REPS)
            ExerciseTrackingType.DURATION, ExerciseTrackingType.DISTANCE_DURATION -> emptyList()
        }

        /** Esercizi con un grafico dei progressi (serie a ripetizioni). */
        fun hasChart(type: ExerciseTrackingType): Boolean = availableFor(type).isNotEmpty()
    }
}

@Immutable
data class MetricPoint(val date: LocalDate, val value: Double)

/** Una sessione dell'esercizio nel periodo, dalla più recente. */
@Immutable
data class ExerciseSessionUi(
    val sessionId: Long,
    val date: LocalDate,
    val setCount: Int,
    val totalReps: Int,
    val maxWeightKg: Double?,
    val volumeKg: Double?,
    val oneRepMaxKg: Double?,
)

/** Record di sempre: tocco → la sessione in cui è stato stabilito. */
@Immutable
data class RecordTileUi(
    val kind: RecordKind,
    val valueKg: Double,
    val weightKg: Double?,
    val reps: Int?,
    val date: LocalDate,
    val sessionId: Long,
)

sealed interface ExerciseProgressUiState {
    data object Loading : ExerciseProgressUiState
    data object NotFound : ExerciseProgressUiState

    data class Content(
        val exerciseId: String,
        val name: String,
        val metrics: List<ProgressMetric>,
        val metric: ProgressMetric?,
        val range: TimeRange,
        /** Un punto per giorno (il migliore se le sessioni sono due), in ordine cronologico. */
        val points: List<MetricPoint>,
        val sessions: List<ExerciseSessionUi>,
        val records: List<RecordTileUi>,
    ) : ExerciseProgressUiState {
        val latest: Double? get() = points.lastOrNull()?.value
        val best: Double? get() = points.maxOfOrNull { it.value }
        val change: Double? get() = if (points.size < 2) null else points.last().value - points.first().value
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProgressRepository,
) : ViewModel() {

    private val exerciseId = savedStateHandle.toRoute<ExerciseProgressRoute>().exerciseId
    private val range = MutableStateFlow(TimeRange.HALF_YEAR)
    private val chosenMetric = MutableStateFlow<ProgressMetric?>(null)

    private val rowsInRange = range.flatMapLatest { selected ->
        val from = selected.startDate(repository.today()).atStartOfDay(repository.zone()).toInstant()
        repository.observeExerciseProgress(exerciseId, from).map { rows -> selected to rows }
    }

    val uiState: StateFlow<ExerciseProgressUiState> = combine(
        repository.observeExercise(exerciseId),
        rowsInRange,
        repository.observeExerciseRecords(exerciseId),
        chosenMetric,
    ) { exercise, (selected, rows), records, chosen ->
        if (exercise == null) return@combine ExerciseProgressUiState.NotFound
        val zone = repository.zone()
        val metrics = ProgressMetric.availableFor(exercise.trackingType)
        val metric = chosen?.takeIf { it in metrics } ?: metrics.firstOrNull()
        ExerciseProgressUiState.Content(
            exerciseId = exercise.id,
            name = exercise.name,
            metrics = metrics,
            metric = metric,
            range = selected,
            points = metric?.let { dailyPoints(rows, it, zone) }.orEmpty(),
            sessions = rows.asReversed().map { it.toUi(zone) },
            records = records?.let { listOfNotNull(it.heaviestSet, it.bestOneRepMax, it.bestSessionVolume) }
                .orEmpty()
                .map { it.toUi(zone) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseProgressUiState.Loading)

    fun onSelectRange(selected: TimeRange) {
        range.value = selected
    }

    fun onSelectMetric(metric: ProgressMetric) {
        chosenMetric.value = metric
    }

    private fun dailyPoints(rows: List<ExerciseProgressPoint>, metric: ProgressMetric, zone: ZoneId): List<MetricPoint> =
        rows.mapNotNull { row -> metric.valueOf(row)?.let { row.performedAt.toLocalDate(zone) to it } }
            .groupBy({ it.first }, { it.second })
            .map { (date, values) -> MetricPoint(date, values.max()) }
            .sortedBy { it.date }

    private fun ExerciseProgressPoint.toUi(zone: ZoneId) = ExerciseSessionUi(
        sessionId = sessionId,
        date = performedAt.toLocalDate(zone),
        setCount = setCount,
        totalReps = totalReps,
        maxWeightKg = maxWeightKg.takeIf { it > 0 },
        volumeKg = totalVolumeKg.takeIf { it > 0 },
        oneRepMaxKg = bestEstimatedOneRepMaxKg,
    )

    private fun PersonalRecord.toUi(zone: ZoneId) = RecordTileUi(
        kind = kind,
        valueKg = valueKg,
        weightKg = weightKg,
        reps = reps,
        date = achievedAt.toLocalDate(zone),
        sessionId = sessionId,
    )
}
