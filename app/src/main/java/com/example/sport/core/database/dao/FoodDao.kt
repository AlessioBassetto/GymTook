package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.sport.core.database.entity.AppMetadataEntity
import com.example.sport.core.database.entity.FoodItemEntity
import com.example.sport.core.database.entity.FoodRecipeIngredientEntity
import com.example.sport.core.database.entity.MealEntryEntity
import com.example.sport.core.model.MealType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Catalogo alimenti, piatti e ricette dell'utente, diario alimentare. */
@Dao
interface FoodDao {

    // ------------------------------------------------------------------ Alimenti

    /**
     * Tutti gli alimenti (qualche centinaio di catalogo più quelli dell'utente): la ricerca per
     * parole e la classifica dei risultati si fanno in memoria, istantanee e testabili.
     */
    @Query("SELECT * FROM food_items ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getFood(id: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE id = :id")
    fun observeFood(id: String): Flow<FoodItemEntity?>

    @Upsert
    suspend fun upsertFood(item: FoodItemEntity)

    @Upsert
    suspend fun upsertFoods(items: List<FoodItemEntity>)

    /** Solo gli alimenti dell'utente si eliminano; il diario passato resta (food_item_id → NULL). */
    @Query("DELETE FROM food_items WHERE id = :id AND source != 'CATALOG'")
    suspend fun deleteUserFood(id: String)

    @Query("SELECT COUNT(*) FROM food_items WHERE source = 'CATALOG'")
    suspend fun countCatalogFoods(): Int

    @Query("SELECT id FROM food_items WHERE source = 'CATALOG'")
    suspend fun getCatalogFoodIds(): List<String>

    @Query("DELETE FROM food_items WHERE source = 'CATALOG' AND id IN (:ids)")
    suspend fun deleteCatalogFoods(ids: List<String>)

    @Query("SELECT `value` FROM app_metadata WHERE `key` = :key")
    suspend fun getMetadata(key: String): String?

    @Upsert
    suspend fun upsertMetadata(entry: AppMetadataEntity)

    suspend fun getInstalledCatalogVersion(): Int =
        getMetadata(AppMetadataEntity.KEY_FOOD_CATALOG_VERSION)?.toIntOrNull() ?: 0

    /**
     * Installa o aggiorna il catalogo italiano in un'unica transazione: aggiorna i piatti esistenti,
     * aggiunge i nuovi, toglie quelli non più presenti (il diario che li usava resta intatto).
     *
     * @return numero di piatti rimossi.
     */
    @Transaction
    suspend fun applyCatalog(version: Int, foods: List<FoodItemEntity>): Int {
        val removed = getCatalogFoodIds() - foods.map { it.id }.toSet()
        upsertFoods(foods)
        removed.chunked(MAX_SQL_ARGS).forEach { deleteCatalogFoods(it) }
        upsertMetadata(AppMetadataEntity(AppMetadataEntity.KEY_FOOD_CATALOG_VERSION, version.toString()))
        return removed.size
    }

    // ------------------------------------------------------------------ Ricette

    @Query("SELECT * FROM food_recipe_ingredients WHERE recipe_id = :recipeId ORDER BY position")
    suspend fun getIngredients(recipeId: String): List<FoodRecipeIngredientEntity>

    @Query("DELETE FROM food_recipe_ingredients WHERE recipe_id = :recipeId")
    suspend fun deleteIngredients(recipeId: String)

    @Insert
    suspend fun insertIngredients(ingredients: List<FoodRecipeIngredientEntity>)

    /** Salva la ricetta e sostituisce i suoi ingredienti in un'unica transazione. */
    @Transaction
    suspend fun saveRecipe(recipe: FoodItemEntity, ingredients: List<FoodRecipeIngredientEntity>) {
        upsertFood(recipe)
        deleteIngredients(recipe.id)
        insertIngredients(ingredients.mapIndexed { index, item -> item.copy(id = 0, recipeId = recipe.id, position = index) })
    }

    // ------------------------------------------------------------------ Diario

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY created_at, id")
    fun observeEntries(date: LocalDate): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entries WHERE id = :id")
    suspend fun getEntry(id: Long): MealEntryEntity?

    @Insert
    suspend fun insertEntry(entry: MealEntryEntity): Long

    @Query("UPDATE meal_entries SET grams = :grams, meal_type = :mealType WHERE id = :id")
    suspend fun updateEntry(id: Long, grams: Double, mealType: MealType)

    @Delete
    suspend fun deleteEntry(entry: MealEntryEntity)
}

/** Ben sotto il limite storico di 999 parametri per query di SQLite. */
private const val MAX_SQL_ARGS = 500
