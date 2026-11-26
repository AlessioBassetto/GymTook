package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.relation.CompletedSetRow
import com.example.sport.core.database.relation.ExerciseProgressPoint
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Query aggregate per i grafici di progressione. */
@Dao
interface ProgressDao {

    /**
     * Serie storica per esercizio (una riga per sessione conclusa da [from] in poi):
     * carico massimo, volume totale, ripetizioni e miglior 1RM stimato (Epley).
     * Le serie senza carico (corpo libero) contano 0 kg: restano utili per le ripetizioni.
     */
    @Query(
        """
        SELECT ws.id AS session_id,
               ws.started_at AS performed_at,
               MAX(COALESCE(es.weight_kg, 0)) AS max_weight_kg,
               SUM(COALESCE(es.weight_kg, 0) * es.reps) AS total_volume_kg,
               SUM(es.reps) AS total_reps,
               COUNT(es.id) AS set_count,
               MAX(CASE
                   WHEN es.weight_kg IS NULL OR es.weight_kg <= 0 THEN NULL
                   WHEN es.reps = 1 THEN es.weight_kg
                   WHEN es.reps BETWEEN 2 AND 12 THEN es.weight_kg * (1.0 + es.reps / 30.0)
                   ELSE NULL
               END) AS best_estimated_one_rep_max_kg
        FROM exercise_sets es
        INNER JOIN session_exercises se ON se.id = es.session_exercise_id
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        WHERE se.exercise_id = :exerciseId
          AND ws.status = 'COMPLETED'
          AND ws.started_at >= :from
          AND es.is_completed = 1
          AND es.set_type != 'WARMUP'
          AND es.reps IS NOT NULL
          AND es.reps > 0
        GROUP BY ws.id
        ORDER BY ws.started_at ASC
        """,
    )
    fun observeExerciseProgress(exerciseId: String, from: Instant): Flow<List<ExerciseProgressPoint>>

    /**
     * Tutte le serie allenanti completate con carico e ripetizioni, in ordine cronologico:
     * record personali e riepiloghi per esercizio si calcolano in Kotlin (logica testabile).
     * Anche dopo anni di allenamenti sono poche migliaia di righe.
     *
     * Solo esercizi "peso × ripetizioni": nella zavorra il peso del corpo manca e nell'assistito
     * il carico è un aiuto, quindi record di carico e 1RM stimato sarebbero fuorvianti.
     */
    @Query(
        """
        SELECT se.exercise_id AS exercise_id,
               e.name AS exercise_name,
               ws.id AS session_id,
               ws.started_at AS performed_at,
               es.weight_kg AS weight_kg,
               es.reps AS reps,
               es.id AS set_id
        FROM exercise_sets es
        INNER JOIN session_exercises se ON se.id = es.session_exercise_id
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        INNER JOIN exercises e ON e.id = se.exercise_id
        WHERE ws.status = 'COMPLETED'
          AND e.tracking_type = 'WEIGHT_REPS'
          AND es.is_completed = 1
          AND es.set_type != 'WARMUP'
          AND es.weight_kg IS NOT NULL AND es.weight_kg > 0
          AND es.reps IS NOT NULL AND es.reps > 0
        ORDER BY ws.started_at ASC, se.position ASC, es.set_index ASC
        """,
    )
    fun observeCompletedWeightedSets(): Flow<List<CompletedSetRow>>

    /** L'esercizio compare in almeno una sessione conclusa (pulsante "Progressi" nella scheda). */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM session_exercises se
            INNER JOIN workout_sessions ws ON ws.id = se.session_id
            WHERE se.exercise_id = :exerciseId AND ws.status = 'COMPLETED'
        )
        """,
    )
    fun observeHasHistory(exerciseId: String): Flow<Boolean>

    /** Esercizi con almeno una sessione conclusa: popolano il selettore dei grafici. */
    @Query(
        """
        SELECT e.* FROM exercises e
        WHERE EXISTS (
            SELECT 1 FROM session_exercises se
            INNER JOIN workout_sessions ws ON ws.id = se.session_id
            WHERE se.exercise_id = e.id AND ws.status = 'COMPLETED'
        )
        ORDER BY e.name COLLATE NOCASE ASC
        """,
    )
    fun observeExercisesWithHistory(): Flow<List<ExerciseEntity>>
}
