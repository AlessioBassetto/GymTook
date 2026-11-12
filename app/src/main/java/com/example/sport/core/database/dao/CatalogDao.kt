package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.sport.core.database.entity.AppMetadataEntity
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.database.entity.ExerciseMediaEntity
import com.example.sport.core.database.entity.ExerciseMuscleEntity
import com.example.sport.core.database.relation.ExerciseUserFlags
import java.time.Instant

/** Operazioni di installazione/aggiornamento del catalogo esercizi pre-popolato. */
@Dao
interface CatalogDao {

    @Query("SELECT `value` FROM app_metadata WHERE `key` = :key")
    suspend fun getMetadata(key: String): String?

    @Upsert
    suspend fun upsertMetadata(entry: AppMetadataEntity)

    suspend fun getInstalledCatalogVersion(): Int =
        getMetadata(AppMetadataEntity.KEY_EXERCISE_CATALOG_VERSION)?.toIntOrNull() ?: 0

    @Query("SELECT id, is_favorite, is_hidden, created_at FROM exercises WHERE is_custom = 0")
    suspend fun getCatalogUserFlags(): List<ExerciseUserFlags>

    /** Upsert = INSERT o UPDATE della riga esistente: non attiva i CASCADE (a differenza di REPLACE). */
    @Upsert
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercise_muscles WHERE exercise_id IN (SELECT id FROM exercises WHERE is_custom = 0)")
    suspend fun deleteCatalogMuscles()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMuscles(muscles: List<ExerciseMuscleEntity>)

    @Query("DELETE FROM exercise_media WHERE exercise_id IN (SELECT id FROM exercises WHERE is_custom = 0)")
    suspend fun deleteCatalogMedia()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedia(media: List<ExerciseMediaEntity>)

    /** Esercizi spariti dal nuovo catalogo: ritirati (non cancellati) per non rompere lo storico. */
    @Query(
        """
        UPDATE exercises SET is_retired = 1, updated_at = :now
        WHERE is_custom = 0 AND is_retired = 0 AND catalog_version < :version
        """,
    )
    suspend fun retireCatalogExercisesOlderThan(version: Int, now: Instant): Int

    /**
     * Applica atomicamente una versione del catalogo. Gli [exercises] devono già contenere i
     * flag utente preservati (preferiti, nascosti) letti con [getCatalogUserFlags].
     *
     * @return numero di esercizi ritirati.
     */
    @Transaction
    suspend fun applyCatalog(
        version: Int,
        exercises: List<ExerciseEntity>,
        muscles: List<ExerciseMuscleEntity>,
        media: List<ExerciseMediaEntity>,
        now: Instant,
    ): Int {
        require(exercises.all { !it.isCustom && it.catalogVersion == version }) {
            "Ogni esercizio di catalogo deve avere is_custom = false e catalog_version = $version"
        }
        upsertExercises(exercises)
        deleteCatalogMuscles()
        insertMuscles(muscles)
        deleteCatalogMedia()
        insertMedia(media)
        val retired = retireCatalogExercisesOlderThan(version, now)
        upsertMetadata(AppMetadataEntity(AppMetadataEntity.KEY_EXERCISE_CATALOG_VERSION, version.toString()))
        return retired
    }
}
