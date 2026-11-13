package com.example.sport.core.data

import com.example.sport.core.database.dao.MachineSetupDao
import com.example.sport.core.database.dao.WorkoutSessionDao
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.database.entity.MachineSetupEntity
import com.example.sport.core.database.relation.ActiveSessionHeader
import com.example.sport.core.database.relation.WorkoutSessionWithExercises
import com.example.sport.core.designsystem.input.NumericInputConfig
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.SetType
import com.example.sport.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Grandezza registrabile in una serie. Collega il campo del logger, le regole del tastierino e la
 * colonna del database (con le conversioni: minuti ↔ secondi, km ↔ metri).
 */
enum class SetMetric(val inputConfig: NumericInputConfig) {
    WEIGHT(NumericInputConfig.WeightKg),
    REPS(NumericInputConfig.Reps),
    RPE(NumericInputConfig.Rpe),
    RIR(NumericInputConfig.Rir),
    DURATION_SECONDS(NumericInputConfig.Seconds),
    DURATION_MINUTES(NumericInputConfig.Minutes),
    DISTANCE_KM(NumericInputConfig.DistanceKm),
    ;

    fun read(set: ExerciseSetEntity): Double? = when (this) {
        WEIGHT -> set.weightKg
        REPS -> set.reps?.toDouble()
        RPE -> set.rpe
        RIR -> set.rir?.toDouble()
        DURATION_SECONDS -> set.durationSeconds?.toDouble()
        DURATION_MINUTES -> set.durationSeconds?.let { it / 60.0 }
        DISTANCE_KM -> set.distanceMeters?.let { it / 1000.0 }
    }

    /** Regole del tastierino nell'unità scelta: in libbre si sale di 5 lb e basta un decimale. */
    fun inputConfig(unit: WeightUnit): NumericInputConfig =
        if (this == WEIGHT && unit == WeightUnit.LB) NumericInputConfig.WeightLb else inputConfig

    /** Valore salvato (kg) → valore mostrato nel campo. */
    fun toDisplay(stored: Double, unit: WeightUnit): Double = if (this == WEIGHT) unit.fromKg(stored) else stored

    /** Valore digitato → valore da salvare (kg). */
    fun toStorage(displayed: Double, unit: WeightUnit): Double = if (this == WEIGHT) unit.toKg(displayed) else displayed

    companion object {
        /**
         * Campi mostrati nella riga serie, nell'ordine di compilazione. Con [withRpe] si aggiunge la
         * colonna dello sforzo nella scala scelta ([effort]: RPE o RIR).
         */
        fun forTracking(type: ExerciseTrackingType, withRpe: Boolean, effort: EffortScale = EffortScale.RPE): List<SetMetric> {
            val base = when (type) {
                ExerciseTrackingType.WEIGHT_REPS,
                ExerciseTrackingType.BODYWEIGHT_REPS,
                ExerciseTrackingType.ASSISTED_BODYWEIGHT,
                -> listOf(WEIGHT, REPS)
                ExerciseTrackingType.DURATION -> listOf(DURATION_SECONDS)
                ExerciseTrackingType.DISTANCE_DURATION -> listOf(DISTANCE_KM, DURATION_MINUTES)
            }
            return if (withRpe) base + effortMetric(effort) else base
        }

        fun effortMetric(effort: EffortScale): SetMetric = when (effort) {
            EffortScale.RPE -> RPE
            EffortScale.RIR -> RIR
        }
    }
}

data class ExerciseSummary(
    val exerciseId: String,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val completedSets: Int,
    val bestSet: ExerciseSetEntity?,
    /** Nuovo record di carico rispetto a tutte le sessioni precedenti. */
    val isWeightRecord: Boolean,
    val previousBestKg: Double?,
)

data class WorkoutSummary(
    val sessionId: Long,
    val title: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val completedSets: Int,
    val totalVolumeKg: Double,
    val exercises: List<ExerciseSummary>,
) {
    val records: List<ExerciseSummary> get() = exercises.filter { it.isWeightRecord }
}

