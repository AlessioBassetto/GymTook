package com.example.sport.core.data

import android.util.Log
import com.example.sport.core.data.calories.BodyMetrics
import com.example.sport.core.data.calories.CalorieExercise
import com.example.sport.core.data.calories.CalorieSession
import com.example.sport.core.data.calories.CalorieSet
import com.example.sport.core.data.calories.WorkoutCalorieCalculator
import com.example.sport.core.database.dao.BodyProfileDao
import com.example.sport.core.database.dao.BodyWeightDao
import com.example.sport.core.database.dao.CalorieDao
import com.example.sport.core.database.entity.BodyProfileEntity
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.database.relation.CalorieSetRow
import com.example.sport.core.database.relation.SessionCalories
import com.example.sport.core.database.relation.SessionTimes
import com.example.sport.core.model.BiologicalSex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profilo corporeo e calorie degli allenamenti.
 *
 * Le calorie sono salvate nella riga di ogni allenamento (storico e totali le leggono senza
 * ricalcolare), ma restano un dato derivato: ogni evento che cambia un ingrediente del calcolo
 * le riscrive.
 * - fine allenamento e correzioni di serie → [recalculate] delle sessioni toccate;
 * - pesate e profilo corporeo → [recalculateAll] (il peso usato dipende dalla data);
 * - primo avvio dopo l'aggiornamento allo schema v4, ripristino di un backup → [recalculateMissing]
 *   / [recalculateAll].
 * Un allenamento eliminato sparisce con la sua riga: i totali calorici lo perdono da soli.
 */
