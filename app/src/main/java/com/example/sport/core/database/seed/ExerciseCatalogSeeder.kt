package com.example.sport.core.database.seed

import android.util.Log
import androidx.room.withTransaction
import com.example.sport.core.database.SportDatabase
import com.example.sport.core.di.ApplicationScope
import com.example.sport.core.model.catalog.validate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface CatalogSeedResult {
    /** Il database contiene già questa versione (o una più recente). */
    data class UpToDate(val installedVersion: Int) : CatalogSeedResult

    data class Installed(
        val previousVersion: Int,
        val version: Int,
        val exerciseCount: Int,
        val retiredCount: Int,
    ) : CatalogSeedResult
}

/**
 * Installa o aggiorna il catalogo esercizi nel database.
 *
 * Idempotente e sicuro da chiamare a ogni avvio: confronta la versione del catalogo impacchettato
 * con quella registrata in `app_metadata` e lavora solo se è più recente. L'aggiornamento avviene
 * in un'unica transazione e preserva preferiti/nascosti; gli esercizi rimossi vengono ritirati.
 */
@Singleton
class ExerciseCatalogSeeder @Inject constructor(
    private val database: SportDatabase,
    private val source: ExerciseCatalogSource,
    private val clock: Clock,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Avvio non bloccante (da `Application.onCreate`): le UI basate su Flow si aggiornano da sole. */
    fun seedInBackground(): Job = applicationScope.launch {
        try {
            val result = seedIfNeeded()
            Log.i(TAG, "Catalogo esercizi: $result")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Installazione catalogo esercizi fallita", error)
        }
    }

    suspend fun seedIfNeeded(): CatalogSeedResult = mutex.withLock {
        val catalogDao = database.catalogDao()
        val installedVersion = catalogDao.getInstalledCatalogVersion()
        val catalog = source.load()
        if (installedVersion >= catalog.version) {
            return@withLock CatalogSeedResult.UpToDate(installedVersion)
        }
        catalog.validate()
        val now = Instant.now(clock)
        database.withTransaction {
            val flagsById = catalogDao.getCatalogUserFlags().associateBy { it.id }
            val exercises = catalog.exercises.map { it.toEntity(catalog.version, now, flagsById[it.id]) }
            val muscles = catalog.exercises.flatMap { it.toMuscleEntities() }
            val media = catalog.exercises.flatMap { it.toMediaEntities() }
            val retired = catalogDao.applyCatalog(catalog.version, exercises, muscles, media, now)
            CatalogSeedResult.Installed(
                previousVersion = installedVersion,
                version = catalog.version,
                exerciseCount = exercises.size,
                retiredCount = retired,
            )
        }
    }

    private companion object {
        const val TAG = "ExerciseCatalogSeeder"
    }
}
