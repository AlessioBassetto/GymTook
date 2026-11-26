package com.example.sport.core.data.food

import com.example.sport.core.database.entity.FoodItemEntity
import com.example.sport.core.database.entity.FoodRecipeIngredientEntity
import com.example.sport.core.database.entity.MealEntryEntity
import com.example.sport.core.database.util.SearchText
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.FoodSource
import com.example.sport.core.model.MealType

/** Energia e macronutrienti (kcal e grammi): per 100 g di un alimento o per una porzione. */
data class Nutrients(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
) {
    operator fun plus(other: Nutrients) = Nutrients(kcal + other.kcal, proteinG + other.proteinG, carbsG + other.carbsG, fatG + other.fatG)

    operator fun times(factor: Double) = Nutrients(kcal * factor, proteinG * factor, carbsG * factor, fatG * factor)

    /**
     * Porzione di [grams] grammi di un alimento con questi valori per 100 g:
     * Calorie = kcalPer100g × grammi / 100 (e lo stesso per ogni macronutriente).
     */
    fun forGrams(grams: Double): Nutrients = this * (grams.coerceAtLeast(0.0) / 100.0)

    companion object {
        val ZERO = Nutrients(0.0, 0.0, 0.0, 0.0)

        /** Atwater: 4 kcal per grammo di proteine e carboidrati, 9 per i grassi. */
        fun kcalFromMacros(proteinG: Double, carbsG: Double, fatG: Double): Double = 4 * proteinG + 4 * carbsG + 9 * fatG
    }
}

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO, Nutrients::plus)

val FoodItemEntity.per100g: Nutrients get() = Nutrients(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g)

val MealEntryEntity.per100g: Nutrients get() = Nutrients(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g)

/** Valori della voce del diario, calcolati ora dalla grammatura: cambiano appena cambiano i grammi. */
val MealEntryEntity.nutrients: Nutrients get() = per100g.forGrams(grams)

val FoodRecipeIngredientEntity.per100g: Nutrients get() = Nutrients(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g)

/** Ingrediente di una ricetta in modifica: l'alimento scelto e i suoi grammi. */
data class RecipeIngredientDraft(
    val foodId: String?,
    val name: String,
    val grams: Double,
    val per100g: Nutrients,
)

/**
 * Valori per 100 g di una ricetta: somma degli ingredienti divisa per il peso. Se l'utente indica
 * il peso del piatto finito (la pasta cotta pesa più di quella cruda, l'arrosto cala) si usa quello,
 * altrimenti la somma dei grammi degli ingredienti.
 */
fun recipePer100g(ingredients: List<RecipeIngredientDraft>, finalWeightGrams: Double? = null): Nutrients {
    val totalGrams = ingredients.sumOf { it.grams.coerceAtLeast(0.0) }
    val weight = finalWeightGrams?.takeIf { it > 0 } ?: totalGrams
    if (weight <= 0) return Nutrients.ZERO
    val total = ingredients.map { it.per100g.forGrams(it.grams) }.sum()
    return total * (100.0 / weight)
}

/** Filtro del selettore piatti: una categoria, tutti, oppure solo i piatti dell'utente. */
sealed interface FoodFilter {
    data object All : FoodFilter
    data object Mine : FoodFilter
    data class Category(val category: FoodCategory) : FoodFilter

    fun accepts(food: FoodItemEntity): Boolean = when (this) {
        All -> true
        Mine -> food.source.isUserFood
        is Category -> food.category == category
    }
}

/**
 * Ricerca istantanea in memoria: ogni parola della query deve comparire in nome, marca o sinonimi
 * (senza accenti e maiuscole: "ragu" trova "Pasta al ragù"). In testa i nomi che iniziano con la
 * query, poi quelli con una parola che inizia così, poi gli altri; a parità il nome più corto.
 * Senza query: tutti i piatti del filtro, prima quelli dell'utente.
 */
fun searchFoods(foods: List<FoodItemEntity>, query: String, filter: FoodFilter = FoodFilter.All): List<FoodItemEntity> {
    val candidates = foods.filter(filter::accepts)
    val tokens = SearchText.tokenize(query)
    if (tokens.isEmpty()) {
        return candidates.sortedWith(compareBy<FoodItemEntity>({ !it.source.isUserFood }, { it.name.lowercase() }))
    }
    val normalizedQuery = tokens.joinToString(" ")
    return candidates
        .filter { food -> tokens.all { it in food.searchText } }
        .sortedWith(
            compareBy<FoodItemEntity>(
                { food ->
                    val name = SearchText.normalize(food.name)
                    when {
                        name.startsWith(normalizedQuery) -> 0
                        name.split(' ').any { it.startsWith(tokens.first()) } -> 1
                        else -> 2
                    }
                },
                { it.name.length },
                { it.name.lowercase() },
            ),
        )
}

/** I cinque pasti del diario, nell'ordine della giornata. */
val DIARY_MEALS: List<MealType> = listOf(
    MealType.BREAKFAST,
    MealType.MORNING_SNACK,
    MealType.LUNCH,
    MealType.AFTERNOON_SNACK,
    MealType.DINNER,
)

/** Prefissi degli id degli alimenti dell'utente. */
const val CUSTOM_FOOD_PREFIX = "custom:"
const val OPEN_FOOD_FACTS_PREFIX = "off:"

/** Un alimento dell'utente si riconosce dalla provenienza: [FoodSource.isUserFood]. */
val FoodItemEntity.isEditable: Boolean get() = source == FoodSource.CUSTOM || source == FoodSource.RECIPE
