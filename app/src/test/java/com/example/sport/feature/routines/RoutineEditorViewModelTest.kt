package com.example.sport.feature.routines

import com.example.sport.core.database.calorieRepository
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.data.ExerciseRepository
import com.example.sport.core.data.ExerciseTargets
import com.example.sport.core.data.RoutineBuilderRepository
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.media.MediaConfig
import com.example.sport.core.media.MediaRepository
import com.example.sport.core.media.OfflineMediaStore
import com.example.sport.core.workout.TestClock
import com.example.sport.feature.workout.ExercisePickerViewModel
import com.example.sport.core.data.WorkoutRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RoutineEditorViewModelTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var builder: RoutineBuilderRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var mediaRepository: MediaRepository

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        seedBundledCatalog()
        builder = RoutineBuilderRepository(db, clock)
        exerciseRepository = ExerciseRepository(db.exerciseDao(), db.mediaDao(), clock)
        val config = MediaConfig("")
        mediaRepository = MediaRepository(config, OfflineMediaStore(File(context.filesDir, "editor-media"), config, OkHttpClient(), Dispatchers.IO), db.mediaDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun editor(routineId: Long, handle: SavedStateHandle = SavedStateHandle(mapOf("routineId" to routineId))) =
        RoutineEditorViewModel(handle, builder, exerciseRepository, mediaRepository)

    private suspend fun RoutineEditorViewModel.content(predicate: (RoutineEditorUiState.Content) -> Boolean = { true }) =
        uiState.first { it is RoutineEditorUiState.Content && predicate(it) } as RoutineEditorUiState.Content

    @Test
    fun dayManagement_selectsTheNewDay_andFallsBackWhenDeleted() = runTest {
        val routineId = builder.createRoutine("Upper Lower", "Upper")
        val viewModel = editor(routineId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        val start = viewModel.content()
        assertEquals("Upper", start.selectedDay!!.name)
        assertTrue(start.routine.isActive)

        viewModel.onAddDay("  Lower ")
        val withLower = viewModel.content { it.selectedDay?.name == "Lower" }
        assertEquals(listOf("Upper", "Lower"), withLower.routine.days.map { it.name })

        viewModel.onMoveDay(withLower.selectedDayId!!, -1)
        viewModel.content { state -> state.routine.days.map { it.name } == listOf("Lower", "Upper") }

        viewModel.onDeleteDay(withLower.selectedDayId)
        val afterDelete = viewModel.content { it.routine.days.size == 1 }
        assertEquals("Si torna al primo giorno rimasto", "Upper", afterDelete.selectedDay!!.name)

        viewModel.onAddDay("   ")
        assertEquals("Nome vuoto: nessun giorno creato", 1, viewModel.content().routine.days.size)
    }

    @Test
    fun exercises_targetsSupersetAndSchedule_areMappedForTheUi() = runTest {
        val routineId = builder.createRoutine("Braccia", "Giorno A")
        val dayId = db.routineDao().getDays(routineId).single().id
        listOf("barbell_curl", "lying_triceps_press", "treadmill_running").forEach { builder.addExercise(dayId, it) }
        val viewModel = editor(routineId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        val (curl, _, treadmill) = viewModel.content { it.selectedDay?.exercises?.size == 3 }.selectedDay!!.exercises
        assertFalse("Il tapis roulant non conta ripetizioni", treadmill.countsReps)
        assertTrue(curl.restIsDefault)

        viewModel.onLinkSuperset(curl.id)
        viewModel.onUpdateTargets(curl.id, ExerciseTargets(sets = 4, repsMin = 10, repsMax = 12, restSeconds = 60))
        viewModel.onAssignWeekday(DayOfWeek.WEDNESDAY, dayId)

        val updated = viewModel.content { state ->
            state.selectedDay!!.exercises.first().sets == 4 && state.routine.schedule.isNotEmpty()
        }
        val (updatedCurl, triceps, _) = updated.selectedDay!!.exercises
        assertEquals(SupersetPosition.FIRST, updatedCurl.supersetPosition)
        assertEquals(SupersetPosition.LAST, triceps.supersetPosition)
        assertEquals(60, updatedCurl.restSeconds)
        assertFalse(updatedCurl.restIsDefault)
        assertEquals(mapOf(DayOfWeek.WEDNESDAY to dayId), updated.routine.schedule)
        assertEquals(10, updated.selectedDay!!.totalSets)
    }

    @Test
    fun duplicateAndDelete_emitEvents() = runTest {
        val routineId = builder.createRoutine("PPL", "Push")
        val viewModel = editor(routineId)
        backgroundScope.launch(Dispatchers.Main) { viewModel.uiState.collect {} }
        viewModel.content()

        viewModel.onDuplicate("PPL · Scarico", deload = true)
        val copy = viewModel.events.first() as RoutineEditorEvent.CopyCreated
        assertEquals("PPL · Scarico", copy.name)
        assertEquals(routineId, db.routineDao().getRoutine(copy.routineId)!!.parentRoutineId)

        viewModel.onDelete()
        assertEquals(RoutineEditorEvent.Deleted, viewModel.events.first())
        assertNull(db.routineDao().getRoutine(routineId))
    }

    @Test
    fun picker_inRoutineMode_addsAndReplacesInTheDay() = runTest {
        val routineId = builder.createRoutine("Gambe", "Giorno A")
        val dayId = db.routineDao().getDays(routineId).single().id
        val workoutRepository = WorkoutRepository(db.workoutSessionDao(), db.machineSetupDao(), db.calorieRepository(clock), clock)
        fun picker(vararg args: Pair<String, Any?>) =
            ExercisePickerViewModel(SavedStateHandle(mapOf(*args)), workoutRepository, builder, exerciseRepository, mediaRepository)

        val adder = picker("routineDayId" to dayId)
        adder.onPick("leg_press")
        adder.done.first()
        val added = db.routineDao().getRoutineExercises(dayId).single()
        assertEquals("leg_press", added.exerciseId)

        val replacer = picker("replaceRoutineExerciseId" to added.id)
        assertTrue(replacer.uiState.first { it.suggestions.isNotEmpty() }.isReplacing)
        replacer.onPick("hack_squat")
        replacer.done.first()
        assertEquals(listOf("hack_squat"), db.routineDao().getRoutineExercises(dayId).map { it.exerciseId })
    }
}
