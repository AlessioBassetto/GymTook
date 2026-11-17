package com.example.sport.tools.catalog

import java.nio.file.Files
import java.nio.file.Path

/**
 * Formato testuale delle traduzioni, comodo da scrivere e da rivedere a mano (un esercizio per
 * blocco, una riga per campo, voci separate da " | "):
 *
 * ```
 * ## Barbell Bench Press - Medium Grip
 * N: Panca piana con bilanciere            (facoltativo: senza, resta il nome di names_it.json)
 * A: bench press | distensioni su panca     (facoltativo: sinonimi per la ricerca)
 * I: Sdraiati sulla panca… | Stacca il bilanciere… | Scendi al petto… | Spingi…
 * K: Scapole addotte | Piedi a terra
 * E: Rimbalzare sul petto | Sollevare i glutei
 * ```
 *
 * `##` apre il blocco con il nome inglese del dataset (la chiave di translations_it.json). Le righe
 * vuote e quelle che iniziano con `#` (una sola) sono commenti.
 */
object TranslationText {

    /** Testo → traduzioni per nome sorgente, marcate con [translatedBy] e `reviewed = false`. */
    fun parse(text: String, translatedBy: String): Map<String, TranslationEntry> {
        val entries = linkedMapOf<String, TranslationEntry>()
        var key: String? = null
        var fields = mutableMapOf<Char, String>()

        fun flush(lineNumber: Int) {
            val currentKey = key ?: return
            val instructions = split(fields['I'])
            require(instructions.isNotEmpty()) { "Riga $lineNumber: \"$currentKey\" senza istruzioni (I:)" }
            require(currentKey !in entries) { "Riga $lineNumber: \"$currentKey\" ripetuto" }
            entries[currentKey] = TranslationEntry(
                name = fields['N']?.trim()?.takeIf { it.isNotEmpty() },
                aliases = split(fields['A']),
                instructions = instructions,
                keyPoints = split(fields['K']),
                commonMistakes = split(fields['E']),
                translatedBy = translatedBy,
                reviewed = false,
            )
        }

        text.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("## ") -> {
                    flush(index + 1)
                    key = line.removePrefix("## ").trim()
                    fields = mutableMapOf()
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                line.length >= 2 && line[1] == ':' && line[0] in FIELDS -> {
                    requireNotNull(key) { "Riga ${index + 1}: campo fuori da un blocco ##" }
                    require(line[0] !in fields) { "Riga ${index + 1}: campo ${line[0]} ripetuto per \"$key\"" }
                    fields[line[0]] = line.substring(2).trim()
                }
                else -> throw IllegalArgumentException("Riga ${index + 1} non riconosciuta: $line")
            }
        }
        flush(text.lines().size)
        return entries
    }

    private fun split(value: String?): List<String> =
        value.orEmpty().split(" | ").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Unisce i file di testo alle traduzioni esistenti (le nuove prevalgono) e le salva.
     * @return quante voci sono state aggiunte o aggiornate.
     */
    fun merge(paths: ImporterPaths, files: List<Path>, translatedBy: String, knownSources: Set<String>): Int {
        val existing = CatalogInputs.load(paths).translations.toMutableMap()
        var count = 0
        files.forEach { file ->
            val parsed = parse(Files.readString(file), translatedBy)
            val unknown = parsed.keys - knownSources
            require(unknown.isEmpty()) { "${file.fileName}: esercizi non presenti nel dataset: $unknown" }
            existing.putAll(parsed)
            count += parsed.size
        }
        CatalogInputs.saveTranslations(paths, existing)
        return count
    }

    /** Esercizi importati ancora senza traduzione, nel formato di partenza (nome, attrezzo, istruzioni EN). */
    fun pending(paths: ImporterPaths, sources: List<SourceExercise>): String {
        val inputs = CatalogInputs.load(paths)
        val linked = inputs.curatedLinks.values.toSet()
        return sources
            .filter(SourceFilter::accept)
            .filter { it.name !in linked && inputs.translations[it.name]?.hasContent != true }
            .sortedBy { it.name }
            .joinToString("\n") { source ->
                buildString {
                    appendLine("## ${source.name}")
                    appendLine("# it: ${inputs.names[source.name].orEmpty()} · ${source.equipment ?: "body only"} · ${source.primaryMuscles.joinToString()}")
                    source.instructions.filter { it.isNotBlank() }.forEach { appendLine("# en: ${it.trim()}") }
                }
            }
    }

    private val FIELDS = setOf('N', 'A', 'I', 'K', 'E')
}
