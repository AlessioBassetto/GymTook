package com.example.sport.core.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.sport.core.database.dao.CustomExerciseRemoval
import com.example.sport.core.database.dao.ExerciseDao
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseMuscleEntity
import com.example.sport.core.database.entity.ExerciseUserVideoEntity
import com.example.sport.core.database.query.ExerciseFilter
import com.example.sport.core.database.util.SearchText
import com.example.sport.core.media.UserVideoStore
import com.example.sport.core.media.VideoImportResult
import com.example.sport.core.model.DifficultyLevel
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleRole
import com.example.sport.core.model.catalog.newCustomExerciseId
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Contenuto modificabile di un esercizio personalizzato (anche bozza dell'editor). */
@Immutable
@Serializable
data class CustomExerciseDraft(
    val name: String = "",
    val equipment: Equipment = Equipment.MACHINE,
    val trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
    /** In ordine di scelta: il primo è il muscolo target mostrato nelle liste. */
    val primaryMuscles: List<Muscle> = emptyList(),
    val secondaryMuscles: List<Muscle> = emptyList(),
    val mechanic: Mechanic? = null,
    val isUnilateral: Boolean = false,
    val defaultRestSeconds: Int = DEFAULT_REST_SECONDS,
    val setupParamHints: List<String> = emptyList(),
    /** Un passaggio per riga. */
    val notes: String = "",
) {
    val isValid: Boolean get() = name.isNotBlank() && primaryMuscles.isNotEmpty()

    companion object {
        const val DEFAULT_REST_SECONDS = 90
        const val MIN_REST_SECONDS = 0
        const val MAX_REST_SECONDS = 600
    }
}

/**
 * Esercizi creati dall'utente e video personali (validi anche per gli esercizi di catalogo).
 *
 * Gli esercizi personalizzati vivono nella stessa tabella del catalogo con `is_custom = 1` e id
 * `custom_<uuid>`: libreria, schede, logger e statistiche li trattano come tutti gli altri, e gli
 * aggiornamenti del catalogo non li toccano.
 */
@Singleton
class CustomExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val videoStore: UserVideoStore,
    private val clock: Clock,
) {

    suspend fun getDraft(exerciseId: String): CustomExerciseDraft? {
        val stored = exerciseDao.getWithMuscles(exerciseId) ?: return null
        val exercise = stored.exercise
        if (!exercise.isCustom) return null
        val primaries = stored.primaryMuscles.sortedBy { if (it == exercise.primaryMuscle) 0 else 1 }
        return CustomExerciseDraft(
            name = exercise.name,
            equipment = exercise.equipment,
            trackingType = exercise.trackingType,
            primaryMuscles = primaries,
            secondaryMuscles = stored.secondaryMuscles,
            mechanic = exercise.mechanic,
            isUnilateral = exercise.isUnilateral,
            defaultRestSeconds = exercise.defaultRestSeconds,
            setupParamHints = exercise.setupParamHints,
            notes = exercise.instructions.joinToString("\n"),
        )
    }

    /** @return id del nuovo esercizio. */
    suspend fun create(draft: CustomExerciseDraft): String {
        require(draft.isValid) { "Nome e almeno un muscolo principale sono obbligatori" }
        val id = newCustomExerciseId(UUID.randomUUID().toString())
        val now = Instant.now(clock)
        exerciseDao.insertCustomExercise(draft.toEntity(id, createdAt = now, updatedAt = now, isFavorite = false), draft.toMuscles(id))
        return id
    }

    suspend fun update(exerciseId: String, draft: CustomExerciseDraft) {
        require(draft.isValid) { "Nome e almeno un muscolo principale sono obbligatori" }
        val existing = checkNotNull(exerciseDao.getById(exerciseId)) { "Esercizio $exerciseId inesistente" }
        require(existing.isCustom) { "Gli esercizi di catalogo non si modificano" }
        val updated = draft.toEntity(exerciseId, createdAt = existing.createdAt, updatedAt = Instant.now(clock), isFavorite = existing.isFavorite)
        exerciseDao.updateCustomExercise(updated, draft.toMuscles(exerciseId))
    }

    /**
     * Elimina l'esercizio se non è mai stato usato (con il suo video), altrimenti lo nasconde dalla
     * libreria: schede e storico restano intatti.
     */
    suspend fun delete(exerciseId: String): CustomExerciseRemoval {
        val result = exerciseDao.deleteOrHideCustomExercise(exerciseId, Instant.now(clock))
        if (result == CustomExerciseRemoval.DELETED) pruneVideos()
        return result
    }

    /** Esercizio visibile con lo stesso nome (normalizzato): l'editor avvisa del possibile doppione. */
    suspend fun findSameName(name: String, excludeId: String? = null): ExerciseEntity? {
        val normalized = SearchText.normalize(name)
        if (normalized.isEmpty()) return null
        return exerciseDao.searchExercises(ExerciseFilter(query = name, limit = SAME_NAME_LOOKUP_LIMIT))
            .firstOrNull { it.id != excludeId && SearchText.normalize(it.name) == normalized }
    }

    // ------------------------------------------------------------------ Video personali

    suspend fun importVideo(exerciseId: String, source: Uri): VideoImportResult =
        videoStore.import(source).also { attach(exerciseId, it) }

    suspend fun adoptRecordedVideo(exerciseId: String, recorded: File): VideoImportResult =
        videoStore.adopt(recorded).also { attach(exerciseId, it) }

    suspend fun removeVideo(exerciseId: String) {
        exerciseDao.deleteUserVideo(exerciseId)
        pruneVideos()
    }

    fun videoUri(fileName: String): String = videoStore.uriFor(fileName)

    private suspend fun attach(exerciseId: String, result: VideoImportResult) {
        if (result !is VideoImportResult.Success) return
        exerciseDao.upsertUserVideo(ExerciseUserVideoEntity(exerciseId, result.fileName, result.sizeBytes, Instant.now(clock)))
        // Il video sostituito (se c'era) non è più referenziato.
        pruneVideos()
    }

    private suspend fun pruneVideos() {
        videoStore.prune(exerciseDao.getUserVideoFileNames().toSet())
    }

    private fun CustomExerciseDraft.toEntity(id: String, createdAt: Instant, updatedAt: Instant, isFavorite: Boolean): ExerciseEntity {
        val cleanName = name.trim()
        val hints = setupParamHints.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return ExerciseEntity(
            id = id,
            name = cleanName,
            nameEn = null,
            aliases = emptyList(),
            searchText = SearchText.build(cleanName, null, emptyList()),
            equipment = equipment,
            primaryMuscle = primaryMuscles.first(),
            trackingType = trackingType,
            category = if (
                trackingType == ExerciseTrackingType.DISTANCE_DURATION ||
                trackingType == ExerciseTrackingType.INCLINE_TIME ||
                equipment == Equipment.CARDIO_MACHINE
            ) {
                ExerciseCategory.CARDIO
            } else {
                ExerciseCategory.STRENGTH
            },
            mechanic = mechanic,
            force = null,
            level = DifficultyLevel.BEGINNER,
            isUnilateral = isUnilateral,
            instructions = notes.lines().map { it.trim() }.filter { it.isNotEmpty() },
            keyPoints = emptyList(),
            commonMistakes = emptyList(),
            supportsMachineSetup = hints.isNotEmpty() || equipment.isAdjustableByDefault,
            setupParamHints = hints,
            defaultRestSeconds = defaultRestSeconds.coerceIn(CustomExerciseDraft.MIN_REST_SECONDS, CustomExerciseDraft.MAX_REST_SECONDS),
            isCustom = true,
            isFavorite = isFavorite,
            catalogVersion = 0,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun CustomExerciseDraft.toMuscles(id: String): List<ExerciseMuscleEntity> {
        val primaries = primaryMuscles.distinct()
        return primaries.map { ExerciseMuscleEntity(id, it, role = MuscleRole.PRIMARY) } +
            secondaryMuscles.distinct().filterNot { it in primaries }.map { ExerciseMuscleEntity(id, it, role = MuscleRole.SECONDARY) }
    }

    private companion object {
        const val SAME_NAME_LOOKUP_LIMIT = 20
    }
}
