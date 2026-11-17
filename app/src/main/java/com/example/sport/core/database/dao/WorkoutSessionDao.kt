package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.database.entity.MachineSetupEntity
import com.example.sport.core.database.entity.RoutineDayEntity
import com.example.sport.core.database.entity.RoutineExerciseEntity
import com.example.sport.core.database.entity.SessionExerciseEntity
import com.example.sport.core.database.entity.WorkoutSessionEntity
import com.example.sport.core.model.SessionStatus
import com.example.sport.core.model.SetType
import com.example.sport.core.database.relation.ActiveSessionHeader
import com.example.sport.core.database.relation.ExerciseBestWeight
import com.example.sport.core.database.relation.SessionSummary
import com.example.sport.core.database.relation.WorkoutSessionWithExercises
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Cosa è sparito dallo storico insieme a una serie o a un esercizio eliminati a posteriori. */
enum class HistoryRemoval {
    /** Non c'era nulla da eliminare (dato già rimosso). */
    NOTHING,

    /** Solo la serie: l'esercizio ne ha ancora altre. */
    SET,

    /** Era l'ultima serie dell'esercizio: l'esercizio è uscito dall'allenamento. */
    EXERCISE,

    /** L'allenamento è rimasto senza esercizi ed è stato eliminato. */
    SESSION,
}

/** Lanciata se si tenta di avviare una sessione mentre un'altra è ancora in corso. */
class ActiveSessionExistsException(val activeSessionId: Long) :
    IllegalStateException("Esiste già una sessione in corso (id=$activeSessionId)")

/**
 * Sessioni di allenamento e serie. Le operazioni composte sono transazionali così il logger
 * non può mai lasciare il database in uno stato intermedio (es. app chiusa a metà).
 */
@Dao
interface WorkoutSessionDao {

    // ---------------------------------------------------------------- Letture reattive

    /** Sessione in corso con esercizi e serie: è la sorgente unica del Workout Logger. */
    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionWithExercises?>

