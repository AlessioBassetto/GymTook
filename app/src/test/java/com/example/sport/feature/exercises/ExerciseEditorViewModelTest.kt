package com.example.sport.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.data.CustomExerciseDraft
import com.example.sport.core.data.CustomExerciseRepository
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.media.UserVideoStore
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.Muscle
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ExerciseEditorViewModelTest : DatabaseTest() {

    private lateinit var repository: CustomExerciseRepository

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        seedBundledCatalog()
        repository = CustomExerciseRepository(
            db.exerciseDao(),
            UserVideoStore(File(context.filesDir, "editor-videos"), context, Dispatchers.IO),
            TestClock(T0),
        )
    }

    private val editors = mutableListOf<ExerciseEditorViewModel>()

    @After
    fun tearDown() {
        // La ricerca ritardata dei nomi uguali non deve partire dopo la chiusura del database
        // (l'eccezione finirebbe, non gestita, nel test successivo).
        editors.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg args: Pair<String, Any?>) =
        ExerciseEditorViewModel(SavedStateHandle(mapOf(*args)), repository).also { editors += it }

    private suspend fun ExerciseEditorViewModel.loaded() = uiState.first { !it.isLoading }

    @Test
    fun newExercise_fromASearch_isPrefilled_andValidatedOnSave() = runTest {
        val editor = viewModel("initialName" to "Pressa Panatta ")
        val start = editor.loaded()
        assertTrue(start.isNew)
        assertEquals("Pressa Panatta", start.draft.name)
        assertFalse("Il nome arrivato dalla ricerca non conta come modifica", start.isDirty)
        assertFalse(start.primaryError)

        editor.onSave()
        assertTrue("Senza muscolo principale non si salva", editor.uiState.value.primaryError)

        editor.onTogglePrimary(Muscle.QUADRICEPS)
        editor.onTogglePrimary(Muscle.GLUTES)
        editor.onToggleSecondary(Muscle.GLUTES)
        assertEquals("Già principale: non diventa secondario", emptyList<Muscle>(), editor.uiState.value.draft.secondaryMuscles)
        editor.onToggleSecondary(Muscle.HAMSTRINGS)
        editor.onRestAdjust(+30)
        editor.onAddSetupHint("Schienale")
        editor.onAddSetupHint("schienale")
        assertEquals(listOf("Schienale"), editor.uiState.value.draft.setupParamHints)
        assertTrue(editor.uiState.value.isDirty)

        editor.onSave()
        val saved = editor.events.first() as ExerciseEditorEvent.Saved
        assertTrue(saved.isNew)
        val stored = repository.getDraft(saved.exerciseId)!!
        assertEquals(listOf(Muscle.QUADRICEPS, Muscle.GLUTES), stored.primaryMuscles)
        assertEquals(listOf(Muscle.HAMSTRINGS), stored.secondaryMuscles)
        assertEquals(CustomExerciseDraft.DEFAULT_REST_SECONDS + 30, stored.defaultRestSeconds)
    }

    @Test
    fun equipment_suggestsWhatToTrack_untilTheUserChooses() = runTest {
        val editor = viewModel()
        editor.loaded()
        editor.onEquipmentChange(Equipment.BODYWEIGHT)
        assertEquals(ExerciseTrackingType.BODYWEIGHT_REPS, editor.uiState.value.draft.trackingType)
        editor.onEquipmentChange(Equipment.CARDIO_MACHINE)
        assertEquals(ExerciseTrackingType.DISTANCE_DURATION, editor.uiState.value.draft.trackingType)

        editor.onTrackingChange(ExerciseTrackingType.DURATION)
        editor.onEquipmentChange(Equipment.MACHINE)
        assertEquals("Scelta dell'utente rispettata", ExerciseTrackingType.DURATION, editor.uiState.value.draft.trackingType)
    }

    @Test
    fun existingExercise_isLoaded_andUpdated() = runTest {
        val id = repository.create(CustomExerciseDraft(name = "Chest press Technogym", primaryMuscles = listOf(Muscle.CHEST_MIDDLE)))
        val editor = viewModel("exerciseId" to id)
        val loaded = editor.loaded()
        assertFalse(loaded.isNew)
        assertEquals("Chest press Technogym", loaded.draft.name)

        editor.onNameChange("Chest press convergente")
        editor.onSave()
        val saved = editor.events.first() as ExerciseEditorEvent.Saved
        assertEquals(ExerciseEditorEvent.Saved(id, isNew = false), saved)
        assertEquals("Chest press convergente", db.exerciseDao().getById(id)!!.name)
    }

    @Test
    fun sameNameWarning_andDraftSurvivesProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val editor = ExerciseEditorViewModel(handle, repository)
        editor.loaded()
        editor.onNameChange("Leg Extension")
        assertEquals("Leg extension", editor.uiState.first { it.sameNameAs != null }.sameNameAs)

        editor.onTogglePrimary(Muscle.QUADRICEPS)
        val restored = ExerciseEditorViewModel(handle, repository).loaded()
        assertEquals("Leg Extension", restored.draft.name)
        assertEquals(listOf(Muscle.QUADRICEPS), restored.draft.primaryMuscles)
        assertTrue(restored.isDirty)
    }
}
