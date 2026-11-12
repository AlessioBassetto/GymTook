package com.example.sport.core.update

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Chiamata a GitHub, confronto delle versioni e download dell'APK contro un server locale. */
@RunWith(AndroidJUnit4::class)
class GitHubUpdateManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private val updatesDir get() = File(context.cacheDir, GitHubUpdateManager.UPDATES_DIRECTORY)

    private var online = true
    private var releaseResponse: () -> MockResponse = { MockResponse.Builder().code(404).build() }
    private var apkResponse: () -> MockResponse = { MockResponse.Builder().code(404).build() }
    private var inspected: ApkInfo? = null
    private val apkBytes = ByteArray(700_000) { (it % 251).toByte() }

    @Before
    fun setUp() {
        updatesDir.deleteRecursively()
        inspected = ApkInfo(context.packageName, versionCode = INSTALLED_CODE + 1)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/repos/owner/app/releases/latest" -> releaseResponse()
                "/download/Sport-1.3.0.apk" -> apkResponse()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        apkResponse = { MockResponse.Builder().body(Buffer().write(apkBytes)).build() }
    }

    @After
    fun tearDown() {
        server.close()
        updatesDir.deleteRecursively()
    }

    private fun manager(repository: String = "owner/app", installed: String = "1.2.0") = GitHubUpdateManager(
        context = context,
        config = UpdateConfig(repository, server.url("/").toString(), installed, INSTALLED_CODE),
        httpClient = OkHttpClient(),
        connectivity = { online },
        apkInspector = { inspected },
        ioDispatcher = Dispatchers.IO,
    )

    private fun release(
        tag: String,
        body: String = "## Novità\n- Aggiornamenti dall'app",
        assets: String = """[{"name":"Sport-1.3.0.apk","size":${apkBytes.size},"browser_download_url":"${server.url("/download/Sport-1.3.0.apk")}"}]""",
        extra: String = "",
    ): () -> MockResponse = {
        val escapedBody = body.replace("\n", "\\n")
        MockResponse.Builder()
            .body("""{"tag_name":"$tag","name":"Versione","body":"$escapedBody","html_url":"https://example.com","assets":$assets$extra}""")
            .build()
    }

    private val update get() = AvailableUpdate("1.3.0", "", "Sport-1.3.0.apk", server.url("/download/Sport-1.3.0.apk").toString(), apkBytes.size.toLong())

    // ------------------------------------------------------------------ Controllo

    @Test
    fun newerRelease_isOffered_withNotesAndApk() = runTest {
        releaseResponse = release("v1.3.0")

        val result = manager().check() as UpdateCheck.Available

        assertEquals("1.3.0", result.update.versionName)
        assertEquals("Novità\n• Aggiornamenti dall'app", result.update.notes)
        assertEquals("Sport-1.3.0.apk", result.update.apkName)
        assertEquals(apkBytes.size.toLong(), result.update.apkSizeBytes)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("application/vnd.github+json", request.headers["Accept"])
    }

    @Test
    fun sameOlderOrUnusableReleases_meanUpToDate() = runTest {
        releaseResponse = release("v1.2.0")
        assertEquals(UpdateCheck.UpToDate, manager().check())
        releaseResponse = release("v1.1.9")
        assertEquals(UpdateCheck.UpToDate, manager().check())
        releaseResponse = release("v1.3.0")
        assertEquals("La build di debug 1.3.0-debug è già la 1.3.0", UpdateCheck.UpToDate, manager(installed = "1.3.0-debug").check())
        releaseResponse = release("nightly")
        assertEquals(UpdateCheck.UpToDate, manager().check())
        releaseResponse = release("v1.3.0", assets = """[{"name":"sorgenti.zip","size":10,"browser_download_url":"${server.url("/x.zip")}"}]""")
        assertEquals("Senza APK non c'è nulla da installare", UpdateCheck.UpToDate, manager().check())
        releaseResponse = release("v1.3.0", extra = ""","prerelease":true""")
        assertEquals(UpdateCheck.UpToDate, manager().check())
    }

    @Test
    fun offline_doesNotEvenCallTheServer() = runTest {
        releaseResponse = release("v1.3.0")
        online = false

        assertEquals(UpdateCheck.Offline, manager().check())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun serverErrors_badAnswers_andDroppedConnections_failQuietly() = runTest {
        releaseResponse = { MockResponse.Builder().code(404).build() }
        assertEquals(UpdateCheck.Failed, manager().check())
        releaseResponse = { MockResponse.Builder().code(403).body("""{"message":"API rate limit exceeded"}""").build() }
        assertEquals(UpdateCheck.Failed, manager().check())
        releaseResponse = { MockResponse.Builder().body("<html>captive portal</html>").build() }
        assertEquals(UpdateCheck.Failed, manager().check())
        releaseResponse = { MockResponse.Builder().body("""{"name":"senza tag"}""").build() }
        assertEquals(UpdateCheck.Failed, manager().check())

        val unreachable = manager()
        server.close()
        assertEquals("Connessione rifiutata: nessuna eccezione verso l'app", UpdateCheck.Failed, unreachable.check())
    }

    @Test
    fun withoutRepository_theUpdaterIsOff() = runTest {
        val disabled = manager(repository = "")
        assertFalse(disabled.isEnabled)
        assertEquals(UpdateCheck.Disabled, disabled.check())
        assertEquals(0, server.requestCount)
    }

    // ------------------------------------------------------------------ Download

    @Test
    fun download_reportsProgress_andLeavesTheVerifiedApkInTheCache() = runTest {
        val steps = manager().download(update).toList()

        val progress = steps.filterIsInstance<DownloadState.Progress>()
        assertEquals(0L, progress.first().downloadedBytes)
        assertEquals(apkBytes.size.toLong(), progress.first().totalBytes)
        assertEquals(apkBytes.size.toLong(), progress.last().downloadedBytes)
        assertTrue("Avanzamento crescente", progress.zipWithNext().all { (a, b) -> a.downloadedBytes <= b.downloadedBytes })
        val apk = (steps.last() as DownloadState.Completed).apk
        assertEquals(File(updatesDir, "update.apk"), apk)
        assertArrayEquals(apkBytes, apk.readBytes())
        assertEquals("Nessun file parziale", listOf("update.apk"), updatesDir.list()!!.toList())
    }

    @Test
    fun download_rejectsIncompleteOrForeignFiles() = runTest {
        // Dimensione diversa da quella dichiarata dalla release.
        val truncated = update.copy(apkSizeBytes = apkBytes.size + 1L)
        assertEquals(DownloadState.Failed(DownloadFailure.INVALID_FILE), manager().download(truncated).toList().last())

        inspected = ApkInfo("un.altro.package", INSTALLED_CODE + 1)
        assertEquals(DownloadState.Failed(DownloadFailure.INVALID_FILE), manager().download(update).toList().last())

        inspected = ApkInfo(context.packageName, INSTALLED_CODE)
        assertEquals("Stesso versionCode: Android rifiuterebbe l'installazione", DownloadState.Failed(DownloadFailure.INVALID_FILE), manager().download(update).toList().last())

        inspected = null
        assertEquals("Non è un APK", DownloadState.Failed(DownloadFailure.INVALID_FILE), manager().download(update).toList().last())
        assertTrue("I file scartati non restano in cache", updatesDir.list().orEmpty().isEmpty())
    }

    @Test
    fun download_networkErrors_areReported_andCancellationCleansUp() = runTest {
        apkResponse = { MockResponse.Builder().code(404).build() }
        assertEquals(DownloadState.Failed(DownloadFailure.NETWORK), manager().download(update).toList().last())

        // Download lento, annullato a metà: nessun file resta in cache.
        apkResponse = { MockResponse.Builder().body(Buffer().write(apkBytes)).throttleBody(64 * 1024, 200, TimeUnit.MILLISECONDS).build() }
        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.IO) {
            manager().download(update).collect { step ->
                if (step is DownloadState.Progress && step.downloadedBytes > 0) started.complete(Unit)
            }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue("Annullato: nessun file resta in cache", updatesDir.list().orEmpty().isEmpty())
    }

    @Test
    fun installIntent_sharesTheApkReadOnly_throughTheFileProvider() {
        // FileProvider confronta i percorsi con "/": sulla JVM di Windows non trova la cartella.
        // Lì la condivisione dell'APK si verifica sul dispositivo (vedi docs/aggiornamenti.md).
        assumeTrue(File.separatorChar == '/')
        val apk = File(updatesDir.apply { mkdirs() }, "update.apk").apply { writeBytes(apkBytes) }

        val intent = manager().installIntent(apk)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals("content://${context.packageName}.fileprovider/updates/update.apk", intent.data.toString())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    private companion object {
        const val INSTALLED_CODE = 12L
    }
}
