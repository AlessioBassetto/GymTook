package com.example.sport.core.model

/** Tipologia di split della scheda. Nomi persistiti come TEXT: non rinominare senza Migration. */
enum class SplitType {
    PUSH_PULL_LEGS,
    UPPER_LOWER,
    FULL_BODY,

    /** Monofrequenza / bro split (un gruppo per giorno). */
    BRO_SPLIT,
    CUSTOM,
}

/**
 * Stato di una sessione di allenamento.
 * Le query SQL usano i letterali 'IN_PROGRESS' / 'COMPLETED': non rinominare senza Migration.
 */
enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    DISCARDED,
}

/**
 * Tipo di serie. Le serie WARMUP sono escluse da volume, record e stime 1RM.
 * Il letterale 'WARMUP' è usato nelle query: non rinominare senza Migration.
 */
enum class SetType {
    WARMUP,
    WORKING,
    DROP,
    FAILURE,
}
