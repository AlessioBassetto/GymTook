package com.example.sport.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        repository = WorkoutRepository(db.workoutSessionDao(), db.machineSetupDao(), clock)
    }

    private suspend fun logBench(weightKg: Double, reps: Int): Long {
        val sessionId = repository.startFree("Libero")
        val sessionExerciseId = repository.addExercise(sessionId, "barbell_bench_press")
        val firstSet = db.workoutSessionDao().getSets(sessionExerciseId).first()
        repository.updateMetric(firstSet.id, SetMetric.WEIGHT, weightKg)
        repository.updateMetric(firstSet.id, SetMetric.REPS, reps.toDouble())
        repository.setCompleted(firstSet.id, true)
        clock.current = clock.current.plus(Duration.ofMinutes(50))
        repository.finish(sessionId)
        clock.current = clock.current.plus(Duration.ofDays(2))
        return sessionId
    }

    @Test
    fun summary_firstSession_hasNoRecords_butCountsVolume() = runTest {
        val sessionId = logBench(100.0, 5)

        val summary = repository.summary(sessionId)!!
        assertEquals(1, summary.completedSets)
        assertEquals(500.0, summary.totalVolumeKg, 0.0)
        assertEquals("Le serie non spuntate sono eliminate alla chiusura", 1, summary.exercises.single().completedSets)
        assertTrue("Senza storico non c'è un record da battere", summary.records.isEmpty())
        assertEquals(Duration.ofMinutes(50), Duration.between(summary.startedAt, summary.endedAt))
    }

    @Test
    fun summary_heavierLift_isARecordAgainstPreviousSessions() = runTest {
        logBench(100.0, 5)
        val second = logBench(105.0, 3)

        val record = repository.summary(second)!!.records.single()
        assertEquals("barbell_bench_press", record.exerciseId)
        assertEquals(105.0, record.bestSet!!.weightKg!!, 0.0)
        assertEquals(100.0, record.previousBestKg!!, 0.0)

        val third = logBench(95.0, 8)
        assertFalse(repository.summary(third)!!.exercises.single().isWeightRecord)
    }

    @Test
    fun startingAgain_returnsTheSessionAlreadyInProgress() = runTest {
        val first = repository.startFree("Libero")
        assertEquals(first, repository.startFree("Un altro"))
    }

    @Test
    fun metrics_areConvertedToStorageUnits() = runTest {
        val sessionId = repository.startFree("Cardio")
        val sessionExerciseId = repository.addExercise(sessionId, "treadmill_running")
        val set = db.workoutSessionDao().getSets(sessionExerciseId).first()

        repository.updateMetric(set.id, SetMetric.DISTANCE_KM, 2.5)
        repository.updateMetric(set.id, SetMetric.DURATION_MINUTES, 12.0)

        val stored = db.workoutSessionDao().getSet(set.id)!!
        assertEquals(2500.0, stored.distanceMeters!!, 0.0)
        assertEquals(720, stored.durationSeconds)
        assertEquals(2.5, SetMetric.DISTANCE_KM.read(stored)!!, 0.0)
        assertEquals(12.0, SetMetric.DURATION_MINUTES.read(stored)!!, 0.0)
        assertEquals(
            listOf(SetMetric.DISTANCE_KM, SetMetric.DURATION_MINUTES),
            SetMetric.forTracking(db.exerciseDao().getById("treadmill_running")!!.trackingType, withRpe = false),
        )
    }
}
