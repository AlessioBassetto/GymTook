package com.example.sport.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.DatabaseTest
import com.example.sport.core.database.T0
import com.example.sport.core.model.MealType
import com.example.sport.core.workout.TestClock
import com.example.sport.feature.nutrition.parseTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class DietPlanRepositoryTest : DatabaseTest() {

    private val clock = TestClock(T0)
    private lateinit var repository: DietPlanRepository

    @Before
    fun setUp() {
        repository = DietPlanRepository(db, clock)
    }

    private suspend fun plan(id: Long) = repository.observePlan(id).first()!!

    @Test
    fun newPlan_hasTheFiveDailyMeals_andTheFirstBecomesActive() = runTest {
        val first = repository.createPlan("  Massa ")
        val second = repository.createPlan("Definizione")

        val massa = plan(first)
        assertEquals("Massa", massa.plan.name)
        assertTrue(massa.plan.isActive)
        assertFalse("Il secondo piano non ruba il posto all'attivo", plan(second).plan.isActive)
        assertEquals(
            listOf(MealType.BREAKFAST, MealType.MORNING_SNACK, MealType.LUNCH, MealType.AFTERNOON_SNACK, MealType.DINNER),
            massa.orderedMeals.map { it.meal.mealType },
        )
        assertEquals(LocalTime.of(7, 30), massa.orderedMeals.first().meal.timeOfDay)
    }

    @Test
    fun items_computeCaloriesFromMacros_whenMissing() = runTest {
        val planId = repository.createPlan("Piano")
        val breakfast = plan(planId).orderedMeals.first().meal.id

        val oats = repository.addItem(breakfast, MealItemInput("Avena", 80.0, "g", proteinG = 10.0, carbsG = 54.0, fatG = 6.0))
        repository.addItem(breakfast, MealItemInput("Albume", 200.0, "ml", caloriesKcal = 100.0, proteinG = 22.0))
        val meal = plan(planId).orderedMeals.first()
        assertEquals(listOf("Avena", "Albume"), meal.orderedItems.map { it.name })
        assertEquals("4 × 10 + 4 × 54 + 9 × 6", 310.0, meal.orderedItems.first().caloriesKcal!!, 0.0)
        assertEquals("Le calorie scritte hanno la precedenza", 100.0, meal.orderedItems.last().caloriesKcal!!, 0.0)
        assertEquals(410.0, meal.totalCaloriesKcal, 0.0)

        repository.updateItem(oats, MealItemInput("Avena integrale", 100.0, "g", caloriesKcal = 370.0, notes = " oppure farro "))
        val updated = plan(planId).orderedMeals.first().orderedItems.first()
        assertEquals("Avena integrale", updated.name)
        assertEquals("oppure farro", updated.notes)
        assertNull(updated.proteinG)

        repository.deleteItem(oats)
        assertEquals(listOf("Albume"), plan(planId).orderedMeals.first().orderedItems.map { it.name })
    }

    @Test
    fun meals_canBeAddedRenamedMovedAndDeleted() = runTest {
        val planId = repository.createPlan("Piano")
        val workout = repository.addMeal(planId, MealType.OTHER, " Pre-workout ", LocalTime.of(17, 30))
        assertEquals("Pre-workout", plan(planId).orderedMeals.last().meal.name)

        repository.moveMeal(workout, -1)
        assertEquals(MealType.OTHER, plan(planId).orderedMeals[4].meal.mealType)

        val lunch = plan(planId).orderedMeals.first { it.meal.mealType == MealType.LUNCH }.meal.id
        repository.updateMeal(lunch, MealType.LUNCH, "", LocalTime.of(12, 45))
        val updatedLunch = plan(planId).orderedMeals.first { it.meal.id == lunch }.meal
        assertNull("Nome vuoto = nome del tipo", updatedLunch.name)
        assertEquals(LocalTime.of(12, 45), updatedLunch.timeOfDay)

        repository.deleteMeal(lunch)
        val positions = plan(planId).orderedMeals.map { it.meal.position }
        assertEquals("Posizioni compatte dopo l'eliminazione", (0 until 5).toList(), positions)
    }

    @Test
    fun duplicate_copiesEverything_andDeleteActivatesAnotherPlan() = runTest {
        val planId = repository.createPlan("Massa")
        repository.updateTargets(planId, DietTargets(2800, 180.0, 350.0, 80.0))
        repository.addItem(plan(planId).orderedMeals.first().meal.id, MealItemInput("Pane", 60.0, "g", caloriesKcal = 160.0))

        val copyId = repository.duplicatePlan(planId, "Massa · riposo")
        val copy = plan(copyId)
        assertFalse(copy.plan.isActive)
        assertEquals(2800, copy.plan.targetCaloriesKcal)
        assertEquals(listOf("Pane"), copy.orderedMeals.first().orderedItems.map { it.name })

        repository.deletePlan(planId)
        assertNull(repository.observePlan(planId).first())
        assertTrue("Eliminato l'attivo, si attiva il piano rimasto", plan(copyId).plan.isActive)
    }

    @Test
    fun handTypedTimes_areParsedLeniently() {
        assertEquals(LocalTime.of(7, 30), parseTime("7:30"))
        assertEquals(LocalTime.of(7, 30), parseTime("07.30"))
        assertEquals(LocalTime.of(19, 30), parseTime("1930"))
        assertEquals(LocalTime.of(7, 30), parseTime("730"))
        assertEquals(LocalTime.of(13, 0), parseTime("13"))
        assertNull(parseTime("25:00"))
        assertNull(parseTime("7:3"))
        assertNull(parseTime(""))
    }
}