    @Query("SELECT id, title, started_at FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    fun observeActiveSessionHeader(): Flow<ActiveSessionHeader?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<WorkoutSessionWithExercises?>

    /** Miglior carico per esercizio nelle sessioni concluse, esclusa [excludeSessionId] (record personali). */
    @Query(
        """
        SELECT se.exercise_id AS exercise_id, MAX(es.weight_kg) AS best_weight_kg
        FROM exercise_sets es
        INNER JOIN session_exercises se ON se.id = es.session_exercise_id
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        WHERE ws.status = 'COMPLETED' AND ws.id != :excludeSessionId
          AND es.is_completed = 1 AND es.set_type != 'WARMUP' AND es.weight_kg IS NOT NULL
          AND se.exercise_id IN (:exerciseIds)
        GROUP BY se.exercise_id
        """,
    )
    suspend fun getBestWeights(exerciseIds: List<String>, excludeSessionId: Long): List<ExerciseBestWeight>

    @Query(
        """
        SELECT ws.id, ws.title, ws.started_at, ws.ended_at, ws.calories_burned,
               COUNT(DISTINCT se.id) AS exercise_count,
               COUNT(es.id) AS working_set_count,
               COALESCE(SUM(CASE WHEN e.tracking_type = 'ASSISTED_BODYWEIGHT' THEN 0.0
                                 ELSE es.weight_kg * es.reps END), 0.0) AS total_volume_kg
        FROM workout_sessions ws
        LEFT JOIN session_exercises se ON se.session_id = ws.id
        LEFT JOIN exercises e ON e.id = se.exercise_id
        LEFT JOIN exercise_sets es ON es.session_exercise_id = se.id
             AND es.is_completed = 1 AND es.set_type != 'WARMUP'
        WHERE ws.status = 'COMPLETED'
        GROUP BY ws.id
        ORDER BY ws.started_at DESC
        LIMIT :limit
        """,
    )
    fun observeCompletedSessionSummaries(limit: Int): Flow<List<SessionSummary>>

    /**
     * Serie allenanti completate dell'ultima sessione conclusa con [exerciseId]:
     * colonna "Precedente" del logger e base per il pre-riempimento.
     */
    @Query(
        """
        SELECT es.* FROM exercise_sets es
        WHERE es.session_exercise_id = (
            SELECT se.id FROM session_exercises se
            INNER JOIN workout_sessions ws ON ws.id = se.session_id
            WHERE se.exercise_id = :exerciseId AND ws.status = 'COMPLETED'
            ORDER BY ws.started_at DESC, se.position ASC
            LIMIT 1
        )
        AND es.is_completed = 1 AND es.set_type != 'WARMUP'
        ORDER BY es.set_index
        """,
    )
    fun observePreviousWorkingSets(exerciseId: String): Flow<List<ExerciseSetEntity>>

    // ---------------------------------------------------------------- Letture puntuali

    @Query("SELECT id FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    suspend fun getActiveSessionId(): Long?

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): WorkoutSessionEntity?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSessionWithExercises(sessionId: Long): WorkoutSessionWithExercises?

    @Query(
        """
        SELECT es.* FROM exercise_sets es
        WHERE es.session_exercise_id = (
            SELECT se.id FROM session_exercises se
            INNER JOIN workout_sessions ws ON ws.id = se.session_id
            WHERE se.exercise_id = :exerciseId AND ws.status = 'COMPLETED'
            ORDER BY ws.started_at DESC, se.position ASC
            LIMIT 1
        )
        AND es.is_completed = 1 AND es.set_type != 'WARMUP'
        ORDER BY es.set_index
        """,
    )
    suspend fun getPreviousWorkingSets(exerciseId: String): List<ExerciseSetEntity>

    @Query("SELECT * FROM session_exercises WHERE id = :sessionExerciseId")
    suspend fun getSessionExercise(sessionExerciseId: Long): SessionExerciseEntity?

    @Query("SELECT * FROM session_exercises WHERE session_id = :sessionId ORDER BY position")
    suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity>

    @Query("SELECT * FROM exercise_sets WHERE session_exercise_id = :sessionExerciseId ORDER BY set_index")
    suspend fun getSets(sessionExerciseId: Long): List<ExerciseSetEntity>

    @Query("SELECT * FROM exercise_sets WHERE id = :setId")
    suspend fun getSet(setId: Long): ExerciseSetEntity?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM session_exercises WHERE session_id = :sessionId")
    suspend fun nextExercisePosition(sessionId: Long): Int

    @Query("SELECT * FROM routine_days WHERE id = :routineDayId")
    suspend fun findRoutineDay(routineDayId: Long): RoutineDayEntity?

    @Query("SELECT * FROM routine_exercises WHERE routine_day_id = :routineDayId ORDER BY position")
    suspend fun findRoutineExercises(routineDayId: Long): List<RoutineExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :exerciseId")
    suspend fun findExercise(exerciseId: String): ExerciseEntity?

    /** Stessa regola di `MachineSetupDao.getLastUsedSetup`: il profilo usato più di recente. */
    @Query("SELECT * FROM machine_setups WHERE exercise_id = :exerciseId ORDER BY last_used_at DESC, id DESC LIMIT 1")
    suspend fun findLastMachineSetup(exerciseId: String): MachineSetupEntity?

    // ---------------------------------------------------------------- Scritture semplici

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM workout_sessions WHERE status = 'DISCARDED'")
    suspend fun purgeDiscardedSessions(): Int

    @Insert
    suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity): Long

    @Update
    suspend fun updateSessionExercise(sessionExercise: SessionExerciseEntity)

    @Update
    suspend fun updateSessionExercises(sessionExercises: List<SessionExerciseEntity>)

    @Delete
    suspend fun deleteSessionExercise(sessionExercise: SessionExerciseEntity)

    @Insert
    suspend fun insertSet(set: ExerciseSetEntity): Long

    @Insert
    suspend fun insertSets(sets: List<ExerciseSetEntity>): List<Long>

    /** Aggiornamento valori della riga serie (peso, reps, RPE/RIR, tipo). */
    @Update
    suspend fun updateSet(set: ExerciseSetEntity)

    @Update
    suspend fun updateSets(sets: List<ExerciseSetEntity>)

    @Delete
    suspend fun deleteSet(set: ExerciseSetEntity)

    /*
     * Aggiornamenti mirati per campo: il tastierino scrive a ogni tasto e due campi della stessa
     * serie possono cambiare in rapida successione. Aggiornare l'intera riga con una copia letta
     * prima rischierebbe di sovrascrivere il campo appena modificato con un valore vecchio.
     */

