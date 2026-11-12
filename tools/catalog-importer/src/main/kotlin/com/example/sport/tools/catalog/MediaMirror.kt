package com.example.sport.tools.catalog

import com.example.sport.core.model.catalog.isSafeRelativeMediaPath
import kotlinx.serialization.builtins.ListSerializer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Copia tutti i media del manifest in una cartella locale con la stessa struttura di path,
 * pronta da caricare sulla CDN di produzione (es. `rclone copy`, `aws s3 sync`).
 * Idempotente: i file già presenti vengono saltati.
 */
class MediaMirror(private val http: HttpClient = FreeExerciseDb.defaultHttpClient()) {

    data class Result(val downloaded: Int, val skipped: Int, val failed: List<String>)

    fun mirror(manifestFile: Path, outDir: Path): Result {
        val entries = ImporterJson.decodeFromString(
            ListSerializer(MediaManifestEntry.serializer()),
            Files.readString(manifestFile, Charsets.UTF_8),
        )
        var downloaded = 0
        var skipped = 0
        val failed = mutableListOf<String>()
        val root = outDir.toAbsolutePath().normalize()
        entries.forEachIndexed { index, entry ->
            check(isSafeRelativeMediaPath(entry.path)) { "Path non sicuro nel manifest: ${entry.path}" }
            val target = root.resolve(entry.path).normalize()
            check(target.startsWith(root)) { "Path fuori dalla cartella di destinazione: ${entry.path}" }
            if (Files.exists(target)) {
                skipped++
                return@forEachIndexed
            }
            val response = http.send(
                HttpRequest.newBuilder(URI.create(entry.url)).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
            if (response.statusCode() == 200) {
                Files.createDirectories(target.parent)
                Files.write(target, response.body())
                downloaded++
            } else {
                failed += "${entry.path} (HTTP ${response.statusCode()})"
            }
            if ((index + 1) % 100 == 0) println("  ${index + 1}/${entries.size}")
        }
        return Result(downloaded, skipped, failed)
    }
}
