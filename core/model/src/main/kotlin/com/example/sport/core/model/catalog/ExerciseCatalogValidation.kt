package com.example.sport.core.model.catalog

private val CATALOG_ID_PATTERN = Regex("^[a-z0-9]+(_[a-z0-9]+)*$")
private val SUPPORTED_LOCALES = setOf(CatalogExerciseDto.CONTENT_LOCALE_IT, CatalogExerciseDto.CONTENT_LOCALE_EN)
private const val CUSTOM_ID_PREFIX = "custom_"
private const val MAX_REST_SECONDS = 900

/** Prefisso riservato agli esercizi creati dall'utente. */
fun newCustomExerciseId(uuid: String): String = CUSTOM_ID_PREFIX + uuid.lowercase().replace("-", "")

/**
 * Un path media è valido se relativo e confinato alla base URL: niente schema, niente path
 * assoluti, niente segmenti `.`/`..` (che permetterebbero di uscire dalla cartella media locale).
 */
fun isSafeRelativeMediaPath(path: String): Boolean {
    if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains("://")) return false
    return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

/** Elenco completo dei problemi del catalogo (vuoto = valido). */
fun ExerciseCatalogDto.problems(): List<String> = buildList {
    if (version < 1) add("version deve essere >= 1 (trovato $version)")
    if (exercises.isEmpty()) add("il catalogo non contiene esercizi")
    val duplicates = exercises.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isNotEmpty()) add("id duplicati: $duplicates")
    exercises.forEach { exercise ->
        val id = exercise.id
        if (!CATALOG_ID_PATTERN.matches(id)) add("$id: id non valido (solo a-z, 0-9, underscore)")
        if (id.startsWith(CUSTOM_ID_PREFIX)) add("$id: il prefisso '$CUSTOM_ID_PREFIX' è riservato")
        if (exercise.name.isBlank()) add("$id: nome vuoto")
        if (exercise.primaryMuscles.isEmpty()) add("$id: nessun muscolo primario")
        val allMuscles = exercise.primaryMuscles + exercise.secondaryMuscles
        if (allMuscles.size != allMuscles.toSet().size) add("$id: muscoli ripetuti tra primari/secondari")
        exercise.defaultRestSeconds?.let { rest ->
            if (rest !in 0..MAX_REST_SECONDS) add("$id: defaultRestSeconds fuori range ($rest)")
        }
        if (exercise.setupParams.any { it.isBlank() }) add("$id: setupParams contiene voci vuote")
        if (exercise.contentLocale !in SUPPORTED_LOCALES) add("$id: contentLocale non supportato (${exercise.contentLocale})")
        exercise.media.forEach { media ->
            if (!isSafeRelativeMediaPath(media.path)) add("$id: path media non sicuro '${media.path}'")
        }
        val mediaPaths = exercise.media.map { it.path }
        if (mediaPaths.size != mediaPaths.toSet().size) add("$id: media duplicati")
    }
}

/** Lancia [IllegalStateException] con l'elenco completo dei problemi se il catalogo non è coerente. */
fun ExerciseCatalogDto.validate() {
    val problems = problems()
    check(problems.isEmpty()) { "Catalogo esercizi non valido:\n" + problems.joinToString("\n") }
}
