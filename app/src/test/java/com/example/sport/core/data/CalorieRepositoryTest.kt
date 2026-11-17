package com.example.sport.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.database.calorieRepository
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.database.expectThrows
import com.example.sport.core.model.BiologicalSex
import com.example.sport.core.workout.TestClock
import com.example.sport.feature.progress.calorieTotals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Calorie salvate nello storico: si stimano a fine allenamento e restano coerenti con ogni
 * correzione, pesata o modifica del profilo corporeo.
 */
@RunWith(AndroidJUnit4::class)
class CalorieRepositoryTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var calories: CalorieRepository
    private lateinit var workout: WorkoutRepository
    private lateinit var progress: ProgressRepository
    private val sessionDao get() = db.workoutSessionDao()
    private val day0: LocalDate = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        calories = db.calorieRepository(clock)
        workout = WorkoutRepository(sessionDao, db.machineSetupDao(), calories, clock)
        progress = ProgressRepository(sessionDao, db.bodyWeightDao(), db.progressDao(), db.exerciseDao(), calories, clock)
    }

    private suspend fun log(dayOffset: Long, vararg exercises: Pair<String, List<LoggedSet>>) =
        logSession(workout, sessionDao, clock, dayOffset, *exercises)

    private suspend fun kcal(sessionId: Long): Int? = sessionDao.getSession(sessionId)?.caloriesBurned

    private suspend fun weighIn(dayOffset: Long, kg: Double) {
        clock.current = T0.plus(Duration.ofDays(dayOffset))
        progress.saveBodyWeight(day0.plusDays(dayOffset), kg)
    }

    private val squats = SQUAT to listOf(LoggedSet(100.0, 5), LoggedSet(100.0, 5), LoggedSet(100.0, 5))

    @Test
    fun finishingAWorkout_storesTheEstimate_inHistoryAndSummary() = runTest {
        weighIn(0, 80.0)
        val sessionId = log(0, squats, BENCH to listOf(LoggedSet(80.0, 10)))

        val stored = sessionDao.getSession(sessionId)!!
        assertTrue("Stima ${stored.caloriesBurned}", (stored.caloriesBurned ?: 0) in 100..600)
        assertEquals("Peso usato per la stima", 80.0, stored.bodyWeightKg!!, 0.0)
        assertEquals(stored.caloriesBurned, progress.observeSessionHistory().first().single().caloriesBurned)
        assertEquals(stored.caloriesBurned, workout.summary(sessionId)!!.caloriesBurned)
    }

    @Test
    fun withoutWeighIns_thereIsNoEstimate_untilTheFirstOne() = runTest {
        val sessionId = log(0, squats)
        assertNull(kcal(sessionId))

        weighIn(3, 82.0)

        assertNotNull("La prima pesata, anche se successiva, completa lo storico", kcal(sessionId))
        assertEquals(82.0, sessionDao.getSession(sessionId)!!.bodyWeightKg!!, 0.0)
    }

    @Test
    fun eachWorkoutUsesTheWeightOfItsDay() = runTest {
        weighIn(0, 80.0)
        val early = log(1, squats)
        weighIn(10, 90.0)
        val late = log(11, squats)

        assertEquals(80.0, sessionDao.getSession(early)!!.bodyWeightKg!!, 0.0)
        assertEquals(90.0, sessionDao.getSession(late)!!.bodyWeightKg!!, 0.0)
        assertTrue("Stesso lavoro, più peso corporeo", kcal(late)!! > kcal(early)!!)

        progress.deleteBodyWeight(day0)
        assertEquals("Senza la pesata del giorno 0 resta la prima disponibile", 90.0, sessionDao.getSession(early)!!.bodyWeightKg!!, 0.0)
        progress.deleteBodyWeight(day0.plusDays(10))
        assertNull("Nessuna pesata: nessuna stima", kcal(early))
        assertNull(sessionDao.getSession(early)!!.bodyWeightKg)
    }

    @Test
    fun correctingOrDeletingSets_rewritesTheEstimate() = runTest {
        weighIn(0, 80.0)
        val sessionId = log(0, squats)
        val sets = sessionDao.getSessionWithExercises(sessionId)!!.orderedExercises.single().orderedSets
        val logged = kcal(sessionId)!!

        progress.updateSet(sets[0].id, SetValues(weightKg = 160.0, reps = 5))
        val heavier = kcal(sessionId)!!
        assertTrue("$heavier > $logged", heavier > logged)

        progress.deleteSet(sets[1].id)
        assertTrue(kcal(sessionId)!! < heavier)
    }

    @Test
    fun deletingAWorkoutOrResettingAnExercise_keepsTheTotalsConsistent() = runTest {
        weighIn(0, 80.0)
        val mixed = log(0, squats, BENCH to listOf(LoggedSet(80.0, 10), LoggedSet(80.0, 10)))
        val benchOnly = log(1, BENCH to listOf(LoggedSet(80.0, 10)))
        val mixedBefore = kcal(mixed)!!
        val today = day0.plusDays(1)
        suspend fun totals() = calorieTotals(progress.observeSessionHistory().first(), today, ZoneOffset.UTC)
        assertEquals(mixedBefore + kcal(benchOnly)!!, totals()!!.weekKcal)

        assertEquals(2, progress.resetExerciseHistory(BENCH))

        assertNull("L'allenamento di sola panca sparisce", sessionDao.getSession(benchOnly))
        assertTrue("Senza la panca l'allenamento misto vale meno", kcal(mixed)!! < mixedBefore)
        assertEquals(kcal(mixed)!!, totals()!!.weekKcal)
        assertEquals(kcal(mixed)!!, totals()!!.monthKcal)

        progress.deleteSession(mixed)
        assertNull("Storico vuoto: nessun totale", totals())
    }

    @Test
    fun theBodyProfile_refinesEveryEstimate() = runTest {
        weighIn(0, 80.0)
        val sessionId = log(0, squats)
        val withoutProfile = kcal(sessionId)!!

        calories.saveProfile(heightCm = 182f, birthYear = 1990, sex = BiologicalSex.MALE)
        val male = kcal(sessionId)!!
        assertTrue("Il profilo cambia la stima ($withoutProfile → $male)", male != withoutProfile)
        assertEquals(182f, calories.profile()!!.heightCm)

        calories.saveProfile(heightCm = 182f, birthYear = 1990, sex = BiologicalSex.FEMALE)
        assertTrue(kcal(sessionId)!! < male)

        expectThrows<IllegalArgumentException> { calories.saveProfile(heightCm = 50f, birthYear = null, sex = null) }
        expectThrows<IllegalArgumentException> { calories.saveProfile(heightCm = null, birthYear = 2024, sex = null) }
    }

    @Test
    fun recalculateMissing_completesOnlyTheWorkoutsWithoutAnEstimate() = runTest {
        val sessionId = log(0, squats)
        assertNull(kcal(sessionId))
        // Pesata scritta direttamente (come dopo la migrazione o un ripristino): nessun ricalcolo automatico.
        db.bodyWeightDao().upsert(BodyWeightEntryEntity(date = day0, weightKg = 75.0, recordedAt = T0))
        assertNull(kcal(sessionId))

        calories.recalculateMissing()

        assertNotNull(kcal(sessionId))
        assertEquals(75.0, sessionDao.getSession(sessionId)!!.bodyWeightKg!!, 0.0)
    }

    private companion object {
        const val SQUAT = "barbell_back_squat"
        const val BENCH = "barbell_bench_press"
    }
}
