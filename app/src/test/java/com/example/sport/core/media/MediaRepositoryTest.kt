package com.example.sport.core.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.expectThrows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaRepositoryTest : DatabaseTest() {

    private lateinit var server: MockWebServer
    private lateinit var root: File
    private lateinit var store: OfflineMediaStore
    private lateinit var repository: MediaRepository

    @Before
    fun setUpMedia() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if ("missing" in request.url.encodedPath) {
                    MockResponse.Builder().code(404).build()
                } else {
                    MockResponse.Builder().code(200).body("jpeg:${request.url.encodedPath}").build()
                }
        }
        server.start()
        root = File(context.filesDir, "media-test").apply { deleteRecursively() }
        val config = MediaConfig(server.url("/cdn/").toString())
        store = OfflineMediaStore(root, config, OkHttpClient(), Dispatchers.IO)
        repository = MediaRepository(config, store, db.mediaDao())
    }

    @After
    fun tearDownMedia() {
        server.close()
        root.deleteRecursively()
    }

    @Test
    fun mediaConfig_buildsEncodedUrls_andRejectsUnsafePaths() {
        val config = MediaConfig("https://cdn.example.com/media")
        assertEquals(
            "https://cdn.example.com/media/exercises/Hip%20Lift%20(Band)/0.jpg",
            config.urlFor("exercises/Hip Lift (Band)/0.jpg"),
        )
        assertNull(config.urlFor("../secret.txt"))
        assertNull(config.urlFor("https://evil.example/x.jpg"))
        assertFalse(MediaConfig("").isRemoteEnabled)
        assertNull(MediaConfig("").urlFor("exercises/a/0.jpg"))
    }

    @Test
    fun store_confinesFilesToItsDirectory() {
        expectThrows<IllegalArgumentException> { store.fileFor("../outside.jpg") }
        expectThrows<IllegalArgumentException> { store.fileFor("/etc/passwd") }
        assertTrue(store.fileFor("exercises/a/0.jpg").path.startsWith(root.canonicalPath))
    }

    @Test
    fun resolve_usesCdnUrlUntilDownloaded_thenLocalFile() = runTest {
        val path = "exercises/Leg_Extensions/0.jpg"
        assertEquals(server.url("/cdn/exercises/Leg_Extensions/0.jpg").toString(), repository.resolve(path))

        assertTrue(store.download(path))

        val resolved = repository.resolve(path)
        assertTrue(resolved is File)
        assertEquals("jpeg:/cdn/exercises/Leg_Extensions/0.jpg", (resolved as File).readText())
    }

    @Test
    fun failedDownload_leavesNoPartialFile() = runTest {
        val path = "exercises/missing/0.jpg"
        assertFalse(store.download(path))
        assertFalse(store.isAvailable(path))
        assertTrue(store.fileFor(path).parentFile!!.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun prefetchRoutine_downloadsAllMedia_isIdempotent_andPruneFreesSpace() = runTest {
        seedBundledCatalog()
        val (routineId, _) = createRoutineWithDay("Gambe", listOf("leg_extension" to 3, "leg_press" to 3))

        val first = repository.prefetchRoutine(routineId)
        assertEquals(MediaPrefetchResult(total = 4, downloaded = 4, alreadyAvailable = 0, failed = emptyList()), first)
        assertEquals(4, server.requestCount)

        val second = repository.prefetchRoutine(routineId)
        assertEquals(4, second.alreadyAvailable)
        assertEquals("Nessun nuovo download", 4, server.requestCount)
        assertTrue(repository.offlineSizeBytes() > 0)

        // Scheda archiviata: i suoi media non servono più offline.
        val routine = db.routineDao().getRoutine(routineId)!!
        db.routineDao().updateRoutine(routine.copy(isArchived = true))
        assertEquals(4, repository.pruneUnused())
        assertEquals(0L, repository.offlineSizeBytes())
    }
}
