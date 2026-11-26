package com.example.sport.feature.nutrition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.R
import com.example.sport.core.data.food.DIARY_MEALS
import com.example.sport.core.data.food.FoodFilter
import com.example.sport.core.data.food.Nutrients
import com.example.sport.core.data.food.OnlineFood
import com.example.sport.core.data.food.sum
import com.example.sport.core.designsystem.component.LocalInlineDialogs
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.FoodSource
import com.example.sport.core.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.roundToInt

private val TODAY = LocalDate.of(2026, 11, 26)
private val PASTA = Nutrients(130.0, 4.4, 23.0, 2.4)
private val YOGURT = Nutrients(57.0, 10.0, 4.0, 0.2)

/** Stato della schermata Dieta costruito come fa il ViewModel (totali dai grammi). */
internal fun diaryState(
    planId: Long? = 1L,
    entries: List<DiaryEntryUi> = emptyList(),
    planMeals: Map<MealType, List<PlanMealUi>> = emptyMap(),
    date: LocalDate = TODAY,
): NutritionUiState.Content {
    val total = entries.map { it.nutrients }.sum()
    return NutritionUiState.Content(
        date = date,
        today = TODAY,
        planId = planId,
        planName = planId?.let { "Massa pulita" },
        macros = listOf(
            MacroUi(R.string.nutrition_calories, total.kcal, planId?.let { 2000.0 }, "kcal"),
            MacroUi(R.string.nutrition_protein, total.proteinG, planId?.let { 150.0 }, "g"),
            MacroUi(R.string.nutrition_carbs, total.carbsG, planId?.let { 220.0 }, "g"),
            MacroUi(R.string.nutrition_fat, total.fatG, planId?.let { 60.0 }, "g"),
        ),
        meals = DIARY_MEALS.map { type ->
            val typeEntries = entries.filter { it.mealType == type }
            DiaryMealUi(type, typeEntries, planMeals[type].orEmpty(), typeEntries.map { it.nutrients }.sum().kcal.roundToInt())
        },
    )
}

