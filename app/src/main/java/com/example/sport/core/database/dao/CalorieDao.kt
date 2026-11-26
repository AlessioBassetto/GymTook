package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.example.sport.core.database.relation.CalorieSetRow
import com.example.sport.core.database.relation.ExerciseCalorieSetRow
import com.example.sport.core.database.relation.SessionCalories
import com.example.sport.core.database.relation.SessionTimes
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Letture e scritture della stima calorica. Le calorie sono un dato derivato salvato nella riga
 * dell'allenamento: queste query alimentano il ricalcolo (fine allenamento, correzioni, pesate,
 * profilo corporeo) e la scrittura del risultato.
 */
@Dao
interface CalorieDao {

    @Query("SELECT id, started_at, ended_at FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY started_at")
    suspend fun getCompletedSessionTimes(): List<SessionTimes>

    @Query("SELECT id, started_at, ended_at FROM workout_sessions WHERE status = 'COMPLETED' AND id IN (:sessionIds)")
    suspend fun getCompletedSessionTimes(sessionIds: List<Long>): List<SessionTimes>

    /** Allenamenti conclusi senza stima: quelli salvati prima della v4 o senza pesate all'epoca. */
    @Query("SELECT id FROM workout_sessions WHERE status = 'COMPLETED' AND calories_burned IS NULL")
    suspend fun getCompletedSessionIdsWithoutCalories(): List<Long>

    /**
     * Serie completate con dati validi degli allenamenti conclusi, in ordine di esecuzione
     * (esercizio, poi serie). Le serie di riscaldamento contano: anche loro consumano energia.
     */
    @Query(
        """
        SELECT se.session_id AS session_id, se.position AS exercise_position, se.rest_seconds AS rest_seconds,
               e.id AS exercise_id, e.name AS name, e.name_en AS name_en, e.equipment AS equipment,
               e.primary_muscle AS primary_muscle, e.tracking_type AS tracking_type, e.category AS category,
               e.mechanic AS mechanic, e.force AS force, e.is_unilateral AS is_unilateral,
               es.set_index AS set_index, es.set_type AS set_type, es.weight_kg AS weight_kg, es.reps AS reps,
               es.rpe AS rpe, es.rir AS rir, es.duration_seconds AS duration_seconds,
               es.distance_meters AS distance_meters, es.incline_percent AS incline_percent,
               es.speed_kmh AS speed_kmh
        FROM exercise_sets es
        INNER JOIN session_exercises se ON se.id = es.session_exercise_id
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        INNER JOIN exercises e ON e.id = se.exercise_id
        WHERE ws.status = 'COMPLETED' AND ws.id IN (:sessionIds) AND es.is_completed = 1
          AND (COALESCE(es.reps, 0) > 0 OR COALESCE(es.duration_seconds, 0) > 0 OR COALESCE(es.distance_meters, 0) > 0)
        ORDER BY se.session_id, se.position, es.set_index
        """,
    )
    suspend fun getCalorieSetRows(sessionIds: List<Long>): List<CalorieSetRow>

    /**
     * Serie completate di un solo esercizio negli allenamenti conclusi dal [from] in poi, con l'inizio
     * dell'allenamento: le calorie del tapis roulant si stimano su queste, sessione per sessione.
     * Il Flow si aggiorna quando cambiano serie, allenamenti o l'anagrafica dell'esercizio.
     */
    @Query(
        """
        SELECT se.session_id AS session_id, se.position AS exercise_position, se.rest_seconds AS rest_seconds,
               e.id AS exercise_id, e.name AS name, e.name_en AS name_en, e.equipment AS equipment,
               e.primary_muscle AS primary_muscle, e.tracking_type AS tracking_type, e.category AS category,
               e.mechanic AS mechanic, e.force AS force, e.is_unilateral AS is_unilateral,
               es.set_index AS set_index, es.set_type AS set_type, es.weight_kg AS weight_kg, es.reps AS reps,
               es.rpe AS rpe, es.rir AS rir, es.duration_seconds AS duration_seconds,
               es.distance_meters AS distance_meters, es.incline_percent AS incline_percent,
               es.speed_kmh AS speed_kmh, ws.started_at AS started_at
        FROM exercise_sets es
        INNER JOIN session_exercises se ON se.id = es.session_exercise_id
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        INNER JOIN exercises e ON e.id = se.exercise_id
        WHERE ws.status = 'COMPLETED' AND se.exercise_id = :exerciseId AND ws.started_at >= :from
          AND es.is_completed = 1
          AND (COALESCE(es.reps, 0) > 0 OR COALESCE(es.duration_seconds, 0) > 0 OR COALESCE(es.distance_meters, 0) > 0)
        ORDER BY ws.started_at, se.session_id, se.position, es.set_index
        """,
    )
    fun observeExerciseCalorieSetRows(exerciseId: String, from: Instant): Flow<List<ExerciseCalorieSetRow>>

    @Query("UPDATE workout_sessions SET calories_burned = :caloriesBurned, body_weight_kg = :bodyWeightKg WHERE id = :sessionId")
    suspend fun updateSessionCalories(sessionId: Long, caloriesBurned: Int?, bodyWeightKg: Double?)

    /** Scrive tutte le stime in un'unica transazione: lo storico non mostra mai totali a metà. */
    @Transaction
    suspend fun applyCalories(results: List<SessionCalories>) {
        results.forEach { updateSessionCalories(it.sessionId, it.caloriesBurned, it.bodyWeightKg) }
    }
}
