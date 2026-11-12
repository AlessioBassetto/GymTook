package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import com.example.sport.core.model.MuscleRole

/**
 * Tabella ponte esercizio ↔ muscolo con ruolo (primario/secondario).
 * È la sorgente di verità per i filtri anatomici e per la mappa muscolare evidenziata.
 */
@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exercise_id", "muscle"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["muscle_group", "role"]),
        Index(value = ["muscle", "role"]),
    ],
)
data class ExerciseMuscleEntity(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    val muscle: Muscle,

    /** Denormalizzato da [muscle] per filtrare per macro-gruppo con un indice. */
    @ColumnInfo(name = "muscle_group")
    val muscleGroup: MuscleGroup = muscle.group,

    val role: MuscleRole,
)
