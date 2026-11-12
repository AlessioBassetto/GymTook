package com.example.sport.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.sport.core.database.entity.DietPlanEntity
import com.example.sport.core.database.entity.MealEntity
import com.example.sport.core.database.entity.MealItemEntity

/** Pasto con i suoi alimenti. */
data class MealWithItems(
    @Embedded
    val meal: MealEntity,

    @Relation(parentColumn = "id", entityColumn = "meal_id")
    val items: List<MealItemEntity>,
) {
    val orderedItems: List<MealItemEntity>
        get() = items.sortedBy { it.position }

    val totalCaloriesKcal: Double get() = items.sumOf { it.caloriesKcal ?: 0.0 }
    val totalProteinG: Double get() = items.sumOf { it.proteinG ?: 0.0 }
    val totalCarbsG: Double get() = items.sumOf { it.carbsG ?: 0.0 }
    val totalFatG: Double get() = items.sumOf { it.fatG ?: 0.0 }
}

/** Piano alimentare completo. */
data class DietPlanWithMeals(
    @Embedded
    val plan: DietPlanEntity,

    @Relation(
        entity = MealEntity::class,
        parentColumn = "id",
        entityColumn = "diet_plan_id",
    )
    val meals: List<MealWithItems>,
) {
    val orderedMeals: List<MealWithItems>
        get() = meals.sortedBy { it.meal.position }
}
