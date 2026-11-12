package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Video personale di un esercizio, registrato o scelto dall'utente (la propria esecuzione, la
 * macchina della propria palestra). Vale per esercizi di catalogo e personalizzati.
 *
 * Tabella separata da `exercise_media` di proposito: gli aggiornamenti del catalogo riscrivono i
 * media di catalogo e non devono mai toccare i contenuti dell'utente. Il file vive nella memoria
 * privata dell'app ([fileName] relativo alla cartella dei video utente), mai sulla CDN.
 */
@Entity(
    tableName = "exercise_user_videos",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExerciseUserVideoEntity(
    @PrimaryKey
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
)
