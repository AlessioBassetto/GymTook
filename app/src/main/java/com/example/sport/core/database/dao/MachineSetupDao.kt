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
 * `last_used_at` viene aggiornato quando l'utente salva/seleziona una regolazione nel logger,
 * quando la salva "a freddo" dalla scheda esercizio ([saveFromLibrary]) e quando conclude una
 * sessione che la usa (vedi `WorkoutSessionDao.finishSession`).
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

    @Query("UPDATE machine_setups SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    /** Elimina il profilo. Lo storico conserva lo snapshot dei valori usati in ogni sessione. */
    @Query("DELETE FROM machine_setups WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Allenamento in corso: gli esercizi che usano il profilo mostrano subito i valori nuovi. */
    @Query(
        """
        UPDATE session_exercises SET machine_setup = :params
        WHERE machine_setup_id = :setupId
          AND session_id IN (SELECT id FROM workout_sessions WHERE status = 'IN_PROGRESS')
        """,
    )
    suspend fun refreshActiveSnapshots(setupId: Long, params: List<MachineSetupParam>)

    /** Allenamento in corso: un profilo appena creato viene agganciato agli esercizi che non ne hanno. */
    @Query(
        """
        UPDATE session_exercises SET machine_setup_id = :setupId, machine_setup = :params
        WHERE exercise_id = :exerciseId AND machine_setup_id IS NULL
          AND session_id IN (SELECT id FROM workout_sessions WHERE status = 'IN_PROGRESS')
        """,
    )
    suspend fun attachToActiveSession(exerciseId: String, setupId: Long, params: List<MachineSetupParam>)

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

    /** Come [saveForSessionExercise], aggiornando anche le note del profilo (vuote = nessuna nota). */
    @Transaction
    suspend fun saveWithNotesForSessionExercise(
        sessionExerciseId: Long,
        params: List<MachineSetupParam>,
        notes: String?,
        defaultLabel: String,
        usedAt: Instant,
    ): Long {
        val setupId = saveForSessionExercise(sessionExerciseId, params, defaultLabel, usedAt)
        updateNotes(setupId, notes.cleanNotes())
        return setupId
    }

    /**
     * Salvataggio "a freddo" dalla scheda esercizio, senza un allenamento avviato: crea il profilo
     * ([setupId] null) o lo aggiorna. Il profilo salvato diventa il più recente, quindi è quello
     * che il logger richiama da solo al prossimo allenamento; se un allenamento è già in corso i
     * valori nuovi compaiono subito anche lì.
     *
     * @return id del profilo salvato.
     */
    @Transaction
    suspend fun saveFromLibrary(
        exerciseId: String,
        setupId: Long?,
        label: String,
        params: List<MachineSetupParam>,
        notes: String?,
        savedAt: Instant,
    ): Long {
        val cleanParams = params.normalized()
        val cleanNotes = notes.cleanNotes()
        val cleanLabel = label.trim()
        require(cleanLabel.isNotEmpty()) { "Il profilo deve avere un nome" }
        require(cleanParams.isNotEmpty() || cleanNotes != null) { "Regolazione senza voci né note" }
        val existing = setupId?.let { getById(it) }
        if (existing != null) {
            require(existing.exerciseId == exerciseId) {
                "La regolazione ${existing.id} appartiene a ${existing.exerciseId}, non a $exerciseId"
            }
            update(existing.copy(label = cleanLabel, params = cleanParams, notes = cleanNotes, lastUsedAt = savedAt))
            refreshActiveSnapshots(existing.id, cleanParams)
            return existing.id
        }
        val newId = insert(
            MachineSetupEntity(
                exerciseId = exerciseId,
                label = cleanLabel,
                params = cleanParams,
                notes = cleanNotes,
                createdAt = savedAt,
                lastUsedAt = savedAt,
            ),
        )
        attachToActiveSession(exerciseId, newId, cleanParams)
        return newId
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

/** Note del profilo ripulite: spazi rimossi, testo vuoto = nessuna nota. */
private fun String?.cleanNotes(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
