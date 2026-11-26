package com.example.sport.core.model

/**
 * Categoria di un alimento o piatto del catalogo dieta. Nomi persistiti come TEXT: non rinominare
 * senza Migration.
 */
enum class FoodCategory {
    /** Colazione e dolci fit: avena, pancake proteici, fette biscottate, yogurt, creme spalmabili. */
    BREAKFAST_SWEETS,

    /** Primi piatti e cereali: pasta, risotti, gnocchi, lasagne, riso, pane. */
    FIRST_COURSES,

    /** Secondi di carne. */
    MEAT,

    /** Secondi di pesce e frutti di mare. */
    FISH,

    /** Piatti vegetariani, legumi, uova e latticini. */
    VEGETARIAN,

    /** Contorni e verdure. */
    SIDES,

    /** Frutta, frutta secca, snack, integratori e bevande. */
    FRUIT_SNACKS_DRINKS,

    /** Prodotti confezionati (da Open Food Facts) e piatti non classificati. */
    OTHER,
}

/** Da dove viene un alimento: il catalogo dell'app non entra nel backup, gli altri sì. */
enum class FoodSource {
    /** Catalogo italiano impacchettato nell'app (`italian_dishes_catalog.json`). */
    CATALOG,

    /** Piatto creato dall'utente con i valori per 100 g. */
    CUSTOM,

    /** Ricetta dell'utente: valori per 100 g calcolati dagli ingredienti. */
    RECIPE,

    /** Prodotto scaricato da Open Food Facts e salvato per l'uso offline. */
    OPEN_FOOD_FACTS,
    ;

    /** Alimenti dell'utente: si modificano, si eliminano e finiscono nel backup. */
    val isUserFood: Boolean get() = this != CATALOG
}
