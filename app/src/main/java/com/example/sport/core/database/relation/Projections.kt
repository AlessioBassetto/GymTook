package com.example.sport.core.database.relation

import androidx.room.ColumnInfo
import java.time.Instant

/**
 * Punto della serie storica di un esercizio (una sessione completata).
 * Calcolato solo su serie completate e non di riscaldamento.
 */
data class ExerciseProgressPoint(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "performed_at")
    val performedAt: Instant,

    /** Carico massimo sollevato nella sessione. */
    @ColumnInfo(name = "max_weight_kg")
    val maxWeightKg: Double,

    /** Volume totale: Σ (kg × ripetizioni). */
    @ColumnInfo(name = "total_volume_kg")
    val totalVolumeKg: Double,

    @ColumnInfo(name = "total_reps")
    val totalReps: Int,

    @ColumnInfo(name = "set_count")
    val setCount: Int,

    /**
     * Miglior 1RM stimato (formula di Epley: kg × (1 + reps / 30); con 1 rep = kg).
     * Considera solo serie da 1 a 12 ripetizioni, oltre le quali la stima perde affidabilità.
     */
    @ColumnInfo(name = "best_estimated_one_rep_max_kg")
    val bestEstimatedOneRepMaxKg: Double?,
)

/** Serie allenante completata con carico: materia prima per record e riepiloghi per esercizio. */
data class CompletedSetRow(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "performed_at")
    val performedAt: Instant,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,

    val reps: Int,

    /** Id della serie: permette di eliminare il singolo dato anomalo che ha generato un record. */
    @ColumnInfo(name = "set_id")
    val setId: Long = 0,
)

/** Riepilogo di una sessione per la lista dello storico. */
data class SessionSummary(
    val id: Long,

    val title: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Instant,

    @ColumnInfo(name = "ended_at")
    val endedAt: Instant?,

    @ColumnInfo(name = "exercise_count")
    val exerciseCount: Int,

    @ColumnInfo(name = "working_set_count")
    val workingSetCount: Int,

    @ColumnInfo(name = "total_volume_kg")
    val totalVolumeKg: Double,

    /** Stima in kcal (null se non ancora calcolabile, es. nessuna pesata registrata). */
    @ColumnInfo(name = "calories_burned")
    val caloriesBurned: Int? = null,
)

/** Intestazione della sessione in corso (banner "Riprendi"), senza caricare esercizi e serie. */
data class ActiveSessionHeader(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Instant,
)

/** Miglior carico storico di un esercizio (serie completate, non di riscaldamento). */
data class ExerciseBestWeight(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,
    @ColumnInfo(name = "best_weight_kg")
    val bestWeightKg: Double,
)

/** Flag utente di un esercizio di catalogo, da preservare durante l'aggiornamento del catalogo. */
data class ExerciseUserFlags(
    val id: String,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,

    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
)

/** Serie a tempo di un esercizio INCLINE_TIME: pendenza (null se non scritta) e durata. */
data class InclineSetRow(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "performed_at")
    val performedAt: Instant,

    @ColumnInfo(name = "incline_percent")
    val inclinePercent: Double?,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,
)
