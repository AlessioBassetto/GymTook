package com.example.sport.tools.catalog

import com.anthropic.models.beta.messages.BetaOutputConfig
import com.example.sport.core.model.catalog.ExerciseCatalogDto
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = """
Uso: catalog-importer <comando> [opzioni]

Comandi:
  build --version N               Genera app/src/main/assets/catalog/exercises.json e il manifest media
  translate [--limit N]           Traduce con Claude gli esercizi senza contenuto italiano
            [--batch-size 8] [--parallel 4] [--model claude-opus-5-5] [--effort medium]
            Richiede ANTHROPIC_API_KEY (o un profilo `ant auth login`).
  mirror-media --out DIR          Scarica tutti i media del manifest in DIR, pronti per la CDN
  pending-translations --out FILE Elenca gli esercizi ancora senza traduzione (nome, attrezzo, istruzioni EN)
  merge-translations --in FILE[,FILE...] [--by NOME]
                                  Unisce traduzioni scritte nel formato testuale (vedi TranslationText)

Opzioni comuni:
  --root DIR                      Radice del repository (default: cartella corrente)
"""

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: fail("Comando mancante")
    val options = parseOptions(args.drop(1))
    val paths = ImporterPaths(Path.of(options["root"] ?: ".").toAbsolutePath().normalize())

    when (command) {
        "build" -> {
            val version = options["version"]?.toIntOrNull() ?: fail("--version N obbligatorio")
            val sources = FreeExerciseDb.load(paths.sourceCacheDir)
            val result = CatalogBuilder().build(version, sources, CatalogInputs.load(paths))
            writeAtomically(paths.outputCatalog, ImporterJson.encodeToString(ExerciseCatalogDto.serializer(), result.catalog) + "\n")
            writeAtomically(paths.mediaManifestFile, ImporterJson.encodeToString(ListSerializer(MediaManifestEntry.serializer()), result.mediaManifest) + "\n")
            println(result.report.render())
            println("Catalogo v$version: ${result.catalog.exercises.size} esercizi → ${paths.outputCatalog}")
            println("Manifest media: ${result.mediaManifest.size} file → ${paths.mediaManifestFile}")
        }
        "translate" -> {
            val translator = ClaudeTranslator(
                model = options["model"] ?: ClaudeTranslator.DEFAULT_MODEL,
                effort = BetaOutputConfig.Effort.of(options["effort"] ?: "medium"),
            )
            val count = translator.translatePending(
                paths = paths,
                sources = FreeExerciseDb.load(paths.sourceCacheDir),
                limit = options["limit"]?.toIntOrNull() ?: Int.MAX_VALUE,
                batchSize = options["batch-size"]?.toIntOrNull() ?: 8,
                parallelism = options["parallel"]?.toIntOrNull() ?: 4,
            )
            println("Tradotti $count esercizi → ${paths.translationsFile}. Esegui `build` per aggiornare il catalogo.")
        }
        "pending-translations" -> {
            val out = options["out"] ?: fail("--out FILE obbligatorio")
            val text = TranslationText.pending(paths, FreeExerciseDb.load(paths.sourceCacheDir))
            writeAtomically(Path.of(out), text)
            println("Esercizi da tradurre: ${text.lines().count { it.startsWith("## ") }} → $out")
        }
        "merge-translations" -> {
            val files = (options["in"] ?: fail("--in FILE obbligatorio")).split(',').map { Path.of(it.trim()) }
            val sources = FreeExerciseDb.load(paths.sourceCacheDir).map { it.name }.toSet()
            val count = TranslationText.merge(paths, files, options["by"] ?: "manuale", sources)
            println("Traduzioni unite: $count → ${paths.translationsFile}. Esegui `build` per aggiornare il catalogo.")
        }
        "mirror-media" -> {
            val out = options["out"] ?: fail("--out DIR obbligatorio")
            val result = MediaMirror().mirror(paths.mediaManifestFile, Path.of(out))
            println("Scaricati ${result.downloaded}, già presenti ${result.skipped}, falliti ${result.failed.size}")
            result.failed.forEach { System.err.println("  $it") }
            if (result.failed.isNotEmpty()) exitProcess(1)
        }
        else -> fail("Comando sconosciuto: $command")
    }
}

internal fun parseOptions(args: List<String>): Map<String, String> {
    val options = mutableMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        require(key.startsWith("--")) { "Opzione non valida: $key" }
        val value = args.getOrNull(index + 1)
        require(value != null && !value.startsWith("--")) { "Valore mancante per $key" }
        options[key.removePrefix("--")] = value
        index += 2
    }
    return options
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    System.err.println(USAGE)
    exitProcess(2)
}
