package com.example.sport.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.entity.RoutineExerciseEntity
import com.example.sport.core.database.entity.WorkoutRoutineEntity
import com.example.sport.core.model.SplitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class RoutineDaoTest : DatabaseTest() {

    private val dao get() = db.routineDao()
    private var pplId: Long = 0
    private var pushId: Long = 0
    private var pullId: Long = 0
    private var legsId: Long = 0

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        pplId = dao.insertRoutine(
            WorkoutRoutineEntity(name = "PPL", splitType = SplitType.PUSH_PULL_LEGS, createdAt = T0, updatedAt = T0),
        )
        pushId = dao.appendDay(pplId, "Push")
        pullId = dao.appendDay(pplId, "Pull")
        legsId = dao.appendDay(pplId, "Legs")
        listOf("barbell_bench_press", "dumbbell_lateral_raise", "cable_triceps_pushdown").forEach {
            dao.appendExerciseToDay(RoutineExerciseEntity(routineDayId = pushId, exerciseId = it, position = 0, targetSets = 3))
        }
        dao.appendExerciseToDay(RoutineExerciseEntity(routineDayId = pullId, exerciseId = "pull_up", position = 0, targetSets = 4))
        dao.appendExerciseToDay(RoutineExerciseEntity(routineDayId = legsId, exerciseId = "leg_press", position = 0, targetSets = 4))
        dao.assignWeekday(pplId, DayOfWeek.MONDAY, pushId)
        dao.assignWeekday(pplId, DayOfWeek.WEDNESDAY, pullId)
        dao.assignWeekday(pplId, DayOfWeek.FRIDAY, legsId)
        dao.assignWeekday(pplId, DayOfWeek.SATURDAY, pushId)
    }

    @Test
    fun weeklySchedule_resolvesTodaysWorkout_onlyForActiveRoutine() = runTest {
        assertNull("Nessuna scheda attiva", dao.observeActiveRoutineDayFor(DayOfWeek.MONDAY).first())

        dao.setActiveRoutine(pplId, T0)

        assertEquals("Push", dao.observeActiveRoutineDayFor(DayOfWeek.MONDAY).first()!!.day.name)
        assertEquals("Push", dao.observeActiveRoutineDayFor(DayOfWeek.SATURDAY).first()!!.day.name)
        assertNull("Martedì = riposo", dao.observeActiveRoutineDayFor(DayOfWeek.TUESDAY).first())

        val routine = dao.observeActiveRoutine().first()!!
        assertEquals(listOf("Push", "Pull", "Legs"), routine.orderedDays.map { it.day.name })
        assertEquals(
            mapOf(DayOfWeek.MONDAY to "Push", DayOfWeek.WEDNESDAY to "Pull", DayOfWeek.FRIDAY to "Legs", DayOfWeek.SATURDAY to "Push"),
            routine.dayByWeekday.mapValues { it.value.day.name },
        )
    }

    @Test
    fun onlyOneRoutine_isActive() = runTest {
        val otherId = dao.insertRoutine(
            WorkoutRoutineEntity(name = "Upper/Lower", splitType = SplitType.UPPER_LOWER, createdAt = T0, updatedAt = T0),
        )
        dao.setActiveRoutine(pplId, T0)
        dao.setActiveRoutine(otherId, hoursAfterT0(1))

        val routines = dao.observeRoutines().first()
        assertEquals(listOf(otherId), routines.filter { it.isActive }.map { it.id })
    }

    @Test
    fun duplicateRoutine_deepCopiesDaysExercisesAndSchedule() = runTest {
        dao.setActiveRoutine(pplId, T0)
        val copyId = dao.duplicateRoutine(pplId, "PPL – variante deload", hoursAfterT0(1))

        val original = dao.getRoutineWithDays(pplId)!!
        val copy = dao.getRoutineWithDays(copyId)!!
        assertEquals("PPL – variante deload", copy.routine.name)
        assertEquals(pplId, copy.routine.parentRoutineId)
        assertFalse(copy.routine.isActive)
        assertEquals(
            original.orderedDays.map { day -> day.day.name to day.orderedExercises.map { it.exercise.id } },
            copy.orderedDays.map { day -> day.day.name to day.orderedExercises.map { it.exercise.id } },
        )
        assertTrue("I giorni sono nuove righe", copy.days.map { it.day.id }.none { it in original.days.map { d -> d.day.id } })
        assertEquals(
            original.dayByWeekday.mapValues { it.value.day.name },
            copy.dayByWeekday.mapValues { it.value.day.name },
        )

        // La copia è indipendente: modificarla non tocca l'originale.
        val copyPush = copy.orderedDays.first()
        dao.removeRoutineExercise(copyPush.orderedExercises.first().routineExercise)
        assertEquals(3, dao.getRoutineExercises(pushId).size)
        assertEquals(listOf(0, 1), dao.getRoutineExercises(copyPush.day.id).map { it.position })
    }

    @Test
    fun duplicateDay_appendsCopyAtTheEnd() = runTest {
        val pushBId = dao.duplicateDay(pushId, "Push B")
        assertNotEquals(pushId, pushBId)
        assertEquals(listOf("Push", "Pull", "Legs", "Push B"), dao.getDays(pplId).map { it.name })
        assertEquals(
            dao.getRoutineExercises(pushId).map { it.exerciseId },
            dao.getRoutineExercises(pushBId).map { it.exerciseId },
        )
    }

    @Test
    fun dragAndDrop_reordersExercisesAndDays() = runTest {
        dao.moveRoutineExercise(pushId, fromIndex = 2, toIndex = 0)
        assertEquals(
            listOf("cable_triceps_pushdown", "barbell_bench_press", "dumbbell_lateral_raise"),
            dao.getRoutineExercises(pushId).map { it.exerciseId },
        )
        assertEquals(listOf(0, 1, 2), dao.getRoutineExercises(pushId).map { it.position })

        dao.moveDay(pplId, fromIndex = 0, toIndex = 2)
        assertEquals(listOf("Pull", "Legs", "Push"), dao.getDays(pplId).map { it.name })
    }

    @Test
    fun assignWeekday_rejectsDayOfAnotherRoutine() = runTest {
        val otherId = dao.insertRoutine(
            WorkoutRoutineEntity(name = "Altra", splitType = SplitType.CUSTOM, createdAt = T0, updatedAt = T0),
        )
        expectThrows<IllegalArgumentException> { dao.assignWeekday(otherId, DayOfWeek.MONDAY, pushId) }
    }

    @Test
    fun deletingDay_clearsItsScheduleEntries() = runTest {
        dao.deleteDay(dao.getDay(pullId)!!)
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            dao.getSchedule(pplId).map { it.dayOfWeek },
        )
    }
}
