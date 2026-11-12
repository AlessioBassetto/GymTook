package com.example.sport.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.RoutineDayEntity
import com.example.sport.core.database.entity.RoutineExerciseEntity
import com.example.sport.core.database.entity.WeeklyScheduleEntity
import com.example.sport.core.database.entity.WorkoutRoutineEntity
import java.time.DayOfWeek

/** Esercizio pianificato con i dati anagrafici dell'esercizio. */
data class RoutineExerciseWithExercise(
    @Embedded
    val routineExercise: RoutineExerciseEntity,

    @Relation(parentColumn = "exercise_id", entityColumn = "id")
    val exercise: ExerciseEntity,
)

/**
 * Giorno di scheda con i suoi esercizi.
 * Room non garantisce l'ordine delle @Relation: usare [orderedExercises].
 */
data class RoutineDayWithExercises(
    @Embedded
    val day: RoutineDayEntity,

    @Relation(
        entity = RoutineExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "routine_day_id",
    )
    val exercises: List<RoutineExerciseWithExercise>,
) {
    val orderedExercises: List<RoutineExerciseWithExercise>
        get() = exercises.sortedBy { it.routineExercise.position }
}

/** Scheda completa: giorni, esercizi e calendario settimanale. */
data class RoutineWithDays(
    @Embedded
    val routine: WorkoutRoutineEntity,

    @Relation(
        entity = RoutineDayEntity::class,
        parentColumn = "id",
        entityColumn = "routine_id",
    )
    val days: List<RoutineDayWithExercises>,

    @Relation(parentColumn = "id", entityColumn = "routine_id")
    val schedule: List<WeeklyScheduleEntity>,
) {
    val orderedDays: List<RoutineDayWithExercises>
        get() = days.sortedBy { it.day.position }

    /** Mappa giorno della settimana → giorno di scheda (assente = riposo). */
    val dayByWeekday: Map<DayOfWeek, RoutineDayWithExercises>
        get() {
            val byId = days.associateBy { it.day.id }
            return schedule
                .mapNotNull { entry -> byId[entry.routineDayId]?.let { entry.dayOfWeek to it } }
                .toMap()
        }
}
