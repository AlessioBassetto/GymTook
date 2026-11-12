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

    /** Elimina un allenamento concluso (es. registrato per errore): record e grafici si ricalcolano. */
    suspend fun deleteSession(sessionId: Long) = sessionDao.deleteSession(sessionId)

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
    }

    /** Ripristina una pesata eliminata (annulla), identica all'originale. */
    suspend fun restoreBodyWeight(entry: BodyWeightEntryEntity) = bodyWeightDao.upsert(entry)

    suspend fun deleteBodyWeight(date: LocalDate) = bodyWeightDao.deleteByDate(date)

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
