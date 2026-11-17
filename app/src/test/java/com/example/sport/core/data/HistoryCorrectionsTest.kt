package com.example.sport.core.data

import com.example.sport.core.database.calorieRepository
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.database.dao.HistoryRemoval
import com.example.sport.core.database.expectThrows
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.SessionStatus
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * Integrità dello storico: sessioni nulle scartate, correzioni a posteriori, reset di un solo
 * esercizio. Record e grafici leggono dal database, quindi qui si verifica che seguano da soli.
 */
@RunWith(AndroidJUnit4::class)
class HistoryCorrectionsTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var workout: WorkoutRepository
    private lateinit var progress: ProgressRepository
    private val sessionDao get() = db.workoutSessionDao()

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        workout = WorkoutRepository(db.workoutSessionDao(), db.machineSetupDao(), db.calorieRepository(clock), clock)
        progress = ProgressRepository(db.workoutSessionDao(), db.bodyWeightDao(), db.progressDao(), db.exerciseDao(), db.calorieRepository(clock), clock)
    }

    private suspend fun log(dayOffset: Long, vararg exercises: Pair<String, List<LoggedSet>>) =
        logSession(workout, sessionDao, clock, dayOffset, *exercises)

    private suspend fun benchRecords() = progress.observeExerciseRecords(BENCH).first()

    private suspend fun history() = progress.observeSessionHistory().first()

    // ------------------------------------------------------------------ Sessioni nulle

    @Test
    fun verdict_asksOnlyWhenThereIsSomethingToDecide() {
        assertEquals(SessionVerdict.EMPTY, sessionVerdict(validCompletedSets = 0, elapsed = Duration.ofMinutes(45)))
        assertEquals(SessionVerdict.TOO_SHORT, sessionVerdict(validCompletedSets = 1, elapsed = Duration.ofSeconds(59)))
        assertEquals(SessionVerdict.VALID, sessionVerdict(validCompletedSets = 1, elapsed = Duration.ofSeconds(60)))
    }

    @Test
    fun finishingWithoutACompletedSet_discardsTheSession() = runTest {
        val sessionId = workout.startFree("Avviata per errore")
        workout.addExercise(sessionId, BENCH)

        assertFalse("Sessione nulla: non va salvata", workout.finish(sessionId))

        assertNull(sessionDao.getSession(sessionId))
        assertTrue(history().isEmpty())
        assertNull("Si può avviare subito un altro allenamento", sessionDao.getActiveSessionId())
    }

    @Test
    fun finishing_dropsTickedSetsWithoutData_andKeepsTheRealOnes() = runTest {
        val sessionId = workout.startFree("Petto")
        val benchId = workout.addExercise(sessionId, BENCH)
        val squatId = workout.addExercise(sessionId, SQUAT)
        val bench = sessionDao.getSets(benchId)
        workout.updateMetric(bench[0].id, SetMetric.WEIGHT, 100.0)
        workout.updateMetric(bench[0].id, SetMetric.REPS, 5.0)
        workout.setCompleted(bench[0].id, true)
        // Spuntata con il solo peso: senza ripetizioni non descrive un lavoro svolto.
        workout.updateMetric(bench[1].id, SetMetric.WEIGHT, 100.0)
        workout.setCompleted(bench[1].id, true)
        sessionDao.getSets(squatId).forEach { workout.setCompleted(it.id, true) }

        assertTrue(workout.finish(sessionId))

        val saved = sessionDao.getSessionWithExercises(sessionId)!!
        assertEquals(SessionStatus.COMPLETED, saved.session.status)
        assertEquals("Lo squat senza dati esce dall'allenamento", listOf(BENCH), saved.orderedExercises.map { it.exercise.id })
        assertEquals(listOf<Int?>(5), saved.orderedExercises.single().sets.map { it.reps })
        assertEquals(1, history().single().workingSetCount)
    }

    @Test
    fun discardedSessions_arePurgedWhenTheNextOneStarts() = runTest {
        val discarded = workout.startFree("Annullata")
        workout.addExercise(discarded, BENCH)
        workout.discard(discarded)
        assertEquals(SessionStatus.DISCARDED, sessionDao.getSession(discarded)!!.status)

        val next = workout.startFree("Nuova")

        assertNull("Nessun dato fantasma resta nel database", sessionDao.getSession(discarded))
        assertEquals(next, sessionDao.getActiveSessionId())
    }

    // ------------------------------------------------------------------ Correzioni a posteriori

    @Test
    fun fixingATypo_recalculatesRecordsAndVolume() = runTest {
        log(0, BENCH to listOf(LoggedSet(100.0, 5)))
        val wrong = log(7, BENCH to listOf(LoggedSet(1000.0, 5), LoggedSet(90.0, 8)))
        val typo = benchRecords()!!.heaviestSet
        assertEquals(1000.0, typo.valueKg, 0.0)

        progress.updateSet(typo.setId!!, SetValues(weightKg = 100.0, reps = 5))

        assertEquals(100.0, benchRecords()!!.heaviestSet.valueKg, 0.0)
        assertEquals("100 × 5 + 90 × 8", 1220.0, history().first { it.id == wrong }.totalVolumeKg, 0.0)
        expectThrows<IllegalArgumentException> { progress.updateSet(typo.setId!!, SetValues(weightKg = 100.0, reps = 0)) }
        expectThrows<IllegalArgumentException> { progress.updateSet(typo.setId!!, SetValues(weightKg = 5000.0, reps = 5)) }
    }

    @Test
    fun deletingSets_prunesEmptyExercisesAndSessions() = runTest {
        val sessionId = log(
            0,
            BENCH to listOf(LoggedSet(100.0, 5), LoggedSet(100.0, 4)),
            SQUAT to listOf(LoggedSet(120.0, 5)),
        )
        val logged = sessionDao.getSessionWithExercises(sessionId)!!.orderedExercises
        val benchSets = logged.first().orderedSets
        val squatSet = logged.last().orderedSets.single()

        assertEquals(HistoryRemoval.SET, progress.deleteSet(benchSets[0].id))
        assertEquals("Gli indici restano compatti", listOf(0), sessionDao.getSets(logged.first().sessionExercise.id).map { it.setIndex })

        assertEquals(HistoryRemoval.EXERCISE, progress.deleteSet(squatSet.id))
        assertEquals(listOf(BENCH), sessionDao.getSessionWithExercises(sessionId)!!.orderedExercises.map { it.exercise.id })

        assertEquals(HistoryRemoval.SESSION, progress.deleteSet(benchSets[1].id))
        assertNull(sessionDao.getSession(sessionId))
        assertEquals(HistoryRemoval.NOTHING, progress.deleteSet(benchSets[1].id))
        assertNull(benchRecords())
        assertTrue(history().isEmpty())
    }

    @Test
    fun deletingAVolumeRecord_removesTheExerciseFromThatWorkoutOnly() = runTest {
        val normal = log(0, BENCH to listOf(LoggedSet(100.0, 5)))
        val inflated = log(7, BENCH to listOf(LoggedSet(100.0, 50)), SQUAT to listOf(LoggedSet(120.0, 5)))
        val volume = benchRecords()!!.bestSessionVolume
        assertEquals(inflated, volume.sessionId)
        assertNull("Il volume riguarda l'intera sessione, non una serie", volume.setId)

        assertEquals(HistoryRemoval.EXERCISE, progress.deleteExerciseFromSession(inflated, BENCH))

        assertEquals(normal, benchRecords()!!.bestSessionVolume.sessionId)
        val kept = sessionDao.getSessionExercises(inflated).single()
        assertEquals(SQUAT, kept.exerciseId)
        assertEquals(0, kept.position)
        assertEquals(HistoryRemoval.NOTHING, progress.deleteExerciseFromSession(inflated, BENCH))
    }

    // ------------------------------------------------------------------ Reset di un esercizio

    @Test
    fun resettingAnExercise_leavesEverythingElseUntouched() = runTest {
        val (_, dayId) = createRoutineWithDay("Petto", listOf(BENCH to 3))
        val setupId = workout.saveSetupFromLibrary(BENCH, null, "Predefinita", listOf(MachineSetupParam("Panca", "foro 3")), null)
        val onlyBench = log(0, BENCH to listOf(LoggedSet(100.0, 5)))
        val mixed = log(
            7,
            SQUAT to listOf(LoggedSet(120.0, 5)),
            BENCH to listOf(LoggedSet(105.0, 5)),
            PULL_UP to listOf(LoggedSet(null, 8)),
        )
        val open = workout.startFree("In corso")
        workout.addExercise(open, BENCH)

        assertEquals("Due allenamenti contenevano la panca", 2, progress.resetExerciseHistory(BENCH))

        assertNull(benchRecords())
        assertFalse(progress.observeHasHistory(BENCH).first())
        assertNull("L'allenamento di sola panca esce dallo storico", sessionDao.getSession(onlyBench))
        val kept = sessionDao.getSessionWithExercises(mixed)!!.orderedExercises
        assertEquals(listOf(SQUAT, PULL_UP), kept.map { it.exercise.id })
        assertEquals(listOf(0, 1), kept.map { it.sessionExercise.position })
        assertEquals(120.0, progress.observeExerciseRecords(SQUAT).first()!!.heaviestSet.valueKg, 0.0)
        // Scheda, settaggi e allenamento in corso non vengono toccati.
        assertEquals(BENCH, sessionDao.findRoutineExercises(dayId).single().exerciseId)
        assertNotNull(db.machineSetupDao().getById(setupId))
        assertEquals(BENCH, sessionDao.getSessionExercises(open).single().exerciseId)
        assertEquals("Non resta nulla da azzerare", 0, progress.resetExerciseHistory(BENCH))
    }

    private companion object {
        const val BENCH = "barbell_bench_press"
        const val SQUAT = "barbell_back_squat"
        const val PULL_UP = "pull_up"
    }
}
