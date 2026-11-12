package com.example.sport.core.model

/**
 * Categoria di attrezzatura di un esercizio.
 *
 * I nomi delle costanti sono persistiti come TEXT nel database e usati nel catalogo JSON:
 * non rinominarli senza una Migration Room.
 */
enum class Equipment(
    /** Se true la card "Regolazione macchinario" è proposta di default nel logger. */
    val isAdjustableByDefault: Boolean,
) {
    /** Corpo libero. */
    BODYWEIGHT(isAdjustableByDefault = false),

    /** Manubri. */
    DUMBBELL(isAdjustableByDefault = false),

    /** Cavi / carrucole (altezza carrucola regolabile). */
    CABLE(isAdjustableByDefault = true),

    /** Macchinari guidati (isotoniche, plate-loaded, selectorized). */
    MACHINE(isAdjustableByDefault = true),

    /** Bilanciere (olimpico, EZ, trap bar). */
    BARBELL(isAdjustableByDefault = false),

    /** Multipower / Smith machine. */
    SMITH_MACHINE(isAdjustableByDefault = true),

    /** Kettlebell. */
    KETTLEBELL(isAdjustableByDefault = false),

    /** Elastici / bande di resistenza. */
    RESISTANCE_BAND(isAdjustableByDefault = false),

    /**
     * Macchine cardio (tapis roulant, bike, vogatore, ellittica). Non regolabili di default:
     * quelle che lo sono (sella della bike, damper del vogatore) lo dichiarano nel catalogo.
     */
    CARDIO_MACHINE(isAdjustableByDefault = false),

    /** Altro (dischi, TRX, palla medica...). */
    OTHER(isAdjustableByDefault = false),
}
