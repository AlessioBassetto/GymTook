package com.example.sport.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Pesata giornaliera. Una sola voce per giorno (chiave = data): una nuova pesata nello stesso
 * giorno sovrascrive la precedente. La media mobile settimanale è calcolata a runtime.
 */
@Entity(tableName = "body_weight_entries")
data class BodyWeightEntryEntity(
    /** Persistita come epoch-day. */
    @PrimaryKey
    val date: LocalDate,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,

    @ColumnInfo(name = "body_fat_percent")
    val bodyFatPercent: Double? = null,

    val note: String? = null,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Instant,
)
