package com.example.sport.core.data.food

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Ricerca su Open Food Facts contro un server finto: richiesta, lettura dei valori, errori. */
class OpenFoodFactsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenFoodFactsClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenFoodFactsClient(
            OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build(),
            OpenFoodFactsConfig(server.url("/").toString(), "Sport/1.0.5 (Android)", retryBaseDelayMillis = 1),
            Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun search_sendsOnlyTheQuery_withAGenericUserAgent_andReadsValuesPer100g() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(SEARCH_RESPONSE).build())

        val foods = client.search("yogurt greco")

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/cgi/search.pl", request.url.encodedPath)
        assertEquals("yogurt greco", request.url.queryParameter("search_terms"))
        assertEquals("1", request.url.queryParameter("json"))
        assertEquals("code,product_name,product_name_it,brands,nutriments", request.url.queryParameter("fields"))
        assertEquals("Sport/1.0.5 (Android)", request.headers["User-Agent"])
        assertFalse("Nessun contatto nel User-Agent", '@' in request.headers["User-Agent"].orEmpty())

        assertEquals(listOf("5200106871183", "8001", "8002"), foods.map { it.code })
        val greek = foods[0]
        assertEquals("YOGURT GRECO", greek.name)
        assertEquals("Koukakis Dairy Farm", greek.brand)
        assertEquals(Nutrients(133.0, 6.6, 4.0, 10.0), greek.per100g)
        assertEquals("Nome italiano se c'è", "Yogurt greco Total 0%", foods[1].name)
        assertEquals("Marca da un elenco con prefisso", "fage", foods[1].brand)
        assertEquals("Numeri scritti come testo con la virgola", 3.5, foods[2].per100g.carbsG, 1e-9)
        assertEquals("Energia solo in kJ: convertita", 100.0, foods[2].per100g.kcal, 0.01)
        assertNull(foods[2].brand)
    }

    @Test
    fun search_shortQuery_doesNotCallTheService() = runTest {
        assertTrue(client.search("yo").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun search_busyService_isRetried_withAGrowingWait() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(429).build())
        server.enqueue(MockResponse.Builder().code(200).body(SEARCH_RESPONSE).build())
        assertEquals(3, client.search("yogurt greco").size)
        assertEquals("Due attese, poi la risposta", 3, server.requestCount)
    }

    @Test
    fun search_otherErrors_areNotRetried() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        try {
            client.search("pasta")
            fail("Doveva fallire")
        } catch (expected: OnlineFoodSearchException) {
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun search_serviceErrorOrGarbage_becomesASearchException() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(503).build()) }
        server.enqueue(MockResponse.Builder().code(200).body("<html>rate limited</html>").build())
        repeat(2) {
            try {
                client.search("pasta")
                fail("Doveva fallire")
            } catch (expected: OnlineFoodSearchException) {
                // Errore gestito: la UI mostra "Ricerca online non riuscita".
            }
        }
    }

    private companion object {
        val SEARCH_RESPONSE = """
            {"count": 5, "page": 1, "page_size": 5, "products": [
              {"code": "5200106871183", "product_name": "YOGURT GRECO", "brands": "Koukakis Dairy Farm",
               "nutriments": {"energy-kcal_100g": 133, "proteins_100g": 6.6, "carbohydrates_100g": 4, "fat_100g": 10}},
              {"code": "8001", "product_name": "Greek yogurt", "product_name_it": "Yogurt greco Total 0%", "brands": ["xx:fage"],
               "nutriments": {"energy-kcal_100g": 54, "proteins_100g": 10.3, "carbohydrates_100g": 3, "fat_100g": 0}},
              {"code": "8002", "product_name": "Yogurt senza marca",
               "nutriments": {"energy_100g": 418.4, "proteins_100g": "5", "carbohydrates_100g": "3,5", "fat_100g": "4"}},
              {"code": "8003", "product_name": "Senza valori", "nutriments": {}},
              {"code": "8004", "nutriments": {"energy-kcal_100g": 100}},
              {"code": "8005", "product_name": "Impossibile", "nutriments": {"energy-kcal_100g": 2000}}
            ]}
        """.trimIndent()
    }
}
