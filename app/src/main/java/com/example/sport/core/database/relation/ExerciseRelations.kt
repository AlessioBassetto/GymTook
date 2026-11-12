package com.example.sport.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseMediaEntity
import com.example.sport.core.database.entity.ExerciseMuscleEntity
import com.example.sport.core.database.entity.ExerciseUserVideoEntity
import com.example.sport.core.database.entity.MachineSetupEntity
import com.example.sport.core.model.MediaKind
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleRole

/** Scheda esercizio completa: anagrafica, muscoli (per la mappa anatomica) e media. */
data class ExerciseDetails(
    @Embedded
    val exercise: ExerciseEntity,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val muscles: List<ExerciseMuscleEntity>,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val media: List<ExerciseMediaEntity>,

    /** Video personale dell'utente, se presente: ha la precedenza su quello di catalogo. */
    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val userVideo: ExerciseUserVideoEntity?,
) {
    val primaryMuscles: List<Muscle>
        get() = muscles.filter { it.role == MuscleRole.PRIMARY }.map { it.muscle }

    val secondaryMuscles: List<Muscle>
        get() = muscles.filter { it.role == MuscleRole.SECONDARY }.map { it.muscle }

    /** Video loop principale, se disponibile. */
    val videoLoop: ExerciseMediaEntity?
        get() = media.filter { it.kind == MediaKind.VIDEO_LOOP }.minByOrNull { it.position }

    /** Fotogrammi in ordine: animati come mini-loop quando manca il video. */
    val frames: List<ExerciseMediaEntity>
        get() = media.filter { it.kind == MediaKind.IMAGE_FRAME }.sortedBy { it.position }

    /** Immagine per liste e anteprime: primo fotogramma. */
    val thumbnail: ExerciseMediaEntity?
        get() = frames.firstOrNull()
}

/** Esercizio con tutti i muscoli coinvolti: alimenta la scheda dettaglio e la mappa anatomica. */
data class ExerciseWithMuscles(
    @Embedded
    val exercise: ExerciseEntity,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val muscles: List<ExerciseMuscleEntity>,
) {
    val primaryMuscles: List<Muscle>
        get() = muscles.filter { it.role == MuscleRole.PRIMARY }.map { it.muscle }

    val secondaryMuscles: List<Muscle>
        get() = muscles.filter { it.role == MuscleRole.SECONDARY }.map { it.muscle }
}

/** Esercizio con i profili di regolazione salvati, ordinati dal più recente. */
data class ExerciseWithMachineSetups(
    @Embedded
    val exercise: ExerciseEntity,

    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val setups: List<MachineSetupEntity>,
) {
    val setupsByRecentUse: List<MachineSetupEntity>
        get() = setups.sortedByDescending { it.lastUsedAt }
}
