package com.example.sport.feature.routines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.R
import com.example.sport.core.data.ExerciseTargets
import com.example.sport.core.designsystem.component.LocalInlineDialogs
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.feature.exercises.DetailActions
import com.example.sport.feature.exercises.ExerciseDetailContent
import com.example.sport.feature.exercises.ExerciseDetailUi
import com.example.sport.feature.exercises.ExerciseDetailUiState
import com.example.sport.feature.exercises.RoutineDayTargetUi
import com.example.sport.feature.exercises.RoutineTargetUi
import com.example.sport.core.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/** Builder delle schede, elenco schede e "Aggiungi a una scheda" (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RoutinesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun exercise(id: Long, name: String, position: SupersetPosition = SupersetPosition.NONE) = BuilderExerciseUi(
        id = id,
        exerciseId = "ex$id",
        name = name,
        muscleLabelRes = R.string.muscle_chest_middle,
        thumbnail = null,
        sets = 3,
        repsMin = 8,
        repsMax = 12,
        restSeconds = 120,
        restIsDefault = true,
        defaultRestSeconds = 120,
        countsReps = true,
        supersetPosition = position,
    )

    private val dayA = BuilderDayUi(
        id = 10,
        name = "Giorno A",
        exercises = listOf(
            exercise(100, "Panca piana", SupersetPosition.FIRST),
            exercise(101, "Curl con bilanciere", SupersetPosition.LAST),
            exercise(102, "French press"),
        ),
        totalSets = 9,
        estimatedMinutes = 30,
    )
    private val dayB = BuilderDayUi(id = 11, name = "Giorno B", exercises = emptyList(), totalSets = 0, estimatedMinutes = 0)

    private val state = RoutineEditorUiState.Content(
        routine = RoutineEditorUi(
            id = 1,
            name = "Petto e braccia",
            isActive = false,
            parentName = "PPL",
            days = listOf(dayA, dayB),
            schedule = mapOf(DayOfWeek.MONDAY to 10L),
        ),
        selectedDayId = 10,
    )

    private fun actions() = BuilderActions(
        onBack = { calls += "back" },
        onRename = { calls += "rename:$it" },
        onSetActive = { calls += "active" },
        onDuplicate = { name, deload -> calls += "duplicate:$name:$deload" },
        onDelete = { calls += "delete" },
        onAssignWeekday = { day, dayId -> calls += "assign:$day:$dayId" },
        onSelectDay = { calls += "select:$it" },
        onAddDay = { calls += "addDay:$it" },
        onRenameDay = { id, name -> calls += "renameDay:$id:$name" },
        onDuplicateDay = { id, name -> calls += "duplicateDay:$id:$name" },
        onMoveDay = { id, delta -> calls += "moveDay:$id:$delta" },
        onDeleteDay = { calls += "deleteDay:$it" },
        onAddExercise = { calls += "addExercise:$it" },
        onReplaceExercise = { calls += "replace:$it" },
        onOpenExercise = { calls += "open:$it" },
        onUpdateTargets = { id, targets -> calls += "targets:$id:$targets" },
        onMoveExercise = { day, from, to -> calls += "move:$day:$from:$to" },
        onMoveExerciseToDay = { id, day -> calls += "moveTo:$id:$day" },
        onLinkSuperset = { calls += "link:$it" },
        onUnlinkSuperset = { calls += "unlink:$it" },
        onRemoveExercise = { calls += "remove:$it" },
    )

    /** I dialoghi con campi di testo in linea: in Robolectric la finestra separata non arriva a riposo. */
    private fun setContent(content: @Composable () -> Unit) = composeRule.setContent {
        CompositionLocalProvider(LocalInlineDialogs provides true) { SportTheme { content() } }
    }

    private fun setEditor() = setContent { RoutineEditorContent(state, actions()) }

    @Test
    fun weekCalendar_assignsADay_andTheRoutineCanBeActivated() {
        setEditor()
        composeRule.onNodeWithText("Variante di «PPL»").assertIsDisplayed()
        composeRule.onNodeWithText("Imposta come attiva").performClick()

        composeRule.onNodeWithContentDescription("Lunedì: Giorno A").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mercoledì: Riposo").performClick()
        composeRule.onNodeWithText("Mercoledì: cosa alleni?").assertIsDisplayed()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule.onNode(hasText("Riposo") and radio).assertIsSelected()
        composeRule.onNode(hasText("Giorno A") and radio).performClick()

        assertEquals(listOf("active", "assign:WEDNESDAY:10"), calls)
    }

    @Test
    fun exerciseMenus_supersetMoveAndAccessibleReorder() {
        setEditor()
        composeRule.onNodeWithText("Superserie").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Opzioni di Panca piana").performScrollTo().performClick()
        composeRule.onNodeWithText("Separa dalla superserie").performClick()

        composeRule.onNodeWithContentDescription("Opzioni di French press").performScrollTo().performClick()
        composeRule.onNodeWithText("Superserie con il successivo").assertDoesNotExist()
        composeRule.onNodeWithText("Sposta in un altro giorno").performClick()
        composeRule.onNode(hasText("Giorno B") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()

        // Alternativa accessibile al trascinamento (azioni personalizzate di TalkBack).
        val curl = composeRule.onNode(hasText("Curl con bilanciere", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .performScrollTo()
            .fetchSemanticsNode()
        composeRule.runOnIdle { curl.config[SemanticsActions.CustomActions].first { it.label == "Sposta giù" }.action() }

        assertEquals(listOf("unlink:100", "moveTo:102:11", "move:10:1:2"), calls)
    }

    @Test
    fun targetsSheet_stepsAndApplies() {
        setEditor()
        composeRule.onNodeWithText("Panca piana").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Aumenta Serie").performClick()
        composeRule.onNodeWithContentDescription("Aumenta Ripetizioni a").performClick()
        composeRule.onNodeWithContentDescription("Riduci Recupero").performClick()
        composeRule.onNodeWithText("Usa il predefinito (2:00)").assertIsDisplayed()
        composeRule.onNodeWithText("Applica").performClick()

        assertEquals(listOf("targets:100:${ExerciseTargets(sets = 4, repsMin = 8, repsMax = 13, restSeconds = 105)}"), calls)
    }

    @Test
    fun dayMenuAndNewDay() {
        setEditor()
        composeRule.onNodeWithContentDescription("Opzioni del giorno").performScrollTo().performClick()
        composeRule.onNodeWithText("Sposta prima").assertDoesNotExist()
        composeRule.onNodeWithText("Elimina giorno").performClick()
        composeRule.onNodeWithText("Elimina").performClick()

        composeRule.onNodeWithText("Giorno").performScrollTo().performClick()
        composeRule.onNodeWithText("Giorno C").assertIsDisplayed()
        composeRule.onNodeWithText("Salva").performClick()

        composeRule.onNodeWithText("Aggiungi esercizio").performScrollTo().performClick()
        assertEquals(listOf("deleteDay:10", "addDay:Giorno C", "addExercise:10"), calls)
    }

    @Test
    fun draggingTheHandle_movesTheExercise() {
        setEditor()
        composeRule.onNodeWithContentDescription("Trascina per riordinare Panca piana").performScrollTo().performTouchInput {
            down(center)
            repeat(30) { moveBy(Offset(0f, 20f)) }
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Trascinato verso il basso: $calls", calls.singleOrNull()?.startsWith("move:10:0:") == true)
    }

    @Test
    fun duplicateDialog_proposesTheDeloadName() {
        setEditor()
        composeRule.onNodeWithContentDescription("Opzioni scheda").performClick()
        composeRule.onNodeWithText("Duplica o crea variante").performClick()
        composeRule.onNodeWithText("Petto e braccia (copia)").assertIsDisplayed()
        composeRule.onNodeWithText("Settimana di scarico").performClick()
        composeRule.onNodeWithText("Petto e braccia · Scarico").assertIsDisplayed()
        composeRule.onNodeWithText("Crea copia").performClick()
        assertEquals(listOf("duplicate:Petto e braccia · Scarico:true"), calls)
    }

    @Test
    fun routinesList_opensCards_andCreatesARoutine() {
        val list = RoutinesUiState.Content(
            routines = listOf(
                RoutineUi(
                    id = 7,
                    name = "PPL · Scarico",
                    splitLabelRes = R.string.split_custom,
                    isActive = false,
                    dayNames = listOf("Push", "Pull", "Legs"),
                    scheduledDays = setOf(DayOfWeek.MONDAY),
                    parentName = "PPL",
                ),
            ),
            templates = emptyList(),
        )
        setContent {
            RoutinesContent(
                state = list,
                onUseTemplate = { calls += "template" },
                onSetActive = { calls += "active:$it" },
                onOpenRoutine = { calls += "open:$it" },
                onCreateRoutine = { calls += "create:$it" },
            )
        }
        composeRule.onNodeWithText("Variante di «PPL»").assertIsDisplayed()
        composeRule.onNodeWithText("PPL · Scarico").performClick()
        composeRule.onNodeWithText("Nuova scheda").performClick()
        composeRule.onNodeWithText("Salva").performClick()
        assertEquals(listOf("open:7", "create:La mia scheda"), calls)
    }

    @Test
    fun exerciseDetail_addsToARoutineDay() {
        val detail = ExerciseDetailUi(
            id = "leg_press",
            name = "Leg press",
            nameEn = null,
            isFavorite = false,
            isCustom = false,
            equipmentLabelRes = R.string.equipment_machine,
            levelLabelRes = R.string.level_beginner,
            mechanicLabelRes = null,
            categoryLabelRes = null,
            primaryMuscles = listOf(Muscle.QUADRICEPS),
            secondaryMuscles = emptyList(),
            frames = emptyList(),
            userVideo = null,
            catalogVideo = null,
            setupHints = emptyList(),
            savedSetups = emptyList(),
            instructions = emptyList(),
            keyPoints = emptyList(),
            commonMistakes = emptyList(),
            isEnglishContent = false,
            mediaCredit = null,
            alternatives = emptyList(),
        )
        val actions = DetailActions(
            onBack = {}, onToggleFavorite = {}, onOpenExercise = {}, onEdit = {}, onDeleteRequest = {},
            onRecordVideo = {}, onPickVideo = {}, onRemoveVideo = {},
            onAddToRoutineDay = { calls += "addTo:$it" },
        )
        val targets = listOf(RoutineTargetUi(1, "Upper Lower", isActive = true, days = listOf(RoutineDayTargetUi(10, "Lower A"), RoutineDayTargetUi(11, "Lower B"))))
        composeRule.setContent {
            SportTheme { ExerciseDetailContent(ExerciseDetailUiState.Content(detail), actions, routineTargets = targets) }
        }
        composeRule.onNodeWithContentDescription("Aggiungi a una scheda").performClick()
        composeRule.onNodeWithText("Aggiungi «Leg press» a…").assertIsDisplayed()
        composeRule.onNodeWithText("Lower B").performClick()
        assertEquals(listOf("addTo:11"), calls)
    }
}
