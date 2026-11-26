package com.example.sport.core.data.food

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.database.seed.AssetFoodCatalogSource
import com.example.sport.core.database.seed.FoodCatalogDto
import com.example.sport.core.database.seed.FoodCatalogItemDto
import com.example.sport.core.database.seed.FoodCatalogSeedResult
import com.example.sport.core.database.seed.FoodCatalogSeeder
import com.example.sport.core.database.seed.problems
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.FoodSource
import com.example.sport.core.model.MealType
import com.example.sport.core.workout.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Catalogo dei piatti italiani, ricerca, diario a grammi, piatti e ricette dell'utente. */
@RunWith(AndroidJUnit4::class)
class FoodRepositoryTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var repository: FoodRepository
    private val day = LocalDate.of(2026, 11, 2)

    private fun foodSeeder(source: com.example.sport.core.database.seed.FoodCatalogSource = AssetFoodCatalogSource(context, Dispatchers.IO)) =
        FoodCatalogSeeder(db.foodDao(), source, Clock.fixed(T0, ZoneOffset.UTC), CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @Before
    fun setUp() = runTest {
        foodSeeder().seedIfNeeded()
        val offline = OpenFoodFactsClient(OkHttpClient(), OpenFoodFactsConfig("http://127.0.0.1:9/", "Sport/test (Android)"), Dispatchers.IO)
        repository = FoodRepository(db.foodDao(), offline, clock)
    }

    // ------------------------------------------------------------------ Catalogo

    @Test
    fun bundledCatalog_isValid_large_andCoversEveryCategory() = runTest {
        val catalog = AssetFoodCatalogSource(context, Dispatchers.IO).load()
        assertEquals(emptyList<String>(), catalog.problems())
        assertTrue("Catalogo ben fornito: ${catalog.foods.size}", catalog.foods.size >= 250)
        val byCategory = catalog.foods.groupingBy { it.category }.eachCount()
        (FoodCategory.entries - FoodCategory.OTHER).forEach { category ->
            assertTrue("$category: ${byCategory[category]}", (byCategory[category] ?: 0) >= 25)
        }
        listOf("pasta_al_pomodoro", "pasta_al_ragu", "spaghetti_carbonara", "risotto_funghi", "petto_pollo_piastra", "salmone_forno",
            "lenticchie_umido", "zucchine_grigliate", "banana", "yogurt_greco_0", "crema_arachidi", "proteine_whey")
            .forEach { id -> assertNotNull("Manca $id", catalog.foods.firstOrNull { it.id == id }) }
        assertEquals(catalog.foods.size, repository.observeFoods().first().size)
    }

    @Test
    fun catalogValidation_reportsIncoherentValues() {
        val broken = FoodCatalogDto(
            version = 1,
            foods = listOf(
                FoodCatalogItemDto("pasta", "Pasta", FoodCategory.FIRST_COURSES, 500.0, 12.0, 70.0, 1.5, 80.0),
                FoodCatalogItemDto("pasta", "Riso", FoodCategory.FIRST_COURSES, 130.0, 2.7, 28.0, 0.3, 1500.0),
            ),
        ).problems().joinToString("\n")
        listOf("Id duplicato", "incoerenti", "porzione fuori scala").forEach { assertTrue("Manca '$it' in:\n$broken", it in broken) }
    }

    @Test
    fun seeder_isIdempotent_andANewVersionKeepsUserFoodsAndDiary() = runTest {
        assertTrue(foodSeeder().seedIfNeeded() is FoodCatalogSeedResult.UpToDate)
        val mine = repository.saveCustomFood(null, "Porridge della casa", FoodCategory.BREAKFAST_SWEETS, null, 10.0, 20.0, 5.0, 250.0)
        repository.addEntry(day, MealType.LUNCH, repository.food("cat:pasta_al_pomodoro")!!, 280.0)

        // Versione 2 del catalogo con un solo piatto: gli altri escono, il diario resta intatto.
        val v2 = FoodCatalogDto(2, foods = listOf(FoodCatalogItemDto("banana", "Banana", FoodCategory.FRUIT_SNACKS_DRINKS, 89.0, 1.1, 20.2, 0.3, 120.0)))
        val result = foodSeeder { v2 }.seedIfNeeded() as FoodCatalogSeedResult.Installed
        assertTrue(result.removedCount > 200)
        assertNotNull(repository.food(mine))
        val entry = repository.observeDiary(day).first().single()
        assertNull("Il collegamento al piatto ritirato si perde", entry.foodItemId)
        assertEquals("I valori copiati restano", 364.0, entry.nutrients.kcal, 1e-9)
    }

    // ------------------------------------------------------------------ Ricerca

    @Test
    fun search_ignoresAccentsAndCase_andRanksNamesStartingWithTheQueryFirst() = runTest {
        val foods = repository.observeFoods().first()
        assertEquals("Pasta al ragù", searchFoods(foods, "RAGU").first().name)
        assertEquals("Pollo arrosto con pelle", searchFoods(foods, "pollo").first().name)
        assertTrue(searchFoods(foods, "pollo").any { it.name == "Petto di pollo alla piastra" })
        assertEquals("Tutte le parole", "Spaghetti alla carbonara", searchFoods(foods, "spaghetti carbonara").single().name)
        assertTrue("Sinonimi", searchFoods(foods, "nutella").any { it.id == "cat:crema_nocciole_cacao" })
        assertTrue(searchFoods(foods, "", FoodFilter.Category(FoodCategory.FISH)).all { it.category == FoodCategory.FISH })
        assertTrue(searchFoods(foods, "", FoodFilter.Mine).isEmpty())
    }

    // ------------------------------------------------------------------ Diario

    @Test
    fun diary_caloriesComeFromGrams_andFollowEveryChange() = runTest {
        val pasta = repository.food("cat:pasta_al_pomodoro")!!
        val id = repository.addEntry(day, MealType.LUNCH, pasta, 280.0)

        // Calorie = kcal per 100 g × grammi / 100 = 130 × 280 / 100.
        assertEquals(364.0, repository.observeDiary(day).first().single().nutrients.kcal, 1e-9)

        repository.updateEntry(id, 135.0, MealType.DINNER)
        val updated = repository.observeDiary(day).first().single()
        assertEquals(175.5, updated.nutrients.kcal, 1e-9)
        assertEquals(MealType.DINNER, updated.mealType)
        assertEquals(4.4 * 1.35, updated.nutrients.proteinG, 1e-9)

        val deleted = repository.deleteEntry(id)!!
        assertTrue(repository.observeDiary(day).first().isEmpty())
        repository.restoreEntry(deleted)
        assertEquals(deleted, repository.observeDiary(day).first().single())
        assertTrue("Altri giorni vuoti", repository.observeDiary(day.plusDays(1)).first().isEmpty())
    }

    // ------------------------------------------------------------------ Piatti e ricette dell'utente

    @Test
    fun customFood_withoutCalories_usesTheMacros_andDeletingItKeepsTheDiary() = runTest {
        val id = repository.saveCustomFood(null, "  Porridge proteico ", FoodCategory.BREAKFAST_SWEETS, null, 10.0, 20.0, 5.0, 250.0)
        val food = repository.food(id)!!
        assertEquals("Porridge proteico", food.name)
        assertEquals(165.0, food.kcalPer100g, 1e-9)
        assertEquals(FoodSource.CUSTOM, food.source)
        assertTrue(searchFoods(repository.observeFoods().first(), "", FoodFilter.Mine).any { it.id == id })

        repository.saveCustomFood(id, "Porridge proteico", FoodCategory.BREAKFAST_SWEETS, 170.0, 10.0, 20.0, 5.0, 300.0)
        assertEquals(170.0, repository.food(id)!!.kcalPer100g, 1e-9)

        repository.addEntry(day, MealType.BREAKFAST, repository.food(id)!!, 300.0)
        repository.deleteUserFood(id)
        assertNull(repository.food(id))
        assertEquals(510.0, repository.observeDiary(day).first().single().nutrients.kcal, 1e-9)
    }

    @Test
    fun recipe_valuesPer100gComeFromTheIngredients_andTheCookedWeight() = runTest {
        val catalog = repository.observeFoods().first().associateBy { it.id }
        fun draft(slug: String, grams: Double): RecipeIngredientDraft {
            val food = catalog.getValue("cat:$slug")
            return RecipeIngredientDraft(food.id, food.name, grams, food.per100g)
        }
        val ingredients = listOf(draft("pasta_secca_cruda", 80.0), draft("tonno_naturale", 60.0), draft("olio_extravergine", 10.0))
        val id = repository.saveRecipe(null, "Pasta al tonno della nonna", FoodCategory.FIRST_COURSES, ingredients)
        val recipe = repository.food(id)!!
        // (282,4 + 61,8 + 89,9) kcal in 150 g.
        assertEquals(434.1 / 150 * 100, recipe.kcalPer100g, 0.01)
        assertEquals(150.0, recipe.defaultGrams, 0.0)
        assertEquals(FoodSource.RECIPE, recipe.source)
        assertEquals(listOf(80.0, 60.0, 10.0), repository.recipeIngredients(id).map { it.grams })

        // Cotta, la pasta assorbe acqua: 150 g di ingredienti diventano un piatto da 250 g.
        repository.saveRecipe(id, "Pasta al tonno della nonna", FoodCategory.FIRST_COURSES, ingredients, finalWeightGrams = 250.0)
        assertEquals(434.1 / 250 * 100, repository.food(id)!!.kcalPer100g, 0.01)
        assertEquals("Ingredienti sostituiti, non duplicati", 3, repository.recipeIngredients(id).size)
    }

    @Test
    fun onlineProduct_isSavedForOfflineUse() = runTest {
        val saved = repository.saveOnlineFood(OnlineFood("8001", "Yogurt greco bianco", "Fage", Nutrients(97.0, 9.0, 3.5, 5.0)))
        assertEquals("off:8001", saved.id)
        assertEquals(FoodSource.OPEN_FOOD_FACTS, saved.source)
        assertTrue(searchFoods(repository.observeFoods().first(), "fage").any { it.id == "off:8001" })
    }
}
