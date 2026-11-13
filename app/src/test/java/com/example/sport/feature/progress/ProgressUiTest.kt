package com.example.sport.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.data.progress.RecordKind
import com.example.sport.core.data.progress.WeightTrendPoint
import com.example.sport.core.database.T0
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.designsystem.component.LocalInlineDialogs
import com.example.sport.core.designsystem.input.NumericInputConfig
import com.example.sport.core.designsystem.input.NumericInputState
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.ExerciseTrackingType
import com.example.sport.core.model.SetType
import com.example.sport.core.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/** Panoramica Progressi, peso corporeo, andamento per esercizio e dettaglio allenamento (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ProgressUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val today = LocalDate.of(2026, 11, 2)

    private fun setContent(content: @Composable () -> Unit) = composeRule.setContent {
        CompositionLocalProvider(LocalInlineDialogs provides true) { SportTheme { content() } }
    }

    private fun weightPoints(days: Int) = (0 until days).map { index ->
        val date = today.minusDays((days - 1 - index).toLong())
        WeightTrendPoint(date, 81.0 - index * 0.1, 81.0 - index * 0.08)
    }

    // ------------------------------------------------------------------ Panoramica

    private fun overview(trends: Int = 6) = ProgressUiState.Content(
        bodyWeight = WeightSummaryUi(
            latestKg = 79.8,
            latestDate = today,
            averageKg = 80.1,
            weeklyChange = -0.4,
            points = weightPoints(20),
        ),
        trends = (1..trends).map {
            ExerciseTrendUi("ex$it", "Esercizio $it", sessionCount = it, lastDate = today, latestOneRepMaxKg = 100.0 + it, oneRepMaxChangeKg = 2.5)
        },
        records = listOf(
            ExerciseRecordUi("ex1", "Panca piana", heaviestWeightKg = 105.0, heaviestReps = 3, oneRepMaxKg = 126.0, bestVolumeKg = 1395.0, latestDate = today),
        ),
        sessions = listOf(SessionHistoryUi(7L, "Spinta A", today, "1 h 5 min", 18, 8450.0)),
    )

    private fun overviewActions() = ProgressActions(
        onWeighIn = { calls += "weighIn" },
        onOpenBodyWeight = { calls += "bodyWeight" },
        onOpenExercise = { calls += "exercise:$it" },
        onOpenSession = { calls += "session:$it" },
    )

    @Test
    fun overview_showsWeightTrendsRecords_andOpensDetails() {
        setContent { ProgressContent(overview(), overviewActions()) }

        composeRule.onNodeWithText("79,8 kg").assertIsDisplayed()
        composeRule.onNodeWithText("Media 7 giorni 80,1 kg").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("in calo di 0,4 kg in una settimana").assertIsDisplayed()
        composeRule.onNodeWithText("Registra peso").performClick()
        composeRule.onNodeWithText("Storico del peso").performClick()

        // Cinque esercizi in anteprima, il sesto dopo "Mostra tutti (6)".
        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Mostra tutti (6)"))
        composeRule.onNodeWithText("Esercizio 6").assertDoesNotExist()
        composeRule.onNodeWithText("Mostra tutti (6)").performClick()
        list.performScrollToNode(hasText("Esercizio 6"))
        composeRule.onNodeWithText("Esercizio 6").performClick()

        list.performScrollToNode(hasText("Panca piana"))
        composeRule.onNodeWithText("Carico massimo 105 kg × 3").assertIsDisplayed()
        composeRule.onNodeWithText("Panca piana").performClick()

        list.performScrollToNode(hasText("Spinta A"))
        composeRule.onNodeWithText("Spinta A").performClick()

        assertEquals(listOf("weighIn", "bodyWeight", "exercise:ex6", "exercise:ex1", "session:7"), calls)
    }

    @Test
    fun overview_withoutData_explainsWhatComesNext() {
        setContent { ProgressContent(ProgressUiState.Content(null, emptyList(), emptyList(), emptyList()), overviewActions()) }
        composeRule.onNodeWithText("Nessuna pesata registrata").assertIsDisplayed()
        composeRule.onNodeWithText("Completa un allenamento con i pesi per vedere qui i progressi di ogni esercizio.").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Ancora nessun allenamento"))
        composeRule.onNodeWithText("Ancora nessun allenamento").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ Foglio "Registra peso"

    @Test
    fun entrySheet_typesWithTheKeypad_andSaves() {
        var entry by mutableStateOf(
            BodyWeightEntryUi(
                date = today,
                today = today,
                input = NumericInputState.of(80.4, NumericInputConfig.BodyWeightKg),
                recordedDates = setOf(today.minusDays(1)),
            ),
        )
        var saved = 0
        val actions = BodyWeightEntryActions(
            onKey = { entry = entry.copy(input = entry.input.reduce(it)) },
            onSelectDate = { entry = entry.copy(date = it) },
            onSave = { saved++ },
            onDismiss = {},
        )
        setContent { BodyWeightEntrySheet(entry, actions) }

        composeRule.onNodeWithText("Oggi").assertIsSelected()
        composeRule.onNodeWithContentDescription("Ieri, pesata già registrata").performClick()
        assertEquals(today.minusDays(1), entry.date)
        composeRule.onNodeWithText("Sostituisce la pesata già registrata").assertIsDisplayed()

        listOf("7", "9", "Virgola", "6").forEach { composeRule.onNodeWithContentDescription(it).performClick() }
        composeRule.onNodeWithText("79,6").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aumenta di 0,1").performClick()
        composeRule.onNodeWithText("79,7").assertIsDisplayed()
        composeRule.onNodeWithText("Salva peso").performClick()
        assertEquals(1, saved)

        entry = entry.copy(showsRangeError = true)
        composeRule.onNodeWithText("Inserisci un peso tra 20 kg e 400 kg").assertIsDisplayed()

        // In libbre: unità, limiti e passo del tastierino seguono la scelta.
        entry = entry.copy(unit = WeightUnit.LB, input = NumericInputState.of(176.4, NumericInputConfig.BodyWeightLb))
        composeRule.onNodeWithText("Inserisci un peso tra 44,1 lb e 881,8 lb").assertIsDisplayed()
        composeRule.onNodeWithText("lb").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aumenta di 0,2").assertExists()
    }

    // ------------------------------------------------------------------ Storico del peso

    private fun weightActions() = BodyWeightActions(
        onBack = { calls += "back" },
        onSelectRange = { calls += "range:$it" },
        onAdd = { calls += "add" },
        onEdit = { calls += "edit:$it" },
        onDelete = { calls += "delete:$it" },
    )

    @Test
    fun bodyWeight_listsEntries_withChartRangesEditAndDelete() {
        val points = weightPoints(10)
        val state = BodyWeightUiState.Content(
            range = TimeRange.QUARTER,
            latest = BodyWeightEntryEntity(date = today, weightKg = points.last().weightKg, recordedAt = T0),
            points = points,
            weeklyChange = -0.56,
            periodChange = -0.72,
        )
        setContent { BodyWeightContent(state, weightActions()) }

        composeRule.onNodeWithText("Ultima pesata").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("in calo di 0,6 kg").assertIsDisplayed()
        composeRule.onNodeWithText("3 mesi").assertIsSelected()
        composeRule.onNodeWithText("1 anno").performClick()
        composeRule.onNodeWithContentDescription("Peso degli ultimi giorni", substring = true).assertExists()

        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Lunedì 2 novembre"))
        composeRule.onNodeWithText("Lunedì 2 novembre").performClick()
        composeRule.onNodeWithContentDescription("Elimina la pesata del lun 2 nov").performClick()
        composeRule.onNodeWithText("Registra peso").performClick()

        assertEquals(listOf("range:YEAR", "edit:$today", "delete:$today", "add"), calls)
    }

    @Test
    fun bodyWeight_empty_offersTheFirstWeighIn() {
        setContent { BodyWeightContent(BodyWeightUiState.Content(TimeRange.QUARTER, null, emptyList(), null, null), weightActions()) }
        composeRule.onNodeWithText("Nessuna pesata").assertIsDisplayed()
        composeRule.onNodeWithText("Registra peso").performClick()
        assertEquals(listOf("add"), calls)
    }

    // ------------------------------------------------------------------ Progressi per esercizio

    private fun exerciseActions() = ExerciseProgressActions(
        onBack = { calls += "back" },
        onSelectMetric = { calls += "metric:$it" },
        onSelectRange = { calls += "range:$it" },
        onOpenSession = { calls += "session:$it" },
    )

    @Test
    fun exerciseProgress_switchesMetric_andOpensRecordSessions() {
        val state = ExerciseProgressUiState.Content(
            exerciseId = "barbell_bench_press",
            name = "Panca piana con bilanciere",
            metrics = ProgressMetric.availableFor(ExerciseTrackingType.WEIGHT_REPS),
            metric = ProgressMetric.ONE_REP_MAX,
            range = TimeRange.HALF_YEAR,
            points = listOf(MetricPoint(today.minusDays(7), 116.7), MetricPoint(today, 122.5)),
            sessions = listOf(ExerciseSessionUi(9L, today, 3, 15, 105.0, 1575.0, 122.5)),
            records = listOf(
                RecordTileUi(RecordKind.HEAVIEST_SET, 105.0, 105.0, 5, today, 9L),
                RecordTileUi(RecordKind.SESSION_VOLUME, 1575.0, null, null, today.minusDays(7), 4L),
            ),
        )
        setContent { ExerciseProgressContent(state, exerciseActions()) }

        composeRule.onNodeWithText("Panca piana con bilanciere").assertIsDisplayed()
        composeRule.onAllNodesWithText("122,5 kg").onFirst().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("in aumento di 5,8 kg").assertIsDisplayed()
        composeRule.onNodeWithText("Volume").performClick()

        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("105 kg × 5"))
        composeRule.onNodeWithText("105 kg × 5").performClick()
        list.performScrollToNode(hasText("3 serie · 15 rip."))
        composeRule.onNodeWithText("3 serie · 15 rip.").performClick()

        assertEquals(listOf("metric:VOLUME", "session:9", "session:9"), calls)
    }

    @Test
    fun exerciseProgress_withoutRepetitions_explainsWhy() {
        val state = ExerciseProgressUiState.Content("plank", "Plank", emptyList(), null, TimeRange.HALF_YEAR, emptyList(), emptyList(), emptyList())
        setContent { ExerciseProgressContent(state, exerciseActions()) }
        composeRule.onNodeWithText("I grafici sono disponibili per gli esercizi a ripetizioni.").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ Dettaglio allenamento

    @Test
    fun sessionDetail_showsSets_andDeletesAfterConfirmation() {
        val state = SessionDetailUiState.Content(
            title = "Spinta A",
            date = today,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(8, 5),
            duration = "1 h 5 min",
            workingSets = 2,
            volumeKg = 1140.0,
            exercises = listOf(
                SessionExerciseUi(
                    id = 1L,
                    exerciseId = "barbell_bench_press",
                    name = "Panca piana",
                    trackingType = ExerciseTrackingType.WEIGHT_REPS,
                    sets = listOf(
                        SessionSetUi(1L, SetType.WARMUP, 0, "60 kg × 10", null, null),
                        SessionSetUi(2L, SetType.WORKING, 1, "100 kg × 5", 8.5, null),
                        SessionSetUi(3L, SetType.DROP, 2, "80 kg × 8", null, 1),
                    ),
                    volumeKg = 1140.0,
                ),
            ),
            notes = null,
        )
        setContent { SessionDetailContent(state, SessionDetailActions({ calls += "back" }, { calls += "exercise:$it" }, { calls += "delete" })) }

        composeRule.onNodeWithText("Lunedì 2 novembre").assertIsDisplayed()
        composeRule.onNodeWithText("07:00–08:05").assertIsDisplayed()
        composeRule.onNodeWithText("RPE 8,5").assertIsDisplayed()
        composeRule.onNodeWithText("RIR 1").assertIsDisplayed()
        composeRule.onNodeWithText("R").assertIsDisplayed()
        composeRule.onNodeWithText("D").assertIsDisplayed()
        composeRule.onNodeWithText("Panca piana").performClick()

        composeRule.onNodeWithContentDescription("Opzioni allenamento").performClick()
        composeRule.onNodeWithText("Elimina allenamento").performClick()
        composeRule.onNodeWithText("Eliminare l'allenamento del lun 2 nov?").assertIsDisplayed()
        composeRule.onNode(hasText("Elimina") and hasClickAction()).performClick()

        assertEquals(listOf("exercise:barbell_bench_press", "delete"), calls)
        assertTrue(composeRule.onAllNodes(hasText("Eliminare l'allenamento del lun 2 nov?")).fetchSemanticsNodes().isEmpty())
    }

    // ------------------------------------------------------------------ Correzioni dello storico

    @Test
    fun sessionDetail_tappingASet_fixesOrDeletesIt() {
        val state = SessionDetailUiState.Content(
            title = "Spinta A",
            date = today,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(8, 5),
            duration = "1 h 5 min",
            workingSets = 2,
            volumeKg = 5640.0,
            exercises = listOf(
                SessionExerciseUi(
                    id = 1L,
                    exerciseId = "barbell_bench_press",
                    name = "Panca piana",
                    trackingType = ExerciseTrackingType.WEIGHT_REPS,
                    sets = listOf(
                        SessionSetUi(2L, SetType.WORKING, 1, "1.000 kg × 5", null, null, weightKg = 1000.0, reps = 5),
                        SessionSetUi(3L, SetType.WORKING, 2, "80 kg × 8", null, null, weightKg = 80.0, reps = 8),
                    ),
                    volumeKg = 5640.0,
                ),
            ),
            notes = null,
        )
        setContent {
            SessionDetailContent(
                state,
                SessionDetailActions(
                    onBack = {},
                    onOpenExercise = {},
                    onDelete = {},
                    onUpdateSet = { id, values -> calls += "update:$id:${values.weightKg}x${values.reps}" },
                    onDeleteSet = { calls += "deleteSet:$it" },
                ),
            )
        }

        composeRule.onNodeWithText("Tocca una serie per correggerla o eliminarla.").assertIsDisplayed()
        composeRule.onNodeWithText("1.000 kg × 5").performClick()
        composeRule.onNodeWithText("Correggi serie").assertIsDisplayed()
        composeRule.onNodeWithText("Valore registrato: 1.000 kg × 5").assertIsDisplayed()
        composeRule.onNode(hasText("1000") and hasSetTextAction()).performTextReplacement("100")
        composeRule.onNodeWithText("Salva").performClick()

        composeRule.onNodeWithText("80 kg × 8").performClick()
        // Zero ripetizioni non è una serie: la correzione non si può salvare.
        composeRule.onNode(hasText("8") and hasSetTextAction()).performTextReplacement("0")
        composeRule.onNodeWithText("Salva").assertIsNotEnabled()
        composeRule.onNodeWithText("Elimina serie").performClick()
        composeRule.onNodeWithText("Eliminare la serie 80 kg × 8?").assertIsDisplayed()
        composeRule.onNode(hasText("Elimina") and hasClickAction()).performClick()

        assertEquals(listOf("update:2:100.0x5", "deleteSet:3"), calls)
    }

    @Test
    fun exerciseProgress_deletesARecord_andResetsTheHistory_afterConfirmation() {
        val state = ExerciseProgressUiState.Content(
            exerciseId = "barbell_bench_press",
            name = "Panca piana con bilanciere",
            metrics = ProgressMetric.availableFor(ExerciseTrackingType.WEIGHT_REPS),
            metric = ProgressMetric.ONE_REP_MAX,
            range = TimeRange.HALF_YEAR,
            points = listOf(MetricPoint(today.minusDays(7), 116.7), MetricPoint(today, 1166.7)),
            sessions = listOf(ExerciseSessionUi(9L, today, 1, 5, 1000.0, 5000.0, 1166.7)),
            records = listOf(
                RecordTileUi(RecordKind.HEAVIEST_SET, 1000.0, 1000.0, 5, today, 9L, setId = 31L),
                RecordTileUi(RecordKind.SESSION_VOLUME, 5000.0, null, null, today, 9L),
            ),
        )
        setContent {
            ExerciseProgressContent(
                state,
                exerciseActions().copy(
                    onDeleteRecord = { calls += "deleteRecord:${it.kind}:${it.setId}" },
                    onResetHistory = { calls += "reset" },
                ),
            )
        }

        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("1.000 kg × 5"))
        composeRule.onNodeWithContentDescription("Elimina il record: Carico massimo").performClick()
        composeRule.onNodeWithText("Eliminare questo record?").assertIsDisplayed()
        composeRule.onNode(hasText("Elimina") and hasClickAction()).performClick()

        list.performScrollToNode(hasText("Volume in una sessione"))
        composeRule.onNodeWithContentDescription("Elimina il record: Volume in una sessione").performClick()
        composeRule.onNode(hasText("Elimina") and hasClickAction()).performClick()

        composeRule.onNodeWithContentDescription("Opzioni esercizio").performClick()
        composeRule.onNodeWithText("Azzera storico esercizio").performClick()
        composeRule.onNodeWithText("Azzerare lo storico di Panca piana con bilanciere?").assertIsDisplayed()
        composeRule.onNodeWithText("Azzera storico").performClick()

        assertEquals(listOf("deleteRecord:HEAVIEST_SET:31", "deleteRecord:SESSION_VOLUME:null", "reset"), calls)
    }

    @Test
    fun overview_deletesAWorkoutFromTheHistory_afterConfirmation() {
        setContent { ProgressContent(overview(), overviewActions().copy(onDeleteSession = { calls += "deleteSession:$it" })) }

        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Spinta A"))
        composeRule.onNodeWithContentDescription("Elimina l'allenamento Spinta A del lun 2 nov").performClick()
        composeRule.onNodeWithText("Eliminare l'allenamento del lun 2 nov?").assertIsDisplayed()
        composeRule.onNode(hasText("Elimina") and hasClickAction()).performClick()

        assertEquals(listOf("deleteSession:7"), calls)
    }
}
