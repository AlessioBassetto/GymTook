package com.example.sport.core.data

import androidx.room.withTransaction
import com.example.sport.core.database.SportDatabase
import com.example.sport.core.database.entity.DietPlanEntity
import com.example.sport.core.database.entity.MealEntity
import com.example.sport.core.database.entity.MealItemEntity
import com.example.sport.core.database.relation.DietPlanWithMeals
import com.example.sport.core.model.MealType
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Obiettivi giornalieri del piano: null = non fissato. */
data class DietTargets(
    val caloriesKcal: Int? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
)

/** Dati di un alimento come li scrive l'utente. */
data class MealItemInput(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val notes: String? = null,
)

/** Calorie dai macronutrienti (Atwater: 4 kcal/g proteine e carboidrati, 9 kcal/g grassi); null se mancano tutti. */
fun caloriesFromMacros(proteinG: Double?, carbsG: Double?, fatG: Double?): Double? =
    if (proteinG == null && carbsG == null && fatG == null) {
        null
    } else {
        (proteinG ?: 0.0) * KCAL_PER_G_PROTEIN + (carbsG ?: 0.0) * KCAL_PER_G_CARBS + (fatG ?: 0.0) * KCAL_PER_G_FAT
    }

private const val KCAL_PER_G_PROTEIN = 4.0
private const val KCAL_PER_G_CARBS = 4.0
private const val KCAL_PER_G_FAT = 9.0

/**
 * Modifica dei piani alimentari: piani, pasti e alimenti. La spunta dei pasti del giorno resta in
 * [NutritionRepository].
 */
