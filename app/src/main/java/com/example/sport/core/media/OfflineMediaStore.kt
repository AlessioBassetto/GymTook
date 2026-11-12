package com.example.sport.core.media

import com.example.sport.core.model.catalog.isSafeRelativeMediaPath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Archivio permanente dei media "disponibili offline" (es. tutti i video/immagini della scheda
 * attiva), separato dalla cache LRU delle immagini: qui i file non vengono mai rimossi
 * automaticamente, solo da [prune] quando nessuna scheda li usa più.
 */
class OfflineMediaStore(
    private val rootDir: File,
    private val config: MediaConfig,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val canonicalRoot: File = rootDir.canonicalFile

    /** File locale di un path relativo, garantito dentro [rootDir] (nessun path traversal). */
    fun fileFor(path: String): File {
        require(isSafeRelativeMediaPath(path)) { "Path media non sicuro: $path" }
        val file = File(canonicalRoot, path).canonicalFile
        require(file.path.startsWith(canonicalRoot.path + File.separator)) { "Path fuori dall'archivio media: $path" }
        return file
    }

    fun isAvailable(path: String): Boolean = runCatching { fileFor(path).isFile }.getOrDefault(false)

    /**
     * Scarica il file se non è già presente. Scrittura su file temporaneo + rename: un download
     * interrotto (app chiusa, rete persa) non lascia mai un file corrotto.
     *
     * @return true se il file è disponibile al termine.
     */
    suspend fun download(path: String): Boolean = withContext(ioDispatcher) {
        val target = fileFor(path)
        if (target.isFile) return@withContext true
        val url = config.urlFor(path) ?: return@withContext false
        val parent = checkNotNull(target.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Impossibile creare ${parent.path}")
        val temp = File(parent, "${target.name}.part")
        try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                temp.outputStream().use { output -> response.body.byteStream().copyTo(output) }
            }
            if (!temp.renameTo(target)) throw IOException("Impossibile salvare ${target.path}")
            true
        } finally {
            temp.delete()
        }
    }

    /** Elimina i file non più richiesti. @return numero di file eliminati. */
    suspend fun prune(keep: Set<String>): Int = withContext(ioDispatcher) {
        val keepFiles = keep.mapNotNull { runCatching { fileFor(it) }.getOrNull() }.toSet()
        var deleted = 0
        canonicalRoot.walkBottomUp().forEach { file ->
            when {
                file == canonicalRoot -> Unit
                file.isFile && file !in keepFiles -> if (file.delete()) deleted++
                file.isDirectory && file.list().isNullOrEmpty() -> file.delete()
            }
        }
        deleted
    }

    fun totalSizeBytes(): Long = canonicalRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