@Singleton
class CalorieRepository @Inject constructor(
    private val calorieDao: CalorieDao,
    private val bodyProfileDao: BodyProfileDao,
    private val bodyWeightDao: BodyWeightDao,
    private val clock: Clock,
) {
    private val calculator = WorkoutCalorieCalculator()

    /** Un ricalcolo alla volta: l'ultimo avviato legge i dati più recenti e scrive per ultimo. */
    private val mutex = Mutex()

    // ------------------------------------------------------------------ Profilo corporeo

    fun observeProfile(): Flow<BodyProfileEntity?> = bodyProfileDao.observe()

    suspend fun profile(): BodyProfileEntity? = bodyProfileDao.get()

    /** Salva altezza, anno di nascita e sesso (tutti facoltativi) e aggiorna le stime di tutto lo storico. */
    suspend fun saveProfile(heightCm: Float?, birthYear: Int?, sex: BiologicalSex?) {
        require(heightCm == null || heightCm in MIN_HEIGHT_CM..MAX_HEIGHT_CM) { "Altezza fuori scala: $heightCm cm" }
        require(birthYear == null || birthYear in birthYearRange()) { "Anno di nascita fuori scala: $birthYear" }
        bodyProfileDao.upsert(
            BodyProfileEntity(heightCm = heightCm, birthYear = birthYear, sex = sex, updatedAt = Instant.now(clock)),
        )
        recalculateAll()
    }

    /** Anni di nascita accettati: dai 10 ai 100 anni di età. */
    fun birthYearRange(): IntRange {
        val year = LocalDate.now(clock).year
        return (year - MAX_AGE_YEARS)..(year - MIN_AGE_YEARS)
    }

    // ------------------------------------------------------------------ Ricalcolo

    /** Ricalcola le sessioni indicate (quelle non più concluse o eliminate vengono ignorate). */
    suspend fun recalculate(sessionIds: Collection<Long>) = mutex.withLock {
        val sessions = sessionIds.distinct().chunked(MAX_SQL_ARGS).flatMap { calorieDao.getCompletedSessionTimes(it) }
        calorieDao.applyCalories(estimate(sessions))
    }

    suspend fun recalculate(sessionId: Long) = recalculate(listOf(sessionId))

    /** Ricalcola tutto lo storico: dopo una pesata, una modifica del profilo o un ripristino. */
    suspend fun recalculateAll() = mutex.withLock {
        calorieDao.applyCalories(estimate(calorieDao.getCompletedSessionTimes()))
    }

    /**
     * Stima gli allenamenti che non l'hanno ancora: quelli salvati prima dell'aggiornamento
     * (la migrazione v3 → v4 li lascia a NULL) o quando non c'era nessuna pesata.
     */
    suspend fun recalculateMissing() {
        val missing = calorieDao.getCompletedSessionIdsWithoutCalories()
        if (missing.isEmpty() || bodyWeightDao.getLatest() == null) return
        recalculate(missing)
    }

    /** All'avvio dell'app, in background: completa le stime mancanti senza rallentare l'apertura. */
    fun recalculateMissingInBackground(scope: CoroutineScope): Job = scope.launch {
        try {
            recalculateMissing()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Stima delle calorie mancanti non riuscita", error)
        }
    }

    /**
     * Calcolo vero e proprio: una query per le serie (a blocchi, per il limite di parametri
     * SQLite), una per le pesate, una per il profilo; poi il calcolatore puro per ogni sessione.
     */
    private suspend fun estimate(sessions: List<SessionTimes>): List<SessionCalories> {
        if (sessions.isEmpty()) return emptyList()
        val weights = bodyWeightDao.getAll()
        val profile = bodyProfileDao.get()
        val setsBySession = sessions.map { it.id }
            .chunked(MAX_SQL_ARGS)
            .flatMap { calorieDao.getCalorieSetRows(it) }
            .groupBy { it.sessionId }
        return sessions.map { session ->
            val date = session.startedAt.atZone(clock.zone).toLocalDate()
            val weightKg = bodyWeightFor(date, weights)
            val sets = setsBySession[session.id].orEmpty().map { it.toCalorieSet() }
            val estimate = weightKg?.let { weight ->
                calculator.estimate(
                    body = BodyMetrics(
                        weightKg = weight,
                        heightCm = profile?.heightCm?.toDouble(),
                        ageYears = profile?.birthYear?.let { (date.year - it).coerceIn(MIN_AGE_YEARS, MAX_AGE_YEARS) },
                        sex = profile?.sex,
                    ),
                    session = CalorieSession(
                        elapsed = session.endedAt?.let { Duration.between(session.startedAt, it) },
                        sets = sets,
                    ),
                )
            }
            SessionCalories(
                sessionId = session.id,
                caloriesBurned = estimate?.totalKcal,
                bodyWeightKg = weightKg.takeIf { estimate != null },
            )
        }
    }

    private fun CalorieSetRow.toCalorieSet() = CalorieSet(
        exercise = CalorieExercise(
            id = exerciseId,
            name = name,
            nameEn = nameEn,
            equipment = equipment,
            primaryMuscle = primaryMuscle,
            trackingType = trackingType,
            category = category,
            mechanic = mechanic,
            force = force,
            isUnilateral = isUnilateral,
        ),
        setType = setType,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        rpe = rpe,
        rir = rir,
        plannedRestSeconds = restSeconds,
    )

    companion object {
        private const val TAG = "CalorieRepository"

        /** Ben sotto il limite storico di 999 parametri per query di SQLite (Android 8). */
        private const val MAX_SQL_ARGS = 500

        const val MIN_HEIGHT_CM = 100f
        const val MAX_HEIGHT_CM = 250f
        const val MIN_AGE_YEARS = 10
        const val MAX_AGE_YEARS = 100

        /**
         * Peso da usare per un allenamento: l'ultima pesata fino a quel giorno; se l'utente ha
         * iniziato a pesarsi dopo, la prima disponibile. [weights] in ordine cronologico.
         */
        fun bodyWeightFor(date: LocalDate, weights: List<BodyWeightEntryEntity>): Double? =
            (weights.lastOrNull { !it.date.isAfter(date) } ?: weights.firstOrNull())?.weightKg
    }
}