@Singleton
class DietPlanRepository @Inject constructor(
    private val database: SportDatabase,
    private val clock: Clock,
) {
    private val dao = database.dietDao()

    private fun now(): Instant = Instant.now(clock)

    fun observePlans(): Flow<List<DietPlanEntity>> = dao.observePlans()

    fun observePlan(planId: Long): Flow<DietPlanWithMeals?> = dao.observePlanWithMeals(planId)

    // ------------------------------------------------------------------ Piani

    /**
     * Nuovo piano con i cinque pasti classici della giornata italiana (vuoti, con orario indicativo).
     * Il primo piano creato diventa attivo.
     */
    suspend fun createPlan(name: String): Long = database.withTransaction {
        val timestamp = now()
        val planId = dao.insertPlan(DietPlanEntity(name = name.trim(), createdAt = timestamp, updatedAt = timestamp))
        DEFAULT_MEALS.forEachIndexed { index, (type, time) ->
            dao.insertMeal(MealEntity(dietPlanId = planId, mealType = type, position = index, timeOfDay = time))
        }
        if (dao.countActivePlans() == 0) dao.setActivePlan(planId, timestamp)
        planId
    }

    suspend fun renamePlan(planId: Long, name: String) = updatePlan(planId) { it.copy(name = name.trim()) }

    suspend fun updateTargets(planId: Long, targets: DietTargets) = updatePlan(planId) {
        it.copy(
            targetCaloriesKcal = targets.caloriesKcal,
            targetProteinG = targets.proteinG,
            targetCarbsG = targets.carbsG,
            targetFatG = targets.fatG,
        )
    }

    suspend fun updateNotes(planId: Long, notes: String?) = updatePlan(planId) { it.copy(notes = notes?.trim()?.ifEmpty { null }) }

    suspend fun setActive(planId: Long) = dao.setActivePlan(planId, now())

    /** Copia completa (pasti e alimenti), non attiva: la variante per i giorni di riposo, il cut… */
    suspend fun duplicatePlan(planId: Long, name: String): Long = database.withTransaction {
        val source = dao.getPlanWithMeals(planId) ?: error("Piano $planId inesistente")
        val timestamp = now()
        val copyId = dao.insertPlan(source.plan.copy(id = 0, name = name.trim(), isActive = false, createdAt = timestamp, updatedAt = timestamp))
        source.orderedMeals.forEach { meal ->
            val mealId = dao.insertMeal(meal.meal.copy(id = 0, dietPlanId = copyId))
            meal.orderedItems.forEach { item -> dao.insertItem(item.copy(id = 0, mealId = mealId)) }
        }
        copyId
    }

    /** Elimina il piano (pasti, alimenti e spunte). Se era attivo, si attiva il più recente rimasto. */
    suspend fun deletePlan(planId: Long) = database.withTransaction {
        val plan = dao.getPlan(planId) ?: return@withTransaction
        dao.deletePlan(plan)
        if (plan.isActive) dao.getMostRecentInactivePlan()?.let { dao.setActivePlan(it.id, now()) }
    }

    // ------------------------------------------------------------------ Pasti

    /** Pasto in coda al piano; nome vuoto = nome del tipo. */
    suspend fun addMeal(planId: Long, type: MealType, name: String? = null, time: LocalTime? = DEFAULT_TIMES[type]): Long =
        database.withTransaction {
            dao.touchPlan(planId, now())
            dao.appendMeal(
                MealEntity(dietPlanId = planId, mealType = type, name = name?.trim()?.ifEmpty { null }, position = 0, timeOfDay = time),
            )
        }

    /** Tipo, nome personalizzato (vuoto = nome del tipo) e orario indicativo. */
    suspend fun updateMeal(mealId: Long, type: MealType, name: String?, time: LocalTime?) = database.withTransaction {
        val meal = dao.getMeal(mealId) ?: return@withTransaction
        dao.updateMeal(meal.copy(mealType = type, name = name?.trim()?.ifEmpty { null }, timeOfDay = time))
        dao.touchPlan(meal.dietPlanId, now())
    }

    suspend fun moveMeal(mealId: Long, delta: Int) = database.withTransaction {
        val meal = dao.getMeal(mealId) ?: return@withTransaction
        val meals = dao.getMeals(meal.dietPlanId)
        val from = meals.indexOfFirst { it.id == mealId }
        dao.moveMeal(meal.dietPlanId, from, (from + delta).coerceIn(0, meals.lastIndex))
    }

    suspend fun deleteMeal(mealId: Long) = database.withTransaction {
        val meal = dao.getMeal(mealId) ?: return@withTransaction
        dao.deleteMeal(meal)
        // Posizioni compatte: lo spostamento su/giù lavora per indice.
        dao.updateMeals(dao.getMeals(meal.dietPlanId).mapIndexed { index, it -> it.copy(position = index) })
        dao.touchPlan(meal.dietPlanId, now())
    }

    // ------------------------------------------------------------------ Alimenti

    suspend fun addItem(mealId: Long, input: MealItemInput): Long = database.withTransaction {
        val meal = dao.getMeal(mealId) ?: error("Pasto $mealId inesistente")
        dao.touchPlan(meal.dietPlanId, now())
        dao.appendItem(input.toEntity(mealId, position = 0))
    }

    suspend fun updateItem(itemId: Long, input: MealItemInput) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.updateItem(input.toEntity(item.mealId, item.position).copy(id = itemId))
        dao.getMeal(item.mealId)?.let { dao.touchPlan(it.dietPlanId, now()) }
    }

    suspend fun deleteItem(itemId: Long) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.deleteItem(item)
        dao.getMeal(item.mealId)?.let { dao.touchPlan(it.dietPlanId, now()) }
    }

    private suspend fun updatePlan(planId: Long, change: (DietPlanEntity) -> DietPlanEntity) {
        val plan = dao.getPlan(planId) ?: return
        dao.updatePlan(change(plan).copy(updatedAt = now()))
    }

    /** Senza calorie ma con i macro, le calorie si ricavano dai macro (arrotondate). */
    private fun MealItemInput.toEntity(mealId: Long, position: Int) = MealItemEntity(
        mealId = mealId,
        name = name.trim(),
        quantity = quantity,
        unit = unit?.trim()?.ifEmpty { null },
        caloriesKcal = caloriesKcal ?: caloriesFromMacros(proteinG, carbsG, fatG)?.roundToInt()?.toDouble(),
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        position = position,
        notes = notes?.trim()?.ifEmpty { null },
    )

    companion object {
        /** Orari indicativi dei pasti di una giornata tipo. */
        val DEFAULT_TIMES: Map<MealType, LocalTime?> = mapOf(
            MealType.BREAKFAST to LocalTime.of(7, 30),
            MealType.MORNING_SNACK to LocalTime.of(10, 30),
            MealType.LUNCH to LocalTime.of(13, 0),
            MealType.AFTERNOON_SNACK to LocalTime.of(16, 30),
            MealType.DINNER to LocalTime.of(20, 0),
            MealType.OTHER to null,
        )

        private val DEFAULT_MEALS = listOf(
            MealType.BREAKFAST,
            MealType.MORNING_SNACK,
            MealType.LUNCH,
            MealType.AFTERNOON_SNACK,
            MealType.DINNER,
        ).map { it to DEFAULT_TIMES[it] }
    }
}
