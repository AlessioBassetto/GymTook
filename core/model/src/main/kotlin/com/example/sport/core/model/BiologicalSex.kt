package com.example.sport.core.model

/**
 * Sesso biologico del profilo corporeo: serve solo alla stima del metabolismo basale
 * (Mifflin-St Jeor usa costanti diverse). Facoltativo: se manca si usa la media delle due.
 * Nomi persistiti come TEXT: non rinominare senza Migration.
 */
enum class BiologicalSex {
    MALE,
    FEMALE,
}
