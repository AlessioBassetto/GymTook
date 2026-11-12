package com.example.sport.core.model.catalog

import com.example.sport.core.model.DifficultyLevel
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.ForceType
import com.example.sport.core.model.MediaKind
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import kotlinx.serialization.Serializable

/**
 * Formato del catalogo esercizi (`app/src/main/assets/catalog/exercises.json`), generato dal tool
 * `tools/catalog-importer`. Incrementare [version] a ogni rigenerazione: all'avvio l'app applica la
 * nuova versione preservando preferiti, esercizi nascosti, regolazioni e storico.
 */
@Serializable
data class ExerciseCatalogDto(
    val version: Int,
    val exercises: List<CatalogExerciseDto>,
)

@Serializable
data class CatalogExerciseDto(
    /** Slug stabile e univoco, mai riutilizzato per un esercizio diverso. */
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val aliases: List<String> = emptyList(),
    val equipment: Equipment,
    val trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
    /** Tipo di movimento; negli esercizi curati si omette quando è forza. */
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val mechanic: Mechanic? = null,
    val force: ForceType? = null,
    val level: DifficultyLevel = DifficultyLevel.BEGINNER,
    val unilateral: Boolean = false,
    /** Il primo elemento è il muscolo target mostrato nelle liste. */
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle> = emptyList(),
    val instructions: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val commonMistakes: List<String> = emptyList(),
    /** Parametri di regolazione suggeriti (es. "Altezza sedile"). */
    val setupParams: List<String> = emptyList(),
    /** Null = default calcolato da meccanica e tipo di tracciamento. */
    val defaultRestSeconds: Int? = null,
    /**
     * Lingua di istruzioni, punti chiave ed errori comuni: [CONTENT_LOCALE_IT] oppure
     * [CONTENT_LOCALE_EN] per gli esercizi importati in attesa di traduzione.
     */
    val contentLocale: String = CONTENT_LOCALE_IT,
    val media: List<CatalogMediaDto> = emptyList(),
    /** Provenienza (es. `free-exercise-db:Barbell_Curl`), solo per tracciabilità e re-import. */
    val origin: String? = null,
) {
    companion object {
        const val CONTENT_LOCALE_IT = "it"
        const val CONTENT_LOCALE_EN = "en"
    }
}

/**
 * Contenuto multimediale. [path] è relativo alla base URL della CDN media configurata nell'app,
 * così lo stesso catalogo funziona con mirror diversi (sviluppo, staging, produzione).
 */
@Serializable
data class CatalogMediaDto(
    val kind: MediaKind,
    val path: String,
    /** Dataset o autore di origine (es. "free-exercise-db"). */
    val source: String? = null,
    /** Identificatore SPDX della licenza (es. "Unlicense", "CC-BY-SA-4.0"). */
    val license: String? = null,
    /** Testo di attribuzione obbligatorio per licenze che lo richiedono. */
    val attribution: String? = null,
)
