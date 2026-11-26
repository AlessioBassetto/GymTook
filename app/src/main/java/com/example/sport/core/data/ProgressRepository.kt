package com.example.sport.core.data

import com.example.sport.core.data.progress.DatedWeight
import com.example.sport.core.data.progress.ExerciseRecords
import com.example.sport.core.data.progress.ExerciseTrend
import com.example.sport.core.data.progress.WEEK_DAYS
import com.example.sport.core.data.progress.WeightTrendPoint
import com.example.sport.core.data.progress.exerciseTrends
import com.example.sport.core.data.progress.movingAverage
import com.example.sport.core.data.progress.personalRecords
import com.example.sport.core.database.dao.BodyWeightDao
import com.example.sport.core.database.dao.ExerciseDao
import com.example.sport.core.database.dao.HistoryRemoval
import com.example.sport.core.database.dao.ProgressDao
import com.example.sport.core.database.dao.WorkoutSessionDao
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.relation.ExerciseProgressPoint
import com.example.sport.core.database.relation.SessionSummary
import com.example.sport.core.database.relation.WorkoutSessionWithExercises
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Valori di una serie corretta a posteriori (peso sempre in kg, come nel database). */
data class SetValues(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    /** Pendenza del tapis roulant (%), per gli esercizi INCLINE_TIME. */
    val inclinePercent: Double? = null,
    /** Velocità del tapis roulant (km/h), per gli esercizi INCLINE_TIME. */
    val speedKmh: Double? = null,
) {
    /** Stesse regole del logger: almeno un dato misurato e nessun valore fuori scala. */
    val isValid: Boolean
        get() = ((reps ?: 0) > 0 || (durationSeconds ?: 0) > 0 || (distanceMeters ?: 0.0) > 0.0) &&
            (weightKg == null || weightKg in 0.0..MAX_WEIGHT_KG) &&
            (reps == null || reps in 0..MAX_REPS) &&
            (durationSeconds == null || durationSeconds in 0..MAX_DURATION_SECONDS) &&
            (distanceMeters == null || distanceMeters in 0.0..MAX_DISTANCE_METERS) &&
            (inclinePercent == null || inclinePercent in 0.0..MAX_INCLINE_PERCENT) &&
            (speedKmh == null || speedKmh in 0.0..MAX_SPEED_KMH)

    companion object {
        const val MAX_WEIGHT_KG = 2000.0
        const val MAX_REPS = 999
        const val MAX_DURATION_SECONDS = 36_000
        const val MAX_DISTANCE_METERS = 300_000.0
        const val MAX_INCLINE_PERCENT = 40.0
        const val MAX_SPEED_KMH = 30.0
    }
}

/** Riepilogo per esercizio della schermata Progressi. */
data class ExerciseOverview(val trends: List<ExerciseTrend>, val records: List<ExerciseRecords>)

/**
 * Progressi: storico allenamenti, record, andamento per esercizio e peso corporeo.
 * I calcoli stanno in `core.data.progress` (funzioni pure); qui solo il collegamento ai dati.
 */
