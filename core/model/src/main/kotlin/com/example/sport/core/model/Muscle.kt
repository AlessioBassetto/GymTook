package com.example.sport.core.model

/**
 * Macro-gruppo muscolare: livello usato dai filtri principali della libreria esercizi
 * e dalle statistiche di volume settimanale.
 *
 * Nomi persistiti come TEXT: non rinominare senza Migration.
 */
enum class MuscleGroup {
    /** Addominali. */
    ABS,

    /** Avambracci. */
    FOREARMS,

    /** Bicipiti. */
    BICEPS,

    /** Collo. */
    NECK,

    /** Dorsali (gran dorsale). */
    LATS,

    /** Parte bassa della schiena (lombari / erettori spinali). */
    LOWER_BACK,

    /** Parte superiore della schiena (romboidi, trapezio medio). */
    UPPER_BACK,

    /** Petto. */
    CHEST,

    /** Spalle (deltoidi). */
    SHOULDERS,

    /** Trapezi (fasci superiori). */
    TRAPS,

    /** Tricipiti. */
    TRICEPS,

    /** Abduttori. */
    ABDUCTORS,

    /** Adduttori. */
    ADDUCTORS,

    /** Femorali (ischiocrurali). */
    HAMSTRINGS,

    /** Glutei. */
    GLUTES,

    /** Polpacci. */
    CALVES,

    /** Quadricipiti. */
    QUADRICEPS,

    /** Cardio & full body. */
    CARDIO_FULL_BODY,
}

/**
 * Regione muscolare specifica (granularità fine: petto alto/centrale/basso, deltoide
 * anteriore/laterale/posteriore, addome alto/basso/obliqui). Ogni regione appartiene a un [MuscleGroup].
 *
 * È l'unità che la mappa anatomica evidenzia nella scheda esercizio.
 * Nomi persistiti come TEXT: non rinominare senza Migration.
 */
enum class Muscle(val group: MuscleGroup) {
    ABS_UPPER(MuscleGroup.ABS),
    ABS_LOWER(MuscleGroup.ABS),
    OBLIQUES(MuscleGroup.ABS),

    FOREARMS(MuscleGroup.FOREARMS),

    BICEPS(MuscleGroup.BICEPS),

    NECK(MuscleGroup.NECK),

    LATS(MuscleGroup.LATS),

    LOWER_BACK(MuscleGroup.LOWER_BACK),

    UPPER_BACK(MuscleGroup.UPPER_BACK),

    CHEST_UPPER(MuscleGroup.CHEST),
    CHEST_MIDDLE(MuscleGroup.CHEST),
    CHEST_LOWER(MuscleGroup.CHEST),

    SHOULDERS_FRONT(MuscleGroup.SHOULDERS),
    SHOULDERS_SIDE(MuscleGroup.SHOULDERS),
    SHOULDERS_REAR(MuscleGroup.SHOULDERS),

    TRAPS(MuscleGroup.TRAPS),

    TRICEPS(MuscleGroup.TRICEPS),

    ABDUCTORS(MuscleGroup.ABDUCTORS),

    ADDUCTORS(MuscleGroup.ADDUCTORS),

    HAMSTRINGS(MuscleGroup.HAMSTRINGS),

    GLUTES(MuscleGroup.GLUTES),

    CALVES(MuscleGroup.CALVES),

    QUADRICEPS(MuscleGroup.QUADRICEPS),

    CARDIO(MuscleGroup.CARDIO_FULL_BODY),
    FULL_BODY(MuscleGroup.CARDIO_FULL_BODY),
}

/** Ruolo di un muscolo in un esercizio: target principale o sinergico. */
enum class MuscleRole {
    PRIMARY,
    SECONDARY,
}
