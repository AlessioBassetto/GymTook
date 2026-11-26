package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.FoodSource
import com.example.sport.core.model.MealType
import java.time.Instant
import java.time.LocalDate

/**
 * Alimento o piatto con i valori nutrizionali **per 100 g**: catalogo italiano dell'app, piatti e
 * ricette dell'utente, prodotti salvati da Open Food Facts.
 *
 * L'id dice la provenienza e resta stabile: `cat:pasta_al_pomodoro`, `custom:<uuid>`,
 * `off:<codice a barre>`.
 */
@Entity(
    tableName = "food_items",
    indices = [
        Index("category"),
        Index("source"),
    ],
)
data class FoodItemEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    /** Marca (solo prodotti confezionati). */
    val brand: String? = null,

    val category: FoodCategory,

    val source: FoodSource,

    @ColumnInfo(name = "kcal_per_100g")
    val kcalPer100g: Double,

    @ColumnInfo(name = "protein_per_100g")
    val proteinPer100g: Double,

    @ColumnInfo(name = "carbs_per_100g")
    val carbsPer100g: Double,

    @ColumnInfo(name = "fat_per_100g")
    val fatPer100g: Double,

    /** Porzione tipica proposta all'apertura del pannello grammi. */
    @ColumnInfo(name = "default_grams")
    val defaultGrams: Double,

    /** Nome, marca e sinonimi normalizzati (minuscole, senza accenti): base della ricerca. */
    @ColumnInfo(name = "search_text")
    val searchText: String,

    /** Versione del catalogo che ha scritto la riga (null per gli alimenti dell'utente). */
    @ColumnInfo(name = "catalog_version")
    val catalogVersion: Int? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)

/**
 * Ingrediente di una ricetta dell'utente ("Pasta al tonno della nonna" = pasta 80 g + tonno 60 g…).
 * I valori per 100 g dell'ingrediente sono copiati: la ricetta resta corretta anche se l'alimento
 * originale cambia o viene eliminato ([ingredientId] diventa null).
 */
@Entity(
    tableName = "food_recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["recipe_id", "position"]),
        Index("ingredient_id"),
    ],
)
data class FoodRecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "recipe_id")
    val recipeId: String,

    @ColumnInfo(name = "ingredient_id")
    val ingredientId: String?,

    val name: String,

    val grams: Double,

    @ColumnInfo(name = "kcal_per_100g")
    val kcalPer100g: Double,

    @ColumnInfo(name = "protein_per_100g")
    val proteinPer100g: Double,

    @ColumnInfo(name = "carbs_per_100g")
    val carbsPer100g: Double,

    @ColumnInfo(name = "fat_per_100g")
    val fatPer100g: Double,

    val position: Int,
)

/**
 * Voce del diario alimentare: un piatto mangiato in un pasto del giorno, con la sua grammatura.
 *
 * Le calorie **non** sono salvate: si calcolano a ogni lettura da grammi × valori per 100 g, così
 * cambiare la grammatura aggiorna subito pasto e giorno. I valori per 100 g sono copiati
 * dall'alimento al momento dell'inserimento: il diario passato non cambia se l'alimento viene
 * modificato o eliminato ([foodItemId] diventa null).
 */
@Entity(
    tableName = "meal_entries",
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_item_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["date", "meal_type"]),
        Index("food_item_id"),
    ],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val date: LocalDate,

    @ColumnInfo(name = "meal_type")
    val mealType: MealType,

    @ColumnInfo(name = "food_item_id")
    val foodItemId: String?,

    val name: String,

    val brand: String? = null,

    val grams: Double,

    @ColumnInfo(name = "kcal_per_100g")
    val kcalPer100g: Double,

    @ColumnInfo(name = "protein_per_100g")
    val proteinPer100g: Double,

    @ColumnInfo(name = "carbs_per_100g")
    val carbsPer100g: Double,

    @ColumnInfo(name = "fat_per_100g")
    val fatPer100g: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
)
