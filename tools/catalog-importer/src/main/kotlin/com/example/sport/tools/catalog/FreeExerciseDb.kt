package com.example.sport.tools.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/** Voce del dataset free-exercise-db (schema ufficiale del progetto). */
@Serializable
data class SourceExercise(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val category: String? = null,
    val images: List<String> = emptyList(),
)

/**
 * Sorgente base del catalogo: https://github.com/yuhonas/free-exercise-db
 * Licenza The Unlicense (pubblico dominio), come il dataset da cui deriva (wrkout/exercises.json):
 * testi e immagini sono riutilizzabili anche commercialmente, senza obbligo di attribuzione.
 *
 * Il commit è fissato e il file verificato con SHA-256: build riproducibili e nessuna modifica
 * a monte può entrare nel catalogo senza un aggiornamento esplicito di queste costanti.
 */
object FreeExerciseDb {
    const val SOURCE_NAME = "free-exercise-db"
    const val LICENSE_SPDX = "Unlicense"
    const val COMMIT = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5"
    const val DATASET_SHA256 = "5bb747e3fc658f095a60dcbf6d53c96627acdcc6ffb6fffde86f7e26995d40bf"

    const val DATASET_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/$COMMIT/dist/exercises.json"

    /** Base URL per i media in sviluppo; in produzione i file vanno copiati sulla propria CDN. */
    const val MEDIA_BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/$COMMIT/"

    /** I path delle immagini nel dataset sono relativi a questa cartella del repository. */
    const val MEDIA_PREFIX = "exercises/"

    fun origin(source: SourceExercise): String = "$SOURCE_NAME:${source.id}"

    fun mediaPath(image: String): String = MEDIA_PREFIX + image

    /** Carica il dataset dalla cache locale, scaricandolo (e verificandolo) se assente. */
    fun load(cacheDir: Path, http: HttpClient = defaultHttpClient()): List<SourceExercise> {
        val file = cacheDir.resolve("free-exercise-db-$COMMIT.json")
        val bytes = if (Files.exists(file) && sha256(Files.readAllBytes(file)) == DATASET_SHA256) {
            Files.readAllBytes(file)
        } else {
            println("Scarico $DATASET_URL")
            val response = http.send(
                HttpRequest.newBuilder(URI.create(DATASET_URL)).timeout(Duration.ofMinutes(2)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
            check(response.statusCode() == 200) { "Download dataset fallito: HTTP ${response.statusCode()}" }
            val body = response.body()
            val digest = sha256(body)
            check(digest == DATASET_SHA256) {
                "SHA-256 inatteso per il dataset ($digest): il file a monte non corrisponde al commit fissato"
            }
            Files.createDirectories(cacheDir)
            Files.write(file, body)
            body
        }
        return ImporterJson.decodeFromString(ListSerializer(SourceExercise.serializer()), bytes.decodeToString())
    }

    fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
