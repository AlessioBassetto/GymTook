package com.example.sport.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.sport.core.database.entity.BodyProfileEntity
import kotlinx.coroutines.flow.Flow

/** Profilo corporeo (riga unica): altezza, anno di nascita, sesso biologico. */
@Dao
interface BodyProfileDao {

    @Query("SELECT * FROM body_profile WHERE id = ${BodyProfileEntity.SINGLE_ROW_ID}")
    fun observe(): Flow<BodyProfileEntity?>

    @Query("SELECT * FROM body_profile WHERE id = ${BodyProfileEntity.SINGLE_ROW_ID}")
    suspend fun get(): BodyProfileEntity?

    /** Crea o sostituisce il profilo (la chiave è sempre la stessa). */
    @Upsert
    suspend fun upsert(profile: BodyProfileEntity)
}