    @Query("UPDATE exercise_sets SET weight_kg = :weightKg WHERE id = :setId")
    suspend fun updateSetWeight(setId: Long, weightKg: Double?)

    @Query("UPDATE exercise_sets SET reps = :reps WHERE id = :setId")
    suspend fun updateSetReps(setId: Long, reps: Int?)

    @Query("UPDATE exercise_sets SET rpe = :rpe WHERE id = :setId")
    suspend fun updateSetRpe(setId: Long, rpe: Double?)

    @Query("UPDATE exercise_sets SET rir = :rir WHERE id = :setId")
    suspend fun updateSetRir(setId: Long, rir: Int?)

    @Query("UPDATE exercise_sets SET duration_seconds = :seconds WHERE id = :setId")
    suspend fun updateSetDuration(setId: Long, seconds: Int?)

    @Query("UPDATE exercise_sets SET distance_meters = :meters WHERE id = :setId")
    suspend fun updateSetDistance(setId: Long, meters: Double?)

    @Query("UPDATE exercise_sets SET set_type = :type WHERE id = :setId")
    suspend fun updateSetType(setId: Long, type: SetType)

    /** Spunta della serie: un'unica UPDATE, nessuna lettura preventiva. */
    @Query("UPDATE exercise_sets SET is_completed = :completed, completed_at = :completedAt WHERE id = :setId")
    suspend fun setSetCompleted(setId: Long, completed: Boolean, completedAt: Instant?)

    @Query("UPDATE session_exercises SET notes = :notes WHERE id = :sessionExerciseId")
    suspend fun updateSessionExerciseNotes(sessionExerciseId: Long, notes: String?)

    @Query("UPDATE session_exercises SET rest_seconds = :restSeconds WHERE id = :sessionExerciseId")
    suspend fun updateRestSeconds(sessionExerciseId: Long, restSeconds: Int)

    @Query("UPDATE session_exercises SET position = position + 1 WHERE session_id = :sessionId AND position > :afterPosition")
    suspend fun shiftExercisesAfter(sessionId: Long, afterPosition: Int)

    @Query(
        """
        UPDATE exercise_sets SET set_index = set_index - 1
        WHERE session_exercise_id = :sessionExerciseId AND set_index > :removedIndex
        """,
    )
    suspend fun compactSetIndexesAfter(sessionExerciseId: Long, removedIndex: Int)

    @Query(
        """
        UPDATE machine_setups SET last_used_at = :usedAt
        WHERE id IN (
            SELECT machine_setup_id FROM session_exercises
            WHERE session_id = :sessionId AND machine_setup_id IS NOT NULL
        )
        """,
    )
    suspend fun touchMachineSetupsOfSession(sessionId: Long, usedAt: Instant)

    @Query(
        """
        DELETE FROM exercise_sets
        WHERE is_completed = 0
          AND session_exercise_id IN (SELECT id FROM session_exercises WHERE session_id = :sessionId)
        """,
    )
    suspend fun deleteIncompleteSets(sessionId: Long)

    @Query(
        """
        DELETE FROM session_exercises
        WHERE session_id = :sessionId
          AND NOT EXISTS (SELECT 1 FROM exercise_sets es WHERE es.session_exercise_id = session_exercises.id)
        """,
    )
    suspend fun deleteEmptySessionExercises(sessionId: Long)

    /**
     * Serie spuntate ma senza alcun dato (né ripetizioni, né durata, né distanza): non descrivono
     * un lavoro svolto e falserebbero conteggi e statistiche.
     */
    @Query(
        """
        DELETE FROM exercise_sets
        WHERE is_completed = 1
          AND NOT (COALESCE(reps, 0) > 0 OR COALESCE(duration_seconds, 0) > 0 OR COALESCE(distance_meters, 0) > 0)
          AND session_exercise_id IN (SELECT id FROM session_exercises WHERE session_id = :sessionId)
        """,
    )
    suspend fun deleteCompletedSetsWithoutData(sessionId: Long)

