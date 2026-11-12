package com.example.sport.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.data.template.RoutineTemplates
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RoutineRepositoryTest : DatabaseTest() {

    @Before
    fun seed() = runTest { seedBundledCatalog() }

    /** T0 è lunedì 2 novembre 2026. */
    private fun repositoryAt(daysAfterT0: Long) =
        RoutineRepository(db, Clock.fixed(T0.plus(Duration.ofDays(daysAfterT0)), ZoneOffset.UTC))

    @Test
    fun everyTemplateExercise_existsInTheBundledCatalog() = runTest {
        RoutineTemplates.all.forEach { template ->
            template.days.flatMap { it.exercises }.forEach { item ->
                assertNotNull("${template.id}: esercizio ${item.exerciseId} assente", db.exerciseDao().getById(item.exerciseId))
                assertTrue("${template.id}: range reps non valido", item.repsMin <= item.repsMax)
            }
            val weekdays = template.days.flatMap { it.weekdays }
            assertEquals("${template.id}: giorno della settimana assegnato due volte", weekdays.size, weekdays.toSet().size)
        }
    }

    @Test
    fun createFromTemplate_buildsTheWholeRoutine_andActivatesIt() = runTest {
        val repository = repositoryAt(0)
        val routineId = repository.createFromTemplate(RoutineTemplates.upperLower)

        val routine = repository.observeRoutines().first().single()
        assertEquals(routineId, routine.routine.id)
        assertTrue(routine.routine.isActive)
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), routine.orderedDays.map { it.day.name })
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            routine.schedule.map { it.dayOfWeek }.toSet(),
        )
        val upperA = routine.orderedDays.first().orderedExercises
        assertEquals("barbell_bench_press", upperA.first().exercise.id)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), upperA.map { it.routineExercise.position })
        assertEquals(4, upperA.first().routineExercise.targetSets)
    }

    @Test
    fun todayPlan_followsTheWeeklySchedule() = runTest {
        repositoryAt(0).createFromTemplate(RoutineTemplates.pushPullLegs)

        val monday = repositoryAt(0).observeTodayPlan().first()
        assertTrue(monday is TodayPlan.Workout)
        assertEquals("Push", (monday as TodayPlan.Workout).day.day.name)
        assertEquals(6, monday.day.orderedExercises.size)

        val tuesday = repositoryAt(1).observeTodayPlan().first()
        assertEquals(
            TodayPlan.RestDay(
                date = LocalDate.of(2026, 11, 3),
                routineName = "Push Pull Legs",
                next = ScheduledWorkout("Pull", LocalDate.of(2026, 11, 4)),
            ),
            tuesday,
        )

        val saturday = repositoryAt(5).observeTodayPlan().first() as TodayPlan.RestDay
        assertEquals("Dopo il weekend si riparte da lunedì", ScheduledWorkout("Push", LocalDate.of(2026, 11, 9)), saturday.next)
    }

    @Test
    fun todayPlan_withoutActiveRoutine() = runTest {
        assertEquals(TodayPlan.NoActiveRoutine(LocalDate.of(2026, 11, 2)), repositoryAt(0).observeTodayPlan().first())
    }

    @Test
    fun switchingTemplate_keepsOnlyOneActiveRoutine() = runTest {
        val repository = repositoryAt(0)
        repository.createFromTemplate(RoutineTemplates.pushPullLegs)
        val fullBodyId = repository.createFromTemplate(RoutineTemplates.fullBody)

        val routines = repository.observeRoutines().first()
        assertEquals(listOf(fullBodyId), routines.filter { it.routine.isActive }.map { it.routine.id })
        val today = repository.observeTodayPlan().first() as TodayPlan.Workout
        assertEquals("Full body A", today.day.day.name)
    }
}
