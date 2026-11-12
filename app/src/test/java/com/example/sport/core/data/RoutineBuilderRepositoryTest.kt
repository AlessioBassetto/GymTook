package com.example.sport.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.database.entity.RoutineExerciseEntity
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
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class RoutineBuilderRepositoryTest : DatabaseTest() {

    private lateinit var builder: RoutineBuilderRepository

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        builder = RoutineBuilderRepository(db, TestClock(T0))
    }

    private suspend fun routine(id: Long) = builder.observeRoutine(id).first()!!

    private suspend fun exerciseIds(dayId: Long) = db.routineDao().getRoutineExercises(dayId).map { it.exerciseId }

    private suspend fun groups(dayId: Long) = db.routineDao().getRoutineExercises(dayId).map { it.supersetGroup }

    private suspend fun dayWith(vararg exerciseIds: String): Pair<Long, Long> {
        val routineId = builder.createRoutine("Petto e braccia", "Giorno A")
        val dayId = routine(routineId).orderedDays.single().day.id
        exerciseIds.forEach { builder.addExercise(dayId, it) }
        return routineId to dayId
    }

    @Test
    fun firstRoutine_becomesActive_withAnEmptyFirstDay() = runTest {
        val first = builder.createRoutine("  La mia scheda ", "Giorno A")
        val second = builder.createRoutine("Seconda", "Giorno A")

        val created = routine(first)
        assertEquals("La mia scheda", created.routine.name)
        assertTrue("Senza altre schede attive, la prima si attiva da sola", created.routine.isActive)
        assertEquals(listOf("Giorno A"), created.orderedDays.map { it.day.name })
        assertFalse(routine(second).routine.isActive)
    }

    @Test
    fun addExercise_usesSensibleTargets_andNoRepsForTimedExercises() = runTest {
        val (_, dayId) = dayWith("barbell_bench_press", "treadmill_running")
        val (bench, treadmill) = db.routineDao().getRoutineExercises(dayId)
        assertEquals(3, bench.targetSets)
        assertEquals(8 to 12, bench.targetRepsMin to bench.targetRepsMax)
        assertNull("Recupero predefinito dell'esercizio", bench.restSeconds)
        assertNull(treadmill.targetRepsMin)
        assertNull(treadmill.targetRepsMax)
    }

    @Test
    fun updateTargets_straightensRanges_andClamps() = runTest {
        val (_, dayId) = dayWith("barbell_bench_press")
        val id = db.routineDao().getRoutineExercises(dayId).single().id

        builder.updateTargets(id, ExerciseTargets(sets = 40, repsMin = 12, repsMax = 6, restSeconds = 5_000))

        val updated = db.routineDao().getRoutineExercise(id)!!
        assertEquals(ExerciseTargets.MAX_SETS, updated.targetSets)
        assertEquals(6 to 12, updated.targetRepsMin to updated.targetRepsMax)
        assertEquals(ExerciseTargets.MAX_REST_SECONDS, updated.restSeconds)
    }

    @Test
    fun supersets_linkMerge_andDissolveWhenSeparated() = runTest {
        val (_, dayId) = dayWith("barbell_bench_press", "barbell_curl", "lying_triceps_press", "leg_extension")
        val (bench, curl, triceps, _) = db.routineDao().getRoutineExercises(dayId)

        builder.linkWithNext(bench.id)
        assertEquals(listOf(1, 1, null, null), groups(dayId))
        builder.linkWithNext(curl.id)
        assertEquals("Il terzo entra nella stessa superserie (circuito)", listOf(1, 1, 1, null), groups(dayId))

        builder.unlinkSuperset(curl.id)
        assertEquals("Togliere il centrale lascia due esercizi isolati", listOf(null, null, null, null), groups(dayId))

        builder.linkWithNext(triceps.id)
        builder.linkWithNext(bench.id)
        assertEquals("Gruppi rinumerati in ordine", listOf(1, 1, 2, 2), groups(dayId))
    }

    @Test
    fun dragging_outOfASuperset_separatesIt() = runTest {
        val (_, dayId) = dayWith("barbell_bench_press", "barbell_curl", "leg_extension")
        val bench = db.routineDao().getRoutineExercises(dayId).first()
        builder.linkWithNext(bench.id)

        builder.moveExercise(dayId, fromIndex = 1, toIndex = 2)

        assertEquals(listOf("barbell_bench_press", "leg_extension", "barbell_curl"), exerciseIds(dayId))
        assertEquals(listOf(null, null, null), groups(dayId))
        assertEquals(listOf(0, 1, 2), db.routineDao().getRoutineExercises(dayId).map { it.position })
    }

    @Test
    fun moveToAnotherDay_appendsAtTheEnd_andCompactsTheSource() = runTest {
        val (routineId, dayA) = dayWith("barbell_bench_press", "barbell_curl", "leg_extension")
        val dayB = builder.addDay(routineId, "Giorno B")
        builder.addExercise(dayB, "leg_press")
        val curl = db.routineDao().getRoutineExercises(dayA)[1]
        builder.linkWithNext(curl.id)

        builder.moveExerciseToDay(curl.id, dayB)

        assertEquals(listOf("barbell_bench_press", "leg_extension"), exerciseIds(dayA))
        assertEquals(listOf(0, 1), db.routineDao().getRoutineExercises(dayA).map { it.position })
        assertEquals(listOf(null, null), groups(dayA))
        assertEquals(listOf("leg_press", "barbell_curl"), exerciseIds(dayB))
        assertEquals(listOf(null, null), groups(dayB))
    }

    @Test
    fun deleteDay_compactsDays_andFreesTheWeekdays() = runTest {
        val (routineId, dayA) = dayWith("barbell_bench_press")
        val dayB = builder.addDay(routineId, "Giorno B")
        val dayC = builder.addDay(routineId, "Giorno C")
        builder.assignWeekday(routineId, DayOfWeek.MONDAY, dayA)
        builder.assignWeekday(routineId, DayOfWeek.WEDNESDAY, dayB)
        builder.assignWeekday(routineId, DayOfWeek.FRIDAY, dayC)

        builder.deleteDay(dayB)

        val updated = routine(routineId)
        assertEquals(listOf("Giorno A" to 0, "Giorno C" to 1), updated.orderedDays.map { it.day.name to it.day.position })
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), updated.dayByWeekday.keys)

        builder.assignWeekday(routineId, DayOfWeek.MONDAY, null)
        assertEquals("Lunedì torna riposo", setOf(DayOfWeek.FRIDAY), routine(routineId).dayByWeekday.keys)
    }

    @Test
    fun duplicate_asDeload_reducesSets_andKeepsEverythingElse() = runTest {
        val (routineId, dayId) = dayWith("barbell_bench_press", "barbell_curl")
        val (bench, curl) = db.routineDao().getRoutineExercises(dayId)
        builder.updateTargets(bench.id, ExerciseTargets(sets = 4, repsMin = 6, repsMax = 8, restSeconds = 180))
        builder.updateTargets(curl.id, ExerciseTargets(sets = 5, repsMin = 10, repsMax = 12, restSeconds = null))
        builder.linkWithNext(bench.id)
        builder.assignWeekday(routineId, DayOfWeek.TUESDAY, dayId)

        val copyId = builder.duplicateRoutine(routineId, "Petto e braccia · Scarico", deload = true)

        val copy = routine(copyId)
        assertEquals(routineId, copy.routine.parentRoutineId)
        assertFalse(copy.routine.isActive)
        val copied = db.routineDao().getRoutineExercises(copy.orderedDays.single().day.id)
        assertEquals(listOf(2, 3), copied.map { it.targetSets })
        assertEquals(listOf(6, 10), copied.map { it.targetRepsMin })
        assertEquals(listOf(1, 1), copied.map { it.supersetGroup })
        assertEquals(setOf(DayOfWeek.TUESDAY), copy.dayByWeekday.keys)
        assertEquals("L'originale non cambia", listOf(4, 5), db.routineDao().getRoutineExercises(dayId).map { it.targetSets })

        val plainCopy = builder.duplicateRoutine(routineId, "Copia", deload = false)
        assertEquals(listOf(4, 5), db.routineDao().getRoutineExercises(routine(plainCopy).orderedDays.single().day.id).map { it.targetSets })
    }

    @Test
    fun deleteRoutine_keepsTheWorkoutHistory() = runTest {
        val (routineId, dayId) = dayWith("barbell_bench_press")
        val sessionId = db.workoutSessionDao().startSessionFromRoutineDay(dayId, T0)
        db.workoutSessionDao().finishSession(sessionId, T0.plusSeconds(1_800), discardIncompleteSets = false)

        builder.deleteRoutine(routineId)

        assertNull(db.routineDao().getRoutine(routineId))
        val session = db.workoutSessionDao().getSessionWithExercises(sessionId)
        assertNotNull("La sessione resta nello storico", session)
        assertNull(session!!.session.routineId)
        assertEquals("barbell_bench_press", session.exercises.single().exercise.id)
    }

    @Test
    fun deloadSets_roundsToAtLeastOne() {
        assertEquals(listOf(1, 1, 2, 2, 3, 4), listOf(1, 2, 3, 4, 5, 6).map(RoutineBuilderRepository::deloadSets))
    }

    @Test
    fun normalize_keepsOnlyContiguousRuns() {
        fun item(id: Long, group: Int?) = RoutineExerciseEntity(id = id, routineDayId = 1, exerciseId = "e$id", position = 0, targetSets = 3, supersetGroup = group)
        val normalized = normalizeSupersets(listOf(item(1, 7), item(2, 7), item(3, null), item(4, 7), item(5, 3), item(6, 3)))
        assertEquals(listOf(1, 1, null, null, 2, 2), normalized.map { it.supersetGroup })
    }
}
