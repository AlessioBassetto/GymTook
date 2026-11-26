package com.example.sport.core.database.seed

import android.content.Context
import android.util.Log
import com.example.sport.core.database.converter.SportJson
import com.example.sport.core.database.dao.FoodDao
import com.example.sport.core.database.entity.FoodItemEntity
import com.example.sport.core.database.util.SearchText
import com.example.sport.core.di.ApplicationScope
import com.example.sport.core.di.IoDispatcher
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.FoodSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/** `assets/food/italian_dishes_catalog.json`: piatti italiani con i valori per 100 g. */
@Serializable
data class FoodCatalogDto(
    val version: Int,
    /** Nota sulle fonti dei valori (mostrata solo agli sviluppatori). */
    val source: String? = null,
    val foods: List<FoodCatalogItemDto>,
)

@Serializable
data class FoodCatalogItemDto(
    /** Slug stabile (`pasta_al_pomodoro`): nel database diventa `cat:pasta_al_pomodoro`. */
    val id: String,
    val name: String,
    val category: FoodCategory,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    /** Porzione tipica in grammi. */
    val defaultGrams: Double,
    /** Sinonimi per la ricerca ("spaghetti al pomodoro", "pasta al sugo"). */
    val aliases: List<String> = emptyList(),
)

/** Id nel database di un piatto del catalogo. */
fun catalogFoodId(slug: String): String = "$CATALOG_FOOD_PREFIX$slug"

const val CATALOG_FOOD_PREFIX = "cat:"

private val SLUG = Regex("[a-z0-9]+(_[a-z0-9]+)*")

/**
 * Controlli di qualità del catalogo: id e nomi unici, valori nei limiti fisici (macro ≤ 100 g,
 * kcal ≤ 900) e calorie coerenti con i macronutrienti (Atwater 4/4/9, con margine per fibre e
 * arrotondamenti). Lista vuota = catalogo valido.
 */
fun FoodCatalogDto.problems(): List<String> = buildList {
    if (version < 1) add("Versione non valida: $version")
    if (foods.isEmpty()) add("Catalogo vuoto")
    foods.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("Id duplicato: $it") }
    foods.groupBy { it.name.trim().lowercase() }.filterValues { it.size > 1 }.keys.forEach { add("Nome duplicato: $it") }
    foods.forEach { food ->
        val label = food.id
        if (!SLUG.matches(food.id)) add("$label: id non valido (minuscole, cifre e _)")
        if (food.name.isBlank()) add("$label: nome vuoto")
        val values = listOf(food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g)
        if (values.any { it < 0 || !it.isFinite() }) add("$label: valori negativi o non validi")
        if (food.kcalPer100g > MAX_KCAL_PER_100G) add("$label: oltre $MAX_KCAL_PER_100G kcal per 100 g")
        if (food.proteinPer100g + food.carbsPer100g + food.fatPer100g > 100.5) add("$label: macronutrienti oltre 100 g")
        if (food.defaultGrams !in 1.0..MAX_PORTION_GRAMS) add("$label: porzione fuori scala (${food.defaultGrams} g)")
        val atwater = 4 * food.proteinPer100g + 4 * food.carbsPer100g + 9 * food.fatPer100g
        val tolerance = max(ENERGY_TOLERANCE_KCAL, ENERGY_TOLERANCE_RATIO * food.kcalPer100g)
        if (abs(food.kcalPer100g - atwater) > tolerance) {
            add("$label: ${food.kcalPer100g} kcal incoerenti con i macronutrienti (${atwater.toInt()} kcal)")
        }
    }
}

fun FoodCatalogDto.validate() {
    val problems = problems()
    require(problems.isEmpty()) { "Catalogo alimenti non valido:\n" + problems.joinToString("\n") }
}

/** Riga del database di un piatto del catalogo. */
fun FoodCatalogItemDto.toEntity(version: Int, now: Instant): FoodItemEntity = FoodItemEntity(
    id = catalogFoodId(id),
    name = name.trim(),
    brand = null,
    category = category,
    source = FoodSource.CATALOG,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    defaultGrams = defaultGrams,
    searchText = SearchText.build(name, null, aliases),
    catalogVersion = version,
    createdAt = now,
    updatedAt = now,
)

const val MAX_KCAL_PER_100G = 900.0
const val MAX_PORTION_GRAMS = 1000.0
private const val ENERGY_TOLERANCE_KCAL = 30.0
private const val ENERGY_TOLERANCE_RATIO = 0.18

/** Sorgente del catalogo dieta (asset in produzione, sorgenti fittizie nei test). */
fun interface FoodCatalogSource {
    suspend fun load(): FoodCatalogDto
}

/** Legge il catalogo impacchettato nell'APK: disponibile al primo avvio anche senza rete. */
class AssetFoodCatalogSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FoodCatalogSource {

    override suspend fun load(): FoodCatalogDto = withContext(ioDispatcher) {
        val text = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        SportJson.decodeFromString(FoodCatalogDto.serializer(), text)
    }

    companion object {
        const val ASSET_PATH = "food/italian_dishes_catalog.json"
    }
}

/** Esito dell'installazione del catalogo dieta. */
sealed interface FoodCatalogSeedResult {
    data class UpToDate(val installedVersion: Int) : FoodCatalogSeedResult
    data class Installed(val previousVersion: Int, val version: Int, val foodCount: Int, val removedCount: Int) : FoodCatalogSeedResult
}

/**
 * Installa o aggiorna il catalogo dieta. Idempotente e sicuro a ogni avvio: lavora solo se la
 * versione impacchettata è più recente di quella registrata in `app_metadata`. I piatti e le
 * ricette dell'utente e il diario non vengono toccati.
 */
@Singleton
class FoodCatalogSeeder @Inject constructor(
    private val foodDao: FoodDao,
    private val source: FoodCatalogSource,
    private val clock: Clock,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Avvio non bloccante (da `Application.onCreate`): la ricerca si aggiorna da sola via Flow. */
    fun seedInBackground(): Job = applicationScope.launch {
        try {
            Log.i(TAG, "Catalogo dieta: ${seedIfNeeded()}")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Installazione catalogo dieta fallita", error)
        }
    }

    suspend fun seedIfNeeded(): FoodCatalogSeedResult = mutex.withLock {
        val installed = foodDao.getInstalledCatalogVersion()
        val catalog = source.load()
        if (installed >= catalog.version && foodDao.countCatalogFoods() > 0) {
            return@withLock FoodCatalogSeedResult.UpToDate(installed)
        }
        catalog.validate()
        val now = Instant.now(clock)
        val removed = foodDao.applyCatalog(catalog.version, catalog.foods.map { it.toEntity(catalog.version, now) })
        FoodCatalogSeedResult.Installed(installed, catalog.version, catalog.foods.size, removed)
    }

    private companion object {
        const val TAG = "FoodCatalogSeeder"
    }
}
