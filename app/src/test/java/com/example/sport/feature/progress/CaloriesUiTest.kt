package com.example.sport.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.T0
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.database.relation.SessionSummary
import com.example.sport.core.designsystem.component.LocalInlineDialogs
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.BiologicalSex
import com.example.sport.feature.workout.WorkoutSummaryContent
import com.example.sport.feature.workout.WorkoutSummaryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Calorie nel riepilogo, nello storico e nel dettaglio; profilo corporeo nella schermata del peso. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CaloriesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val today = LocalDate.of(2026, 11, 2)

    private fun setContent(content: @Composable () -> Unit) = composeRule.setContent {
        CompositionLocalProvider(LocalInlineDialogs provides true) { SportTheme { content() } }
    }

    private fun summary(calories: String?, suggestProfile: Boolean = false) = WorkoutSummaryUiState.Content(
        title = "Gambe A",
        duration = "1 h 5 min",
        sets = "16",
        volume = "9.800 kg",
        calories = calories,
        suggestProfile = suggestProfile,
        records = emptyList(),
        exercises = emptyList(),
    )

    @Test
    fun summary_showsTheCalories_nextToDurationVolumeAndSets() {
        setContent { WorkoutSummaryContent(summary("412 kcal"), onDone = {}) }
        composeRule.onNodeWithText("412 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("Calorie").assertIsDisplayed()
        composeRule.onNodeWithText("1 h 5 min").assertIsDisplayed()
        composeRule.onNodeWithText("9.800 kg").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Registra il tuo peso", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun summary_explainsWhatIsMissing() {
        setContent { WorkoutSummaryContent(summary(null), onDone = {}) }
        composeRule.onNodeWithText("Registra il tuo peso per stimare le calorie bruciate.").assertIsDisplayed()
    }

    @Test
    fun summary_suggestsTheHeight_whenTheProfileIsIncomplete() {
        setContent { WorkoutSummaryContent(summary("300 kcal", suggestProfile = true), onDone = {}) }
        composeRule.onNodeWithText("Aggiungi l'altezza", substring = true).assertIsDisplayed()
    }

    @Test
    fun history_showsCaloriesPerWorkout_andTheTotals() {
        val state = ProgressUiState.Content(
            bodyWeight = null,
            trends = emptyList(),
            records = emptyList(),
            sessions = listOf(
                SessionHistoryUi(7L, "Spinta A", today, "1 h 5 min", 18, 8450.0, caloriesBurned = 1312),
                SessionHistoryUi(6L, "Gambe", today.minusDays(1), "50 min", 14, 9000.0, caloriesBurned = null),
            ),
            calorieTotals = CalorieTotalsUi(weekKcal = 1312, monthKcal = 2900, hasEstimates = true),
        )
        setContent { ProgressContent(state, ProgressActions({}, {}, {}, {})) }

        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Calorie bruciate (stima)"))
        composeRule.onNodeWithText("2.900 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("ultimi 30 giorni").assertIsDisplayed()
        list.performScrollToNode(hasText("Spinta A"))
        // Il totale settimanale e la riga dell'allenamento mostrano lo stesso valore.
        assertEquals(2, composeRule.onAllNodesWithText("1.312 kcal").fetchSemanticsNodes().size)
        list.performScrollToNode(hasText("Gambe"))
        composeRule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun calorieTotals_countThisWeekAndTheLast30Days() {
        fun session(id: Long, daysAgo: Long, kcal: Int?) = SessionSummary(
            id = id,
            title = "A",
            startedAt = T0.minusSeconds(daysAgo * 86_400),
            endedAt = null,
            exerciseCount = 1,
            workingSetCount = 3,
            totalVolumeKg = 0.0,
            caloriesBurned = kcal,
        )
        // Oggi è lunedì 2 novembre: la settimana è iniziata oggi.
        val totals = calorieTotals(
            listOf(session(1, 0, 300), session(2, 1, 250), session(3, 29, 200), session(4, 30, 999), session(5, 3, null)),
            today,
            ZoneOffset.UTC,
        )!!
        assertEquals(300, totals.weekKcal)
        assertEquals(750, totals.monthKcal)
        assertNull(calorieTotals(emptyList(), today, ZoneOffset.UTC))
    }

    @Test
    fun sessionDetail_showsTheCalories_andTheWeightUsed() {
        val state = SessionDetailUiState.Content(
            title = "Spinta A",
            date = today,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(8, 5),
            duration = "1 h 5 min",
            workingSets = 18,
            volumeKg = 8450.0,
            exercises = emptyList(),
            notes = null,
            caloriesBurned = 312,
            caloriesBodyWeightKg = 80.0,
        )
        setContent { SessionDetailContent(state, SessionDetailActions({}, {}, {})) }
        composeRule.onNodeWithText("312 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("Stima calcolata con un peso corporeo di 80,0 kg.").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ Profilo corporeo

    private fun weightActions() = BodyWeightActions(
        onBack = {},
        onSelectRange = {},
        onAdd = { calls += "add" },
        onEdit = {},
        onDelete = {},
        onEditProfile = { calls += "profile" },
    )

    @Test
    fun bodyWeight_showsTheProfile_evenBeforeTheFirstWeighIn() {
        setContent {
            BodyWeightContent(BodyWeightUiState.Content(TimeRange.QUARTER, null, emptyList(), null, null), weightActions())
        }
        composeRule.onNodeWithText("Profilo corporeo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Modifica il profilo corporeo").performClick()
        composeRule.onNodeWithText("Nessuna pesata").assertIsDisplayed()
        assertEquals(listOf("profile"), calls)
    }

    @Test
    fun overview_withoutWeighIns_stillLeadsToTheBodyProfile() {
        setContent {
            ProgressContent(
                ProgressUiState.Content(null, emptyList(), emptyList(), emptyList()),
                ProgressActions({}, { calls += "bodyWeight" }, {}, {}),
            )
        }
        composeRule.onNodeWithText("Profilo corporeo").performClick()
        assertEquals(listOf("bodyWeight"), calls)
    }

    @Test
    fun bodyWeight_summarisesHeightAgeAndSex() {
        val state = BodyWeightUiState.Content(
            range = TimeRange.QUARTER,
            latest = BodyWeightEntryEntity(date = today, weightKg = 80.0, recordedAt = T0),
            points = emptyList(),
            weeklyChange = null,
            periodChange = null,
            profile = BodyProfileUi(heightCm = 180f, birthYear = 1992, sex = BiologicalSex.MALE, age = 34),
        )
        setContent { BodyWeightContent(state, weightActions()) }
        composeRule.onNodeWithText("180 cm · 34 anni · Uomo").assertIsDisplayed()
    }

    @Test
    fun profileDialog_validatesAndSaves() {
        var saved: BodyProfileInput? = null
        setContent {
            BodyProfileDialog(
                profile = BodyProfileUi(),
                birthYears = 1926..2016,
                onSave = { saved = it },
                onDismiss = {},
            )
        }
        val height = composeRule.onNode(hasSetTextAction() and hasText("Altezza (cm)"))
        height.performTextReplacement("18")
        // Nessun allarme mentre si scrive "18" per arrivare a "180": l'errore compare solo con Salva.
        assertEquals(0, composeRule.onAllNodesWithText("Inserisci un'altezza tra 100 e 250 cm").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Salva").performClick()
        composeRule.onNodeWithText("Inserisci un'altezza tra 100 e 250 cm").assertIsDisplayed()
        assertNull("Un valore non valido non si salva", saved)

        height.performTextReplacement("181,5")
        composeRule.onNode(hasSetTextAction() and hasText("Anno di nascita")).performTextReplacement("1990")
        composeRule.onNodeWithText("Donna").performClick()
        composeRule.onNodeWithText("Salva").assertIsEnabled().performClick()

        assertEquals(BodyProfileInput(heightCm = 181.5f, birthYear = 1990, sex = BiologicalSex.FEMALE), saved)
    }

    @Test
    fun parseBodyProfile_acceptsEmptyFields_andRejectsImplausibleValues() {
        val years = 1926..2016
        assertEquals(BodyProfileInput(null, null, null), parseBodyProfile(" ", "", null, years))
        assertEquals(BodyProfileInput(175f, 1985, BiologicalSex.MALE), parseBodyProfile("175", "1985", BiologicalSex.MALE, years))
        assertNull(parseBodyProfile("2,5", "", null, years))
        assertNull(parseBodyProfile("", "2020", null, years))
        assertNull(parseBodyProfile("abc", "", null, years))
    }
}
