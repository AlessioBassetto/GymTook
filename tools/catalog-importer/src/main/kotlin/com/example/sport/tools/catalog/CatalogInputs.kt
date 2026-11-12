package com.example.sport.tools.catalog

import com.example.sport.core.model.catalog.CatalogExerciseDto
import com.example.sport.core.model.catalog.ExerciseCatalogDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Traduzione completa di un esercizio importato, chiave = nome inglese nel dataset.
 * Generata dal comando `translate` (Claude) e versionata nel repository: ogni build è
 * riproducibile senza rete né costi; [reviewed] = true dopo la revisione umana.
 */
@Serializable
data class TranslationEntry(
    val name: String? = null,
    val aliases: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val commonMistakes: List<String> = emptyList(),
    val translatedBy: String? = null,
    val reviewed: Boolean = false,
) {
    val hasContent: Boolean get() = instructions.isNotEmpty()
}

/** Tutti gli input versionati del catalogo. */
data class CatalogInputs(
    val curated: List<CatalogExerciseDto>,
    /** id esercizio curato → nome sorgente. */
    val curatedLinks: Map<String, String>,
    /** nome sorgente → nome italiano. */
    val names: Map<String, String>,
    /** nome sorgente → traduzione completa. */
    val translations: Map<String, TranslationEntry>,
) {
    companion object {
        private val stringMap = MapSerializer(String.serializer(), String.serializer())
        private val translationMap = MapSerializer(String.serializer(), TranslationEntry.serializer())

        fun load(paths: ImporterPaths): CatalogInputs = CatalogInputs(
            curated = ImporterJson.decodeFromString(ExerciseCatalogDto.serializer(), read(paths.curatedFile)).exercises,
            curatedLinks = ImporterJson.decodeFromString(stringMap, read(paths.curatedLinksFile)),
            names = ImporterJson.decodeFromString(stringMap, read(paths.namesFile)),
            translations = if (Files.exists(paths.translationsFile)) {
                ImporterJson.decodeFromString(translationMap, read(paths.translationsFile))
            } else {
                emptyMap()
            },
        )

        /** Ordinamento per chiave: il file cambia solo dove cambiano davvero le traduzioni. */
        fun saveTranslations(paths: ImporterPaths, translations: Map<String, TranslationEntry>) {
            writeAtomically(paths.translationsFile, ImporterJson.encodeToString(translationMap, translations.toSortedMap()) + "\n")
        }

        private fun read(path: Path): String = Files.readString(path, Charsets.UTF_8)
    }
}

/** Scrive su file temporaneo e poi rinomina: un'interruzione non lascia mai un file troncato. */
fun writeAtomically(target: Path, content: String) {
    Files.createDirectories(target.parent)
    val temp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    Files.writeString(temp, content, Charsets.UTF_8)
    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
