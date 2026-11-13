package com.example.sport.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.dao.ActiveSessionExistsException
import com.example.sport.core.model.SessionStatus
import com.example.sport.core.model.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSessionDaoTest : DatabaseTest() {

    private val dao get() = db.workoutSessionDao()
    private var routineId: Long = 0
    private var dayId: Long = 0

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        val ids = createRoutineWithDay(
            "Push A",
            listOf("barbell_bench_press" to 3, "machine_chest_press" to 3, "cable_triceps_pushdown" to 2),
        )
        routineId = ids.first
        dayId = ids.second
    }

    @Test
    fun startFromRoutineDay_createsPlannedStructure() = runTest {
        val sessionId = dao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val session = dao.observeActiveSession().first()!!

        assertEquals(sessionId, session.session.id)
        assertEquals("Push A", session.session.title)
        assertEquals(routineId, session.session.routineId)
        assertEquals(
            listOf("barbell_bench_press", "machine_chest_press", "cable_triceps_pushdown"),
            session.orderedExercises.map { it.sessionExercise.exerciseId },
        )
        assertEquals(listOf(3, 3, 2), session.orderedExercises.map { it.sets.size })
        assertEquals("Recupero dal catalogo", 180, session.orderedExercises[0].sessionExercise.restSeconds)
    }

    @Test
    fun onlyOneActiveSession_isAllowed() = runTest {
        val active = dao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val error = expectThrows<ActiveSessionExistsException> {
            dao.startEmptySession("Libera", startedAt = hoursAfterT0(1))
        }
        assertEquals(active, error.activeSessionId)
    }

    @Test
    fun addSet_copiesLastValues_removeSet_compactsIndexes() = runTest {
        val sessionId = dao.startEmptySession("Libera", startedAt = T0)
        val sessionExerciseId = dao.addExerciseToSession(sessionId, "dumbbell_lateral_raise", setCount = 1)
        val firstSet = dao.getSets(sessionExerciseId).single()
        dao.updateSet(firstSet.copy(weightKg = 10.0, reps = 15))

        dao.addSet(sessionExerciseId)
        dao.addSet(sessionExerciseId)
        val sets = dao.getSets(sessionExerciseId)
        assertEquals(listOf(0, 1, 2), sets.map { it.setIndex })
        assertTrue(sets.all { it.weightKg == 10.0 && it.reps == 15 })

        dao.removeSet(sets[1].id)
        assertEquals(listOf(0, 1), dao.getSets(sessionExerciseId).map { it.setIndex })
    }

    @Test
    fun replaceExercise_withoutCompletedSets_replacesInPlace() = runTest {
        val sessionId = dao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val chestPress = dao.getSessionExercises(sessionId)[1]

        val resultId = dao.replaceExercise(chestPress.id, "incline_dumbbell_press")

        assertEquals(chestPress.id, resultId)
        val exercises = dao.getSessionExercises(sessionId)
        assertEquals(3, exercises.size)
        assertEquals("incline_dumbbell_press", exercises[1].exerciseId)
        assertEquals("machine_chest_press", exercises[1].replacedExerciseId)
        assertEquals(listOf(0, 1, 2), dao.getSets(resultId).map { it.setIndex })
    }

    @Test
    fun replaceExercise_afterSomeCompletedSets_splitsAndKeepsHistory() = runTest {
        val sessionId = dao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val chestPress = dao.getSessionExercises(sessionId)[1]
        completeSet(dao.getSets(chestPress.id)[0], weightKg = 70.0, reps = 10, at = T0)

        val newId = dao.replaceExercise(chestPress.id, "incline_dumbbell_press")

        val exercises = dao.getSessionExercises(sessionId)
        assertEquals(
            listOf("barbell_bench_press", "machine_chest_press", "incline_dumbbell_press", "cable_triceps_pushdown"),
            exercises.map { it.exerciseId },
        )
        assertEquals(listOf(0, 1, 2, 3), exercises.map { it.position })
        val original = dao.getSets(chestPress.id)
        assertEquals("La serie fatta resta sulla chest press", 1, original.size)
        assertEquals(70.0, original.single().weightKg!!, 0.0)
        val moved = dao.getSets(newId)
        assertEquals(listOf(0, 1), moved.map { it.setIndex })
        assertTrue(moved.none { it.isCompleted })
        assertEquals("machine_chest_press", exercises[2].replacedExerciseId)
    }

    @Test
    fun finishSession_discardsIncompleteWork_andFeedsHistory() = runTest {
        val sessionId = dao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val bench = dao.getSessionExercises(sessionId)[0]
        val benchSets = dao.getSets(bench.id)
        dao.updateSet(benchSets[0].copy(setType = SetType.WARMUP, weightKg = 60.0, reps = 10, isCompleted = true, completedAt = T0))
        completeSet(benchSets[1], weightKg = 100.0, reps = 5, at = T0)
        completeSet(benchSets[2], weightKg = 100.0, reps = 5, at = T0)

        dao.finishSession(sessionId, endedAt = hoursAfterT0(1), discardIncompleteSets = true)

        val finished = dao.getSessionWithExercises(sessionId)!!
        assertEquals(SessionStatus.COMPLETED, finished.session.status)
        assertEquals(hoursAfterT0(1), finished.session.endedAt)
        assertEquals("Esercizi senza serie fatte rimossi", listOf("barbell_bench_press"), finished.exercises.map { it.exercise.id })
        assertNull(dao.getActiveSessionId())

        val summary = dao.observeCompletedSessionSummaries(limit = 10).first().single()
        assertEquals(1, summary.exerciseCount)
        assertEquals("Il riscaldamento non conta", 2, summary.workingSetCount)
        assertEquals(1000.0, summary.totalVolumeKg, 0.001)

        assertEquals(listOf(100.0, 100.0), dao.getPreviousWorkingSets("barbell_bench_press").map { it.weightKg })
    }

    @Test
    fun progressQuery_computesMaxVolumeAndEstimatedOneRepMax() = runTest {
        val first = dao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val firstSets = dao.getSets(dao.getSessionExercises(first)[0].id)
        completeSet(firstSets[0], weightKg = 100.0, reps = 5, at = T0)
        completeSet(firstSets[1], weightKg = 100.0, reps = 5, at = T0)
        dao.finishSession(first, endedAt = hoursAfterT0(1), discardIncompleteSets = true)

        val second = dao.startSessionFromRoutineDay(dayId, startedAt = hoursAfterT0(72))
        val secondSets = dao.getSets(dao.getSessionExercises(second)[0].id)
        completeSet(secondSets[0], weightKg = 110.0, reps = 1, at = hoursAfterT0(72))
        completeSet(secondSets[1], weightKg = 90.0, reps = 15, at = hoursAfterT0(72))
        dao.finishSession(second, endedAt = hoursAfterT0(73), discardIncompleteSets = true)

        val points = db.progressDao().observeExerciseProgress("barbell_bench_press", from = T0).first()
        assertEquals(2, points.size)

        val (p1, p2) = points
        assertEquals(100.0, p1.maxWeightKg, 0.001)
        assertEquals(1000.0, p1.totalVolumeKg, 0.001)
        assertEquals(10, p1.totalReps)
        assertEquals(100.0 * (1 + 5 / 30.0), p1.bestEstimatedOneRepMaxKg!!, 0.001)

        assertEquals(110.0, p2.maxWeightKg, 0.001)
        assertEquals(110.0 + 90.0 * 15, p2.totalVolumeKg, 0.001)
        assertEquals("1 rep = carico reale; 15 reps escluse dalla stima", 110.0, p2.bestEstimatedOneRepMaxKg!!, 0.001)

        assertEquals(
            listOf("barbell_bench_press"),
            db.progressDao().observeExercisesWithHistory().first().map { it.id },
        )
    }

    @Test
    fun discardAndRestore_andCascadeDelete() = runTest {
        val sessionId = dao.startSessionFromRoutineDay(dayId, startedAt = T0)
        val sessionExerciseIds = dao.getSessionExercises(sessionId).map { it.id }

        dao.discardSession(sessionId, endedAt = hoursAfterT0(1))
        assertNull(dao.observeActiveSession().first())
        dao.restoreDiscardedSession(sessionId)
        assertEquals(sessionId, dao.getActiveSessionId())

        dao.deleteSession(sessionId)
        assertTrue(dao.getSessionExercises(sessionId).isEmpty())
        assertTrue("Le serie vengono cancellate a cascata", sessionExerciseIds.all { dao.getSets(it).isEmpty() })
    }

    @Test
    fun deletingRoutine_keepsSessionHistory() = runTest {
        val sessionId = dao.startSessionFromRoutineDay(dayId, startedAt = T0)
        // Almeno una serie valida: una sessione senza dati verrebbe scartata come nulla.
        completeSet(dao.getSets(dao.getSessionExercises(sessionId).first().id).first(), weightKg = 60.0, reps = 8, at = T0)
        dao.finishSession(sessionId, endedAt = hoursAfterT0(1), discardIncompleteSets = false)

        db.routineDao().deleteRoutine(db.routineDao().getRoutine(routineId)!!)

        val session = dao.getSession(sessionId)!!
        assertNull(session.routineId)
        assertNull(session.routineDayId)
        assertEquals("Push A", session.title)
        assertEquals(3, dao.getSessionExercises(sessionId).size)
    }
}
