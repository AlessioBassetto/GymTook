package com.example.sport.tools.catalog

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** JSON leggibile e stabile (diff puliti nei commit): campi di default e null omessi. */
@OptIn(ExperimentalSerializationApi::class)
val ImporterJson: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

/** Tutti i path usati dal tool, relativi alla radice del repository. */
class ImporterPaths(val root: Path) {
    private val toolDir: Path = root.resolve("tools/catalog-importer")

    val dataDir: Path = toolDir.resolve("data")

    /** Esercizi scritti a mano in italiano (contenuto completo e revisionato). */
    val curatedFile: Path = dataDir.resolve("curated_it.json")

    /** id esercizio curato → nome dell'esercizio sorgente da cui prendere le immagini. */
    val curatedLinksFile: Path = dataDir.resolve("curated_links.json")

    /** Nome sorgente (inglese) → nome italiano, revisionato a mano. */
    val namesFile: Path = dataDir.resolve("names_it.json")

    /** Nome sorgente → traduzione completa (istruzioni, punti chiave, errori) generata con Claude. */
    val translationsFile: Path = dataDir.resolve("translations_it.json")

    val sourceCacheDir: Path = toolDir.resolve("build/sources")
    val mediaManifestFile: Path = toolDir.resolve("build/media-manifest.json")
    val outputCatalog: Path = root.resolve("app/src/main/assets/catalog/exercises.json")
}
