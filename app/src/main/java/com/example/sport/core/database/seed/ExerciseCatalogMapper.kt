package com.example.sport.core.database.seed

import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseMediaEntity
import com.example.sport.core.database.entity.ExerciseMuscleEntity
import com.example.sport.core.database.relation.ExerciseUserFlags
import com.example.sport.core.database.util.SearchText
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.MuscleRole
import com.example.sport.core.model.catalog.CatalogExerciseDto
import java.time.Instant

/** Recupero di default: 150 s multiarticolari, 90 s complementari, 60 s cardio/isometrie. */
internal fun defaultRestSecondsFor(mechanic: Mechanic?, trackingType: ExerciseTrackingType): Int = when {
    trackingType == ExerciseTrackingType.DURATION ||
        trackingType == ExerciseTrackingType.DISTANCE_DURATION ||
        trackingType == ExerciseTrackingType.INCLINE_TIME -> 60
    mechanic == Mechanic.COMPOUND -> 150
    else -> 90
}

/** Converte la voce di catalogo in entità, preservando i flag utente se l'esercizio esisteva già. */
internal fun CatalogExerciseDto.toEntity(
    catalogVersion: Int,
    now: Instant,
    existingFlags: ExerciseUserFlags?,
): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name.trim(),
    nameEn = nameEn?.trim(),
    aliases = aliases.map { it.trim() }.filter { it.isNotEmpty() },
    searchText = SearchText.build(name, nameEn, aliases),
    equipment = equipment,
    primaryMuscle = primaryMuscles.first(),
    trackingType = trackingType,
    category = category,
    mechanic = mechanic,
    force = force,
    level = level,
    isUnilateral = unilateral,
    instructions = instructions,
    keyPoints = keyPoints,
    commonMistakes = commonMistakes,
    contentLocale = contentLocale,
    supportsMachineSetup = setupParams.isNotEmpty() || equipment.isAdjustableByDefault,
    setupParamHints = setupParams.map { it.trim() },
    defaultRestSeconds = defaultRestSeconds ?: defaultRestSecondsFor(mechanic, trackingType),
    isCustom = false,
    isFavorite = existingFlags?.isFavorite ?: false,
    isHidden = existingFlags?.isHidden ?: false,
    isRetired = false,
    catalogVersion = catalogVersion,
    createdAt = existingFlags?.createdAt ?: now,
    updatedAt = now,
)

internal fun CatalogExerciseDto.toMuscleEntities(): List<ExerciseMuscleEntity> =
    primaryMuscles.map { ExerciseMuscleEntity(exerciseId = id, muscle = it, role = MuscleRole.PRIMARY) } +
        secondaryMuscles.map { ExerciseMuscleEntity(exerciseId = id, muscle = it, role = MuscleRole.SECONDARY) }

/** La posizione è l'ordine all'interno dello stesso tipo di media, come nel catalogo. */
internal fun CatalogExerciseDto.toMediaEntities(): List<ExerciseMediaEntity> =
    media.groupBy { it.kind }.flatMap { (kind, items) ->
        items.mapIndexed { index, item ->
            ExerciseMediaEntity(
                exerciseId = id,
                kind = kind,
                position = index,
                path = item.path,
                source = item.source,
                license = item.license,
                attribution = item.attribution,
            )
        }
    }
