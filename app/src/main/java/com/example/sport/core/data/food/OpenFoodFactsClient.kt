package com.example.sport.core.data.food

import com.example.sport.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/** Prodotto trovato su Open Food Facts, con i valori per 100 g. */
data class OnlineFood(
    /** Codice a barre: diventa l'id `off:<codice>` quando il prodotto viene salvato. */
    val code: String,
    val name: String,
    val brand: String?,
    val per100g: Nutrients,
)

/** Indirizzo del servizio e User-Agent (generico: nessun dato dell'utente). */
data class OpenFoodFactsConfig(
    val baseUrl: String,
    val userAgent: String,
    /** Prima attesa prima di riprovare (raddoppia a ogni tentativo). */
    val retryBaseDelayMillis: Long = 1_000,
) {
    companion object {
        const val PRODUCTION_URL = "https://world.openfoodfacts.org/"
    }
}

/** Ricerca non riuscita (rete assente, servizio lento o risposta non valida). */
class OnlineFoodSearchException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Ricerca testuale su Open Food Facts (database aperto di prodotti confezionati, licenza ODbL).
 *
 * Usa l'endpoint `cgi/search.pl`, l'unico che restituisce anche i valori nutrizionali per 100 g.
 * Il servizio limita le ricerche a 10 al minuto per indirizzo: l'app cerca solo quando l'utente lo
 * chiede (nessuna ricerca mentre scrive). Si invia solo il testo cercato, mai dati dell'utente.
 */
class OpenFoodFactsClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val config: OpenFoodFactsConfig,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, pageSize: Int = DEFAULT_PAGE_SIZE): List<OnlineFood> = withContext(ioDispatcher) {
        val terms = query.trim()
        if (terms.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        val request = Request.Builder()
            .url(searchUrl(terms, pageSize))
            .header("User-Agent", config.userAgent)
            .get()
            .build()
        parseSearchResponse(fetchWithRetry(request))
    }

    /**
     * Il servizio risponde 503 a intermittenza quando è carico (limite globale) e 429 oltre il limite
     * per indirizzo: come chiede la sua documentazione si riprova con attese crescenti (1 s, poi 2 s),
     * al massimo [MAX_ATTEMPTS] volte. Altri errori non si ripetono.
     */
    private suspend fun fetchWithRetry(request: Request): String {
        var attempt = 0
        while (true) {
            attempt++
            val outcome = try {
                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> Result.success(response.body.string())
                        response.code in RETRYABLE_CODES -> Result.failure(OnlineFoodSearchException("Risposta ${response.code} da Open Food Facts"))
                        else -> throw OnlineFoodSearchException("Risposta ${response.code} da Open Food Facts")
                    }
                }
            } catch (error: OnlineFoodSearchException) {
                throw error
            } catch (error: IOException) {
                Result.failure(OnlineFoodSearchException("Open Food Facts non raggiungibile", error))
            }
            outcome.onSuccess { return it }
            if (attempt >= MAX_ATTEMPTS) throw outcome.exceptionOrNull()!!
            delay(config.retryBaseDelayMillis shl (attempt - 1))
        }
    }

    internal fun searchUrl(terms: String, pageSize: Int): HttpUrl = config.baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("cgi/search.pl")
        .addQueryParameter("search_terms", terms)
        .addQueryParameter("search_simple", "1")
        .addQueryParameter("action", "process")
        .addQueryParameter("json", "1")
        .addQueryParameter("page_size", pageSize.coerceIn(1, MAX_PAGE_SIZE).toString())
        .addQueryParameter("fields", FIELDS)
        .build()

    /**
     * `{"products": [{"code", "product_name", "product_name_it", "brands", "nutriments": {...}}]}`.
     * Si scartano i prodotti senza nome o senza energia, e quelli con valori impossibili.
     */
    internal fun parseSearchResponse(body: String): List<OnlineFood> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (error: SerializationException) {
            throw OnlineFoodSearchException("Risposta non valida da Open Food Facts", error)
        } catch (error: IllegalArgumentException) {
            throw OnlineFoodSearchException("Risposta non valida da Open Food Facts", error)
        }
        val products = (root["products"] as? JsonArray).orEmpty()
        return products.mapNotNull { (it as? JsonObject)?.toOnlineFood() }.distinctBy { it.code }
    }

    private fun JsonObject.toOnlineFood(): OnlineFood? {
        val code = string("code")?.takeIf { it.isNotBlank() } ?: return null
        val name = (string("product_name_it") ?: string("product_name"))?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val nutriments = this["nutriments"] as? JsonObject ?: return null
        val kcal = nutriments.number("energy-kcal_100g")
            ?: nutriments.number("energy_100g")?.let { kilojoules -> kilojoules / KJ_PER_KCAL }
            ?: return null
        val protein = nutriments.number("proteins_100g") ?: 0.0
        val carbs = nutriments.number("carbohydrates_100g") ?: 0.0
        val fat = nutriments.number("fat_100g") ?: 0.0
        val values = listOf(kcal, protein, carbs, fat)
        if (values.any { it < 0 } || kcal > MAX_KCAL || protein + carbs + fat > MAX_MACROS_G) return null
        return OnlineFood(code = code, name = name, brand = brandOf(this["brands"]), per100g = Nutrients(kcal, protein, carbs, fat))
    }

    /** "Barilla" oppure ["xx:barilla", …] a seconda dell'indice: si mostra la prima marca. */
    private fun brandOf(element: JsonElement?): String? {
        val raw = when (element) {
            is JsonPrimitive -> element.contentOrNull?.split(',')?.firstOrNull()
            is JsonArray -> (element.firstOrNull() as? JsonPrimitive)?.contentOrNull
            else -> null
        }
        return raw?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Numero o testo numerico ("12,5" compreso): i dati sono inseriti da volontari. */
    private fun JsonObject.number(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return (primitive.doubleOrNull ?: primitive.contentOrNull?.replace(',', '.')?.toDoubleOrNull())?.takeIf { it.isFinite() }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        /** Tentativi per ricerca: 3 restano ben sotto il limite di 10 ricerche al minuto. */
        private const val MAX_ATTEMPTS = 3
        private val RETRYABLE_CODES = setOf(429, 502, 503, 504)
        const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 50
        private const val FIELDS = "code,product_name,product_name_it,brands,nutriments"
        private const val KJ_PER_KCAL = 4.184
        private const val MAX_KCAL = 900.0
        private const val MAX_MACROS_G = 101.0
    }
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
