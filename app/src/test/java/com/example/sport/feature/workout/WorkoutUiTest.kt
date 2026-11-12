package com.example.sport.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.R
import com.example.sport.core.data.SetMetric
import com.example.sport.core.database.T0
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.MachineSetupParam
import com.example.sport.core.model.SetType
import com.example.sport.core.workout.RestTimerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Interfaccia del logger con uno stato fittizio: righe, spunta, regolazione, timer, tastierino.
 * Schermo di un telefono reale: con timer e tastierino aperti servono ~900dp di altezza.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WorkoutUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun row(id: Long, number: Int, weight: String, reps: String, completed: Boolean = false) = SetRowUi(
        setId = id,
        number = number,
        position = number,
        type = SetType.WORKING,
        values = mapOf(SetMetric.WEIGHT to weight, SetMetric.REPS to reps),
        previous = "40 × 12",
        isCompleted = completed,
        isRecord = false,
    )

    private val exercise = WorkoutExerciseUi(
        sessionExerciseId = 1,
        exerciseId = "leg_extension",
        name = "Leg extension",
        muscleLabelRes = R.string.muscle_quadriceps,
        thumbnail = null,
        restLabel = "1:15",
        trackingType = ExerciseTrackingType.WEIGHT_REPS,
        metrics = listOf(SetMetric.WEIGHT, SetMetric.REPS),
        showSetup = true,
        setup = SetupUi(params = listOf(MachineSetupParam("Schienale", "3"), MachineSetupParam("Rullo tibie", "5")), hints = emptyList()),
        showRpe = false,
        wasReplaced = false,
        sets = listOf(row(11, 1, "42,5", "12", completed = true), row(12, 2, "45", "10")),
    )

    private fun actions() = WorkoutActions(
        onMinimize = { clicks += "minimize" },
        onFinishRequest = { clicks += "finish" },
        onFieldClick = { id, metric -> clicks += "field:$id:$metric" },
        onKey = { clicks += "key:$it" },
        onNext = { clicks += "next" },
        onCloseKeypad = { clicks += "close" },
        onToggleComplete = { clicks += "complete:$it" },
        onSetTypeChange = { id, type -> clicks += "type:$id:$type" },
        onRemoveSet = { clicks += "remove:$it" },
        onAddSet = { clicks += "addSet:$it" },
        onOpenSetup = { clicks += "setup:$it" },
        onToggleRpe = { clicks += "rpe:$it" },
        onRemoveExercise = { clicks += "removeExercise:$it" },
        onReplaceExercise = { clicks += "replace:$it" },
        onOpenExercise = { clicks += "open:$it" },
        onAddExercise = { clicks += "addExercise" },
        onDiscardRequest = { clicks += "discard" },
        onRestAdjust = { clicks += "rest:$it" },
        onRestSkip = { clicks += "skip" },
        onRestElapsed = { clicks += "elapsed" },
        now = { T0 },
        onGrantExactAlarms = { clicks += "grantExact" },
        onDismissExactAlarmHint = { clicks += "dismissHint" },
    )

    private fun state(editing: EditingUi? = null, rest: RestTimerState? = null, exactAlarmHint: Boolean = false) = WorkoutUiState.Active(
        sessionId = 99,
        title = "Gambe",
        startedAt = T0.minusSeconds(754),
        exercises = listOf(exercise),
        completedSets = 1,
        totalSets = 2,
        editing = editing,
        rest = rest,
        exactAlarmHint = exactAlarmHint,
    )

    @Test
    fun logger_showsProgressSetup_andRoutesTaps() {
        composeRule.setContent { SportTheme { WorkoutContent(state(), actions()) } }

        composeRule.onNodeWithText("12:34 · 1/2 serie").assertIsDisplayed()
        composeRule.onNodeWithText("Schienale 3 · Rullo tibie 5").performClick()
        composeRule.onNodeWithContentDescription("Completa la serie 2").performClick()
        composeRule.onNodeWithContentDescription("Peso kg: 45").performClick()
        composeRule.onNodeWithText("Termina").performClick()

        assertEquals(listOf("setup:1", "complete:12", "field:12:WEIGHT", "finish"), clicks)
    }

    @Test
    fun keypadAndRestTimer_appearWhenActive() {
        val editing = EditingUi(
            setId = 12,
            metric = SetMetric.WEIGHT,
            text = "47,5",
            exerciseName = "Leg extension",
            setPosition = 2,
            allowDecimal = true,
            stepLabel = "2,5",
        )
        val rest = RestTimerState(endsAt = T0.plusSeconds(90), totalSeconds = 120, nextExercise = "Leg press")
        composeRule.setContent { SportTheme { WorkoutContent(state(editing, rest), actions()) } }

        composeRule.onNodeWithText("Peso · serie 2 · Leg extension").assertIsDisplayed()
        composeRule.onNodeWithText("1:30").assertIsDisplayed()
        composeRule.onNodeWithText("Poi: Leg press").assertIsDisplayed()
        composeRule.onNodeWithText("+15 s").performClick()
        composeRule.onNodeWithContentDescription("Aumenta di 2,5").performClick()
        composeRule.onNodeWithContentDescription("Avanti").performClick()

        assertEquals(listOf("rest:15", "key:Increment", "next"), clicks)
    }

    @Test
    fun restEnd_withTheScreenOff_isLeftToTheAlarm() {
        val owner = FakeLifecycleOwner(Lifecycle.State.CREATED)
        val elapsed = RestTimerState(endsAt = T0, totalSeconds = 90, nextExercise = "Leg press")
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                SportTheme { WorkoutContent(state(rest = elapsed), actions()) }
            }
        }
        composeRule.waitForIdle()
        assertEquals("Logger non visibile: niente chiusura muta, avvisa l'allarme", emptyList<String>(), clicks)

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        assertEquals("Tornando nel logger il recupero scaduto si chiude", listOf("elapsed"), clicks)
    }

    private class FakeLifecycleOwner(initial: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = initial }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun restPanel_invitesToAllowExactAlarms_onlyWhenNeeded() {
        val rest = RestTimerState(endsAt = T0.plusSeconds(60), totalSeconds = 90, nextExercise = "Leg press")
        var hint by mutableStateOf(true)
        composeRule.setContent { SportTheme { WorkoutContent(state(rest = rest, exactAlarmHint = hint), actions()) } }

        composeRule.onNodeWithText("Consenti").performClick()
        composeRule.onNodeWithText("Non ora").performClick()
        assertEquals(listOf("grantExact", "dismissHint"), clicks)

        hint = false
        composeRule.onNodeWithText("Consenti").assertDoesNotExist()
        composeRule.onNodeWithText("1:00").assertIsDisplayed()
    }
}
