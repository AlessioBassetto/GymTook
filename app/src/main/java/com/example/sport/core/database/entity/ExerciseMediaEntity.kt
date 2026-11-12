package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.sport.core.model.MediaKind

/**
 * Contenuto multimediale di un esercizio: video loop o fotogrammi in sequenza.
 *
 * Nel database c'è solo il path relativo: il file vive sulla CDN e viene scaricato su richiesta
 * (cache progressiva) oppure in anticipo per le schede da usare offline. Così l'APK resta leggero
 * anche con migliaia di contenuti.
 */
@Entity(
    tableName = "exercise_media",
    primaryKeys = ["exercise_id", "kind", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("path")],
)
data class ExerciseMediaEntity(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    val kind: MediaKind,

    /** Ordine all'interno dello stesso [kind] (fotogramma 0, 1, ...). */
    val position: Int,

    /** Path relativo alla base URL media (es. `exercises/Barbell_Curl/0.jpg`). */
    val path: String,

    /** Dataset o autore di origine, per la pagina crediti. */
    val source: String? = null,

    /** Licenza SPDX del contenuto. */
    val license: String? = null,

    /** Attribuzione obbligatoria (licenze CC-BY e simili). */
    val attribution: String? = null,
)
