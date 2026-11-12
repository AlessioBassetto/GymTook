package com.example.sport.core.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.database.dao.CustomExerciseRemoval
import com.example.sport.core.database.query.ExerciseFilter
import com.example.sport.core.media.UserVideoStore
import com.example.sport.core.media.VideoImportResult
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.Mechanic
import com.example.sport.core.model.Muscle
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CustomExerciseRepositoryTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var videoDir: File
    private lateinit var repository: CustomExerciseRepository

    private val pendulum = CustomExerciseDraft(
        name = "  Pendulum squat  ",
        equipment = Equipment.MACHINE,
        trackingType = ExerciseTrackingType.WEIGHT_REPS,
        primaryMuscles = listOf(Muscle.QUADRICEPS, Muscle.GLUTES),
        secondaryMuscles = listOf(Muscle.GLUTES, Muscle.ADDUCTORS),
        mechanic = Mechanic.COMPOUND,
        defaultRestSeconds = 120,
        setupParamHints = listOf("Spalliere", " ", "Spalliere", "Pedana"),
        notes = "Piedi a metà pedana\n\n  Scendi lento  ",
    )

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        videoDir = File(context.filesDir, "test-videos").apply { deleteRecursively() }
        repository = repositoryWith(UserVideoStore(videoDir, context, Dispatchers.IO))
    }

    private fun repositoryWith(store: UserVideoStore) = CustomExerciseRepository(db.exerciseDao(), store, clock)

    @Test
    fun create_normalizesTheDraft_andAppearsInTheLibrary() = runTest {
        val id = repository.create(pendulum)
        assertTrue(id.startsWith("custom_"))

        val stored = db.exerciseDao().getDetails(id)!!
        assertEquals("Pendulum squat", stored.exercise.name)
        assertEquals(Muscle.QUADRICEPS, stored.exercise.primaryMuscle)
        assertEquals(listOf("Spalliere", "Pedana"), stored.exercise.setupParamHints)
        assertTrue(stored.exercise.supportsMachineSetup)
        assertEquals(listOf("Piedi a metà pedana", "Scendi lento"), stored.exercise.instructions)
        assertEquals(ExerciseCategory.STRENGTH, stored.exercise.category)
        assertEquals("Un muscolo principale non è anche secondario", listOf(Muscle.ADDUCTORS), stored.secondaryMuscles)

        val mine = db.exerciseDao().searchExercises(ExerciseFilter(query = "pendulum", onlyCustom = true))
        assertEquals(listOf(id), mine.map { it.id })
    }

    @Test
    fun draft_roundTrips_keepingTheTargetFirst() = runTest {
        val id = repository.create(pendulum.copy(primaryMuscles = listOf(Muscle.GLUTES, Muscle.QUADRICEPS)))
        val draft = repository.getDraft(id)!!
        assertEquals(listOf(Muscle.GLUTES, Muscle.QUADRICEPS), draft.primaryMuscles)
        assertEquals("Piedi a metà pedana\nScendi lento", draft.notes)
        assertNull("Gli esercizi di catalogo non sono modificabili", repository.getDraft("leg_extension"))
    }

    @Test
    fun update_keepsFavoriteAndHistoryReferences() = runTest {
        val id = repository.create(pendulum)
        db.exerciseDao().setFavorite(id, favorite = true, updatedAt = T0)
        clock.current = T0.plusSeconds(60)

        repository.update(id, pendulum.copy(name = "Pendulum squat Panatta", trackingType = ExerciseTrackingType.DISTANCE_DURATION))

        val updated = db.exerciseDao().getById(id)!!
        assertEquals("Pendulum squat Panatta", updated.name)
        assertTrue(updated.isFavorite)
        assertEquals(T0, updated.createdAt)
        assertEquals(T0.plusSeconds(60), updated.updatedAt)
        assertEquals("Distanza e tempo = cardio", ExerciseCategory.CARDIO, updated.category)
    }

    @Test
    fun sameName_isDetectedIgnoringCaseAndAccents() = runTest {
        assertEquals("leg_extension", repository.findSameName("LEG EXTENSION")?.id)
        assertNull(repository.findSameName("Leg extension monopodalica"))
        val id = repository.create(pendulum)
        assertNull("Modificando se stesso non è un doppione", repository.findSameName("pendulum squat", excludeId = id))
    }

    @Test
    fun delete_removesUnusedExercises_withTheirVideo_andHidesUsedOnes() = runTest {
        val unused = repository.create(pendulum)
        val video = repository.importVideo(unused, videoSource("video-a", 2_048))
        assertTrue(video is VideoImportResult.Success)
        assertEquals(1, videoDir.listFiles()!!.size)

        assertEquals(CustomExerciseRemoval.DELETED, repository.delete(unused))
        assertNull(db.exerciseDao().getById(unused))
        assertEquals("Il file del video se ne va con l'esercizio", 0, videoDir.listFiles()!!.size)

        val used = repository.create(pendulum.copy(name = "Hip thrust machine"))
        createRoutineWithDay("Gambe", listOf(used to 3))
        assertEquals(CustomExerciseRemoval.HIDDEN, repository.delete(used))
        assertTrue(db.exerciseDao().getById(used)!!.isHidden)
    }

    @Test
    fun videos_canBeAddedToCatalogExercises_replacedAndRemoved() = runTest {
        val first = repository.importVideo("leg_extension", videoSource("first", 1_000)) as VideoImportResult.Success
        val second = repository.importVideo("leg_extension", videoSource("second", 3_000)) as VideoImportResult.Success

        val stored = db.exerciseDao().getUserVideo("leg_extension")!!
        assertEquals(second.fileName, stored.fileName)
        assertEquals(3_000, stored.sizeBytes)
        assertEquals("Il video sostituito viene cancellato", listOf(second.fileName), videoDir.list()!!.toList())
        assertFalse(File(videoDir, first.fileName).exists())
        assertTrue(repository.videoUri(second.fileName).startsWith("file://"))

        repository.removeVideo("leg_extension")
        assertNull(db.exerciseDao().getUserVideo("leg_extension"))
        assertEquals(0, videoDir.list()!!.size)
    }

    @Test
    fun recordedVideo_isMovedIntoTheStore_andOversizedOnesAreRejected() = runTest {
        val recorded = File(context.cacheDir, "captures/capture.mp4").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(4_096) { 1 })
        }
        val result = repository.adoptRecordedVideo("leg_extension", recorded) as VideoImportResult.Success
        assertFalse("Il file temporaneo non resta in cache", recorded.exists())
        assertEquals(4_096, File(videoDir, result.fileName).length())

        val small = repositoryWith(UserVideoStore(videoDir, context, Dispatchers.IO, maxBytes = 1_000))
        assertEquals(VideoImportResult.TooLarge, small.importVideo("leg_press", videoSource("big", 5_000)))
        assertNull(db.exerciseDao().getUserVideo("leg_press"))
        assertEquals("Nessun file parziale rimasto", 1, videoDir.list()!!.size)
        assertNotNull(db.exerciseDao().getUserVideo("leg_extension"))
    }

    private fun videoSource(name: String, size: Int): Uri {
        val file = File(context.cacheDir, "picked/$name.mp4").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(size) { (it % 7).toByte() })
        }
        return Uri.fromFile(file)
    }
}
