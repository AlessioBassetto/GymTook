package com.example.sport.feature.nutrition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.backup.BackupFile
import com.example.sport.core.backup.BackupSummary
import com.example.sport.core.data.DietTargets
import com.example.sport.core.data.MealItemInput
import com.example.sport.core.designsystem.component.LocalInlineDialogs
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.MealType
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.UserSettings
import com.example.sport.feature.settings.PendingRestore
import com.example.sport.feature.settings.SettingsActions
import com.example.sport.feature.settings.SettingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/** Editor del piano alimentare, sezione Dieta senza piano e impostazioni (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DietUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setContent(content: @Composable () -> Unit) = composeRule.setContent {
        CompositionLocalProvider(LocalInlineDialogs provides true) { SportTheme { content() } }
    }

    private fun meal(id: Long, type: MealType, items: List<EditorItemUi>, first: Boolean, last: Boolean) = EditorMealUi(
        id = id,
        type = type,
        customName = null,
        time = LocalTime.of(7, 30),
        items = items,
        totals = MacroTotals(items.sumOf { it.input.caloriesKcal ?: 0.0 }, 10.0, 54.0, 6.0),
        isFirst = first,
        isLast = last,
    )

    private val plan = DietPlanEditorUiState.Content(
        planId = 1L,
        name = "Massa",
        isActive = false,
        targets = DietTargets(2800, 180.0, 350.0, 80.0),
        notes = null,
        meals = listOf(
            meal(10L, MealType.BREAKFAST, listOf(EditorItemUi(100L, MealItemInput("Avena", 80.0, "g", 310.0, 10.0, 54.0, 6.0))), first = true, last = false),
            meal(11L, MealType.LUNCH, emptyList(), first = false, last = true),
        ),
        totals = MacroTotals(310.0, 10.0, 54.0, 6.0),
    )

    private fun actions() = DietPlanEditorActions(
        onBack = { calls += "back" },
        onRename = { calls += "rename:$it" },
        onTargets = { calls += "targets:$it" },
        onNotes = { calls += "notes:$it" },
        onSetActive = { calls += "active" },
        onDuplicate = { calls += "duplicate:$it" },
        onDelete = { calls += "delete" },
        onAddMeal = { type, name, time -> calls += "addMeal:$type:$name:$time" },
        onUpdateMeal = { id, type, name, time -> calls += "updateMeal:$id:$type:$name:$time" },
        onMoveMeal = { id, delta -> calls += "move:$id:$delta" },
        onDeleteMeal = { calls += "deleteMeal:$it" },
        onAddItem = { mealId, input -> calls += "addItem:$mealId:${input.name}:${input.quantity}:${input.proteinG}" },
        onUpdateItem = { id, input -> calls += "updateItem:$id:${input.name}" },
        onDeleteItem = { calls += "deleteItem:$it" },
    )

    @Test
    fun editor_showsTargetsAgainstThePlan_andAddsAnItem() {
        setContent { DietPlanEditorContent(plan, actions()) }

        composeRule.onNodeWithText("Obiettivi giornalieri").assertIsDisplayed()
        composeRule.onNodeWithText("2.800").assertIsDisplayed()
        composeRule.onNodeWithText("Imposta come attivo").performClick()
        composeRule.onNodeWithText("Avena · 80 g").assertIsDisplayed()

        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Pranzo · 07:30"))
        composeRule.onNodeWithText("Nessun alimento").assertIsDisplayed()
        // Due pasti → due "Aggiungi alimento": quello del pranzo è il secondo.
        composeRule.onAllNodes(hasText("Aggiungi alimento") and hasClickAction())[1].performClick()
        composeRule.onNodeWithText("Nuovo alimento").assertIsDisplayed()
        composeRule.onNodeWithText("Salva").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction() and hasText("Alimento")).performTextInput("Riso basmati")
        composeRule.onNode(hasSetTextAction() and hasText("Quantità")).performTextInput("70")
        composeRule.onNode(hasSetTextAction() and hasText("Proteine")).performTextInput("5,5")
        composeRule.onNode(hasSetTextAction() and hasText("Carbo")).performTextInput("54")
        composeRule.onNodeWithText("Dai macro: 238 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("Salva").performClick()

        assertEquals(listOf("active", "addItem:11:Riso basmati:70.0:5.5"), calls)
    }

    @Test
    fun editor_editsTargets_andDeletesAMealAfterConfirmation() {
        setContent { DietPlanEditorContent(plan, actions()) }

        composeRule.onNodeWithText("Obiettivi giornalieri").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("2800")).performTextReplacement("2600")
        composeRule.onNodeWithText("Salva").performClick()

        composeRule.onNodeWithContentDescription("Opzioni di Colazione · 07:30").performClick()
        composeRule.onNodeWithText("Sposta giù").performClick()
        composeRule.onNodeWithContentDescription("Opzioni di Colazione · 07:30").performClick()
        composeRule.onNodeWithText("Elimina pasto").performClick()
        composeRule.onNodeWithText("Eliminare «Colazione»?").assertIsDisplayed()
        composeRule.onNode(hasText("Elimina") and hasClickAction()).performClick()

        assertEquals(
            listOf("targets:${DietTargets(2600, 180.0, 350.0, 80.0)}", "move:10:1", "deleteMeal:10"),
            calls,
        )
    }

    @Test
    fun editor_editsAMealTime_withValidation() {
        setContent { DietPlanEditorContent(plan, actions()) }

        composeRule.onNodeWithContentDescription("Opzioni di Colazione · 07:30").performClick()
        composeRule.onNodeWithText("Modifica pasto").performClick()
        composeRule.onNodeWithText("Colazione").assertIsSelected()
        composeRule.onNode(hasSetTextAction() and hasText("07:30")).performTextReplacement("25:00")
        composeRule.onNodeWithText("Orario non valido").assertIsDisplayed()
        composeRule.onNodeWithText("Salva").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction() and hasText("25:00")).performTextReplacement("8")
        composeRule.onNodeWithText("Salva").performClick()

        assertEquals(listOf("updateMeal:10:BREAKFAST::08:00"), calls)
    }

    @Test
    fun dietTab_withoutPlan_createsOne() {
        setContent {
            NutritionContent(
                state = NutritionUiState.NoPlan(hasPlans = true),
                onSetConsumed = { _, _ -> },
                onOpenPlans = { calls += "plans" },
                onCreatePlan = { calls += "create:$it" },
            )
        }
        composeRule.onNodeWithText("I tuoi piani").performClick()
        composeRule.onNodeWithText("Crea un piano").performClick()
        composeRule.onNodeWithText("Crea").performClick()
        assertEquals(listOf("plans", "create:Il mio piano"), calls)
    }

    // ------------------------------------------------------------------ Impostazioni

    private fun settingsActions() = SettingsActions(
        onBack = { calls += "back" },
        onWeightUnit = { calls += "unit:$it" },
        onEffortScale = { calls += "effort:$it" },
        onExport = { calls += "export" },
        onRestore = { calls += "restore" },
        onConfirmRestore = { calls += "confirm" },
        onCancelRestore = { calls += "cancel" },
    )

    @Test
    fun settings_switchUnitsAndScale_andStartBackups() {
        setContent { SettingsContent(UserSettings(), busy = false, pendingRestore = null, actions = settingsActions()) }

        composeRule.onNodeWithText("Chilogrammi (kg)").assertIsSelected()
        composeRule.onNodeWithText("Libbre (lb)").performClick()
        composeRule.onNodeWithText("RIR").performClick()
        composeRule.onNodeWithText("Esporta backup").performScrollTo().performClick()
        composeRule.onNodeWithText("Ripristina da backup").performScrollTo().performClick()

        assertEquals(listOf("unit:${WeightUnit.LB}", "effort:${EffortScale.RIR}", "export", "restore"), calls)
    }

    @Test
    fun settings_restoreAsksForConfirmation_withTheBackupContents() {
        val pending = PendingRestore(
            file = BackupFile(schemaVersion = 3, exportedAt = "2026-11-02T07:00:00Z", tables = emptyMap()),
            summary = BackupSummary(sessions = 42, routines = 2, bodyWeights = 90, dietPlans = 1, customExercises = 3),
            exportedOn = LocalDate.of(2026, 11, 2),
        )
        setContent { SettingsContent(UserSettings(), busy = false, pendingRestore = pending, actions = settingsActions()) }

        composeRule.onNodeWithText("Backup del Lunedì 2 novembre:").assertIsDisplayed()
        composeRule.onNodeWithText("42 allenamenti, 2 schede, 90 pesate, 1 piano alimentare e 3 esercizi personalizzati.").assertIsDisplayed()
        composeRule.onNodeWithText("Ripristina").performClick()
        assertEquals(listOf("confirm"), calls)
    }
}
