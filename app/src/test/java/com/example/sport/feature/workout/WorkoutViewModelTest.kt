package com.example.sport.feature.workout

import com.example.sport.core.database.calorieRepository
import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.data.ExerciseRepository
import com.example.sport.core.data.RoutineBuilderRepository
import com.example.sport.core.data.SessionVerdict
import com.example.sport.core.data.SetMetric
import com.example.sport.core.data.WorkoutRepository
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.designsystem.input.KeypadKey
import com.example.sport.core.media.MediaConfig
import com.example.sport.core.media.MediaRepository
import com.example.sport.core.media.OfflineMediaStore
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.workout.RestTimerController
import com.example.sport.core.workout.RestTimerNotifier
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
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

/** Flusso reale del logger: tastierino → database → spunta → timer di recupero. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WorkoutViewModelTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var restTimer: RestTimerController
    private lateinit var viewModel: WorkoutViewModel
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        seedBundledCatalog()
        workoutRepository = WorkoutRepository(db.workoutSessionDao(), db.machineSetupDao(), db.calorieRepository(clock), clock)
        restTimer = RestTimerController(context, clock, RestTimerNotifier(context))
        restTimer.cancel()
        settings = testSettingsRepository(context.filesDir)
        val config = MediaConfig("")
        viewModel = WorkoutViewModel(
            workoutRepository = workoutRepository,
            exerciseRepository = ExerciseRepository(db.exerciseDao(), db.mediaDao(), clock),
            mediaRepository = MediaRepository(config, OfflineMediaStore(File(context.filesDir, "vm-media"), config, OkHttpClient(), Dispatchers.IO), db.mediaDao()),
            restTimer = restTimer,
            settingsRepository = settings,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun awaitActive(predicate: (WorkoutUiState.Active) -> Boolean = { true }): WorkoutUiState.Active =
        viewModel.uiState.first { it is WorkoutUiState.Active && predicate(it) } as WorkoutUiState.Active

    @Test
    fun pounds_areShownAndTyped_butStoredInKilograms() = runTest {
        val (_, dayId) = createRoutineWithDay("Petto", listOf("barbell_bench_press" to 1))
        workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        val set = awaitActive().exercises[0].sets[0]
        workoutRepository.updateMetric(set.setId, SetMetric.WEIGHT, 100.0)

        settings.setWeightUnit(WeightUnit.LB)
        awaitActive { it.exercises[0].sets[0].values[SetMetric.WEIGHT] == "220,5" }

        viewModel.onFieldClick(set.setId, SetMetric.WEIGHT)
        val editing = awaitActive { it.editing != null }.editing!!
        assertEquals("Il campo parte dal valore in libbre", "220,5", editing.text)
        assertEquals("In libbre si sale di 5", "5", editing.stepLabel)
        listOf(KeypadKey.Digit(2), KeypadKey.Digit(2), KeypadKey.Digit(5)).forEach(viewModel::onKey)
        awaitActive { it.exercises[0].sets[0].values[SetMetric.WEIGHT] == "225" }
        assertEquals(225 * 0.45359237, db.workoutSessionDao().getSet(set.setId)!!.weightKg!!, 1e-9)
    }

    @Test
    fun rirScale_addsTheRirColumn_andStoresWholeReps() = runTest {
        val (_, dayId) = createRoutineWithDay("Gambe", listOf("leg_press" to 1))
        workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        settings.setEffortScale(EffortScale.RIR)
        val exercise = awaitActive().exercises[0]

        viewModel.onToggleRpe(exercise.sessionExerciseId)
        val withRir = awaitActive { SetMetric.RIR in it.exercises[0].metrics }.exercises[0]
        assertEquals(listOf(SetMetric.WEIGHT, SetMetric.REPS, SetMetric.RIR), withRir.metrics)

        viewModel.onFieldClick(withRir.sets[0].setId, SetMetric.RIR)
        viewModel.onKey(KeypadKey.Digit(2))
        awaitActive { it.exercises[0].sets[0].values[SetMetric.RIR] == "2" }
        assertEquals(2, db.workoutSessionDao().getSet(withRir.sets[0].setId)!!.rir)
    }

    @Test
    fun keypad_writesEachField_andNextWalksThroughTheSets() = runTest {
        val (_, dayId) = createRoutineWithDay("Gambe", listOf("leg_extension" to 2, "leg_press" to 1))
        workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }

        val start = awaitActive { it.exercises.size == 2 }
        val (firstSet, secondSet) = start.exercises[0].sets
        assertEquals("Reps target pre-compilate al primo allenamento", "12", firstSet.values[SetMetric.REPS])

        viewModel.onFieldClick(firstSet.setId, SetMetric.WEIGHT)
        listOf(KeypadKey.Digit(4), KeypadKey.Digit(2), KeypadKey.DecimalSeparator, KeypadKey.Digit(5)).forEach(viewModel::onKey)
        awaitActive { it.exercises[0].sets[0].values[SetMetric.WEIGHT] == "42,5" }
        assertEquals(42.5, db.workoutSessionDao().getSet(firstSet.setId)!!.weightKg!!, 0.0)

        viewModel.onNext()
        val onReps = awaitActive { it.editing?.metric == SetMetric.REPS }
        assertEquals(firstSet.setId, onReps.editing!!.setId)
        viewModel.onKey(KeypadKey.Digit(1))
        viewModel.onKey(KeypadKey.Digit(0))
        awaitActive { it.exercises[0].sets[0].values[SetMetric.REPS] == "10" }

        viewModel.onNext()
        val onNextSet = awaitActive { it.editing?.setId == secondSet.setId }
        assertEquals(SetMetric.WEIGHT, onNextSet.editing!!.metric)

        // La prima cifra sostituisce il valore pre-compilato anche sulla serie successiva.
        viewModel.onKey(KeypadKey.Digit(5))
        viewModel.onKey(KeypadKey.Digit(0))
        awaitActive { it.exercises[0].sets[1].values[SetMetric.WEIGHT] == "50" }
        val stored = db.workoutSessionDao().getSet(firstSet.setId)!!
        assertEquals("Il campo peso non viene sovrascritto dalle reps", 42.5, stored.weightKg!!, 0.0)
        assertEquals(10, stored.reps)
    }

    @Test
    fun completingASet_startsTheRest_andTheLastOneDoesNot() = runTest {
        val (_, dayId) = createRoutineWithDay("Gambe", listOf("leg_extension" to 1, "leg_press" to 1))
        workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        val start = awaitActive { it.exercises.size == 2 }
        val legExtensionSet = start.exercises[0].sets.single()
        val legPressSet = start.exercises[1].sets.single()

        viewModel.onToggleComplete(legExtensionSet.setId)
        val resting = awaitActive { it.rest != null && it.exercises[0].sets[0].isCompleted }
        assertEquals(75, resting.rest!!.totalSeconds)
        assertEquals("Il prossimo esercizio da fare", "Leg press 45°", resting.rest.nextExercise)
        assertEquals(1, resting.completedSets)

        viewModel.onRestSkip()
        awaitActive { it.rest == null }

        viewModel.onToggleComplete(legPressSet.setId)
        val done = awaitActive { it.completedSets == 2 }
        assertNull("Ultima serie: nessun recupero da avviare", done.rest)
    }

    @Test
    fun tickingAnEmptySet_copiesTheValuesOfTheSetBefore() = runTest {
        val (_, dayId) = createRoutineWithDay("Gambe", listOf("leg_extension" to 3))
        workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        val (first, second) = awaitActive().exercises[0].sets

        viewModel.onFieldClick(first.setId, SetMetric.WEIGHT)
        listOf(KeypadKey.Digit(6), KeypadKey.Digit(0)).forEach(viewModel::onKey)
        viewModel.onCloseKeypad()
        awaitActive { it.exercises[0].sets[0].values[SetMetric.WEIGHT] == "60" }
        viewModel.onToggleComplete(first.setId)
        awaitActive { it.exercises[0].sets[0].isCompleted }

        // Seconda serie mai toccata: una spunta = stessi kg della prima.
        viewModel.onToggleComplete(second.setId)
        val done = awaitActive { it.exercises[0].sets[1].isCompleted }
        assertEquals("60", done.exercises[0].sets[1].values[SetMetric.WEIGHT])
        assertEquals(60.0, db.workoutSessionDao().getSet(second.setId)!!.weightKg!!, 0.0)
    }

    @Test
    fun superset_skipsTheRestBetweenPartners_andRestsAtTheEndOfTheRound() = runTest {
        val (_, dayId) = createRoutineWithDay("Braccia", listOf("barbell_curl" to 2, "lying_triceps_press" to 2, "leg_extension" to 1))
        val curl = db.routineDao().getRoutineExercises(dayId).first()
        RoutineBuilderRepository(db, clock).linkWithNext(curl.id)
        workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        val start = awaitActive { it.exercises.size == 3 }
        assertEquals(listOf("A", "A", null), start.exercises.map { it.supersetLabel })
        val (curlSets, tricepsSets) = start.exercises[0].sets to start.exercises[1].sets

        viewModel.onToggleComplete(curlSets[0].setId)
        val afterCurl = awaitActive { it.exercises[0].sets[0].isCompleted }
        assertNull("Superserie: si passa subito ai tricipiti, senza recupero", afterCurl.rest)

        viewModel.onToggleComplete(tricepsSets[0].setId)
        val endOfRound = awaitActive { it.rest != null }
        assertEquals("Fine giro: si torna al curl", start.exercises[0].name, endOfRound.rest!!.nextExercise)

        viewModel.onRestSkip()
        viewModel.onToggleComplete(curlSets[1].setId)
        awaitActive { it.exercises[0].sets[1].isCompleted }
        viewModel.onToggleComplete(tricepsSets[1].setId)
        val lastRound = awaitActive { it.exercises[1].sets[1].isCompleted && it.rest != null }
        assertEquals("Superserie finita: si passa all'esercizio successivo", "Leg extension", lastRound.rest!!.nextExercise)
    }

    @Test
    fun finishing_removesUnloggedSets_andEmitsTheSummaryEvent() = runTest {
        val (_, dayId) = createRoutineWithDay("Gambe", listOf("leg_extension" to 3))
        val sessionId = workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        val start = awaitActive()
        viewModel.onToggleComplete(start.exercises[0].sets[0].setId)
        awaitActive { it.completedSets == 1 }

        viewModel.onFinish()
        assertEquals(WorkoutEvent.Finished(sessionId), viewModel.events.first())
        assertEquals(1, db.workoutSessionDao().getSessionWithExercises(sessionId)!!.exercises.single().sets.size)
        assertNull(restTimer.state.value)
        assertEquals(WorkoutUiState.NoActiveSession, viewModel.uiState.first { it == WorkoutUiState.NoActiveSession })
    }

    @Test
    fun finishingWithoutValidSets_isJudgedEmpty_andTheSessionIsDiscarded() = runTest {
        val (_, dayId) = createRoutineWithDay("Gambe", listOf("leg_extension" to 3))
        val sessionId = workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        assertEquals(0, awaitActive().validCompletedSets)
        clock.current = T0.plusSeconds(1_800)
        assertEquals("Mezz'ora senza serie resta una sessione vuota", SessionVerdict.EMPTY, viewModel.finishVerdict())

        viewModel.onFinish()

        assertEquals("Niente riepilogo: la sessione nulla viene scartata", WorkoutEvent.Discarded, viewModel.events.first())
        assertNull(db.workoutSessionDao().getSession(sessionId))
    }

    @Test
    fun finishingWithinAMinute_asksBeforeSaving() = runTest {
        val (_, dayId) = createRoutineWithDay("Gambe", listOf("leg_extension" to 3))
        workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        viewModel.onToggleComplete(awaitActive().exercises[0].sets[0].setId)
        assertEquals(1, awaitActive { it.completedSets == 1 }.validCompletedSets)

        clock.current = T0.plusSeconds(30)
        assertEquals(SessionVerdict.TOO_SHORT, viewModel.finishVerdict())
        clock.current = T0.plusSeconds(600)
        assertEquals(SessionVerdict.VALID, viewModel.finishVerdict())
    }

    @Test
    fun setupSavedFromTheExercisePage_reachesTheLogger_withItsNotes() = runTest {
        val seat = listOf(MachineSetupParam("Schienale", "3"))
        // Salvata "a freddo", prima di qualsiasi allenamento.
        workoutRepository.saveSetupFromLibrary("leg_extension", null, "Predefinita", seat, "Carico abituale 40 kg")
        val (_, dayId) = createRoutineWithDay("Gambe", listOf("leg_extension" to 3))
        workoutRepository.startFromRoutineDay(dayId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }

        val exercise = awaitActive().exercises.single()
        assertEquals(seat, exercise.setup.params)
        assertEquals("Carico abituale 40 kg", exercise.setup.notes)

        viewModel.onOpenSetup(exercise.sessionExerciseId)
        val sheet = viewModel.setupSheet.first { it != null }!!
        assertEquals("Carico abituale 40 kg", sheet.notes)
        viewModel.onSaveSetup(sheet.params, "Carico abituale 45 kg", "Predefinita")
        awaitActive { it.exercises.single().setup.notes == "Carico abituale 45 kg" }
    }
}
