package com.example.sport.core.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.T0
import com.example.sport.core.settings.testDataStore
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Regia dell'aggiornamento: controllo silenzioso all'avvio, consenso, download, rinvio. */
@RunWith(AndroidJUnit4::class)
class UpdateControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = TestClock(T0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = UpdatePreferences(testDataStore(context.filesDir))
    private val updatesDir = File(context.cacheDir, GitHubUpdateManager.UPDATES_DIRECTORY)
    private val apkBytes = ByteArray(300_000) { (it % 127).toByte() }
    private lateinit var server: MockWebServer

    private var online = true
    private var latestTag = "v1.1.0"
    private var apkResponse: () -> MockResponse = { MockResponse.Builder().body(Buffer().write(apkBytes)).build() }

    @Before
    fun setUp() {
        updatesDir.deleteRecursively()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/repos/owner/app/releases/latest" -> MockResponse.Builder()
                    .body(
                        """{"tag_name":"$latestTag","body":"Correzioni","assets":[{"name":"Sport.apk","size":${apkBytes.size},""" +
                            """"browser_download_url":"${server.url("/Sport.apk")}"}]}""",
                    )
                    .build()
                "/Sport.apk" -> apkResponse()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        updatesDir.deleteRecursively()
    }

    private fun controller(repository: String = "owner/app") = UpdateController(
        manager = GitHubUpdateManager(
            context = context,
            config = UpdateConfig(repository, server.url("/").toString(), "1.0.0", 1),
            httpClient = OkHttpClient(),
            connectivity = { online },
            apkInspector = { ApkInfo(context.packageName, versionCode = 2) },
            ioDispatcher = Dispatchers.IO,
        ),
        preferences = preferences,
        clock = clock,
        scope = scope,
    )

    private fun releaseRequests() = server.requestCount

    @Test
    fun startup_isSilentWhenOffline_andOffersTheUpdateOnceOnline() = runTest {
        online = false
        val offline = controller()
        offline.checkOnStartup()!!.join()
        assertEquals("Nessun dialogo, nessun errore", UpdateUiState.Hidden, offline.state.value)
        assertEquals(0, releaseRequests())
        assertNull("Controllo fallito: si riprova al prossimo avvio", preferences.lastAutomaticCheck())

        online = true
        val app = controller()
        app.checkOnStartup()!!.join()
        val offered = app.state.value as UpdateUiState.Available
        assertEquals("1.1.0", offered.update.versionName)
        assertEquals("1.0.0", offered.currentVersion)
        assertEquals("Correzioni", offered.update.notes)
        assertTrue("Nulla viene scaricato senza consenso", updatesDir.list().orEmpty().isEmpty())
    }

    @Test
    fun later_closesTheDialog_andTheNextStartsDoNotAskAgainForTwelveHours() = runTest {
        val app = controller()
        app.checkOnStartup()!!.join()
        assertTrue(app.state.value is UpdateUiState.Available)

        app.onDismiss()
        assertEquals(UpdateUiState.Hidden, app.state.value)
        assertEquals(1, releaseRequests())

        // Riaperta un'ora dopo: né richiesta a GitHub né dialogo.
        clock.current = T0.plus(Duration.ofHours(1))
        val reopened = controller()
        reopened.checkOnStartup()!!.join()
        assertEquals(UpdateUiState.Hidden, reopened.state.value)
        assertEquals(1, releaseRequests())

        clock.current = T0.plus(Duration.ofHours(13))
        val nextDay = controller()
        nextDay.checkOnStartup()!!.join()
        assertTrue(nextDay.state.value is UpdateUiState.Available)
        assertEquals(2, releaseRequests())
    }

    @Test
    fun accept_downloadsWithProgress_andEndsReadyToInstall() = runTest {
        val app = controller()
        app.checkOnStartup()!!.join()

        app.onAccept()
        val ready = app.state.first { it is UpdateUiState.ReadyToInstall } as UpdateUiState.ReadyToInstall
        assertEquals("1.1.0", ready.update.versionName)
        assertEquals(apkBytes.size.toLong(), ready.apk.length())

        // "Più tardi" sull'installazione: il dialogo si chiude, il prossimo avvio ripulisce la cache.
        app.onDismiss()
        assertEquals(UpdateUiState.Hidden, app.state.value)
        clock.current = T0.plus(Duration.ofHours(1))
        controller().checkOnStartup()!!.join()
        assertTrue(updatesDir.list().orEmpty().isEmpty())
    }

    @Test
    fun failedDownload_canBeRetried_andASlowOneCancelled() = runTest {
        val app = controller()
        app.checkOnStartup()!!.join()
        apkResponse = { MockResponse.Builder().code(500).build() }

        app.onAccept()
        val failed = app.state.first { it is UpdateUiState.Failed } as UpdateUiState.Failed
        assertEquals(DownloadFailure.NETWORK, failed.reason)

        apkResponse = { MockResponse.Builder().body(Buffer().write(apkBytes)).throttleBody(32 * 1024, 200, TimeUnit.MILLISECONDS).build() }
        app.onAccept()
        val downloading = app.state.first { it is UpdateUiState.Downloading && it.downloadedBytes > 0 } as UpdateUiState.Downloading
        assertTrue(downloading.fraction!! > 0f && downloading.fraction!! < 1f)

        app.onDismiss()
        assertEquals("Annulla ferma il download", UpdateUiState.Hidden, app.state.value)
    }

    @Test
    fun manualCheck_alwaysAnswers_andIgnoresTheInterval() = runTest {
        val app = controller()
        app.checkOnStartup()!!.join()
        app.onDismiss()

        assertEquals("Nessuna attesa di 12 ore per il controllo manuale", ManualCheckResult.FOUND, app.checkNow())
        assertTrue(app.state.value is UpdateUiState.Available)
        app.onDismiss()

        latestTag = "v1.0.0"
        assertEquals(ManualCheckResult.UP_TO_DATE, app.checkNow())
        online = false
        assertEquals(ManualCheckResult.OFFLINE, app.checkNow())
        online = true
        server.close()
        assertEquals(ManualCheckResult.FAILED, app.checkNow())
        assertEquals(UpdateUiState.Hidden, app.state.value)
    }

    @Test
    fun withoutRepository_nothingEverHappens() = runTest {
        val app = controller(repository = "")
        assertFalse(app.isEnabled)
        assertNull(app.checkOnStartup())
        assertEquals(ManualCheckResult.FAILED, app.checkNow())
        assertEquals(0, releaseRequests())
    }
}
