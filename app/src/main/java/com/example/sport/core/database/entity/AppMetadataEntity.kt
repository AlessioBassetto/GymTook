package com.example.sport.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Coppie chiave/valore interne al database (es. versione del catalogo esercizi installato).
 * Vivono nello stesso file DB dei dati, quindi restano coerenti anche dopo backup/ripristino.
 */
@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String,
) {
    companion object {
        const val KEY_EXERCISE_CATALOG_VERSION = "exercise_catalog_version"
    }
}