/** Operazioni del Workout Logger. Ogni timestamp passa dal [Clock] iniettato. */
@Singleton
class WorkoutRepository @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val setupDao: MachineSetupDao,
    private val clock: Clock,
) {
    fun now(): Instant = Instant.now(clock)

    fun observeActiveSession(): Flow<WorkoutSessionWithExercises?> = sessionDao.observeActiveSession()

    fun observeActiveSessionHeader(): Flow<ActiveSessionHeader?> = sessionDao.observeActiveSessionHeader()

    /** Avvia la sessione del giorno di scheda; se ce n'è già una in corso restituisce quella. */
    suspend fun startFromRoutineDay(routineDayId: Long): Long =
        sessionDao.getActiveSessionId() ?: run {
            sessionDao.purgeDiscardedSessions()
            sessionDao.startSessionFromRoutineDay(routineDayId, now())
        }

    /** Allenamento libero, senza scheda: gli esercizi si aggiungono dal logger. */
    suspend fun startFree(title: String): Long =
        sessionDao.getActiveSessionId() ?: run {
            // Le sessioni annullate in passato non servono più: non devono restare nel database.
            sessionDao.purgeDiscardedSessions()
            sessionDao.startEmptySession(title, now())
        }

    suspend fun updateMetric(setId: Long, metric: SetMetric, value: Double?) {
        when (metric) {
            SetMetric.WEIGHT -> sessionDao.updateSetWeight(setId, value)
            SetMetric.REPS -> sessionDao.updateSetReps(setId, value?.roundToInt())
            SetMetric.RPE -> sessionDao.updateSetRpe(setId, value)
            SetMetric.RIR -> sessionDao.updateSetRir(setId, value?.roundToInt())
            SetMetric.DURATION_SECONDS -> sessionDao.updateSetDuration(setId, value?.roundToInt())
            SetMetric.DURATION_MINUTES -> sessionDao.updateSetDuration(setId, value?.let { (it * 60).roundToInt() })
            SetMetric.DISTANCE_KM -> sessionDao.updateSetDistance(setId, value?.let { it * 1000 })
        }
    }

    suspend fun setCompleted(setId: Long, completed: Boolean) =
        sessionDao.setSetCompleted(setId, completed, if (completed) now() else null)

    suspend fun setSetType(setId: Long, type: SetType) = sessionDao.updateSetType(setId, type)

    suspend fun addSet(sessionExerciseId: Long): Long = sessionDao.addSet(sessionExerciseId)

    suspend fun removeSet(setId: Long) = sessionDao.removeSet(setId)

    suspend fun addExercise(sessionId: Long, exerciseId: String): Long =
        sessionDao.addExerciseToSession(sessionId, exerciseId, DEFAULT_SETS_FOR_NEW_EXERCISE)

    suspend fun replaceExercise(sessionExerciseId: Long, exerciseId: String): Long =
        sessionDao.replaceExercise(sessionExerciseId, exerciseId)

    suspend fun removeExercise(sessionExerciseId: Long) = sessionDao.removeSessionExercise(sessionExerciseId)

    suspend fun exerciseIdOf(sessionExerciseId: Long): String? = sessionDao.getSessionExercise(sessionExerciseId)?.exerciseId

    suspend fun previousWorkingSets(exerciseId: String): List<ExerciseSetEntity> = sessionDao.getPreviousWorkingSets(exerciseId)

    suspend fun bestWeights(exerciseIds: Collection<String>, excludeSessionId: Long): Map<String, Double> =
        if (exerciseIds.isEmpty()) {
            emptyMap()
        } else {
            sessionDao.getBestWeights(exerciseIds.toList(), excludeSessionId).associate { it.exerciseId to it.bestWeightKg }
        }

    fun observeSetups(exerciseId: String): Flow<List<MachineSetupEntity>> = setupDao.observeSetupsForExercise(exerciseId)

    /** Salvataggio dal logger: aggiorna il profilo in uso (o ne crea uno), note comprese. */
    suspend fun saveSetup(sessionExerciseId: Long, params: List<MachineSetupParam>, notes: String?, defaultLabel: String): Long =
        setupDao.saveWithNotesForSessionExercise(sessionExerciseId, params, notes, defaultLabel, now())

    suspend fun saveSetupAsNew(sessionExerciseId: Long, label: String, params: List<MachineSetupParam>, notes: String?): Long {
        val setupId = setupDao.saveAsNewForSessionExercise(sessionExerciseId, label, params, now())
        setupDao.updateNotes(setupId, notes?.trim()?.takeIf { it.isNotEmpty() })
        return setupId
    }

    /**
     * Regolazioni "a freddo" dalla scheda esercizio, senza avviare un allenamento: crea il profilo
     * ([setupId] null) o lo aggiorna. Diventa il più recente, quindi il logger lo richiama da solo.
     */
    suspend fun saveSetupFromLibrary(
        exerciseId: String,
        setupId: Long?,
        label: String,
        params: List<MachineSetupParam>,
        notes: String?,
    ): Long = setupDao.saveFromLibrary(exerciseId, setupId, label, params, notes, now())

    suspend fun deleteSetup(setupId: Long) = setupDao.deleteById(setupId)

    suspend fun selectSetup(sessionExerciseId: Long, setupId: Long) =
        setupDao.selectForSessionExercise(sessionExerciseId, setupId, now())

    /**
     * Conclude la sessione eliminando le serie non spuntate o senza dati e gli esercizi rimasti vuoti.
     * @return false se non restava nessuna serie valida: la sessione era nulla ed è stata scartata.
     */
    suspend fun finish(sessionId: Long): Boolean = sessionDao.finishSession(sessionId, now(), discardIncompleteSets = true)

    suspend fun discard(sessionId: Long) = sessionDao.discardSession(sessionId, now())

    /** Riepilogo di una sessione conclusa, con i record di carico rispetto alle sessioni precedenti. */
    suspend fun summary(sessionId: Long): WorkoutSummary? {
        val session = sessionDao.getSessionWithExercises(sessionId) ?: return null
        val exercises = session.orderedExercises
        val previousBest = bestWeights(exercises.map { it.exercise.id }.toSet(), excludeSessionId = sessionId)
        val countedSets = exercises.map { item -> item to item.sets.filter { it.isCompleted && it.setType != SetType.WARMUP } }
        return WorkoutSummary(
            sessionId = session.session.id,
            title = session.session.title,
            startedAt = session.session.startedAt,
            endedAt = session.session.endedAt,
            completedSets = countedSets.sumOf { (_, sets) -> sets.size },
            totalVolumeKg = countedSets.sumOf { (item, sets) -> loadVolumeKg(item.exercise.trackingType, sets) },
            exercises = exercises.map { item ->
                val done = item.orderedSets.filter { it.isCompleted && it.setType != SetType.WARMUP }
                val best = done.maxWithOrNull(compareBy<ExerciseSetEntity>({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }, { it.durationSeconds ?: 0 }))
                val before = previousBest[item.exercise.id]
                ExerciseSummary(
                    exerciseId = item.exercise.id,
                    name = item.exercise.name,
                    trackingType = item.exercise.trackingType,
                    completedSets = done.size,
                    bestSet = best,
                    // Nell'assistito più peso significa più aiuto: non è un record.
                    isWeightRecord = item.exercise.trackingType != ExerciseTrackingType.ASSISTED_BODYWEIGHT &&
                        before != null && (best?.weightKg ?: 0.0) > before,
                    previousBestKg = before,
                )
            },
        )
    }

    private companion object {
        const val DEFAULT_SETS_FOR_NEW_EXERCISE = 3
    }
}

/**
 * Volume di carico (Σ kg × ripetizioni) delle serie date. Nell'assistito il peso è un aiuto e non
 * un carico, quindi non conta (come nella query dello storico).
 */
fun loadVolumeKg(type: ExerciseTrackingType, sets: List<ExerciseSetEntity>): Double =
    if (type == ExerciseTrackingType.ASSISTED_BODYWEIGHT) 0.0 else sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
