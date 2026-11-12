package com.example.sport.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.designsystem.component.NumericKeypad
import com.example.sport.core.designsystem.input.NumericInputConfig
import com.example.sport.core.designsystem.input.NumericInputState
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.feature.today.PlannedExerciseUi
import com.example.sport.feature.today.TodayContent
import com.example.sport.feature.today.TodayUiState
import com.example.sport.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Test di interfaccia Compose eseguiti sulla JVM (Robolectric), senza emulatore. */
@RunWith(AndroidJUnit4::class)
class ComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keypad_editsAPrefilledWeight_withDigitsStepsAndNext() {
        var state by mutableStateOf(NumericInputState.of(80.0, NumericInputConfig.WeightKg))
        var nextPressed = 0
        composeRule.setContent {
            SportTheme {
                Column {
                    Text(state.displayText, Modifier.testTag("value"))
                    NumericKeypad(
                        onKey = { state = state.reduce(it) },
                        onDone = { nextPressed++ },
                        stepLabel = "2,5",
                    )
                }
            }
        }
        listOf("4", "2", "Virgola", "5").forEach { composeRule.onNodeWithContentDescription(it).performClick() }
        composeRule.onNodeWithTag("value").assertTextEquals("42,5")

        composeRule.onNodeWithContentDescription("Aumenta di 2,5").performClick()
        composeRule.onNodeWithTag("value").assertTextEquals("45")

        composeRule.onNodeWithContentDescription("Cancella").performClick()
        composeRule.onNodeWithTag("value").assertTextEquals("4")

        composeRule.onNodeWithContentDescription("Avanti").performClick()
        assertEquals(1, nextPressed)
    }

    @Test
    fun today_withoutRoutine_leadsToRoutines() {
        var openedRoutines = false
        composeRule.setContent {
            SportTheme {
                TodayContent(
                    state = TodayUiState.NoRoutine("Lunedì 2 novembre"),
                    onOpenRoutines = { openedRoutines = true },
                    onOpenExercise = {},
                )
            }
        }
        composeRule.onNodeWithText("Nessuna scheda attiva").assertIsDisplayed()
        composeRule.onNodeWithText("Vai alle schede").performClick()
        assertTrue(openedRoutines)
    }

    @Test
    fun today_workout_showsPlanAndOpensExercise() {
        var opened: String? = null
        var startedDay: Long? = null
        composeRule.setContent {
            SportTheme {
                TodayContent(
                    onStartWorkout = { startedDay = it },
                    state = TodayUiState.Workout(
                        dateLabel = "Lunedì 2 novembre",
                        routineName = "Push Pull Legs",
                        routineDayId = 7,
                        dayName = "Push",
                        exercises = listOf(
                            PlannedExerciseUi(
                                exerciseId = "barbell_bench_press",
                                name = "Panca piana con bilanciere",
                                muscleLabelRes = R.string.muscle_chest_middle,
                                equipmentLabelRes = R.string.equipment_barbell,
                                sets = 4,
                                reps = "6–8",
                                rest = "3:00",
                                thumbnail = null,
                            ),
                        ),
                        totalSets = 4,
                        estimatedMinutes = 15,
                    ),
                    onOpenRoutines = {},
                    onOpenExercise = { opened = it },
                )
            }
        }
        composeRule.onNodeWithText("Push").assertIsDisplayed()
        composeRule.onNodeWithText("1 esercizi · 4 serie · ≈ 15 min").assertIsDisplayed()
        composeRule.onNodeWithText("4 × 6–8").assertIsDisplayed()
        composeRule.onNodeWithText("Panca piana con bilanciere").performClick()
        assertEquals("barbell_bench_press", opened)
        composeRule.onNodeWithText("Inizia allenamento").performClick()
        assertEquals(7L, startedDay)
    }
}
