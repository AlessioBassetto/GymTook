package com.example.sport.tools.catalog

import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.catalog.CatalogExerciseDto
import com.example.sport.core.model.catalog.ExerciseCatalogDto
import com.example.sport.core.model.catalog.problems
import kotlinx.serialization.Serializable
import java.net.URI

/** Voce del manifest per copiare i media sulla propria CDN (`mirror-media`). */
@Serializable
data class MediaManifestEntry(val path: String, val url: String)

data class BuildReport(
    val curatedCount: Int,
    val importedCount: Int,
    val excludedByFilter: Int,
    val italianContentCount: Int,
    val missingItalianNames: List<String>,
    val unusedNameKeys: List<String>,
    val unusedTranslationKeys: List<String>,
    val warnings: List<String>,
) {
    fun render(): String = buildString {
        appendLine("Esercizi curati (italiano completo): $curatedCount")
        appendLine("Esercizi importati: $importedCount (esclusi dal filtro: $excludedByFilter)")
        appendLine("Contenuto in italiano: $italianContentCount / ${curatedCount + importedCount}")
        appendLine("Istruzioni ancora in inglese: ${curatedCount + importedCount - italianContentCount} (comando `translate`)")
        if (missingItalianNames.isNotEmpty()) appendLine("Nomi senza traduzione (${missingItalianNames.size}): $missingItalianNames")
        if (unusedNameKeys.isNotEmpty()) appendLine("Chiavi di names_it.json non usate: $unusedNameKeys")
        if (unusedTranslationKeys.isNotEmpty()) appendLine("Chiavi di translations_it.json non usate: $unusedTranslationKeys")
        warnings.forEach { appendLine("Avviso: $it") }
    }
}

data class BuildResult(
    val catalog: ExerciseCatalogDto,
    val mediaManifest: List<MediaManifestEntry>,
    val report: BuildReport,
)

/**
 * Unisce le fonti in un catalogo valido:
 * 1. esercizi curati (italiano revisionato) — collegati alla sorgente per ereditarne le immagini;
 * 2. esercizi importati da free-exercise-db, con tassonomia mappata, nome italiano e, se presente,
 *    traduzione completa di istruzioni/punti chiave/errori.
 *
 * Fallisce (invece di "aggiustare") su link rotti, id duplicati o catalogo non valido.
 */
class CatalogBuilder(private val mapper: TaxonomyMapper = TaxonomyMapper()) {

    fun build(version: Int, sources: List<SourceExercise>, inputs: CatalogInputs): BuildResult {
        val sourcesByName = sources.groupBy { it.name }
        val duplicateNames = sourcesByName.filterValues { it.size > 1 }.keys
        check(duplicateNames.isEmpty()) { "Nomi duplicati nel dataset: $duplicateNames" }

        val curatedIds = inputs.curated.map { it.id }.toSet()
        val brokenLinks = inputs.curatedLinks.filter { (id, sourceName) ->
            id !in curatedIds || sourceName !in sourcesByName
        }
        check(brokenLinks.isEmpty()) { "Link curati non validi (id o nome sorgente inesistente): $brokenLinks" }

        val curated = inputs.curated.map { exercise ->
            val source = inputs.curatedLinks[exercise.id]?.let { sourcesByName.getValue(it).single() }
            exercise.copy(
                category = curatedCategory(exercise, source),
                contentLocale = CatalogExerciseDto.CONTENT_LOCALE_IT,
                origin = source?.let(FreeExerciseDb::origin) ?: exercise.origin,
                media = exercise.media.ifEmpty { source?.let(mapper::mediaOf).orEmpty() },
            )
        }

        val linkedSourceNames = inputs.curatedLinks.values.toSet()
        val accepted = sources.filter(SourceFilter::accept)
        val warnings = mutableListOf<String>()
        val missingNames = mutableListOf<String>()
        val imported = accepted
            .filter { it.name !in linkedSourceNames }
            .mapNotNull { source ->
                val mapped = mapper.map(source)
                warnings += mapped.warnings
                mapped.exercise?.let { localize(it, source, inputs, missingNames) }
            }
            .sortedBy { it.id }

        val all = curated + imported
        val catalog = ExerciseCatalogDto(version = version, exercises = all)
        val problems = catalog.problems()
        check(problems.isEmpty()) { "Catalogo generato non valido:\n" + problems.joinToString("\n") }

        val importedNames = accepted.map { it.name }.toSet() - linkedSourceNames
        return BuildResult(
            catalog = catalog,
            mediaManifest = all.flatMap { it.media }
                .filter { it.source == FreeExerciseDb.SOURCE_NAME }
                .map { MediaManifestEntry(path = it.path, url = mediaUrl(it.path)) }
                .distinctBy { it.path }
                .sortedBy { it.path },
            report = BuildReport(
                curatedCount = curated.size,
                importedCount = imported.size,
                excludedByFilter = sources.size - accepted.size,
                italianContentCount = all.count { it.contentLocale == CatalogExerciseDto.CONTENT_LOCALE_IT },
                missingItalianNames = missingNames.sorted(),
                unusedNameKeys = (inputs.names.keys - importedNames).sorted(),
                unusedTranslationKeys = (inputs.translations.keys - importedNames).sorted(),
                warnings = warnings,
            ),
        )
    }

    /**
     * Gli esercizi curati dichiarano la categoria solo se non è forza; altrimenti la prendono dalla
     * voce collegata del dataset o, per il cardio senza collegamento, dall'attrezzo e dai muscoli.
     */
    private fun curatedCategory(exercise: CatalogExerciseDto, source: SourceExercise?): ExerciseCategory = when {
        exercise.category != ExerciseCategory.STRENGTH -> exercise.category
        source != null -> mapper.categoryOf(source)
        exercise.equipment == Equipment.CARDIO_MACHINE || Muscle.CARDIO in exercise.primaryMuscles -> ExerciseCategory.CARDIO
        else -> ExerciseCategory.STRENGTH
    }

    private fun localize(
        exercise: CatalogExerciseDto,
        source: SourceExercise,
        inputs: CatalogInputs,
        missingNames: MutableList<String>,
    ): CatalogExerciseDto {
        val translation = inputs.translations[source.name]
        val italianName = translation?.name ?: inputs.names[source.name]
        if (italianName == null) missingNames += source.name
        val withName = exercise.copy(
            name = italianName ?: source.name,
            aliases = translation?.aliases.orEmpty(),
        )
        return if (translation != null && translation.hasContent) {
            withName.copy(
                instructions = translation.instructions,
                keyPoints = translation.keyPoints,
                commonMistakes = translation.commonMistakes,
                contentLocale = CatalogExerciseDto.CONTENT_LOCALE_IT,
            )
        } else {
            withName
        }
    }

    companion object {
        /** URL sorgente di un media, con i segmenti del path codificati. */
        fun mediaUrl(path: String): String {
            val base = URI.create(FreeExerciseDb.MEDIA_BASE_URL)
            val encodedPath = path.split('/').joinToString("/") { segment ->
                URI(null, null, segment, null).rawPath
            }
            return base.resolve(encodedPath).toString()
        }
    }
}
