package com.example.sport.core.model

/**
 * Determina quali campi mostra la riga serie del logger e come si calcola il volume.
 * Nomi persistiti come TEXT: non rinominare senza Migration.
 */
enum class ExerciseTrackingType {
    /** Peso + ripetizioni (bilanciere, manubri, macchine, cavi). */
    WEIGHT_REPS,

    /** Ripetizioni a corpo libero, con eventuale zavorra in kg (trazioni, dip). */
    BODYWEIGHT_REPS,

    /** Ripetizioni con assistenza: il "peso" è il contrappeso che aiuta (lat assistita). */
    ASSISTED_BODYWEIGHT,

    /** Solo durata (plank, isometrie). */
    DURATION,

    /** Distanza + durata (corsa, vogatore). */
    DISTANCE_DURATION,

    /**
     * Inclinazione (%) + tempo: tapis roulant in salita, camminate e corse in pendenza.
     * Il peso non c'è: la fatica sta nella pendenza e nella durata.
     */
    INCLINE_TIME,
}

/**
 * Tipo di movimento. Distingue i sostituti credibili di un esercizio da pesi (stacco rumeno →
 * stacco a gambe tese) dagli esercizi di potenza che allenano gli stessi muscoli (girate, salti).
 * Nomi persistiti come TEXT: non rinominare senza Migration.
 */
enum class ExerciseCategory {
    /** Forza e ipertrofia: pesi, macchinari, corpo libero, powerlifting. */
    STRENGTH,

    /** Sollevamento olimpico e derivati (girate, strappi, slanci). */
    OLYMPIC,

    /** Pliometria: salti, balzi, lanci esplosivi. */
    PLYOMETRIC,

    /** Cardio: corsa, bike, vogatore, ellittica. */
    CARDIO,
}

/** Multiarticolare o monoarticolare. */
enum class Mechanic {
    COMPOUND,
    ISOLATION,
}

/** Direzione della forza prevalente. */
enum class ForceType {
    PUSH,
    PULL,
    STATIC,
}

/** Livello tecnico richiesto. */
enum class DifficultyLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}
