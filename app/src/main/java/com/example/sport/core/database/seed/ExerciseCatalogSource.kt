package com.example.sport.core.database.seed

import android.content.Context
import com.example.sport.core.database.converter.SportJson
import com.example.sport.core.di.IoDispatcher
import com.example.sport.core.model.catalog.ExerciseCatalogDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Sorgente del catalogo esercizi (asset in produzione, sorgenti fittizie nei test). */
fun interface ExerciseCatalogSource {
    suspend fun load(): ExerciseCatalogDto
}

/** Legge il catalogo impacchettato nell'APK: disponibile al primo avvio anche senza rete. */
class AssetExerciseCatalogSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExerciseCatalogSource {

    override suspend fun load(): ExerciseCatalogDto = withContext(ioDispatcher) {
        val text = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        SportJson.decodeFromString(ExerciseCatalogDto.serializer(), text)
    }

    companion object {
        const val ASSET_PATH = "catalog/exercises.json"
    }
}
