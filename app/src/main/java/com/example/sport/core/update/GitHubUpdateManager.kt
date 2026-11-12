package com.example.sport.core.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.sport.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Release pubblicata più recente dell'app, con l'APK da scaricare. */
data class AvailableUpdate(
    /** Versione senza il prefisso del tag: "1.2.0". */
    val versionName: String,
    /** Note di rilascio in testo semplice (dal `body` della release). */
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    /** Dimensione dichiarata da GitHub; 0 se assente. */
    val apkSizeBytes: Long,
)

/** Esito del controllo: solo [Available] interessa l'utente, gli altri restano silenziosi all'avvio. */
sealed interface UpdateCheck {
    data class Available(val update: AvailableUpdate) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Offline : UpdateCheck

    /** Timeout, errore HTTP, risposta illeggibile, limite di richieste di GitHub. */
    data object Failed : UpdateCheck

    /** Aggiornamento in-app non configurato in questa build. */
    data object Disabled : UpdateCheck
}

enum class DownloadFailure {
    /** Connessione caduta o server non raggiungibile. */
    NETWORK,

    /** Il file scaricato non è un aggiornamento di questa app (incompleto, altro package, versione non più recente). */
    INVALID_FILE,

    /** Spazio insufficiente sul dispositivo. */
    NO_SPACE,
}

sealed interface DownloadState {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data class Completed(val apk: File) : DownloadState
    data class Failed(val reason: DownloadFailure) : DownloadState
}

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

/**
 * Aggiornamento in-app dalle GitHub Releases: interroga `releases/latest`, confronta il tag con la
 * versione installata, scarica l'APK nella cache privata e prepara l'installazione di sistema.
 *
 * Non mostra nulla e non decide nulla: senza rete o in caso di errore restituisce un esito che
 * all'avvio viene ignorato (l'app è offline-first); download e installazione partono solo su
 * richiesta esplicita dell'utente (vedi [UpdateController]).
 */
