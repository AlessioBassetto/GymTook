package com.example.sport.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.example.sport.core.database.entity.ExerciseMediaEntity
import kotlinx.coroutines.flow.Flow

/** Miniatura di un esercizio: primo fotogramma. */
data class ExerciseThumbnailPath(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val path: String,
)

/** Letture dei contenuti multimediali per UI, download offline e pulizia della cache. */
@Dao
interface MediaDao {

    /** Miniature di tutti gli esercizi in una sola query (liste da centinaia di righe). */
    @Query("SELECT exercise_id, path FROM exercise_media WHERE kind = 'IMAGE_FRAME' AND position = 0")
    fun observeThumbnailPaths(): Flow<List<ExerciseThumbnailPath>>

    @Query("SELECT * FROM exercise_media WHERE exercise_id = :exerciseId ORDER BY kind, position")
    fun observeMediaForExercise(exerciseId: String): Flow<List<ExerciseMediaEntity>>

    @Query("SELECT * FROM exercise_media WHERE exercise_id = :exerciseId ORDER BY kind, position")
    suspend fun getMediaForExercise(exerciseId: String): List<ExerciseMediaEntity>

    /** Tutti i media degli esercizi di una scheda: input del download "Disponibile offline". */
    @Query(
        """
        SELECT DISTINCT m.path FROM exercise_media m
        INNER JOIN routine_exercises re ON re.exercise_id = m.exercise_id
        INNER JOIN routine_days rd ON rd.id = re.routine_day_id
        WHERE rd.routine_id = :routineId
        ORDER BY m.path
        """,
    )
    suspend fun getMediaPathsForRoutine(routineId: Long): List<String>

    /** Media che devono restare offline: quelli delle schede attive o non archiviate. */
    @Query(
        """
        SELECT DISTINCT m.path FROM exercise_media m
        INNER JOIN routine_exercises re ON re.exercise_id = m.exercise_id
        INNER JOIN routine_days rd ON rd.id = re.routine_day_id
        INNER JOIN routines r ON r.id = rd.routine_id
        WHERE r.is_archived = 0
        """,
    )
    suspend fun getMediaPathsOfLiveRoutines(): List<String>
}
