package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.SessionStatus
import com.example.sport.core.model.SetType
import java.time.Instant

/**
 * Sessione di allenamento eseguita (o in corso).
 * I riferimenti a scheda/giorno sono SET_NULL: cancellare una scheda non cancella lo storico.
 */
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_day_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["status", "started_at"]),
        Index("routine_id"),
        Index("routine_day_id"),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "routine_id")
    val routineId: Long? = null,

    @ColumnInfo(name = "routine_day_id")
    val routineDayId: Long? = null,

    /** Snapshot del nome ("Push A") così lo storico resta leggibile anche se la scheda cambia. */
    val title: String,

    val status: SessionStatus,

    @ColumnInfo(name = "started_at")
    val startedAt: Instant,

    @ColumnInfo(name = "ended_at")
    val endedAt: Instant? = null,

    /**
     * Peso corporeo con cui sono state stimate le [caloriesBurned]: la pesata del giorno
     * dell'allenamento o la più vicina. Null finché la stima non è possibile.
     */
    @ColumnInfo(name = "body_weight_kg")
    val bodyWeightKg: Double? = null,

    val notes: String? = null,

    /**
     * Calorie totali stimate (kcal lorde: metabolismo a riposo + lavoro) da `WorkoutCalorieCalculator`.
     * Null per la sessione in corso e quando manca una pesata. È un dato derivato: si ricalcola
     * quando cambiano le serie della sessione, le pesate o il profilo corporeo.
     */
    @ColumnInfo(name = "calories_burned")
    val caloriesBurned: Int? = null,
)

/**
 * Esercizio eseguito all'interno di una sessione.
 *
 * Regolazione macchinario: [machineSetupId] punta al profilo usato (per aggiornarne
 * `last_used_at`), mentre [machineSetup] è lo snapshot immutabile dei valori usati quel giorno,
 * così lo storico mostra la regolazione reale anche se il profilo viene modificato in seguito.
 */
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["replaced_exercise_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RoutineExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_exercise_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = MachineSetupEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_setup_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["session_id", "position"]),
        Index("exercise_id"),
        Index("replaced_exercise_id"),
        Index("routine_exercise_id"),
        Index("machine_setup_id"),
    ],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /** Esercizio pianificato da cui deriva (null per sessioni libere o esercizi aggiunti al volo). */
    @ColumnInfo(name = "routine_exercise_id")
    val routineExerciseId: Long? = null,

    val position: Int,

    @ColumnInfo(name = "superset_group")
    val supersetGroup: Int? = null,

    /** Recupero che il timer avvia al completamento di ogni serie. */
    @ColumnInfo(name = "rest_seconds")
    val restSeconds: Int,

    /** Profilo di regolazione usato (null se nessuno). */
    @ColumnInfo(name = "machine_setup_id")
    val machineSetupId: Long? = null,

    /** Snapshot dei valori di regolazione usati in questa sessione. */
    @ColumnInfo(name = "machine_setup")
    val machineSetup: List<MachineSetupParam> = emptyList(),

    /** Se l'esercizio è stato sostituito al volo: l'esercizio originariamente pianificato. */
    @ColumnInfo(name = "replaced_exercise_id")
    val replacedExerciseId: String? = null,

    val notes: String? = null,
)

/** Singola serie registrata. Il peso è sempre in kg; la conversione in lb è solo di presentazione. */
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_exercise_id", "set_index"]),
    ],
)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_exercise_id")
    val sessionExerciseId: Long,

    /** Ordine della serie nell'esercizio (0-based). */
    @ColumnInfo(name = "set_index")
    val setIndex: Int,

    @ColumnInfo(name = "set_type")
    val setType: SetType = SetType.WORKING,

    /** Carico in kg (zavorra per BODYWEIGHT_REPS, contrappeso per ASSISTED_BODYWEIGHT). */
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double? = null,

    val reps: Int? = null,

    /** Rate of Perceived Exertion (1–10, mezzi punti ammessi). Opzionale. */
    val rpe: Double? = null,

    /** Reps In Reserve. Opzionale. */
    val rir: Int? = null,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int? = null,

    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Double? = null,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "completed_at")
    val completedAt: Instant? = null,

    val notes: String? = null,
)