    /** Serie completate con un dato valido: se sono zero la sessione è nulla (vedi [finishSession]). */
    @Query(
        """
        SELECT COUNT(*) FROM exercise_sets es
        INNER JOIN session_exercises se ON se.id = es.session_exercise_id
        WHERE se.session_id = :sessionId AND es.is_completed = 1
          AND (COALESCE(es.reps, 0) > 0 OR COALESCE(es.duration_seconds, 0) > 0 OR COALESCE(es.distance_meters, 0) > 0)
        """,
    )
    suspend fun countValidCompletedSets(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM exercise_sets WHERE session_exercise_id = :sessionExerciseId")
    suspend fun countSets(sessionExerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM session_exercises WHERE session_id = :sessionId")
    suspend fun countSessionExercises(sessionId: Long): Int

    @Query("SELECT id FROM session_exercises WHERE session_id = :sessionId AND exercise_id = :exerciseId")
    suspend fun getSessionExerciseIds(sessionId: Long, exerciseId: String): List<Long>

    @Query(
        """
        SELECT DISTINCT se.session_id FROM session_exercises se
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        WHERE se.exercise_id = :exerciseId AND ws.status = 'COMPLETED'
        """,
    )
    suspend fun getCompletedSessionIdsWithExercise(exerciseId: String): List<Long>

    @Query(
        """
        DELETE FROM session_exercises
        WHERE exercise_id = :exerciseId
          AND session_id IN (SELECT id FROM workout_sessions WHERE status = 'COMPLETED')
        """,
    )
    suspend fun deleteExerciseFromCompletedSessions(exerciseId: String)

    /** Correzione di una serie già registrata (es. 1000 kg digitati al posto di 100). */
    @Query(
        """
        UPDATE exercise_sets
        SET weight_kg = :weightKg, reps = :reps, duration_seconds = :durationSeconds, distance_meters = :distanceMeters
        WHERE id = :setId
        """,
    )
    suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, durationSeconds: Int?, distanceMeters: Double?)

    /** Allenamento a cui appartiene una serie (per ricalcolarne le calorie dopo una correzione). */
    @Query("SELECT se.session_id FROM exercise_sets es INNER JOIN session_exercises se ON se.id = es.session_exercise_id WHERE es.id = :setId")
    suspend fun getSessionIdOfSet(setId: Long): Long?

    @Query("UPDATE workout_sessions SET status = :status, ended_at = :endedAt WHERE id = :sessionId")
    suspend fun updateStatus(sessionId: Long, status: SessionStatus, endedAt: Instant?)

    // ---------------------------------------------------------------- Transazioni del logger

    /** Sessione libera senza scheda. */
    @Transaction
    suspend fun startEmptySession(title: String, startedAt: Instant, bodyWeightKg: Double? = null): Long {
        getActiveSessionId()?.let { throw ActiveSessionExistsException(it) }
        return insertSession(
            WorkoutSessionEntity(
                title = title,
                status = SessionStatus.IN_PROGRESS,
                startedAt = startedAt,
                bodyWeightKg = bodyWeightKg,
            ),
        )
    }

    /**
     * Avvia la sessione di un giorno di scheda. Per ogni esercizio pianificato:
     * - richiama automaticamente l'ultima regolazione macchinario usata (snapshot + riferimento);
     * - crea le serie target pre-compilate con peso/reps dell'ultima sessione conclusa
     *   (in assenza di storico: reps target, peso vuoto), tutte non spuntate.
     *
     * @throws ActiveSessionExistsException se c'è già una sessione in corso.
     * @return id della nuova sessione.
     */
    @Transaction
    suspend fun startSessionFromRoutineDay(
        routineDayId: Long,
        startedAt: Instant,
        bodyWeightKg: Double? = null,
    ): Long {
        getActiveSessionId()?.let { throw ActiveSessionExistsException(it) }
        val day = checkNotNull(findRoutineDay(routineDayId)) { "Giorno di scheda $routineDayId inesistente" }
        val sessionId = insertSession(
            WorkoutSessionEntity(
                routineId = day.routineId,
                routineDayId = day.id,
                title = day.name,
                status = SessionStatus.IN_PROGRESS,
                startedAt = startedAt,
                bodyWeightKg = bodyWeightKg,
            ),
        )
        findRoutineExercises(routineDayId).forEachIndexed { index, planned ->
            val exercise = checkNotNull(findExercise(planned.exerciseId)) {
                "Esercizio ${planned.exerciseId} inesistente"
            }
            val setup = findLastMachineSetup(planned.exerciseId)
            val sessionExerciseId = insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    exerciseId = planned.exerciseId,
                    routineExerciseId = planned.id,
                    position = index,
                    supersetGroup = planned.supersetGroup,
                    restSeconds = planned.restSeconds ?: exercise.defaultRestSeconds,
                    machineSetupId = setup?.id,
                    machineSetup = setup?.params.orEmpty(),
                ),
            )
            insertSets(
                buildPrefilledSets(
                    sessionExerciseId = sessionExerciseId,
                    count = planned.targetSets.coerceAtLeast(1),
                    previous = getPreviousWorkingSets(planned.exerciseId),
                    fallbackReps = planned.targetRepsMax ?: planned.targetRepsMin,
                ),
            )
        }
        return sessionId
    }

    /** Aggiunge un esercizio in coda alla sessione (con regolazione e pre-compilazione). */
    @Transaction
    suspend fun addExerciseToSession(sessionId: Long, exerciseId: String, setCount: Int): Long {
        val exercise = checkNotNull(findExercise(exerciseId)) { "Esercizio $exerciseId inesistente" }
        val setup = findLastMachineSetup(exerciseId)
        val sessionExerciseId = insertSessionExercise(
            SessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = nextExercisePosition(sessionId),
                restSeconds = exercise.defaultRestSeconds,
                machineSetupId = setup?.id,
                machineSetup = setup?.params.orEmpty(),
            ),
        )
        insertSets(
            buildPrefilledSets(
                sessionExerciseId = sessionExerciseId,
                count = setCount.coerceAtLeast(1),
                previous = getPreviousWorkingSets(exerciseId),
                fallbackReps = null,
            ),
        )
        return sessionExerciseId
    }

    /** "+ Serie": copia i valori dell'ultima serie così basta spuntare. */
    @Transaction
    suspend fun addSet(sessionExerciseId: Long): Long {
        val last = getSets(sessionExerciseId).lastOrNull()
        return insertSet(
            ExerciseSetEntity(
                sessionExerciseId = sessionExerciseId,
                setIndex = (last?.setIndex ?: -1) + 1,
                setType = last?.setType?.takeIf { it != SetType.WARMUP } ?: SetType.WORKING,
                weightKg = last?.weightKg,
                reps = last?.reps,
                durationSeconds = last?.durationSeconds,
                distanceMeters = last?.distanceMeters,
            ),
        )
    }

    /** Elimina una serie e ricompatta gli indici delle successive. */
    @Transaction
    suspend fun removeSet(setId: Long) {
        val set = getSet(setId) ?: return
        deleteSet(set)
        compactSetIndexesAfter(set.sessionExerciseId, set.setIndex)
    }

    /** Elimina un esercizio dalla sessione (con le sue serie) e ricompatta le posizioni. */
    @Transaction
    suspend fun removeSessionExercise(sessionExerciseId: Long) {
        val target = getSessionExercise(sessionExerciseId) ?: return
        deleteSessionExercise(target)
        updateSessionExercises(
            getSessionExercises(target.sessionId).mapIndexed { index, item -> item.copy(position = index) },
        )
    }

    /** Riordino drag-and-drop degli esercizi durante la sessione. */
    @Transaction
    suspend fun moveSessionExercise(sessionId: Long, fromIndex: Int, toIndex: Int) {
        val items = getSessionExercises(sessionId).toMutableList()
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        items.add(toIndex, items.removeAt(fromIndex))
        updateSessionExercises(items.mapIndexed { index, item -> item.copy(position = index) })
    }

    /**
     * Sostituzione al volo (macchina occupata, dolore...).
     *
     * - Nessuna serie completata: l'esercizio viene sostituito sul posto.
     * - Alcune serie completate: restano registrate sull'esercizio originale (lo storico resta
     *   corretto) e le serie mancanti passano a un nuovo esercizio inserito subito dopo.
     *
     * Le serie spostate vengono ripulite e ri-precompilate con lo storico del nuovo esercizio;
     * la regolazione macchinario viene richiamata per il nuovo esercizio.
     *
     * @return id dell'esercizio di sessione che contiene il nuovo esercizio.
     */
    @Transaction
    suspend fun replaceExercise(sessionExerciseId: Long, newExerciseId: String): Long {
        val current = checkNotNull(getSessionExercise(sessionExerciseId)) {
            "Esercizio di sessione $sessionExerciseId inesistente"
        }
        if (current.exerciseId == newExerciseId) return current.id
        checkNotNull(findExercise(newExerciseId)) { "Esercizio $newExerciseId inesistente" }
        val setup = findLastMachineSetup(newExerciseId)
        val (completed, pending) = getSets(sessionExerciseId).partition { it.isCompleted }

        val targetId = if (completed.isEmpty()) {
            updateSessionExercise(
                current.copy(
                    exerciseId = newExerciseId,
                    replacedExerciseId = current.replacedExerciseId ?: current.exerciseId,
                    machineSetupId = setup?.id,
                    machineSetup = setup?.params.orEmpty(),
                ),
            )
            current.id
        } else {
            // Le serie completate restano sull'originale, con indici ricompattati (0, 1, 2...).
            updateSets(completed.mapIndexed { index, set -> set.copy(setIndex = index) })
            shiftExercisesAfter(current.sessionId, current.position)
            insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = current.sessionId,
                    exerciseId = newExerciseId,
                    routineExerciseId = current.routineExerciseId,
                    position = current.position + 1,
                    supersetGroup = current.supersetGroup,
                    restSeconds = current.restSeconds,
                    machineSetupId = setup?.id,
                    machineSetup = setup?.params.orEmpty(),
                    replacedExerciseId = current.exerciseId,
                ),
            )
        }

        val previous = getPreviousWorkingSets(newExerciseId)
        if (pending.isEmpty()) {
            insertSets(buildPrefilledSets(targetId, count = 1, previous = previous, fallbackReps = null))
        } else {
            updateSets(
                pending.mapIndexed { index, set ->
                    val reference = previous.getOrNull(index) ?: previous.lastOrNull()
                    set.copy(
                        sessionExerciseId = targetId,
                        setIndex = index,
                        weightKg = reference?.weightKg,
                        reps = reference?.reps ?: set.reps,
                        rpe = null,
                        rir = null,
                        durationSeconds = reference?.durationSeconds,
                        distanceMeters = reference?.distanceMeters,
                    )
                },
            )
        }
        return targetId
    }

    /**
     * Conclude la sessione.
     *
     * Una sessione senza nemmeno una serie completata con dati validi è nulla (avviata per errore):
     * non viene salvata ma eliminata, così non entra nello storico né nelle statistiche.
     *
     * @param discardIncompleteSets se true elimina le serie non spuntate, quelle spuntate senza
     *   dati e gli esercizi rimasti vuoti.
     * @return true se la sessione è stata salvata nello storico, false se era nulla ed è stata scartata.
     */
    @Transaction
    suspend fun finishSession(sessionId: Long, endedAt: Instant, discardIncompleteSets: Boolean): Boolean {
        val session = checkNotNull(getSession(sessionId)) { "Sessione $sessionId inesistente" }
        check(session.status == SessionStatus.IN_PROGRESS) { "La sessione $sessionId non è in corso" }
        if (discardIncompleteSets) {
            deleteIncompleteSets(sessionId)
            deleteCompletedSetsWithoutData(sessionId)
            deleteEmptySessionExercises(sessionId)
        }
        if (countValidCompletedSets(sessionId) == 0) {
            deleteSession(sessionId)
            return false
        }
        touchMachineSetupsOfSession(sessionId, endedAt)
        updateStatus(sessionId, SessionStatus.COMPLETED, endedAt)
        return true
    }

    // ---------------------------------------------------------------- Correzioni dello storico

    /** Elimina l'allenamento se non contiene più esercizi. @return true se è stato eliminato. */
    @Transaction
    suspend fun deleteSessionIfEmpty(sessionId: Long): Boolean {
        if (countSessionExercises(sessionId) > 0) return false
        deleteSession(sessionId)
        return true
    }

    /**
     * Elimina una serie da un allenamento concluso (dato anomalo). Se era l'ultima dell'esercizio
     * sparisce anche l'esercizio, e se l'allenamento resta vuoto viene eliminato: nello storico
     * non restano gusci senza dati. Record e grafici leggono dal database e si ricalcolano da soli.
     */
    @Transaction
    suspend fun removeHistorySet(setId: Long): HistoryRemoval {
        val set = getSet(setId) ?: return HistoryRemoval.NOTHING
        val owner = getSessionExercise(set.sessionExerciseId) ?: return HistoryRemoval.NOTHING
        deleteSet(set)
        compactSetIndexesAfter(set.sessionExerciseId, set.setIndex)
        val isHistory = getSession(owner.sessionId)?.status == SessionStatus.COMPLETED
        if (!isHistory || countSets(owner.id) > 0) return HistoryRemoval.SET
        removeSessionExercise(owner.id)
        return if (deleteSessionIfEmpty(owner.sessionId)) HistoryRemoval.SESSION else HistoryRemoval.EXERCISE
    }

    /** Elimina da un allenamento tutte le serie di [exerciseId] (es. il record di volume anomalo). */
    @Transaction
    suspend fun removeExerciseFromSession(sessionId: Long, exerciseId: String): HistoryRemoval {
        val targets = getSessionExerciseIds(sessionId, exerciseId)
        if (targets.isEmpty()) return HistoryRemoval.NOTHING
        targets.forEach { removeSessionExercise(it) }
        return if (deleteSessionIfEmpty(sessionId)) HistoryRemoval.SESSION else HistoryRemoval.EXERCISE
    }

    /**
     * Azzera lo storico di un solo esercizio: lo toglie da tutti gli allenamenti conclusi (con le
     * sue serie) ed elimina gli allenamenti rimasti vuoti. Schede, regolazioni, esercizio e tutti
     * gli altri dati restano; l'allenamento in corso non viene toccato.
     *
     * @return numero di allenamenti da cui l'esercizio è stato rimosso.
     */
    @Transaction
    suspend fun resetExerciseHistory(exerciseId: String): Int {
        val sessions = getCompletedSessionIdsWithExercise(exerciseId)
        if (sessions.isEmpty()) return 0
        deleteExerciseFromCompletedSessions(exerciseId)
        sessions.forEach { sessionId ->
            if (!deleteSessionIfEmpty(sessionId)) {
                updateSessionExercises(
                    getSessionExercises(sessionId).mapIndexed { index, item -> item.copy(position = index) },
                )
            }
        }
        return sessions.size
    }

    /** Annulla la sessione (soft delete, permette un "Annulla" via snackbar). */
    @Transaction
    suspend fun discardSession(sessionId: Long, endedAt: Instant) {
        val session = checkNotNull(getSession(sessionId)) { "Sessione $sessionId inesistente" }
        check(session.status == SessionStatus.IN_PROGRESS) { "La sessione $sessionId non è in corso" }
        updateStatus(sessionId, SessionStatus.DISCARDED, endedAt)
    }

    /** Ripristina una sessione annullata, se nel frattempo non ne è stata avviata un'altra. */
    @Transaction
    suspend fun restoreDiscardedSession(sessionId: Long) {
        getActiveSessionId()?.let { throw ActiveSessionExistsException(it) }
        val session = checkNotNull(getSession(sessionId)) { "Sessione $sessionId inesistente" }
        check(session.status == SessionStatus.DISCARDED) { "La sessione $sessionId non è annullata" }
        updateStatus(sessionId, SessionStatus.IN_PROGRESS, endedAt = null)
    }
}

/**
 * Serie pre-compilate: la serie i-esima copia la i-esima serie della volta precedente
 * (o l'ultima disponibile), così l'utente deve solo spuntare se ripete lo stesso lavoro.
 */
internal fun buildPrefilledSets(
    sessionExerciseId: Long,
    count: Int,
    previous: List<ExerciseSetEntity>,
    fallbackReps: Int?,
): List<ExerciseSetEntity> = List(count) { index ->
    val reference = previous.getOrNull(index) ?: previous.lastOrNull()
    ExerciseSetEntity(
        sessionExerciseId = sessionExerciseId,
        setIndex = index,
        setType = SetType.WORKING,
        weightKg = reference?.weightKg,
        reps = reference?.reps ?: fallbackReps,
        durationSeconds = reference?.durationSeconds,
        distanceMeters = reference?.distanceMeters,
    )
}
