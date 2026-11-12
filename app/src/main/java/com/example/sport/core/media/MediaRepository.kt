package com.example.sport.core.media

import com.example.sport.core.database.dao.MediaDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Esito di un download "Disponibile offline". */
data class MediaPrefetchResult(
    val total: Int,
    val downloaded: Int,
    val alreadyAvailable: Int,
    val failed: List<String>,
) {
    val isComplete: Boolean get() = failed.isEmpty()
}

/**
 * Punto unico di accesso ai media per la UI e per i download in background.
 *
 * Strategia (APK leggero, funzionamento offline):
 * 1. file nell'archivio offline → usato direttamente (palestra senza campo);
 * 2. altrimenti URL CDN → Coil lo scarica e lo tiene nella cache su disco (cache progressiva);
 * 3. senza rete né copia locale → null: la UI mostra la mappa muscolare al posto dell'immagine.
 */
@Singleton
class MediaRepository @Inject constructor(
    private val config: MediaConfig,
    private val offlineStore: OfflineMediaStore,
    private val mediaDao: MediaDao,
) {

    /** Modello da passare a Coil (`AsyncImage(model = ...)`): [File] locale, URL o null. */
    fun resolve(path: String): Any? =
        if (offlineStore.isAvailable(path)) offlineStore.fileFor(path) else config.urlFor(path)

    fun localFileOrNull(path: String): File? = path.takeIf(offlineStore::isAvailable)?.let(offlineStore::fileFor)

    /** Scarica tutti i media degli esercizi di una scheda. */
    suspend fun prefetchRoutine(routineId: Long): MediaPrefetchResult = prefetch(mediaDao.getMediaPathsForRoutine(routineId))

    suspend fun prefetch(paths: List<String>): MediaPrefetchResult {
        var downloaded = 0
        var alreadyAvailable = 0
        val failed = mutableListOf<String>()
        paths.distinct().forEach { path ->
            when {
                offlineStore.isAvailable(path) -> alreadyAvailable++
                runCatching { offlineStore.download(path) }.getOrDefault(false) -> downloaded++
                else -> failed += path
            }
        }
        return MediaPrefetchResult(paths.distinct().size, downloaded, alreadyAvailable, failed)
    }

    /** Libera spazio: rimuove i media offline che nessuna scheda non archiviata usa più. */
    suspend fun pruneUnused(): Int = offlineStore.prune(mediaDao.getMediaPathsOfLiveRoutines().toSet())

    fun offlineSizeBytes(): Long = offlineStore.totalSizeBytes()
}
