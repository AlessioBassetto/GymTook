package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.sport.core.database.entity.MachineSetupEntity
import com.example.sport.core.database.entity.SessionExerciseEntity
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.normalized
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Regolazioni macchinario ("Altezza sedile: 4, Schienale: foro 2...").
 *
 * Regola di memoria: per ogni esercizio l'app propone il profilo con `last_used_at` più recente.
 * `last_used_at` viene aggiornato quando l'utente salva/seleziona una regolazione nel logger e
 * quando conclude una sessione che la usa (vedi `WorkoutSessionDao.finishSession`).
 */
@Dao
interface MachineSetupDao {

    @Query("SELECT * FROM machine_setups WHERE exercise_id = :exerciseId ORDER BY last_used_at DESC, id DESC")
    fun observeSetupsForExercise(exerciseId: String): Flow<List<MachineSetupEntity>>

    @Query("SELECT * FROM machine_setups WHERE exercise_id = :exerciseId ORDER BY last_used_at DESC, id DESC LIMIT 1")
    fun observeLastUsedSetup(exerciseId: String): Flow<MachineSetupEntity?>

    @Query("SELECT * FROM machine_setups WHERE exercise_id = :exerciseId ORDER BY last_used_at DESC, id DESC LIMIT 1")
    suspend fun getLastUsedSetup(exerciseId: String): MachineSetupEntity?

    @Query("SELECT * FROM machine_setups WHERE id = :id")
    suspend fun getById(id: Long): MachineSetupEntity?

    @Insert
    suspend fun insert(setup: MachineSetupEntity): Long

    @Update
    suspend fun update(setup: MachineSetupEntity)

    @Delete
    suspend fun delete(setup: MachineSetupEntity)

    @Query("UPDATE machine_setups SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String)

    @Query("UPDATE machine_setups SET last_used_at = :usedAt WHERE id = :setupId")
    suspend fun touchLastUsed(setupId: Long, usedAt: Instant)

    @Query("SELECT * FROM session_exercises WHERE id = :sessionExerciseId")
    suspend fun getSessionExercise(sessionExerciseId: Long): SessionExerciseEntity?

    @Query(
        """
        UPDATE session_exercises SET machine_setup_id = :setupId, machine_setup = :params
        WHERE id = :sessionExerciseId
        """,
    )
    suspend fun attachToSessionExercise(
        sessionExerciseId: Long,
        setupId: Long?,
        params: List<MachineSetupParam>,
    )

    /**
     * Salvataggio dal logger (azione principale, nessuna scelta richiesta all'utente):
     * - se la sessione sta già usando un profilo, lo aggiorna con i nuovi valori;
     * - altrimenti crea un nuovo profilo chiamato [defaultLabel].
     * In entrambi i casi il profilo diventa "ultimo usato" e i valori vengono copiati come
     * snapshot nell'esercizio di sessione.
     *
     * @return id del profilo salvato.
     */
    @Transaction
    suspend fun saveForSessionExercise(
        sessionExerciseId: Long,
        params: List<MachineSetupParam>,
        defaultLabel: String,
        usedAt: Instant,
    ): Long {
        val sessionExercise = checkNotNull(getSessionExercise(sessionExerciseId)) {
            "Esercizio di sessione $sessionExerciseId inesistente"
        }
        val cleanParams = params.normalized()
        val current = sessionExercise.machineSetupId?.let { getById(it) }
        val setupId = if (current != null) {
            update(current.copy(params = cleanParams, lastUsedAt = usedAt))
            current.id
        } else {
            insert(
                MachineSetupEntity(
                    exerciseId = sessionExercise.exerciseId,
                    label = defaultLabel,
                    params = cleanParams,
                    createdAt = usedAt,
                    lastUsedAt = usedAt,
                ),
            )
        }
        attachToSessionExercise(sessionExerciseId, setupId, cleanParams)
        return setupId
    }

    /** "Salva come nuovo profilo" (es. palestra diversa): il profilo corrente resta invariato. */
    @Transaction
    suspend fun saveAsNewForSessionExercise(
        sessionExerciseId: Long,
        label: String,
        params: List<MachineSetupParam>,
        usedAt: Instant,
    ): Long {
        val sessionExercise = checkNotNull(getSessionExercise(sessionExerciseId)) {
            "Esercizio di sessione $sessionExerciseId inesistente"
        }
        val cleanParams = params.normalized()
        val setupId = insert(
            MachineSetupEntity(
                exerciseId = sessionExercise.exerciseId,
                label = label.trim(),
                params = cleanParams,
                createdAt = usedAt,
                lastUsedAt = usedAt,
            ),
        )
        attachToSessionExercise(sessionExerciseId, setupId, cleanParams)
        return setupId
    }

    /** Cambio profilo dal menu (es. "Palestra Centro" → "Palestra Ufficio"). */
    @Transaction
    suspend fun selectForSessionExercise(sessionExerciseId: Long, setupId: Long, usedAt: Instant) {
        val sessionExercise = checkNotNull(getSessionExercise(sessionExerciseId)) {
            "Esercizio di sessione $sessionExerciseId inesistente"
        }
        val setup = checkNotNull(getById(setupId)) { "Regolazione $setupId inesistente" }
        require(setup.exerciseId == sessionExercise.exerciseId) {
            "La regolazione $setupId appartiene a ${setup.exerciseId}, non a ${sessionExercise.exerciseId}"
        }
        touchLastUsed(setupId, usedAt)
        attachToSessionExercise(sessionExerciseId, setupId, setup.params)
    }

    /** Rimuove la regolazione da questa sessione senza cancellare il profilo salvato. */
    suspend fun detachFromSessionExercise(sessionExerciseId: Long) {
        attachToSessionExercise(sessionExerciseId, setupId = null, params = emptyList())
    }
}
