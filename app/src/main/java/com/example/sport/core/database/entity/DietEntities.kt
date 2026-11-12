package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sport.core.model.MealType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Piano alimentare con target di macro-nutrienti giornalieri. */
@Entity(
    tableName = "diet_plans",
    indices = [Index("is_active")],
)
data class DietPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** Al massimo un piano attivo: garantito da `DietDao.setActivePlan`. */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,

    @ColumnInfo(name = "target_calories_kcal")
    val targetCaloriesKcal: Int? = null,

    @ColumnInfo(name = "target_protein_g")
    val targetProteinG: Double? = null,

    @ColumnInfo(name = "target_carbs_g")
    val targetCarbsG: Double? = null,

    @ColumnInfo(name = "target_fat_g")
    val targetFatG: Double? = null,

    /** Note libere (integrazione, idratazione, indicazioni del nutrizionista...). */
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)

/** Pasto del piano (Colazione, Spuntino, Pranzo, Snack, Cena...). */
@Entity(
    tableName = "meals",
    foreignKeys = [
        ForeignKey(
            entity = DietPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["diet_plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["diet_plan_id", "position"]),
    ],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "diet_plan_id")
    val dietPlanId: Long,

    @ColumnInfo(name = "meal_type")
    val mealType: MealType,

    /** Nome personalizzato; null = nome di default del [mealType]. */
    val name: String? = null,

    val position: Int,

    /** Orario indicativo, persistito come secondi dalla mezzanotte. */
    @ColumnInfo(name = "time_of_day")
    val timeOfDay: LocalTime? = null,

    val notes: String? = null,
)

/** Alimento (riga) di un pasto: "Avena 80 g", "Albume 200 ml". */
@Entity(
    tableName = "meal_items",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["meal_id", "position"]),
    ],
)
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "meal_id")
    val mealId: Long,

    val name: String,

    val quantity: Double? = null,

    /** Unità libera: "g", "ml", "pz", "cucchiaio". */
    val unit: String? = null,

    @ColumnInfo(name = "calories_kcal")
    val caloriesKcal: Double? = null,

    @ColumnInfo(name = "protein_g")
    val proteinG: Double? = null,

    @ColumnInfo(name = "carbs_g")
    val carbsG: Double? = null,

    @ColumnInfo(name = "fat_g")
    val fatG: Double? = null,

    val position: Int,

    /** Alternative o note ("oppure 70 g di riso basmati"). */
    val notes: String? = null,
)

/** Check-off: il pasto [mealId] è stato consumato nel giorno [date]. */
@Entity(
    tableName = "meal_logs",
    primaryKeys = ["meal_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("date"),
    ],
)
data class MealLogEntity(
    @ColumnInfo(name = "meal_id")
    val mealId: Long,

    val date: LocalDate,

    @ColumnInfo(name = "consumed_at")
    val consumedAt: Instant,

    val note: String? = null,
)
