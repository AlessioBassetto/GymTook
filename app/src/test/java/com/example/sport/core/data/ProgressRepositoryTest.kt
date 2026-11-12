package com.example.sport.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.data.progress.RecordKind
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.database.dao.WorkoutSessionDao
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.database.expectThrows
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.SetType
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/** Serie da registrare in una sessione di prova. */
internal data class LoggedSet(
    val weightKg: Double?,
    val reps: Int,
    val type: SetType = SetType.WORKING,
    val completed: Boolean = true,
)

/** Registra e conclude un allenamento libero [dayOffset] giorni dopo T0 (per i test dei progressi). */
internal suspend fun logSession(
    workout: WorkoutRepository,
    dao: WorkoutSessionDao,
    clock: TestClock,
    dayOffset: Long,
    vararg exercises: Pair<String, List<LoggedSet>>,
    title: String = "Allenamento",
): Long {
    clock.current = T0.plus(Duration.ofDays(dayOffset))
    val sessionId = workout.startFree(title)
    exercises.forEach { (exerciseId, specs) ->
        val sessionExerciseId = workout.addExercise(sessionId, exerciseId)
        while (dao.getSets(sessionExerciseId).size < specs.size) workout.addSet(sessionExerciseId)
        val sets = dao.getSets(sessionExerciseId).sortedBy { it.setIndex }
        specs.forEachIndexed { index, spec ->
            val set = sets[index]
            workout.updateMetric(set.id, SetMetric.WEIGHT, spec.weightKg)
            workout.updateMetric(set.id, SetMetric.REPS, spec.reps.toDouble())
            if (spec.type != SetType.WORKING) workout.setSetType(set.id, spec.type)
            workout.setCompleted(set.id, spec.completed)
        }
    }
    clock.current = clock.current.plus(Duration.ofMinutes(60))
    workout.finish(sessionId)
    return sessionId
}

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var workout: WorkoutRepository
    private lateinit var progress: ProgressRepository
    private val today: LocalDate = T0.atZone(ZoneOffset.UTC).toLocalDate()

    @Before
    fun setUp() = runTest {
        seedBundledCatalog()
        workout = WorkoutRepository(db.workoutSessionDao(), db.machineSetupDao(), clock)
        progress = ProgressRepository(db.workoutSessionDao(), db.bodyWeightDao(), db.progressDao(), db.exerciseDao(), clock)
    }

    private suspend fun log(dayOffset: Long, vararg exercises: Pair<String, List<LoggedSet>>) =
        logSession(workout, db.workoutSessionDao(), clock, dayOffset, *exercises)

    @Test
    fun catalogFixtures_haveTheExpectedTrackingTypes() = runTest {
        assertEquals(ExerciseTrackingType.WEIGHT_REPS, db.exerciseDao().getById(BENCH)!!.trackingType)
        assertEquals(ExerciseTrackingType.BODYWEIGHT_REPS, db.exerciseDao().getById(PULL_UP)!!.trackingType)
        assertEquals(ExerciseTrackingType.ASSISTED_BODYWEIGHT, db.exerciseDao().getById(ASSISTED)!!.trackingType)
    }

    @Test
    fun records_ignoreWarmups_assistance_andUnfinishedSessions() = runTest {
        log(
            0,
            BENCH to listOf(LoggedSet(60.0, 10, SetType.WARMUP), LoggedSet(100.0, 5), LoggedSet(100.0, 5)),
            ASSISTED to listOf(LoggedSet(40.0, 8)),
        )
        log(3, BENCH to listOf(LoggedSet(140.0, 1, SetType.WARMUP), LoggedSet(105.0, 3), LoggedSet(110.0, 2, completed = false)))
        // Sessione in corso: non deve contare.
        clock.current = T0.plus(Duration.ofDays(5))
        val open = workout.startFree("In corso")
        val openExercise = workout.addExercise(open, BENCH)
        val openSet = sessionDao().getSets(openExercise).first()
        workout.updateMetric(openSet.id, SetMetric.WEIGHT, 150.0)
        workout.updateMetric(openSet.id, SetMetric.REPS, 1.0)
        workout.setCompleted(openSet.id, true)

        val overview = progress.observeExerciseOverview().first()
        assertEquals("L'assistito non ha record di carico", listOf(BENCH), overview.records.map { it.exerciseId })
        val bench = overview.records.single()
        assertEquals("Il riscaldamento da 140 kg e la serie non spuntata non contano", 105.0, bench.heaviestSet.valueKg, 0.0)
        assertEquals(RecordKind.HEAVIEST_SET, bench.heaviestSet.kind)
        assertEquals(1000.0, bench.bestSessionVolume.valueKg, 0.0)
        assertEquals(2, overview.trends.single().sessionCount)
    }

    @Test
    fun exerciseProgress_keepsBodyweightSetsForReps() = runTest {
        log(0, PULL_UP to listOf(LoggedSet(null, 10), LoggedSet(0.0, 8), LoggedSet(10.0, 6)))

        val point = progress.observeExerciseProgress(PULL_UP, T0.minusSeconds(1)).first().single()
        assertEquals(24, point.totalReps)
        assertEquals(3, point.setCount)
        assertEquals("La zavorra è il carico massimo", 10.0, point.maxWeightKg, 0.0)
        assertEquals("1RM stimato solo sulla serie zavorrata", 12.0, point.bestEstimatedOneRepMaxKg!!, 1e-9)
        assertTrue(progress.observeHasHistory(PULL_UP).first())
        assertFalse(progress.observeHasHistory(BENCH).first())
        assertTrue("I record contano solo peso × ripetizioni", progress.observeExerciseOverview().first().records.isEmpty())
    }

    @Test
    fun history_volumeExcludesAssistance_andDeleteRemovesTheSession() = runTest {
        val first = log(0, BENCH to listOf(LoggedSet(100.0, 5)), ASSISTED to listOf(LoggedSet(40.0, 8)))
        val second = log(2, BENCH to listOf(LoggedSet(102.5, 5)))

        val history = progress.observeSessionHistory().first()
        assertEquals(listOf(second, first), history.map { it.id })
        assertEquals("40 kg di assistenza non sono volume", 500.0, history.last().totalVolumeKg, 0.0)
        assertEquals(500.0, workout.summary(first)!!.totalVolumeKg, 0.0)

        progress.deleteSession(second)
        assertEquals(listOf(first), progress.observeSessionHistory().first().map { it.id })
        assertNull(progress.session(second))
        assertEquals(100.0, progress.observeExerciseRecords(BENCH).first()!!.heaviestSet.valueKg, 0.0)
    }

    @Test
    fun bodyWeightTrend_warmsUpTheAverageWithTheWeekBefore() = runTest {
        // 80 kg per 6 giorni, poi 78: la media del primo giorno visibile tiene conto dei giorni prima.
        (0L..5L).forEach { db.bodyWeightDao().upsert(entry(today.minusDays(10 - it), 80.0)) }
        db.bodyWeightDao().upsert(entry(today.minusDays(4), 78.0))

        val points = progress.observeBodyWeightTrend(from = today.minusDays(4)).first()
        assertEquals(listOf(today.minusDays(4)), points.map { it.date })
        assertEquals("Finestra di 7 giorni: 80 × 6 + 78", (80.0 * 6 + 78.0) / 7, points.single().averageKg, 1e-9)
    }

    @Test
    fun saveBodyWeight_validates_andKeepsTheOtherFields() = runTest {
        expectThrows<IllegalArgumentException> { progress.saveBodyWeight(today, 19.9) }
        expectThrows<IllegalArgumentException> { progress.saveBodyWeight(today.plusDays(1), 80.0) }

        db.bodyWeightDao().upsert(entry(today, 81.0).copy(bodyFatPercent = 14.5, note = "Dopo la vacanza"))
        progress.saveBodyWeight(today, 80.4)
        val saved = progress.bodyWeightOn(today)!!
        assertEquals(80.4, saved.weightKg, 0.0)
        assertEquals(14.5, saved.bodyFatPercent!!, 0.0)
        assertEquals("Dopo la vacanza", saved.note)

        progress.deleteBodyWeight(today)
        assertNull(progress.bodyWeightOn(today))
        progress.restoreBodyWeight(saved)
        assertEquals(saved, progress.bodyWeightOn(today))
        assertEquals(saved, progress.latestBodyWeight())
    }

    private fun entry(date: LocalDate, weight: Double) = BodyWeightEntryEntity(date = date, weightKg = weight, recordedAt = T0)

    private fun sessionDao() = db.workoutSessionDao()

    private companion object {
        const val BENCH = "barbell_bench_press"
        const val PULL_UP = "pull_up"
        const val ASSISTED = "assisted_pull_up_machine"
    }
}
