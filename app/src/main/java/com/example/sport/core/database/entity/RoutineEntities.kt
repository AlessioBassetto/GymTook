package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sport.core.model.SplitType
import java.time.DayOfWeek
import java.time.Instant

/**
 * Scheda di allenamento (programma), es. "PPL Ipertrofia".
 * Contiene giorni ([RoutineDayEntity]) e la pianificazione settimanale ([WeeklyScheduleEntity]).
 */
@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_routine_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("is_active"),
        Index("parent_routine_id"),
    ],
)
data class WorkoutRoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val description: String? = null,

    @ColumnInfo(name = "split_type")
    val splitType: SplitType,

    /** Al massimo una scheda attiva: garantito da `RoutineDao.setActiveRoutine`. */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    /** Scheda da cui questa è stata duplicata: traccia le varianti (A → A deload, A → B). */
    @ColumnInfo(name = "parent_routine_id")
    val parentRoutineId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)

/** Giorno di allenamento di una scheda, es. "Push A", "Lower 1". */
@Entity(
    tableName = "routine_days",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["routine_id", "position"]),
    ],
)
data class RoutineDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "routine_id")
    val routineId: Long,

    val name: String,

    /** Ordine nel builder (0-based). */
    val position: Int,

    val notes: String? = null,
)

/**
 * Esercizio pianificato in un giorno di scheda, con target di serie/ripetizioni/intensità.
 * `exercise_id` è RESTRICT: un esercizio usato in una scheda non può essere cancellato,
 * solo nascosto.
 */
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_day_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["routine_day_id", "position"]),
        Index("exercise_id"),
    ],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "routine_day_id")
    val routineDayId: Long,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /** Ordine nel giorno (0-based), modificato dal drag-and-drop. */
    val position: Int,

    @ColumnInfo(name = "target_sets")
    val targetSets: Int,

    @ColumnInfo(name = "target_reps_min")
    val targetRepsMin: Int? = null,

    @ColumnInfo(name = "target_reps_max")
    val targetRepsMax: Int? = null,

    /** RPE target (es. 8.0). Alternativo a [targetRir]: l'utente sceglie la scala nelle impostazioni. */
    @ColumnInfo(name = "target_rpe")
    val targetRpe: Double? = null,

    /** Ripetizioni in riserva target (es. 2). */
    @ColumnInfo(name = "target_rir")
    val targetRir: Int? = null,

    /** Recupero in secondi; null = usa `exercises.default_rest_seconds`. */
    @ColumnInfo(name = "rest_seconds")
    val restSeconds: Int? = null,

    /** Esercizi dello stesso giorno con lo stesso valore formano un superset/circuito. */
    @ColumnInfo(name = "superset_group")
    val supersetGroup: Int? = null,

    val notes: String? = null,
)

/**
 * Calendario settimanale della scheda: quale giorno di scheda si esegue in quale giorno
 * della settimana. Nessuna riga per un giorno = riposo.
 * Lo stesso giorno di scheda può essere assegnato a più giorni (Push lunedì e giovedì).
 */
@Entity(
    tableName = "weekly_schedule",
    primaryKeys = ["routine_id", "day_of_week"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_day_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("routine_day_id"),
    ],
)
data class WeeklyScheduleEntity(
    @ColumnInfo(name = "routine_id")
    val routineId: Long,

    /** Persistito come intero ISO-8601 (1 = lunedì … 7 = domenica). */
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: DayOfWeek,

    @ColumnInfo(name = "routine_day_id")
    val routineDayId: Long,
)