@Singleton
class GitHubUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: UpdateConfig,
    httpClient: OkHttpClient,
    private val connectivity: ConnectivityChecker,
    private val apkInspector: ApkInspector,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Il controllo all'avvio non deve mai far aspettare: pochi secondi, poi si rinuncia. */
    private val apiClient = httpClient.newBuilder()
        .connectTimeout(API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(API_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val downloadClient = httpClient

    private val json = Json { ignoreUnknownKeys = true }

    private val updatesDir: File get() = File(context.cacheDir, UPDATES_DIRECTORY)

    val isEnabled: Boolean get() = config.isEnabled

    val currentVersionName: String get() = config.currentVersionName

    /** Cerca una release più recente della versione installata. Non lancia eccezioni. */
    suspend fun check(): UpdateCheck {
        if (!config.isEnabled) return UpdateCheck.Disabled
        val installed = AppVersion.parse(config.currentVersionName) ?: return UpdateCheck.Failed
        if (!connectivity.isOnline()) return UpdateCheck.Offline
        return withContext(ioDispatcher) {
            try {
                val request = Request.Builder()
                    .url(config.latestReleaseUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                    .build()
                apiClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext UpdateCheck.Failed
                    val release = json.decodeFromString(GitHubRelease.serializer(), response.body.string())
                    evaluate(release, installed)
                }
            } catch (_: IOException) {
                UpdateCheck.Failed
            } catch (_: SerializationException) {
                UpdateCheck.Failed
            } catch (_: IllegalArgumentException) {
                UpdateCheck.Failed
            }
        }
    }

    private fun evaluate(release: GitHubRelease, installed: AppVersion): UpdateCheck {
        if (release.draft || release.prerelease) return UpdateCheck.UpToDate
        val latest = AppVersion.parse(release.tagName) ?: return UpdateCheck.UpToDate
        if (latest <= installed) return UpdateCheck.UpToDate
        // Una release senza APK (o con un indirizzo non cifrato) non è installabile: come se non ci fosse.
        val asset = release.assets.firstOrNull { it.name.endsWith(APK_EXTENSION, ignoreCase = true) && config.acceptsDownloadUrl(it.downloadUrl) }
            ?: return UpdateCheck.UpToDate
        return UpdateCheck.Available(
            AvailableUpdate(
                versionName = release.tagName.trim().removePrefix("v").removePrefix("V"),
                notes = plainReleaseNotes(release.body.orEmpty()),
                apkName = asset.name,
                apkUrl = asset.downloadUrl,
                apkSizeBytes = asset.size.coerceAtLeast(0),
            ),
        )
    }

    /**
     * Scarica l'APK in `cache/updates` emettendo l'avanzamento. Il file diventa disponibile solo
     * se è completo ed è davvero un aggiornamento di questa app (stesso package, versionCode
     * maggiore); altrimenti viene cancellato. Annullare la raccolta interrompe il download.
     */
    fun download(update: AvailableUpdate): Flow<DownloadState> = flow {
        val directory = updatesDir
        clearDownloads()
        if (!directory.mkdirs() && !directory.isDirectory) {
            emit(DownloadState.Failed(DownloadFailure.NO_SPACE))
            return@flow
        }
        val target = File(directory, TARGET_FILE_NAME)
        val partial = File(directory, "$TARGET_FILE_NAME$PARTIAL_SUFFIX")
        val call = downloadClient.newCall(Request.Builder().url(update.apkUrl).build())
        val outcome = coroutineScope {
            // La lettura dalla rete è bloccante: se il download viene annullato, questa coroutine
            // chiude la connessione e sblocca subito la lettura, anche con il server fermo.
            val canceller = launch {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                transfer(call, update, directory, partial, target) { emit(it) }
            } finally {
                canceller.cancel()
            }
        }
        emit(outcome)
    }.flowOn(ioDispatcher)

    /** Copia la risposta su disco e la verifica; restituisce l'esito finale ([DownloadState.Completed] o [DownloadState.Failed]). */
    private suspend fun transfer(
        call: Call,
        update: AvailableUpdate,
        directory: File,
        partial: File,
        target: File,
        onProgress: suspend (DownloadState.Progress) -> Unit,
    ): DownloadState {
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return DownloadState.Failed(DownloadFailure.NETWORK)
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: update.apkSizeBytes
                if (total > 0 && directory.usableSpace < total) return DownloadState.Failed(DownloadFailure.NO_SPACE)
                onProgress(DownloadState.Progress(0, total))
                var downloaded = 0L
                var lastReported = 0L
                val buffer = Buffer()
                body.source().use { source ->
                    partial.sink().buffer().use { sink ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer, BUFFER_BYTES)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            downloaded += read
                            if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                                lastReported = downloaded
                                onProgress(DownloadState.Progress(downloaded, total))
                            }
                        }
                    }
                }
                onProgress(DownloadState.Progress(downloaded, total.coerceAtLeast(downloaded)))
            }
            if (!isValidUpdate(partial, update) || !partial.renameTo(target)) return DownloadState.Failed(DownloadFailure.INVALID_FILE)
            return DownloadState.Completed(target)
        } catch (_: IOException) {
            // Connessione chiusa dall'annullamento: non è un errore di rete da mostrare.
            currentCoroutineContext().ensureActive()
            return DownloadState.Failed(
                if (directory.usableSpace < MIN_FREE_BYTES) DownloadFailure.NO_SPACE else DownloadFailure.NETWORK,
            )
        } finally {
            partial.delete()
        }
    }

    private fun isValidUpdate(apk: File, update: AvailableUpdate): Boolean {
        if (update.apkSizeBytes > 0 && apk.length() != update.apkSizeBytes) return false
        val info = apkInspector.inspect(apk) ?: return false
        return info.packageName == context.packageName && info.versionCode > config.currentVersionCode
    }

    /**
     * Intent dell'installer di sistema per l'APK scaricato. Il file è esposto tramite FileProvider
     * con un permesso di sola lettura limitato a questa richiesta; Android chiede sempre conferma
     * all'utente e, alla prima volta, l'autorizzazione "Installa app sconosciute".
     */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Elimina gli APK scaricati (aggiornamento già installato, rinviato o interrotto). */
    fun clearDownloads() {
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val UPDATES_DIRECTORY = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val TARGET_FILE_NAME = "update.apk"
        private const val PARTIAL_SUFFIX = ".part"
        private const val APK_EXTENSION = ".apk"
        private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val API_CONNECT_TIMEOUT_SECONDS = 5L
        private const val API_READ_TIMEOUT_SECONDS = 8L
        private const val API_CALL_TIMEOUT_SECONDS = 12L
        private const val BUFFER_BYTES = 64L * 1024
        private const val PROGRESS_STEP_BYTES = 256L * 1024
        private const val MIN_FREE_BYTES = 1L * 1024 * 1024
    }
}
