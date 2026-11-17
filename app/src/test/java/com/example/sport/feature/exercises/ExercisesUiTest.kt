package com.example.sport.feature.exercises

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.R
import com.example.sport.core.data.CustomExerciseDraft
import com.example.sport.core.designsystem.component.LocalInlineDialogs
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import com.example.sport.core.ui.exercise.ExerciseRowUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Libreria con filtri, editor degli esercizi personalizzati e scheda esercizio (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ExercisesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun row(id: String, name: String, custom: Boolean = false) = ExerciseRowUi(
        id = id,
        name = name,
        muscleLabelRes = R.string.muscle_hamstrings,
        equipmentLabelRes = R.string.equipment_barbell,
        thumbnail = null,
        isFavorite = false,
        isCustom = custom,
    )

    private fun libraryActions() = LibraryActions(
        onQueryChange = { clicks += "query:$it" },
        onGroupSelected = { clicks += "group:$it" },
        onToggleFavorites = { clicks += "favorites" },
        onToggleEquipment = { clicks += "equipment:$it" },
        onToggleCategory = { clicks += "category:$it" },
        onToggleMuscle = { clicks += "muscle:$it" },
        onIncludeSecondaryChange = { clicks += "secondary:$it" },
        onOnlyCustomChange = { clicks += "custom:$it" },
        onClearAdvanced = { clicks += "clear" },
        onOpenExercise = { clicks += "open:$it" },
        onCreateExercise = { clicks += "create" },
    )

    @Test
    fun library_filterSheet_quickChips_andNewExercise() {
        val state = ExerciseListUiState(isLoading = false, exercises = listOf(row("a", "Stacco rumeno"), row("b", "Il mio stacco", custom = true)))
        val filters = LibraryFilters(equipment = setOf(Equipment.BARBELL), categories = setOf(ExerciseCategory.STRENGTH))
        composeRule.setContent { SportTheme { ExerciseListContent(state, query = "", filters = filters, actions = libraryActions()) } }

        composeRule.onNodeWithText("Tuo").assertIsDisplayed()
        composeRule.onNodeWithText("Preferiti").performClick()
        composeRule.onNodeWithText("Nuovo esercizio").performClick()

        composeRule.onNodeWithText("Filtri · 2").performClick()
        composeRule.onNodeWithText("Mostra 2 esercizi").assertIsDisplayed()
        composeRule.onNodeWithText("Kettlebell").performClick()
        composeRule.onNodeWithText("Pliometria").performClick()
        composeRule.onNodeWithText("Deltoide posteriore").performScrollTo().performClick()
        composeRule.onNodeWithText("Includi i muscoli secondari").performScrollTo().performClick()
        composeRule.onNodeWithText("Azzera").performClick()

        assertEquals(
            listOf(
                "favorites",
                "create",
                "equipment:${Equipment.KETTLEBELL}",
                "category:${ExerciseCategory.PLYOMETRIC}",
                "muscle:${Muscle.SHOULDERS_REAR}",
                "clear",
            ),
            clicks.filterNot { it.startsWith("secondary") },
        )
        assertEquals(listOf("secondary:true"), clicks.filter { it.startsWith("secondary") })
    }

    @Test
    fun library_groupChip_togglesOff() {
        val state = ExerciseListUiState(isLoading = false, exercises = listOf(row("a", "Leg curl")))
        composeRule.setContent {
            SportTheme { ExerciseListContent(state, "", LibraryFilters(group = MuscleGroup.HAMSTRINGS), libraryActions()) }
        }
        composeRule.onNodeWithTag(QUICK_FILTERS_TAG).performScrollToNode(hasText("Femorali"))
        composeRule.onNodeWithTag(QUICK_FILTERS_TAG).onChildren().filterToOne(hasText("Femorali")).performClick()
        assertEquals("Toccare il gruppo già scelto lo deseleziona", listOf("group:null"), clicks)
    }

    private fun editorActions() = EditorActions(
        onNameChange = { clicks += "name:$it" },
        onEquipmentChange = { clicks += "equipment:$it" },
        onTrackingChange = { clicks += "tracking:$it" },
        onTogglePrimary = { clicks += "primary:$it" },
        onToggleSecondary = { clicks += "secondary:$it" },
        onMechanicChange = { clicks += "mechanic:$it" },
        onUnilateralChange = { clicks += "unilateral:$it" },
        onRestAdjust = { clicks += "rest:$it" },
        onAddSetupHint = { clicks += "hint:$it" },
        onRemoveSetupHint = { clicks += "removeHint:$it" },
        onNotesChange = { clicks += "notes" },
        onSave = { clicks += "save" },
        onClose = { clicks += "close" },
    )

    @Test
    fun editor_showsErrors_andRoutesEdits() {
        val state = ExerciseEditorUiState(
            isLoading = false,
            isNew = true,
            draft = CustomExerciseDraft(name = "", setupParamHints = listOf("Schienale")),
            showErrors = true,
        )
        composeRule.setContent { SportTheme { ExerciseEditorContent(state, editorActions()) } }

        composeRule.onNodeWithText("Inserisci un nome").assertIsDisplayed()
        composeRule.onNodeWithText("Scegli almeno un muscolo principale").assertIsDisplayed()
        // Senza muscoli principali, "Quadricipiti" compare sia tra i principali sia tra i secondari.
        composeRule.onAllNodesWithText("Quadricipiti")[0].performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Aumenta il recupero di 15 secondi").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Rimuovi Schienale").performScrollTo().performClick()
        composeRule.onNodeWithText("Nuova regolazione").performScrollTo().performTextInput("Altezza sedile")
        composeRule.onNodeWithText("Aggiungi").performClick()
        composeRule.onNodeWithText("Salva esercizio").performClick()

        assertEquals(
            listOf("primary:${Muscle.QUADRICEPS}", "rest:15", "removeHint:Schienale", "hint:Altezza sedile", "save"),
            clicks,
        )
    }

    private fun detailActions() = DetailActions(
        onBack = { clicks += "back" },
        onToggleFavorite = { clicks += "favorite" },
        onOpenExercise = { clicks += "open:$it" },
        onEdit = { clicks += "edit" },
        onDeleteRequest = { clicks += "delete" },
        onRecordVideo = { clicks += "record" },
        onPickVideo = { clicks += "pick" },
        onRemoveVideo = { clicks += "removeVideo" },
    )

    private fun detail(custom: Boolean) = ExerciseDetailUi(
        id = "custom_1",
        name = "Il mio stacco",
        nameEn = null,
        isFavorite = false,
        isCustom = custom,
        equipmentLabelRes = R.string.equipment_barbell,
        levelLabelRes = R.string.level_beginner,
        mechanicLabelRes = R.string.mechanic_compound,
        categoryLabelRes = null,
        primaryMuscles = listOf(Muscle.HAMSTRINGS),
        secondaryMuscles = listOf(Muscle.GLUTES),
        frames = emptyList(),
        userVideo = null,
        catalogVideo = null,
        setupHints = emptyList(),
        savedSetups = listOf(
            SavedSetupUi(
                id = 7L,
                label = "Palestra centro",
                params = listOf(MachineSetupParam("Altezza supporti", "12")),
                notes = "Presa larga",
                isLatest = true,
            ),
        ),
        instructions = listOf("Schiena neutra"),
        keyPoints = emptyList(),
        commonMistakes = emptyList(),
        isEnglishContent = false,
        mediaCredit = null,
        alternatives = listOf(row("stiff_legged_barbell_deadlift", "Stacco a gambe tese con bilanciere")),
    )

    @Test
    fun detail_customExercise_menuVideoSetupsAndAlternatives() {
        composeRule.setContent { SportTheme { ExerciseDetailContent(ExerciseDetailUiState.Content(detail(custom = true)), detailActions()) } }

        composeRule.onNodeWithText("Esercizio creato da te").assertIsDisplayed()
        composeRule.onNodeWithText("Altezza supporti: 12").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Aggiungi il tuo video").performScrollTo().performClick()
        composeRule.onNodeWithText("Scegli dalla galleria").performClick()

        composeRule.onNodeWithContentDescription("Altre opzioni").performClick()
        composeRule.onNodeWithText("Modifica").performClick()
        composeRule.onNodeWithContentDescription("Altre opzioni").performClick()
        composeRule.onNodeWithText("Elimina esercizio").performClick()

        // La sezione dei settaggi allunga la scheda: le alternative vanno portate in vista.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Stacco a gambe tese con bilanciere"))
        composeRule.onNodeWithText("Stacco a gambe tese con bilanciere").performClick()
        assertEquals(listOf("pick", "edit", "delete", "open:stiff_legged_barbell_deadlift"), clicks)
    }

    // ------------------------------------------------------------------ Settaggi "a freddo" e gestione dati

    /** I dialoghi con campi di testo in linea: in Robolectric la finestra separata non arriva a riposo. */
    private fun setInline(content: @Composable () -> Unit) = composeRule.setContent {
        CompositionLocalProvider(LocalInlineDialogs provides true) { SportTheme { content() } }
    }

    private fun setupActions() = detailActions().copy(
        onSaveSetup = { id, label, params, notes ->
            clicks += "save:$id:$label:${params.joinToString { "${it.name}=${it.value}" }}:$notes"
        },
        onDeleteSetup = { clicks += "deleteSetup:$it" },
        onResetHistory = { clicks += "reset" },
    )

    @Test
    fun detail_setups_areEditedWithoutStartingAWorkout() {
        setInline { ExerciseDetailContent(ExerciseDetailUiState.Content(detail(custom = false)), setupActions()) }

        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Palestra centro · usato dal logger"))
        composeRule.onNodeWithText("I miei settaggi / Note macchinario").assertIsDisplayed()
        composeRule.onNodeWithText("Altezza supporti: 12").assertIsDisplayed()
        composeRule.onNodeWithText("Presa larga").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Modifica settaggi Palestra centro").performClick()
        composeRule.onNodeWithText("Modifica settaggi").assertIsDisplayed()
        composeRule.onNode(hasText("12") and hasSetTextAction()).performTextReplacement("14")
        composeRule.onNode(hasText("Presa larga") and hasSetTextAction()).performTextReplacement("Presa stretta")
        composeRule.onNodeWithText("Salva").performClick()

        composeRule.onNodeWithContentDescription("Modifica settaggi Palestra centro").performClick()
        composeRule.onNodeWithText("Elimina profilo").performScrollTo().performClick()
        composeRule.onNodeWithText("Eliminare il profilo «Palestra centro»?").assertIsDisplayed()
        composeRule.onNode(hasText("Elimina") and hasClickAction()).performClick()

        assertEquals(listOf("save:7:Palestra centro:Altezza supporti=14:Presa stretta", "deleteSetup:7"), clicks)
    }

    @Test
    fun detail_withoutSetups_startsFromTheSuggestedFields_andCanResetTheHistory() {
        val exercise = detail(custom = false).copy(savedSetups = emptyList(), setupHints = listOf("Schienale", "Sedile"))
        setInline { ExerciseDetailContent(ExerciseDetailUiState.Content(exercise), setupActions(), hasHistory = true) }

        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Aggiungi settaggi"))
        composeRule.onNodeWithText("Voci suggerite: Schienale · Sedile").assertIsDisplayed()
        composeRule.onNodeWithText("Aggiungi settaggi").performClick()
        composeRule.onNodeWithText("Nuovi settaggi").assertIsDisplayed()
        // Finché non c'è almeno un valore o una nota non c'è nulla da salvare.
        composeRule.onNodeWithText("Salva").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Valore")[0].performTextInput("3")
        composeRule.onNodeWithText("Note macchinario").performScrollTo().performTextInput("Carico abituale 40 kg")
        composeRule.onNodeWithText("Salva").performClick()

        list.performScrollToNode(hasText("Azzera storico esercizio"))
        composeRule.onNodeWithText("Gestione dati").assertIsDisplayed()
        composeRule.onNodeWithText("Azzera storico esercizio").performClick()
        composeRule.onNodeWithText("Azzerare lo storico di Il mio stacco?").assertIsDisplayed()
        composeRule.onNodeWithText("Azzera storico").performClick()

        assertEquals(listOf("save:null:Predefinita:Schienale=3, Sedile=:Carico abituale 40 kg", "reset"), clicks)
    }

    @Test
    fun detail_catalogExercise_hasNoEditMenu_butCanRecordAVideo() {
        composeRule.setContent { SportTheme { ExerciseDetailContent(ExerciseDetailUiState.Content(detail(custom = false)), detailActions()) } }

        composeRule.onNodeWithContentDescription("Altre opzioni").assertDoesNotExist()
        composeRule.onNodeWithText("Aggiungi il tuo video").performScrollTo().performClick()
        composeRule.onNodeWithText("Registra un video").performClick()
        composeRule.onNodeWithText("Rimuovi il tuo video").assertDoesNotExist()
        assertEquals(listOf("record"), clicks)
    }

    @Test
    fun detail_importInProgress_replacesTheVideoButton() {
        composeRule.setContent {
            SportTheme { ExerciseDetailContent(ExerciseDetailUiState.Content(detail(custom = false), isImportingVideo = true), detailActions()) }
        }
        composeRule.onNodeWithText("Importazione del video…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Aggiungi il tuo video").assertDoesNotExist()
    }

    @Test
    fun detail_offlineVideoNotCached_showsTheAnatomyWithADiscreetBadge() {
        val exercise = detail(custom = false).copy(
            catalogVideo = "https://media.example.org/exercises/stacco.mp4",
            catalogVideoOnlineOnly = true,
        )
        composeRule.setContent { SportTheme { ExerciseDetailContent(ExerciseDetailUiState.Content(exercise), detailActions()) } }

        composeRule.onNodeWithText("Video disponibile online").assertIsDisplayed()
        // Mappa anatomica al posto del video: nessun errore a schermo.
        composeRule.onNodeWithContentDescription("Esecuzione di Il mio stacco").assertIsDisplayed()
        composeRule.onNodeWithText("errore", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun detail_framesThatCannotLoad_fallBackToTheAnatomy_notABlankBox() {
        val missing = java.io.File(composeRuleContextDir(), "mai-scaricato/0.jpg")
        val exercise = detail(custom = false).copy(frames = listOf(missing, missing))
        composeRule.setContent { SportTheme { ExerciseDetailContent(ExerciseDetailUiState.Content(exercise), detailActions()) } }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(EXERCISE_ANATOMY_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(EXERCISE_ANATOMY_TAG).assertIsDisplayed()
    }

    private fun composeRuleContextDir(): java.io.File =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir

    @Test
    fun detail_onlineVideo_hasNoBadge() {
        val exercise = detail(custom = false).copy(catalogVideo = "https://media.example.org/exercises/stacco.gif")
        composeRule.setContent { SportTheme { ExerciseDetailContent(ExerciseDetailUiState.Content(exercise), detailActions()) } }
        composeRule.onNodeWithText("Video disponibile online").assertDoesNotExist()
    }
}
