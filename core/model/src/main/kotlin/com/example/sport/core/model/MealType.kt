package com.example.sport.core.model

/** Slot del piano alimentare. Nomi persistiti come TEXT: non rinominare senza Migration. */
enum class MealType {
    /** Colazione. */
    BREAKFAST,

    /** Spuntino di metà mattina. */
    MORNING_SNACK,

    /** Pranzo. */
    LUNCH,

    /** Snack pomeridiano. */
    AFTERNOON_SNACK,

    /** Cena. */
    DINNER,

    /** Pasto extra (pre/post workout, spuntino serale...). */
    OTHER,
}
