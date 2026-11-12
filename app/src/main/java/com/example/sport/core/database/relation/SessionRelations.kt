package com.example.sport.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseSetEntity
import com.example.sport.core.database.entity.SessionExerciseEntity
import com.example.sport.core.database.entity.WorkoutSessionEntity

/** Esercizio di sessione con anagrafica e serie: è il modello di una "card" del Workout Logger. */
data class SessionExerciseWithSets(
    @Embedded
    val sessionExercise: SessionExerciseEntity,

    @Relation(parentColumn = "exercise_id", entityColumn = "id")
    val exercise: ExerciseEntity,

    @Relation(parentColumn = "id", entityColumn = "session_exercise_id")
    val sets: List<ExerciseSetEntity>,
) {
    val orderedSets: List<ExerciseSetEntity>
        get() = sets.sortedBy { it.setIndex }
}

/** Sessione completa: alimenta il Workout Logger attivo e il dettaglio storico. */
data class WorkoutSessionWithExercises(
    @Embedded
    val session: WorkoutSessionEntity,

    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "session_id",
    )
    val exercises: List<SessionExerciseWithSets>,
) {
    val orderedExercises: List<SessionExerciseWithSets>
        get() = exercises.sortedBy { it.sessionExercise.position }
}
