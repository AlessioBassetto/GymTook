package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sport.core.model.MachineSetupParam
import java.time.Instant

/**
 * Profilo di regolazione di un macchinario per uno specifico esercizio.
 *
 * Esempio per "Leg extension": label = "Palestra Centro",
 * params = [Schienale = 3, Rullo tibie = 5, Asse ginocchio = B].
 *
 * Un esercizio può avere più profili (palestre diverse, macchine di marche diverse):
 * l'app propone automaticamente quello con [lastUsedAt] più recente, aggiornato a ogni
 * sessione in cui il profilo viene usato o modificato.
 */
@Entity(
    tableName = "machine_setups",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["exercise_id", "last_used_at"]),
    ],
)
data class MachineSetupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    /** Nome del profilo, es. "Predefinita", "Palestra Centro", "Technogym". */
    val label: String,

    /** Valori di regolazione, nell'ordine in cui l'utente li ha inseriti. */
    val params: List<MachineSetupParam>,

    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Instant,
)
