package com.example.sport.core.database.relation

import androidx.room.ColumnInfo
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.ForceType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.SetType
import java.time.Instant

/** Inizio e fine di un allenamento concluso: la finestra temporale della stima calorica. */
data class SessionTimes(
    val id: Long,

    @ColumnInfo(name = "started_at")
    val startedAt: Instant,

    @ColumnInfo(name = "ended_at")
    val endedAt: Instant?,
)

/**
 * Una serie completata con tutto ciò che serve al calcolo delle calorie: valori registrati,
 * anagrafica dell'esercizio (tipo, attrezzatura, muscolo, mono/multiarticolare) e recupero
 * impostato. Una sola query carica le serie di tutti gli allenamenti da ricalcolare.
 */
data class CalorieSetRow(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "exercise_position")
    val exercisePosition: Int,

    /** Recupero impostato per l'esercizio: pesa la ripartizione del tempo di recupero. */
    @ColumnInfo(name = "rest_seconds")
    val restSeconds: Int,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    val name: String,

    @ColumnInfo(name = "name_en")
    val nameEn: String?,

    val equipment: Equipment,

    @ColumnInfo(name = "primary_muscle")
    val primaryMuscle: Muscle,

    @ColumnInfo(name = "tracking_type")
    val trackingType: ExerciseTrackingType,

    val category: ExerciseCategory,

    val mechanic: Mechanic?,

    val force: ForceType?,

    @ColumnInfo(name = "is_unilateral")
    val isUnilateral: Boolean,

    @ColumnInfo(name = "set_index")
    val setIndex: Int,

    @ColumnInfo(name = "set_type")
    val setType: SetType,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double?,

    val reps: Int?,

    val rpe: Double?,

    val rir: Int?,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int?,

    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Double?,

    @ColumnInfo(name = "incline_percent")
    val inclinePercent: Double? = null,
)

/** Esito della stima per un allenamento, da scrivere nella sua riga. */
data class SessionCalories(
    val sessionId: Long,
    val caloriesBurned: Int?,
    val bodyWeightKg: Double?,
)
