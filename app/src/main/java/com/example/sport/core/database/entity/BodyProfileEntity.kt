package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sport.core.model.BiologicalSex
import java.time.Instant

/**
 * Profilo corporeo dell'utente (monitoraggio corporeo, accanto alle pesate): altezza, anno di
 * nascita e sesso biologico. Riga unica con [id] = [SINGLE_ROW_ID].
 *
 * Il peso non sta qui: cambia spesso ed è già storicizzato in `body_weight_entries`. Dell'età si
 * salva l'anno di nascita, così il valore non invecchia: l'età si ricava alla data di ogni allenamento.
 * Tutti i campi sono facoltativi; il calcolo delle calorie usa valori predefiniti per quelli mancanti.
 */
@Entity(tableName = "body_profile")
data class BodyProfileEntity(
    @PrimaryKey
    val id: Int = SINGLE_ROW_ID,

    @ColumnInfo(name = "height_cm")
    val heightCm: Float? = null,

    @ColumnInfo(name = "birth_year")
    val birthYear: Int? = null,

    val sex: BiologicalSex? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
