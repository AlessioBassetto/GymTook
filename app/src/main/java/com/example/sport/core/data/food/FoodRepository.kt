package com.example.sport.core.data.food

import com.example.sport.core.database.dao.FoodDao
import com.example.sport.core.database.entity.FoodItemEntity
import com.example.sport.core.database.entity.FoodRecipeIngredientEntity
import com.example.sport.core.database.entity.MealEntryEntity
import com.example.sport.core.database.util.SearchText
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.FoodSource
import com.example.sport.core.model.MealType
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dieta "a grammi": catalogo dei piatti (italiano, dell'utente, da Open Food Facts) e diario del
 * giorno. Le calorie delle voci del diario non sono salvate: si calcolano dai grammi a ogni lettura.
 */
@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val openFoodFacts: OpenFoodFactsClient,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    // ------------------------------------------------------------------ Piatti

    fun observeFoods(): Flow<List<FoodItemEntity>> = foodDao.observeAll()

    suspend fun food(id: String): FoodItemEntity? = foodDao.getFood(id)

    /**
     * Piatto dell'utente con i valori per 100 g ([id] null = nuovo). Senza calorie si calcolano dai
     * macronutrienti (4/4/9). @return id del piatto.
     */
    suspend fun saveCustomFood(
        id: String?,
        name: String,
        category: FoodCategory,
        kcalPer100g: Double?,
        proteinPer100g: Double,
        carbsPer100g: Double,
        fatPer100g: Double,
        defaultGrams: Double,
    ): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Il piatto deve avere un nome" }
        val kcal = kcalPer100g ?: Nutrients.kcalFromMacros(proteinPer100g, carbsPer100g, fatPer100g)
        requireValues(kcal, proteinPer100g, carbsPer100g, fatPer100g)
        require(defaultGrams in 1.0..MAX_GRAMS) { "Porzione fuori scala: $defaultGrams g" }
        val now = Instant.now(clock)
        val existing = id?.let { foodDao.getFood(it) }?.also { require(it.source == FoodSource.CUSTOM) { "Non è un piatto personalizzato" } }
        val entity = FoodItemEntity(
            id = existing?.id ?: newCustomId(),
            name = cleanName,
            category = category,
            source = FoodSource.CUSTOM,
            kcalPer100g = kcal,
            proteinPer100g = proteinPer100g,
            carbsPer100g = carbsPer100g,
            fatPer100g = fatPer100g,
            defaultGrams = defaultGrams,
            searchText = SearchText.build(cleanName, null, emptyList()),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        foodDao.upsertFood(entity)
        return entity.id
    }

    /**
     * Ricetta dell'utente fatta di alimenti del database: i valori per 100 g si calcolano dagli
     * ingredienti ([finalWeightGrams] = peso del piatto finito, facoltativo). @return id della ricetta.
     */
    suspend fun saveRecipe(
        id: String?,
        name: String,
        category: FoodCategory,
        ingredients: List<RecipeIngredientDraft>,
        finalWeightGrams: Double? = null,
    ): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "La ricetta deve avere un nome" }
        val valid = ingredients.filter { it.grams > 0 }
        require(valid.isNotEmpty()) { "La ricetta deve avere almeno un ingrediente" }
        val totalGrams = valid.sumOf { it.grams }
        require(totalGrams <= MAX_RECIPE_GRAMS) { "Ricetta troppo pesante: $totalGrams g" }
        val per100 = recipePer100g(valid, finalWeightGrams)
        val now = Instant.now(clock)
        val existing = id?.let { foodDao.getFood(it) }?.also { require(it.source == FoodSource.RECIPE) { "Non è una ricetta" } }
        val recipe = FoodItemEntity(
            id = existing?.id ?: newCustomId(),
            name = cleanName,
            category = category,
            source = FoodSource.RECIPE,
            kcalPer100g = per100.kcal,
            proteinPer100g = per100.proteinG,
            carbsPer100g = per100.carbsG,
            fatPer100g = per100.fatG,
            // Una porzione = il piatto intero, finché l'utente non sceglie altro nel pannello grammi.
            defaultGrams = (finalWeightGrams?.takeIf { it > 0 } ?: totalGrams).coerceIn(1.0, MAX_GRAMS),
            searchText = SearchText.build(cleanName, null, valid.map { it.name }),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        foodDao.saveRecipe(
            recipe,
            valid.map { ingredient ->
                FoodRecipeIngredientEntity(
                    recipeId = recipe.id,
                    ingredientId = ingredient.foodId,
                    name = ingredient.name,
                    grams = ingredient.grams,
                    kcalPer100g = ingredient.per100g.kcal,
                    proteinPer100g = ingredient.per100g.proteinG,
                    carbsPer100g = ingredient.per100g.carbsG,
                    fatPer100g = ingredient.per100g.fatG,
                    position = 0,
                )
            },
        )
        return recipe.id
    }

    suspend fun recipeIngredients(recipeId: String): List<RecipeIngredientDraft> =
        foodDao.getIngredients(recipeId).map { RecipeIngredientDraft(it.ingredientId, it.name, it.grams, it.per100g) }

    /** Elimina un piatto, una ricetta o un prodotto salvato; le voci del diario restano. */
    suspend fun deleteUserFood(id: String) = foodDao.deleteUserFood(id)

    // ------------------------------------------------------------------ Open Food Facts

    /** Ricerca online (solo su richiesta dell'utente): vedi [OpenFoodFactsClient]. */
    suspend fun searchOnline(query: String): List<OnlineFood> = openFoodFacts.search(query)

    /** Salva il prodotto scelto per usarlo anche offline (id stabile `off:<codice>`). */
    suspend fun saveOnlineFood(food: OnlineFood): FoodItemEntity {
        val now = Instant.now(clock)
        val id = OPEN_FOOD_FACTS_PREFIX + food.code
        val existing = foodDao.getFood(id)
        val entity = FoodItemEntity(
            id = id,
            name = food.name,
            brand = food.brand,
            category = FoodCategory.OTHER,
            source = FoodSource.OPEN_FOOD_FACTS,
            kcalPer100g = food.per100g.kcal,
            proteinPer100g = food.per100g.proteinG,
            carbsPer100g = food.per100g.carbsG,
            fatPer100g = food.per100g.fatG,
            defaultGrams = existing?.defaultGrams ?: DEFAULT_PRODUCT_GRAMS,
            searchText = SearchText.build(food.name, null, listOfNotNull(food.brand)),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        foodDao.upsertFood(entity)
        return entity
    }

    // ------------------------------------------------------------------ Diario

    fun observeDiary(date: LocalDate): Flow<List<MealEntryEntity>> = foodDao.observeEntries(date)

    /** Aggiunge [grams] g di [food] al pasto: i valori per 100 g vengono copiati nella voce. */
    suspend fun addEntry(date: LocalDate, mealType: MealType, food: FoodItemEntity, grams: Double): Long {
        requireGrams(grams)
        return foodDao.insertEntry(
            MealEntryEntity(
                date = date,
                mealType = mealType,
                foodItemId = food.id,
                name = food.name,
                brand = food.brand,
                grams = grams,
                kcalPer100g = food.kcalPer100g,
                proteinPer100g = food.proteinPer100g,
                carbsPer100g = food.carbsPer100g,
                fatPer100g = food.fatPer100g,
                createdAt = Instant.now(clock),
            ),
        )
    }

    /** Nuova grammatura (ed eventualmente un altro pasto) di una voce già inserita. */
    suspend fun updateEntry(id: Long, grams: Double, mealType: MealType) {
        requireGrams(grams)
        foodDao.updateEntry(id, grams, mealType)
    }

    /** Elimina la voce e la restituisce, per l'"Annulla" della barra in basso. */
    suspend fun deleteEntry(id: Long): MealEntryEntity? {
        val entry = foodDao.getEntry(id) ?: return null
        foodDao.deleteEntry(entry)
        return entry
    }

    /** Annulla un'eliminazione: la voce torna identica (stesso id, stessi valori). */
    suspend fun restoreEntry(entry: MealEntryEntity) {
        foodDao.insertEntry(entry)
    }

    private fun requireGrams(grams: Double) = require(grams > 0 && grams <= MAX_GRAMS) { "Grammatura fuori scala: $grams g" }

    private fun requireValues(kcal: Double, protein: Double, carbs: Double, fat: Double) {
        require(listOf(kcal, protein, carbs, fat).all { it >= 0 && it.isFinite() }) { "Valori nutrizionali non validi" }
        require(kcal <= MAX_KCAL_PER_100G) { "Oltre $MAX_KCAL_PER_100G kcal per 100 g" }
        require(protein + carbs + fat <= 100.5) { "I macronutrienti superano 100 g" }
    }

    private fun newCustomId(): String = CUSTOM_FOOD_PREFIX + UUID.randomUUID()

    companion object {
        /** Grammatura massima di una voce del diario (5 kg: anche un'anguria intera). */
        const val MAX_GRAMS = 5000.0
        const val MAX_RECIPE_GRAMS = 20_000.0
        const val MAX_KCAL_PER_100G = 900.0

        /** Porzione proposta per un prodotto confezionato appena scaricato. */
        const val DEFAULT_PRODUCT_GRAMS = 100.0
    }
}
