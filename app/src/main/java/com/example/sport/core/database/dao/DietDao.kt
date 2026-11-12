package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.example.sport.core.database.entity.DietPlanEntity
import com.example.sport.core.database.entity.MealEntity
import com.example.sport.core.database.entity.MealItemEntity
import com.example.sport.core.database.entity.MealLogEntity
import com.example.sport.core.database.relation.DietPlanWithMeals
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Piani alimentari, pasti, alimenti e check-off giornaliero. */
@Dao
interface DietDao {

    @Query("SELECT * FROM diet_plans ORDER BY is_active DESC, updated_at DESC")
    fun observePlans(): Flow<List<DietPlanEntity>>

    @Transaction
    @Query("SELECT * FROM diet_plans WHERE is_active = 1 LIMIT 1")
    fun observeActivePlanWithMeals(): Flow<DietPlanWithMeals?>

    @Transaction
    @Query("SELECT * FROM diet_plans WHERE id = :planId")
    fun observePlanWithMeals(planId: Long): Flow<DietPlanWithMeals?>

    /** Pasti spuntati nel giorno [date] (per il piano attivo li incrocia la UI tramite meal_id). */
    @Query("SELECT * FROM meal_logs WHERE date = :date")
    fun observeLogsForDate(date: LocalDate): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM diet_plans WHERE id = :planId")
    suspend fun getPlan(planId: Long): DietPlanEntity?

    @Transaction
    @Query("SELECT * FROM diet_plans WHERE id = :planId")
    suspend fun getPlanWithMeals(planId: Long): DietPlanWithMeals?

    @Query("SELECT COUNT(*) FROM diet_plans WHERE is_active = 1")
    suspend fun countActivePlans(): Int

    @Query("SELECT * FROM diet_plans WHERE is_active = 0 ORDER BY updated_at DESC LIMIT 1")
    suspend fun getMostRecentInactivePlan(): DietPlanEntity?

    @Query("SELECT * FROM meals WHERE id = :mealId")
    suspend fun getMeal(mealId: Long): MealEntity?

    @Query("SELECT * FROM meal_items WHERE id = :itemId")
    suspend fun getItem(itemId: Long): MealItemEntity?

    @Query("UPDATE diet_plans SET updated_at = :now WHERE id = :planId")
    suspend fun touchPlan(planId: Long, now: Instant)

    @Query("SELECT * FROM meals WHERE diet_plan_id = :planId ORDER BY position")
    suspend fun getMeals(planId: Long): List<MealEntity>

    @Query("SELECT * FROM meal_items WHERE meal_id = :mealId ORDER BY position")
    suspend fun getItems(mealId: Long): List<MealItemEntity>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM meals WHERE diet_plan_id = :planId")
    suspend fun nextMealPosition(planId: Long): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM meal_items WHERE meal_id = :mealId")
    suspend fun nextItemPosition(mealId: Long): Int

    @Insert
    suspend fun insertPlan(plan: DietPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: DietPlanEntity)

    @Delete
    suspend fun deletePlan(plan: DietPlanEntity)

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Update
    suspend fun updateMeals(meals: List<MealEntity>)

    @Delete
    suspend fun deleteMeal(meal: MealEntity)

    @Insert
    suspend fun insertItem(item: MealItemEntity): Long

    @Update
    suspend fun updateItem(item: MealItemEntity)

    @Delete
    suspend fun deleteItem(item: MealItemEntity)

    @Upsert
    suspend fun upsertLog(log: MealLogEntity)

    @Query("DELETE FROM meal_logs WHERE meal_id = :mealId AND date = :date")
    suspend fun deleteLog(mealId: Long, date: LocalDate)

    @Query("UPDATE diet_plans SET is_active = 0 WHERE is_active = 1")
    suspend fun clearActivePlan()

    @Query("UPDATE diet_plans SET is_active = 1, updated_at = :now WHERE id = :planId")
    suspend fun markActive(planId: Long, now: Instant)

    @Transaction
    suspend fun setActivePlan(planId: Long, now: Instant) {
        clearActivePlan()
        markActive(planId, now)
    }

    /** Aggiunge un pasto in coda al piano (la `position` passata viene ignorata). */
    @Transaction
    suspend fun appendMeal(meal: MealEntity): Long =
        insertMeal(meal.copy(id = 0, position = nextMealPosition(meal.dietPlanId)))

    /** Aggiunge un alimento in coda al pasto (la `position` passata viene ignorata). */
    @Transaction
    suspend fun appendItem(item: MealItemEntity): Long =
        insertItem(item.copy(id = 0, position = nextItemPosition(item.mealId)))

    @Transaction
    suspend fun moveMeal(planId: Long, fromIndex: Int, toIndex: Int) {
        val meals = getMeals(planId).toMutableList()
        if (fromIndex !in meals.indices || toIndex !in meals.indices || fromIndex == toIndex) return
        meals.add(toIndex, meals.removeAt(fromIndex))
        updateMeals(meals.mapIndexed { index, meal -> meal.copy(position = index) })
    }

    /** Check-off del pasto nel giorno indicato. */
    @Transaction
    suspend fun setMealConsumed(mealId: Long, date: LocalDate, consumed: Boolean, at: Instant) {
        if (consumed) {
            upsertLog(MealLogEntity(mealId = mealId, date = date, consumedAt = at))
        } else {
            deleteLog(mealId, date)
        }
    }
}