/** Diario a grammi: calcolo live, pannello grammi, selettore dei piatti, editor di piatti e ricette. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FoodDiaryUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setContent(content: @Composable () -> Unit) = composeRule.setContent {
        CompositionLocalProvider(LocalInlineDialogs provides true) { SportTheme { content() } }
    }

    // ------------------------------------------------------------------ Diario

    @Test
    fun diary_showsCaloriesFromGrams_perEntryMealAndDay_andEditsOrDeletesWithOneTap() {
        val pasta = DiaryEntryUi(7, "Pasta al pomodoro", null, 280.0, MealType.LUNCH, PASTA)
        val yogurt = DiaryEntryUi(8, "Yogurt greco 0% grassi", null, 170.0, MealType.BREAKFAST, YOGURT)
        val plan = PlanMealUi(3, null, R.string.meal_breakfast, "07:30", "Avena 80 g", "300 kcal · P 10 · C 50 · G 6", consumed = false)
        setContent {
            NutritionContent(
                state = diaryState(entries = listOf(pasta, yogurt), planMeals = mapOf(MealType.BREAKFAST to listOf(plan))),
                actions = NutritionActions(
                    onEditEntry = { calls += "edit:${it.id}" },
                    onDeleteEntry = { calls += "delete:$it" },
                    onSetConsumed = { id, consumed -> calls += "plan:$id:$consumed" },
                    onPreviousDay = { calls += "prev" },
                    onAddFood = { calls += "add:$it" },
                ),
            )
        }

        // 280 g × 130 kcal / 100 = 364 kcal; 170 g di yogurt = 97 kcal; giorno 461 su 2.000.
        composeRule.onNodeWithText("461 / 2.000 kcal").assertIsDisplayed()
        composeRule.onAllNodesWithText("364 kcal").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("280 g · P 12,3 g · C 64,4 g · G 6,7 g").assertIsDisplayed()
        composeRule.onNodeWithText("Dal piano: Colazione · 07:30").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Segna come consumato").performClick()
        composeRule.onNodeWithText("Pasta al pomodoro").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Elimina Pasta al pomodoro").performClick()
        composeRule.onNodeWithText("Aggiungi a Pranzo").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Giorno precedente").performScrollTo().performClick()

        assertEquals(listOf("plan:3:true", "edit:7", "delete:7", "add:LUNCH", "prev"), calls)
    }

    @Test
    fun diary_anotherDay_offersToGoBackToToday() {
        setContent { NutritionContent(state = diaryState(date = TODAY.minusDays(2)), actions = NutritionActions(onToday = { calls += "today" })) }
        composeRule.onNodeWithText("Oggi").performClick()
        assertEquals(listOf("today"), calls)
    }

    // ------------------------------------------------------------------ Pannello grammi

    @Test
    fun gramsSheet_recalculatesCaloriesWhileTheGramsChange() {
        setContent {
            GramsSheetContent(
                target = GramsTarget("Pasta al pomodoro", null, PASTA, 280.0, MealType.LUNCH),
                onConfirm = { grams, meal -> calls += "confirm:$grams:$meal" },
                onDelete = null,
            )
        }
        composeRule.onNodeWithText("364 kcal").assertIsDisplayed()

        // Il primo tasto sostituisce la porzione proposta: 1-5-0 = 150 g → 195 kcal.
        listOf("1", "5", "0").forEach { composeRule.onNodeWithContentDescription(it).performClick() }
        composeRule.onNodeWithText("195 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("P 6,6 g · C 34,5 g · G 3,6 g").assertIsDisplayed()

        composeRule.onNodeWithText("+50 g").performScrollTo().performClick()
        composeRule.onNodeWithText("260 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("−10 g").performScrollTo().performClick()
        composeRule.onNodeWithText("247 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("100 g").performScrollTo().performClick()
        composeRule.onNodeWithText("130 kcal").assertIsDisplayed()

        composeRule.onNodeWithText("Cena").performScrollTo().performClick()
        composeRule.onNodeWithText("Aggiungi").performClick()
        assertEquals(listOf("confirm:100.0:DINNER"), calls)
    }

    @Test
    fun gramsSheet_forAnEntry_savesOrDeletes() {
        setContent {
            GramsSheetContent(
                target = GramsTarget("Yogurt greco 0% grassi", null, YOGURT, 170.0, MealType.BREAKFAST, isEdit = true),
                onConfirm = { grams, meal -> calls += "save:$grams:$meal" },
                onDelete = { calls += "delete" },
            )
        }
        composeRule.onNodeWithText("Elimina dal diario").performClick()
        composeRule.onNodeWithText("Salva").performClick()
        assertEquals(listOf("delete", "save:170.0:BREAKFAST"), calls)
    }

    // ------------------------------------------------------------------ Selettore

    private val rows = listOf(
        FoodRowUi("cat:pasta_al_pomodoro", "Pasta al pomodoro", null, FoodCategory.FIRST_COURSES, FoodSource.CATALOG, 130.0, editable = false),
        FoodRowUi("custom:1", "Il mio porridge", null, FoodCategory.BREAKFAST_SWEETS, FoodSource.CUSTOM, 110.0, editable = true),
    )

    private fun pickerActions() = FoodPickerActions(
        onSelectFood = { calls += "select:$it" },
        onFilterChange = { calls += "filter:$it" },
        onSearchOnline = { calls += "online" },
        onSelectOnline = { calls += "pick:${it.code}" },
        onEditFood = { calls += "edit:$it" },
        onNewRecipe = { calls += "recipe" },
    )

    @Test
    fun picker_selectsFiltersAndSearchesOpenFoodFactsOnlyWhenAsked() {
        val state = FoodPickerUiState(TODAY, MealType.LUNCH, results = rows, mealKcal = 364, dayKcal = 461, isOnline = true)
        setContent { FoodPickerContent(state = state, query = "pasta", filter = FoodFilter.All, actions = pickerActions()) }

        composeRule.onNodeWithText("Aggiungi a Pranzo").assertIsDisplayed()
        composeRule.onNodeWithText("Pasto 364 kcal · Giorno 461 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("130 kcal/100 g").assertIsDisplayed()
        composeRule.onNodeWithText("Pasta al pomodoro").performClick()
        composeRule.onNodeWithText("I miei").performClick()
        composeRule.onNodeWithContentDescription("Modifica Il mio porridge").performClick()
        composeRule.onNodeWithText("Cerca «pasta» su Open Food Facts").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Nuova ricetta").onFirst().performScrollTo().performClick()

        assertEquals(
            listOf("select:cat:pasta_al_pomodoro", "filter:${FoodFilter.Mine}", "edit:custom:1", "online", "recipe"),
            calls,
        )
    }

    @Test
    fun picker_showsOnlineResultsWithAttribution_andExplainsWhenOffline() {
        val online = OnlineFood("8001", "Yogurt greco bianco", "Fage", Nutrients(97.0, 9.0, 3.5, 5.0))
        val state = FoodPickerUiState(
            TODAY,
            MealType.BREAKFAST,
            results = emptyList(),
            isOnline = true,
            online = OnlineSearchUi.Results("yogurt greco", listOf(online)),
        )
        setContent { FoodPickerContent(state = state, query = "yogurt greco", filter = FoodFilter.All, actions = pickerActions()) }

        composeRule.onNodeWithText("Nessun piatto trovato.").assertIsDisplayed()
        composeRule.onNodeWithText("Open Food Facts: «yogurt greco»").assertIsDisplayed()
        composeRule.onNodeWithText("Dati da Open Food Facts (licenza ODbL), inseriti da volontari: confrontali con l'etichetta.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Yogurt greco bianco").performClick()
        assertEquals(listOf("pick:8001"), calls)
    }

    @Test
    fun picker_offline_hidesTheOnlineSearch() {
        val state = FoodPickerUiState(TODAY, MealType.DINNER, results = rows, isOnline = false)
        setContent { FoodPickerContent(state = state, query = "salmone", filter = FoodFilter.All, actions = pickerActions()) }
        composeRule.onNodeWithText("Senza connessione: la ricerca su Open Food Facts torna disponibile appena sei online.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cerca «salmone» su Open Food Facts").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ Editor

    @Test
    fun editor_recipe_computesValuesPer100gFromTheIngredients() {
        val form = FoodEditorForm(
            isRecipe = true,
            name = "Pasta al tonno della nonna",
            category = FoodCategory.FIRST_COURSES,
            ingredients = listOf(
                IngredientUi("cat:pasta_secca_cruda", "Pasta di semola (cruda)", "80", Nutrients(353.0, 12.5, 71.0, 1.5)),
                IngredientUi("cat:tonno_naturale", "Tonno al naturale (sgocciolato)", "60", Nutrients(103.0, 24.0, 0.0, 0.8)),
                IngredientUi("cat:olio_extravergine", "Olio extravergine d'oliva", "10", Nutrients(899.0, 0.0, 0.0, 99.9)),
            ),
        )
        setContent {
            FoodEditorContent(
                state = FoodEditorUiState(form, isNew = true),
                actions = FoodEditorActions(onSave = { calls += "save" }, onRemoveIngredient = { calls += "remove:$it" }),
            )
        }
        // 282,4 + 61,8 + 89,9 = 434,1 kcal in 150 g → 289 kcal per 100 g.
        composeRule.onNodeWithText("289 kcal").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Somma degli ingredienti: 150 g. Scrivi il peso da cotto se cambia (la pasta assorbe acqua, la carne cala).")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Togli Tonno al naturale (sgocciolato)").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Salva").onFirst().assertIsEnabled().performClick()
        assertEquals(listOf("remove:1", "save"), calls)
    }

    @Test
    fun editor_food_withoutCalories_computesThemFromTheMacros() {
        val form = FoodEditorForm(isRecipe = false, name = "Porridge proteico", protein = "10", carbs = "20", fat = "5")
        setContent { FoodEditorContent(state = FoodEditorUiState(form, isNew = true), actions = FoodEditorActions()) }
        composeRule.onNodeWithText("Calcolate dai macronutrienti: 165 kcal").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Salva").onFirst().assertIsEnabled()
    }
}
