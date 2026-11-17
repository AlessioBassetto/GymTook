package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.sport.core.database.entity.BodyWeightEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BodyWeightDao {

    /** Inserisce o sovrascrive la pesata del giorno. */
    @Upsert
    suspend fun upsert(entry: BodyWeightEntryEntity)

    @Query("DELETE FROM body_weight_entries WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)

    /** Intervallo inclusivo, in ordine cronologico (input per grafico e media mobile). */
    @Query("SELECT * FROM body_weight_entries WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<BodyWeightEntryEntity>>

    @Query("SELECT * FROM body_weight_entries WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getBetween(from: LocalDate, to: LocalDate): List<BodyWeightEntryEntity>

    /** Tutte le pesate in ordine cronologico (poche centinaia al massimo): base del calcolo calorico. */
    @Query("SELECT * FROM body_weight_entries ORDER BY date ASC")
    suspend fun getAll(): List<BodyWeightEntryEntity>

    @Query("SELECT * FROM body_weight_entries ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<BodyWeightEntryEntity?>

    @Query("SELECT * FROM body_weight_entries ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): BodyWeightEntryEntity?

    @Query("SELECT * FROM body_weight_entries WHERE date = :date")
    suspend fun getByDate(date: LocalDate): BodyWeightEntryEntity?
}