@Singleton
class ProgressRepository @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val bodyWeightDao: BodyWeightDao,
    private val progressDao: ProgressDao,
    private val exerciseDao: ExerciseDao,
    private val calories: CalorieRepository,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun now(): Instant = Instant.now(clock)

    /** Fuso dell'utente: converte gli istanti delle sessioni in giorni di calendario. */
    fun zone(): ZoneId = clock.zone

    // ------------------------------------------------------------------ Allenamenti

    fun observeSessionHistory(limit: Int = SESSION_HISTORY_LIMIT): Flow<List<SessionSummary>> =
        sessionDao.observeCompletedSessionSummaries(limit)

    suspend fun session(sessionId: Long): WorkoutSessionWithExercises? = sessionDao.getSessionWithExercises(sessionId)

    /** Dettaglio "vivo" di un allenamento: si aggiorna quando una serie viene corretta o eliminata. */
    fun observeSession(sessionId: Long): Flow<WorkoutSessionWithExercises?> = sessionDao.observeSession(sessionId)

    /**
     * Elimina un allenamento concluso (es. registrato per errore): record e grafici si ricalcolano.
     * Le sue calorie spariscono con la riga, quindi anche i totali calorici.
     */
    suspend fun deleteSession(sessionId: Long) = sessionDao.deleteSession(sessionId)

    /**
     * Corregge una serie già registrata (es. 1000 kg al posto di 100). Record, volumi e grafici
     * leggono dal database e si ricalcolano da soli; la stima calorica dell'allenamento si riscrive.
     */
    suspend fun updateSet(setId: Long, values: SetValues) {
        require(values.isValid) { "Serie senza dati validi: $values" }
        sessionDao.updateSetValues(setId, values.weightKg, values.reps, values.durationSeconds, values.distanceMeters, values.inclinePercent, values.speedKmh)
        sessionDao.getSessionIdOfSet(setId)?.let { calories.recalculate(it) }
    }

    /** Elimina una serie anomala; se l'esercizio o l'allenamento restano vuoti spariscono anche loro. */
    suspend fun deleteSet(setId: Long): HistoryRemoval {
        val sessionId = sessionDao.getSessionIdOfSet(setId)
        return sessionDao.removeHistorySet(setId).also { sessionId?.let { calories.recalculate(it) } }
    }

    /** Elimina da un allenamento tutte le serie di un esercizio (es. un record di volume falsato). */
    suspend fun deleteExerciseFromSession(sessionId: Long, exerciseId: String): HistoryRemoval =
        sessionDao.removeExerciseFromSession(sessionId, exerciseId).also { calories.recalculate(sessionId) }

    /**
     * Azzera lo storico di un solo esercizio, lasciando intatti tutti gli altri dati.
     * @return numero di allenamenti da cui è stato rimosso.
     */
    suspend fun resetExerciseHistory(exerciseId: String): Int {
        val touched = sessionDao.getCompletedSessionIdsWithExercise(exerciseId)
        // Gli allenamenti rimasti vuoti spariscono; gli altri perdono l'esercizio e le sue calorie.
        return sessionDao.resetExerciseHistory(exerciseId).also { calories.recalculate(touched) }
    }

    // ------------------------------------------------------------------ Esercizi e record

    /** Andamento e record di tutti gli esercizi, da un'unica lettura delle serie. */
    fun observeExerciseOverview(): Flow<ExerciseOverview> =
        progressDao.observeCompletedWeightedSets()
            .map { sets -> ExerciseOverview(trends = exerciseTrends(sets), records = personalRecords(sets)) }
            .flowOn(Dispatchers.Default)

    fun observeExerciseRecords(exerciseId: String): Flow<ExerciseRecords?> =
        progressDao.observeCompletedWeightedSets()
            .map { sets -> personalRecords(sets.filter { it.exerciseId == exerciseId }).firstOrNull() }
            .flowOn(Dispatchers.Default)

    /** Un punto per sessione: carico massimo, volume e massimale stimato. */
    fun observeExerciseProgress(exerciseId: String, from: Instant): Flow<List<ExerciseProgressPoint>> =
        progressDao.observeExerciseProgress(exerciseId, from)

    fun observeHasHistory(exerciseId: String): Flow<Boolean> = progressDao.observeHasHistory(exerciseId)

    fun observeExercise(exerciseId: String): Flow<ExerciseEntity?> = exerciseDao.observeById(exerciseId)

    // ------------------------------------------------------------------ Peso corporeo

    fun observeLatestBodyWeight(): Flow<BodyWeightEntryEntity?> = bodyWeightDao.observeLatest()

    /**
     * Pesate da [from] a oggi con la media mobile a 7 giorni. Le pesate dei 6 giorni prima di
     * [from] entrano nel calcolo della media ma non nel risultato: il primo punto non parte "a freddo".
     */
    fun observeBodyWeightTrend(from: LocalDate): Flow<List<WeightTrendPoint>> {
        val warmUpStart = from.minusDays((WEEK_DAYS - 1).toLong())
        return bodyWeightDao.observeBetween(warmUpStart, LAST_DATE).map { entries ->
            movingAverage(entries.map { DatedWeight(it.date, it.weightKg) }).filter { !it.date.isBefore(from) }
        }
    }

    /**
     * Una pesata al giorno: un nuovo valore per la stessa data sostituisce il precedente
     * (conservando massa grassa e nota già registrate).
     */
    suspend fun saveBodyWeight(date: LocalDate, weightKg: Double) {
        require(weightKg in MIN_BODY_WEIGHT_KG..MAX_BODY_WEIGHT_KG) { "Peso fuori scala: $weightKg kg" }
        require(!date.isAfter(today())) { "Data futura: $date" }
        val existing = bodyWeightDao.getByDate(date)
        bodyWeightDao.upsert(
            BodyWeightEntryEntity(
                date = date,
                weightKg = weightKg,
                bodyFatPercent = existing?.bodyFatPercent,
                note = existing?.note,
                recordedAt = now(),
            ),
        )
        // Il peso entra nella stima calorica degli allenamenti vicini a questa data.
        calories.recalculateAll()
    }

    /** Ripristina una pesata eliminata (annulla), identica all'originale. */
    suspend fun restoreBodyWeight(entry: BodyWeightEntryEntity) {
        bodyWeightDao.upsert(entry)
        calories.recalculateAll()
    }

    suspend fun deleteBodyWeight(date: LocalDate) {
        bodyWeightDao.deleteByDate(date)
        calories.recalculateAll()
    }

    suspend fun bodyWeightOn(date: LocalDate): BodyWeightEntryEntity? = bodyWeightDao.getByDate(date)

    suspend fun latestBodyWeight(): BodyWeightEntryEntity? = bodyWeightDao.getLatest()

    suspend fun bodyWeightsBetween(from: LocalDate, to: LocalDate): List<BodyWeightEntryEntity> =
        bodyWeightDao.getBetween(from, to)

    companion object {
        const val SESSION_HISTORY_LIMIT = 500
        const val MIN_BODY_WEIGHT_KG = 20.0
        const val MAX_BODY_WEIGHT_KG = 400.0

        /** Inizio dello storico ("Tutto" nei filtri di periodo): prima dell'app non ci sono dati. */
        val EARLIEST_DATE: LocalDate = LocalDate.of(2000, 1, 1)

        /** Limite superiore aperto per le query sul peso (le date future sono comunque rifiutate). */
        private val LAST_DATE: LocalDate = LocalDate.of(9999, 12, 31)
    }
}
