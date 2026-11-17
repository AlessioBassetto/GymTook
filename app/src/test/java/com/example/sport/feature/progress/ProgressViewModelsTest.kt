package com.example.sport.feature.progress

import com.example.sport.core.database.calorieRepository
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.R
import com.example.sport.core.data.LoggedSet
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.data.SetValues
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.data.logSession
import com.example.sport.core.data.progress.RecordKind
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.designsystem.input.KeypadKey
import com.example.sport.core.model.SetType
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.settings.testSettingsRepository
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ProgressViewModelsTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var workout: WorkoutRepository
    private lateinit var progress: ProgressRepository
    private lateinit var settings: SettingsRepository
    private val today: LocalDate get() = clock.current.atZone(ZoneOffset.UTC).toLocalDate()

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        seedBundledCatalog()
        workout = WorkoutRepository(db.workoutSessionDao(), db.machineSetupDao(), db.calorieRepository(clock), clock)
        progress = ProgressRepository(db.workoutSessionDao(), db.bodyWeightDao(), db.progressDao(), db.exerciseDao(), db.calorieRepository(clock), clock)
        settings = testSettingsRepository(context.filesDir)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun log(dayOffset: Long, vararg exercises: Pair<String, List<LoggedSet>>) =
        logSession(workout, db.workoutSessionDao(), clock, dayOffset, *exercises)

    private suspend fun weigh(date: LocalDate, kg: Double) =
        db.bodyWeightDao().upsert(BodyWeightEntryEntity(date = date, weightKg = kg, recordedAt = T0))

    // ------------------------------------------------------------------ Foglio "Registra peso"

    @Test
    fun entryEditor_prefillsTheLatestWeight_andSavesTheEditedValue() = runTest {
        weigh(today.minusDays(2), 80.4)
        val editor = BodyWeightEntryEditor(progress, settings, backgroundScope)

        editor.open()
        val opened = editor.state.first { it != null }!!
        assertEquals(today, opened.date)
        assertEquals("80,4", opened.input.displayText)
        assertEquals(setOf(today.minusDays(2)), opened.recordedDates)
        assertEquals(7, opened.days.size)
        assertFalse(opened.replacesExisting)

        // Si cambia solo il decimale: la prima cifra sostituisce il valore pre-compilato.
        listOf(KeypadKey.Digit(8), KeypadKey.Digit(0), KeypadKey.DecimalSeparator, KeypadKey.Digit(1)).forEach(editor::onKey)
        editor.save()
        assertEquals(today, editor.saved.first())
        assertNull(editor.state.value)
        assertEquals(80.1, progress.bodyWeightOn(today)!!.weightKg, 0.0)
    }

    @Test
    fun entryEditor_inPounds_savesKilograms() = runTest {
        settings.setWeightUnit(WeightUnit.LB)
        weigh(today.minusDays(1), 80.0)
        val editor = BodyWeightEntryEditor(progress, settings, backgroundScope)

        editor.open()
        val opened = editor.state.first { it != null }!!
        assertEquals(WeightUnit.LB, opened.unit)
        assertEquals("80 kg in libbre, al decimo", "176,4", opened.input.displayText)
        listOf(KeypadKey.Digit(1), KeypadKey.Digit(7), KeypadKey.Digit(5)).forEach(editor::onKey)
        editor.save()
        assertEquals(today, editor.saved.first())
        assertEquals(175 * 0.45359237, progress.bodyWeightOn(today)!!.weightKg, 1e-9)
    }

    @Test
    fun entryEditor_showsTheDayValue_andRejectsOutOfRange() = runTest {
        weigh(today.minusDays(1), 79.0)
        weigh(today.minusDays(3), 81.0)
        val editor = BodyWeightEntryEditor(progress, settings, backgroundScope)

        editor.open()
        editor.state.first { it != null }
        editor.onSelectDate(today.minusDays(3))
        val dayBefore = editor.state.first { it?.date == today.minusDays(3) }!!
        assertEquals("81", dayBefore.input.displayText)
        assertTrue(dayBefore.replacesExisting)

        editor.onKey(KeypadKey.Digit(9))
        editor.save()
        val rejected = editor.state.first { it?.showsRangeError == true }!!
        assertEquals("9", rejected.input.displayText)
        assertEquals(81.0, progress.bodyWeightOn(today.minusDays(3))!!.weightKg, 0.0)

        editor.onKey(KeypadKey.Digit(0))
        assertFalse("Digitare cancella l'errore", editor.state.value!!.showsRangeError)
        editor.dismiss()
        assertNull(editor.state.value)
    }

    // ------------------------------------------------------------------ Storico del peso

    @Test
    fun bodyWeight_rangeAndStats_followTheSelection() = runTest {
        clock.current = T0.plusSeconds(120L * 24 * 3600)
        // Una pesata al giorno per 100 giorni, in calo di 0,1 kg al giorno.
        (0L until 100L).forEach { weigh(today.minusDays(99 - it), 90.0 - it * 0.1) }
        val viewModel = BodyWeightViewModel(progress, db.calorieRepository(clock), settings)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }

        val quarter = viewModel.uiState.first { it is BodyWeightUiState.Content } as BodyWeightUiState.Content
        assertEquals(TimeRange.QUARTER, quarter.range)
        assertEquals(today.minusMonths(3), quarter.points.first().date)
        assertEquals(80.1, quarter.latest!!.weightKg, 1e-9)
        assertEquals("La media scende di 0,7 kg a settimana", -0.7, quarter.weeklyChange!!, 1e-9)
        assertEquals(quarter.points.last().averageKg, quarter.latestAverageKg!!, 0.0)

        viewModel.onSelectRange(TimeRange.MONTH)
        val month = viewModel.uiState.first { (it as? BodyWeightUiState.Content)?.range == TimeRange.MONTH } as BodyWeightUiState.Content
        assertEquals(today.minusMonths(1), month.points.first().date)
        assertTrue(month.periodChange!! < 0)

        viewModel.onSelectRange(TimeRange.ALL)
        val all = viewModel.uiState.first { (it as? BodyWeightUiState.Content)?.range == TimeRange.ALL } as BodyWeightUiState.Content
        assertEquals(100, all.points.size)
    }

    @Test
    fun bodyWeight_deleteAndUndo() = runTest {
        weigh(today, 80.0)
        val viewModel = BodyWeightViewModel(progress, db.calorieRepository(clock), settings)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }

        viewModel.onDelete(today)
        val deleted = viewModel.events.first() as BodyWeightEvent.Deleted
        assertEquals(today, deleted.entry.date)
        val empty = viewModel.uiState.first { (it as? BodyWeightUiState.Content)?.hasAnyEntry == false }
        assertTrue((empty as BodyWeightUiState.Content).points.isEmpty())

        viewModel.onUndoDelete(deleted.entry)
        viewModel.uiState.first { (it as? BodyWeightUiState.Content)?.hasAnyEntry == true }
        assertEquals(80.0, progress.bodyWeightOn(today)!!.weightKg, 0.0)
    }

    // ------------------------------------------------------------------ Panoramica

    @Test
    fun overview_combinesWeightTrendsRecordsAndHistory() = runTest {
        log(0, BENCH to listOf(LoggedSet(100.0, 5)))
        log(3, BENCH to listOf(LoggedSet(105.0, 5)), SQUAT to listOf(LoggedSet(140.0, 5)))
        weigh(today, 80.0)
        val viewModel = ProgressViewModel(progress, settings)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }

        val content = viewModel.uiState.first { (it as? ProgressUiState.Content)?.sessions?.size == 2 } as ProgressUiState.Content
        assertEquals(80.0, content.bodyWeight!!.latestKg, 0.0)
        assertEquals(today, content.bodyWeight.latestDate)
        assertEquals(setOf(BENCH, SQUAT), content.trends.map { it.exerciseId }.toSet())
        val bench = content.trends.first { it.exerciseId == BENCH }
        assertEquals(2, bench.sessionCount)
        assertEquals(105.0 * (1 + 5 / 30.0) - 100.0 * (1 + 5 / 30.0), bench.oneRepMaxChangeKg!!, 1e-9)
        assertEquals(105.0, content.records.first { it.exerciseId == BENCH }.heaviestWeightKg, 0.0)
        assertEquals("1 h 0 min", content.sessions.first().duration)
    }

    // ------------------------------------------------------------------ Progressi per esercizio

    @Test
    fun exerciseProgress_metricsRangesAndRecords() = runTest {
        clock.current = T0
        log(0, BENCH to listOf(LoggedSet(60.0, 10, SetType.WARMUP), LoggedSet(100.0, 5), LoggedSet(90.0, 8)))
        log(4, BENCH to listOf(LoggedSet(102.5, 5), LoggedSet(102.5, 4)))
        val viewModel = ExerciseProgressViewModel(SavedStateHandle(mapOf("exerciseId" to BENCH)), progress)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }

        val content = viewModel.uiState.first { (it as? ExerciseProgressUiState.Content)?.points?.size == 2 } as ExerciseProgressUiState.Content
        assertEquals(listOf(ProgressMetric.ONE_REP_MAX, ProgressMetric.MAX_WEIGHT, ProgressMetric.VOLUME, ProgressMetric.REPS), content.metrics)
        assertEquals(ProgressMetric.ONE_REP_MAX, content.metric)
        assertEquals(102.5 * (1 + 5 / 30.0), content.latest!!, 1e-9)
        assertEquals("Il punto della sessione è la serie migliore: 100 × 5 batte 90 × 8", 100.0 * (1 + 5 / 30.0), content.points.first().value, 1e-9)
        assertEquals(listOf(RecordKind.HEAVIEST_SET, RecordKind.ESTIMATED_ONE_REP_MAX, RecordKind.SESSION_VOLUME), content.records.map { it.kind })
        assertEquals("Sessioni dalla più recente", listOf(9, 13), content.sessions.map { it.totalReps })

        viewModel.onSelectMetric(ProgressMetric.VOLUME)
        val volume = viewModel.uiState.first { (it as? ExerciseProgressUiState.Content)?.metric == ProgressMetric.VOLUME } as ExerciseProgressUiState.Content
        assertEquals(listOf(1220.0, 922.5), volume.points.map { it.value })
        assertEquals(922.5 - 1220.0, volume.change!!, 1e-9)
    }

    @Test
    fun exerciseProgress_bodyweightUsesReps_andUnknownIsNotFound() = runTest {
        log(0, PULL_UP to listOf(LoggedSet(null, 10), LoggedSet(null, 8)))
        val viewModel = ExerciseProgressViewModel(SavedStateHandle(mapOf("exerciseId" to PULL_UP)), progress)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        val content = viewModel.uiState.first { (it as? ExerciseProgressUiState.Content)?.points?.isNotEmpty() == true } as ExerciseProgressUiState.Content
        assertEquals(ProgressMetric.REPS, content.metric)
        assertEquals(18.0, content.points.single().value, 0.0)
        assertTrue("A corpo libero niente record di carico", content.records.isEmpty())

        val missing = ExerciseProgressViewModel(SavedStateHandle(mapOf("exerciseId" to "non_esiste")), progress)
        backgroundScope.launch(Dispatchers.Main) { missing.uiState.collect {} }
        assertEquals(ExerciseProgressUiState.NotFound, missing.uiState.first { it !is ExerciseProgressUiState.Loading })
    }

    // ------------------------------------------------------------------ Dettaglio allenamento

    @Test
    fun sessionDetail_numbersWorkingSets_andDeletes() = runTest {
        val sessionId = log(
            0,
            BENCH to listOf(LoggedSet(60.0, 10, SetType.WARMUP), LoggedSet(100.0, 5), LoggedSet(80.0, 8, SetType.DROP)),
            ASSISTED to listOf(LoggedSet(30.0, 8)),
            SQUAT to listOf(LoggedSet(null, 8)),
        )
        val viewModel = SessionDetailViewModel(SavedStateHandle(mapOf("sessionId" to sessionId)), progress, settings)

        val content = viewModel.uiState.first { it is SessionDetailUiState.Content } as SessionDetailUiState.Content
        assertEquals(4, content.workingSets)
        assertEquals("Peso non inserito", "× 8", content.exercises.last().sets.single().description)
        assertEquals("100 × 5 + 80 × 8; l'assistenza non conta", 1140.0, content.volumeKg, 0.0)
        val bench = content.exercises.first()
        assertEquals(listOf(0, 1, 2), bench.sets.map { it.number })
        assertEquals(listOf("60 kg × 10", "100 kg × 5", "80 kg × 8"), bench.sets.map { it.description })
        assertEquals("−30 × 8", content.exercises[1].sets.single().description)

        viewModel.onDelete()
        assertEquals(SessionDetailEvent.Deleted, viewModel.events.first())
        assertNull(progress.session(sessionId))
    }

    @Test
    fun sessionDetail_inProgressSessionIsNotShown() = runTest {
        val open = workout.startFree("In corso")
        val viewModel = SessionDetailViewModel(SavedStateHandle(mapOf("sessionId" to open)), progress, settings)
        assertEquals(SessionDetailUiState.NotFound, viewModel.uiState.first { it !is SessionDetailUiState.Loading })
    }

    @Test
    fun sessionDetail_fixesAndDeletesSets_andFollowsTheDatabase() = runTest {
        val sessionId = log(0, BENCH to listOf(LoggedSet(1000.0, 5), LoggedSet(80.0, 8)))
        val viewModel = SessionDetailViewModel(SavedStateHandle(mapOf("sessionId" to sessionId)), progress, settings)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }

        val content = viewModel.uiState.first { it is SessionDetailUiState.Content } as SessionDetailUiState.Content
        val typo = content.exercises.single().sets.first()
        assertEquals("I valori salvati servono a precompilare la correzione", 1000.0, typo.weightKg!!, 0.0)

        viewModel.onUpdateSet(typo.id, SetValues(weightKg = 100.0, reps = 5))
        assertEquals(SessionDetailEvent.Message(R.string.set_updated), viewModel.events.first())
        val fixed = viewModel.uiState.first { (it as? SessionDetailUiState.Content)?.volumeKg == 1140.0 } as SessionDetailUiState.Content
        assertEquals(listOf("100 kg × 5", "80 kg × 8"), fixed.exercises.single().sets.map { it.description })

        viewModel.onDeleteSet(typo.id)
        assertEquals(SessionDetailEvent.Message(R.string.set_deleted), viewModel.events.first())
        viewModel.onDeleteSet(fixed.exercises.single().sets.last().id)
        assertEquals("Senza serie l'allenamento esce dallo storico", SessionDetailEvent.Emptied, viewModel.events.first())
        assertNull(progress.session(sessionId))
    }

    @Test
    fun exerciseProgress_deletesAnAnomalousRecord_andResetsOnlyThatExercise() = runTest {
        clock.current = T0
        log(0, BENCH to listOf(LoggedSet(100.0, 5)), SQUAT to listOf(LoggedSet(120.0, 5)))
        log(7, BENCH to listOf(LoggedSet(1000.0, 5)))
        val viewModel = ExerciseProgressViewModel(SavedStateHandle(mapOf("exerciseId" to BENCH)), progress)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }

        fun ExerciseProgressUiState.heaviest() = (this as? ExerciseProgressUiState.Content)?.records?.firstOrNull { it.kind == RecordKind.HEAVIEST_SET }
        val anomaly = viewModel.uiState.first { it.heaviest()?.valueKg == 1000.0 }.heaviest()!!

        viewModel.onDeleteRecord(anomaly)
        assertEquals(ExerciseProgressEvent.Message(R.string.record_deleted), viewModel.events.first())
        val cleaned = viewModel.uiState.first { it.heaviest()?.valueKg == 100.0 } as ExerciseProgressUiState.Content
        assertEquals("L'allenamento rimasto vuoto esce dallo storico", 1, cleaned.sessions.size)

        viewModel.onResetHistory()
        assertEquals(ExerciseProgressEvent.HistoryReset, viewModel.events.first())
        assertNull(progress.observeExerciseRecords(BENCH).first())
        assertEquals("Lo squat non viene toccato", 120.0, progress.observeExerciseRecords(SQUAT).first()!!.heaviestSet.valueKg, 0.0)
    }

    @Test
    fun overview_deletesAWorkoutFromTheHistoryList() = runTest {
        val kept = log(0, BENCH to listOf(LoggedSet(100.0, 5)))
        val mistake = log(1, BENCH to listOf(LoggedSet(1000.0, 5)))
        val viewModel = ProgressViewModel(progress, settings)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        viewModel.uiState.first { (it as? ProgressUiState.Content)?.sessions?.size == 2 }

        viewModel.onDeleteSession(mistake)
        viewModel.sessionDeleted.first()

        val content = viewModel.uiState.first { (it as? ProgressUiState.Content)?.sessions?.size == 1 } as ProgressUiState.Content
        assertEquals(kept, content.sessions.single().id)
        assertEquals("Il record torna quello vero", 100.0, progress.observeExerciseRecords(BENCH).first()!!.heaviestSet.valueKg, 0.0)
    }

    private companion object {
        const val BENCH = "barbell_bench_press"
        const val SQUAT = "barbell_back_squat"
        const val PULL_UP = "pull_up"
        const val ASSISTED = "assisted_pull_up_machine"
    }
}
