package com.example.sport.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.sport.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Esito dell'importazione di un video personale. */
sealed interface VideoImportResult {
    data class Success(val fileName: String, val sizeBytes: Long) : VideoImportResult
    data object TooLarge : VideoImportResult
    data object Failed : VideoImportResult
}

/**
 * Video personali degli esercizi (la propria esecuzione, la macchina della propria palestra).
 *
 * Vivono nella memoria privata dell'app, mai sulla CDN: nessun permesso di archiviazione, nessuna
 * copia nei backup cloud (cartella esclusa in `backup_rules.xml`). Il nome file è un UUID generato
 * qui: l'utente non può far scrivere fuori dalla cartella.
 */
@Singleton
class UserVideoStore(
    private val directory: File,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxBytes: Long = MAX_BYTES,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(File(context.filesDir, DIRECTORY), context, ioDispatcher)

    /** Copia il video scelto dalla galleria (Uri `content://` del selettore di sistema). */
    suspend fun import(source: Uri): VideoImportResult = withContext(ioDispatcher) {
        if (declaredSize(source)?.let { it > maxBytes } == true) return@withContext VideoImportResult.TooLarge
        val input = runCatching { context.contentResolver.openInputStream(source) }.getOrNull()
            ?: return@withContext VideoImportResult.Failed
        input.use(::store)
    }

    /**
     * Adotta un video appena registrato dalla fotocamera in una cartella temporanea dell'app: lo
     * sposta (stesso volume, nessuna copia) e lo elimina dalla posizione temporanea.
     */
    suspend fun adopt(recorded: File): VideoImportResult = withContext(ioDispatcher) {
        try {
            val size = recorded.length()
            when {
                !recorded.exists() || size == 0L -> VideoImportResult.Failed
                size > maxBytes -> VideoImportResult.TooLarge
                else -> {
                    directory.mkdirs()
                    val fileName = newFileName()
                    val target = File(directory, fileName)
                    if (recorded.renameTo(target)) {
                        VideoImportResult.Success(fileName, size)
                    } else {
                        recorded.inputStream().use(::store)
                    }
                }
            }
        } finally {
            recorded.delete()
        }
    }

    fun fileFor(fileName: String): File = File(directory, File(fileName).name)

    /** Uri `file://` riproducibile da Media3. */
    fun uriFor(fileName: String): String = Uri.fromFile(fileFor(fileName)).toString()

    suspend fun delete(fileName: String) = withContext(ioDispatcher) {
        fileFor(fileName).delete()
    }

    /** Elimina i file non più referenziati dal database (es. dopo un'importazione interrotta). */
    suspend fun prune(referenced: Set<String>): Int = withContext(ioDispatcher) {
        directory.listFiles().orEmpty().count { file -> file.name !in referenced && file.delete() }
    }

    private fun store(input: InputStream): VideoImportResult {
        directory.mkdirs()
        val fileName = newFileName()
        val target = File(directory, fileName)
        val partial = File(directory, "$fileName.part")
        return try {
            var written = 0L
            partial.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    if (written > maxBytes) return VideoImportResult.TooLarge
                    output.write(buffer, 0, read)
                }
            }
            if (written == 0L || !partial.renameTo(target)) return VideoImportResult.Failed
            VideoImportResult.Success(fileName, written)
        } catch (_: IOException) {
            VideoImportResult.Failed
        } finally {
            partial.delete()
        }
    }

    private fun declaredSize(source: Uri): Long? = runCatching {
        context.contentResolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun newFileName() = "${UUID.randomUUID()}.mp4"

    companion object {
        const val DIRECTORY = "user-media/videos"
        const val MAX_MEGABYTES = 200
        const val MAX_BYTES = MAX_MEGABYTES * 1024L * 1024L
        private const val BUFFER_SIZE = 64 * 1024
    }
}
