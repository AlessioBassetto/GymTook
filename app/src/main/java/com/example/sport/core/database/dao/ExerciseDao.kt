package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseMuscleEntity
import com.example.sport.core.database.entity.ExerciseUserVideoEntity
import com.example.sport.core.database.query.ExerciseFilter
import com.example.sport.core.database.query.ExerciseQueryBuilder
import com.example.sport.core.database.relation.ExerciseDetails
import com.example.sport.core.database.relation.ExerciseWithMachineSetups
import com.example.sport.core.database.relation.ExerciseWithMuscles
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Esito della rimozione di un esercizio custom. */
enum class CustomExerciseRemoval {
    /** Mai usato: eliminato definitivamente. */
    DELETED,

    /** Presente in storico o schede: nascosto dalla libreria, storico intatto. */
    HIDDEN,
}

@Dao
interface ExerciseDao {

    /** Libreria esercizi reattiva: si aggiorna da sola a ogni modifica di esercizi o muscoli. */
    fun observeExercises(filter: ExerciseFilter): Flow<List<ExerciseEntity>> =
        observeExercisesRaw(ExerciseQueryBuilder.build(filter))

    suspend fun searchExercises(filter: ExerciseFilter): List<ExerciseEntity> =
        searchExercisesRaw(ExerciseQueryBuilder.build(filter))

    @RawQuery(observedEntities = [ExerciseEntity::class, ExerciseMuscleEntity::class])
    fun observeExercisesRaw(query: SupportSQLiteQuery): Flow<List<ExerciseEntity>>

    @RawQuery
    suspend fun searchExercisesRaw(query: SupportSQLiteQuery): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeWithMuscles(id: String): Flow<ExerciseWithMuscles?>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getWithMuscles(id: String): ExerciseWithMuscles?

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeWithMachineSetups(id: String): Flow<ExerciseWithMachineSetups?>

    /** Scheda dettaglio: anagrafica + muscoli + media in un'unica lettura transazionale. */
    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeDetails(id: String): Flow<ExerciseDetails?>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getDetails(id: String): ExerciseDetails?

    @Query("SELECT * FROM exercise_muscles WHERE exercise_id = :exerciseId")
    suspend fun getMuscles(exerciseId: String): List<ExerciseMuscleEntity>

    /**
     * Candidati per la sostituzione al volo: stesso macro-gruppo del muscolo primario, oppure
     * esercizi che hanno quel muscolo tra i primari (lo stacco da terra per lo stacco rumeno).
     * L'ordine lo decide `AlternativeRanker`.
     */
    @Query(
        """
        SELECT e.* FROM exercises e
        WHERE e.id != :exerciseId
          AND e.is_hidden = 0 AND e.is_retired = 0
          AND (
            e.primary_muscle_group = (SELECT primary_muscle_group FROM exercises WHERE id = :exerciseId)
            OR e.id IN (
              SELECT m.exercise_id FROM exercise_muscles m
              WHERE m.role = 'PRIMARY'
                AND m.muscle = (SELECT primary_muscle FROM exercises WHERE id = :exerciseId)
            )
          )
        """,
    )
    suspend fun getAlternativeCandidates(exerciseId: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercise_muscles WHERE exercise_id IN (:exerciseIds)")
    suspend fun getMusclesFor(exerciseIds: List<String>): List<ExerciseMuscleEntity>

    /** Esercizi usati di recente (accesso rapido nel picker "Aggiungi esercizio"). */
    @Query(
        """
        SELECT e.* FROM exercises e
        INNER JOIN session_exercises se ON se.exercise_id = e.id
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        WHERE e.is_hidden = 0 AND ws.status != 'DISCARDED'
        GROUP BY e.id
        ORDER BY MAX(ws.started_at) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentlyUsed(limit: Int): Flow<List<ExerciseEntity>>

    @Query("UPDATE exercises SET is_favorite = :favorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Instant)

    @Query("UPDATE exercises SET is_hidden = :hidden, updated_at = :updatedAt WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, updatedAt: Instant)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(exercise: ExerciseEntity)

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMuscles(muscles: List<ExerciseMuscleEntity>)

    @Query("DELETE FROM exercise_muscles WHERE exercise_id = :exerciseId")
    suspend fun deleteMuscles(exerciseId: String)

    @Transaction
    suspend fun insertCustomExercise(exercise: ExerciseEntity, muscles: List<ExerciseMuscleEntity>) {
        require(exercise.isCustom) { "Solo esercizi custom possono essere creati dall'utente" }
        require(muscles.all { it.exerciseId == exercise.id }) { "Muscoli non associati a ${exercise.id}" }
        insertExercise(exercise)
        insertMuscles(muscles)
    }

    @Transaction
    suspend fun updateCustomExercise(exercise: ExerciseEntity, muscles: List<ExerciseMuscleEntity>) {
        require(exercise.isCustom) { "Gli esercizi di catalogo si aggiornano solo tramite il catalogo" }
        require(muscles.all { it.exerciseId == exercise.id }) { "Muscoli non associati a ${exercise.id}" }
        updateExercise(exercise)
        deleteMuscles(exercise.id)
        insertMuscles(muscles)
    }

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM session_exercises WHERE exercise_id = :id)
             + (SELECT COUNT(*) FROM routine_exercises WHERE exercise_id = :id)
        """,
    )
    suspend fun countUsages(id: String): Int

    @Query("DELETE FROM exercises WHERE id = :id AND is_custom = 1")
    suspend fun deleteCustomExercise(id: String)

    // ------------------------------------------------------------------ Video personali

    @Query("SELECT * FROM exercise_user_videos WHERE exercise_id = :exerciseId")
    suspend fun getUserVideo(exerciseId: String): ExerciseUserVideoEntity?

    @Upsert
    suspend fun upsertUserVideo(video: ExerciseUserVideoEntity)

    @Query("DELETE FROM exercise_user_videos WHERE exercise_id = :exerciseId")
    suspend fun deleteUserVideo(exerciseId: String)

    /** File ancora referenziati: tutto il resto nella cartella dei video utente è orfano. */
    @Query("SELECT file_name FROM exercise_user_videos")
    suspend fun getUserVideoFileNames(): List<String>

    /** Elimina un esercizio custom mai usato; se usato lo nasconde preservando lo storico. */
    @Transaction
    suspend fun deleteOrHideCustomExercise(id: String, now: Instant): CustomExerciseRemoval {
        val exercise = checkNotNull(getById(id)) { "Esercizio $id inesistente" }
        require(exercise.isCustom) { "Gli esercizi di catalogo possono solo essere nascosti" }
        return if (countUsages(id) == 0) {
            deleteCustomExercise(id)
            CustomExerciseRemoval.DELETED
        } else {
            setHidden(id, hidden = true, updatedAt = now)
            CustomExerciseRemoval.HIDDEN
        }
    }
}
