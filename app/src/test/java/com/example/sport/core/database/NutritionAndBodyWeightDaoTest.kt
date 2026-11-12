package com.example.sport.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import com.example.sport.core.database.entity.DietPlanEntity
import com.example.sport.core.database.entity.MealEntity
import com.example.sport.core.database.entity.MealItemEntity
import com.example.sport.core.model.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class NutritionAndBodyWeightDaoTest : DatabaseTest() {

    @Test
    fun bodyWeight_oneEntryPerDay_orderedChronologically() = runTest {
        val dao = db.bodyWeightDao()
        val monday = LocalDate.of(2026, 11, 2)
        dao.upsert(BodyWeightEntryEntity(date = monday.plusDays(1), weightKg = 80.4, recordedAt = T0))
        dao.upsert(BodyWeightEntryEntity(date = monday, weightKg = 81.0, recordedAt = T0))
        dao.upsert(BodyWeightEntryEntity(date = monday, weightKg = 80.8, recordedAt = hoursAfterT0(1)))

        val week = dao.observeBetween(monday, monday.plusDays(6)).first()
        assertEquals(listOf(monday, monday.plusDays(1)), week.map { it.date })
        assertEquals("La seconda pesata del giorno sovrascrive la prima", 80.8, week.first().weightKg, 0.0)
        assertEquals(monday.plusDays(1), dao.observeLatest().first()!!.date)
    }

    @Test
    fun dietPlan_mealsItemsTotals_andDailyCheckOff() = runTest {
        val dao = db.dietDao()
        val planId = dao.insertPlan(
            DietPlanEntity(
                name = "Massa pulita",
                targetCaloriesKcal = 2800,
                targetProteinG = 180.0,
                targetCarbsG = 330.0,
                targetFatG = 80.0,
                createdAt = T0,
                updatedAt = T0,
            ),
        )
        dao.setActivePlan(planId, T0)
        val breakfastId = dao.appendMeal(
            MealEntity(dietPlanId = planId, mealType = MealType.BREAKFAST, position = 0, timeOfDay = LocalTime.of(7, 30)),
        )
        val lunchId = dao.appendMeal(MealEntity(dietPlanId = planId, mealType = MealType.LUNCH, position = 0))
        dao.appendItem(
            MealItemEntity(mealId = breakfastId, name = "Fiocchi d'avena", quantity = 80.0, unit = "g",
                caloriesKcal = 300.0, proteinG = 10.0, carbsG = 54.0, fatG = 5.6, position = 0),
        )
        dao.appendItem(
            MealItemEntity(mealId = breakfastId, name = "Albume", quantity = 200.0, unit = "ml",
                caloriesKcal = 100.0, proteinG = 22.0, carbsG = 1.4, fatG = 0.4, position = 0),
        )

        val plan = dao.observeActivePlanWithMeals().first()!!
        assertEquals(listOf(MealType.BREAKFAST, MealType.LUNCH), plan.orderedMeals.map { it.meal.mealType })
        val breakfast = plan.orderedMeals.first()
        assertEquals(LocalTime.of(7, 30), breakfast.meal.timeOfDay)
        assertEquals(listOf("Fiocchi d'avena", "Albume"), breakfast.orderedItems.map { it.name })
        assertEquals(400.0, breakfast.totalCaloriesKcal, 0.001)
        assertEquals(32.0, breakfast.totalProteinG, 0.001)

        val today = LocalDate.of(2026, 11, 2)
        dao.setMealConsumed(breakfastId, today, consumed = true, at = T0)
        dao.setMealConsumed(lunchId, today, consumed = true, at = hoursAfterT0(5))
        dao.setMealConsumed(lunchId, today, consumed = false, at = hoursAfterT0(6))
        assertEquals(listOf(breakfastId), dao.observeLogsForDate(today).first().map { it.mealId })
        assertTrue(dao.observeLogsForDate(today.plusDays(1)).first().isEmpty())
    }
}
