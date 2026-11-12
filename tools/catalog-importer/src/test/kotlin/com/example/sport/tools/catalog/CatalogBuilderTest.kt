package com.example.sport.tools.catalog

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.catalog.CatalogExerciseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CatalogBuilderTest {

    private val sources = listOf(
        SourceExercise(
            id = "Leg_Extensions", name = "Leg Extensions", equipment = "machine", category = "strength",
            primaryMuscles = listOf("quadriceps"), instructions = listOf("Sit down."),
            images = listOf("Leg_Extensions/0.jpg", "Leg_Extensions/1.jpg"),
        ),
        SourceExercise(
            id = "Dumbbell_Flyes", name = "Dumbbell Flyes", equipment = "dumbbell", category = "strength",
            primaryMuscles = listOf("chest"), instructions = listOf("Lie down.", "Open your arms."),
            images = listOf("Dumbbell_Flyes/0.jpg"),
        ),
        SourceExercise(
            id = "Arnold_Dumbbell_Press", name = "Arnold Dumbbell Press", equipment = "dumbbell", category = "strength",
            primaryMuscles = listOf("shoulders"), instructions = listOf("Press."), images = listOf("Arnold_Dumbbell_Press/0.jpg"),
        ),
        SourceExercise(
            id = "Hamstring_Stretch", name = "Hamstring Stretch", equipment = "body only", category = "stretching",
            primaryMuscles = listOf("hamstrings"), instructions = listOf("Stretch."),
        ),
        SourceExercise(
            id = "Iron_Cross", name = "Iron Cross", equipment = "dumbbell", category = "strength",
            primaryMuscles = listOf("shoulders"), instructions = emptyList(), images = listOf("Iron_Cross/0.jpg"),
        ),
    )

    private val curated = CatalogExerciseDto(
        id = "leg_extension",
        name = "Leg extension",
        nameEn = "Leg Extension",
        equipment = Equipment.MACHINE,
        primaryMuscles = listOf(Muscle.QUADRICEPS),
        instructions = listOf("Siediti", "Estendi", "Torna giù"),
        keyPoints = listOf("Ginocchio allineato"),
        commonMistakes = listOf("Slancio"),
    )

    private fun inputs(
        links: Map<String, String> = mapOf("leg_extension" to "Leg Extensions"),
        translations: Map<String, TranslationEntry> = emptyMap(),
    ) = CatalogInputs(
        curated = listOf(curated),
        curatedLinks = links,
        names = mapOf("Dumbbell Flyes" to "Croci su panca piana con manubri", "Unknown Name" to "Sconosciuto"),
        translations = translations,
    )

    @Test
    fun build_mergesCuratedAndImported_withMediaNamesAndFilters() {
        val result = CatalogBuilder().build(version = 2, sources = sources, inputs = inputs())
        val byId = result.catalog.exercises.associateBy { it.id }

        assertEquals(listOf("leg_extension", "arnold_dumbbell_press", "dumbbell_flyes"), result.catalog.exercises.map { it.id })
        val legExtension = byId.getValue("leg_extension")
        assertEquals("Leg extension", legExtension.name)
        assertEquals("free-exercise-db:Leg_Extensions", legExtension.origin)
        assertEquals(2, legExtension.media.size)

        val flyes = byId.getValue("dumbbell_flyes")
        assertEquals("Croci su panca piana con manubri", flyes.name)
        assertEquals("Dumbbell Flyes", flyes.nameEn)
        assertEquals("en", flyes.contentLocale)

        assertEquals(listOf("Arnold Dumbbell Press"), result.report.missingItalianNames)
        assertEquals(listOf("Unknown Name"), result.report.unusedNameKeys)
        assertEquals("Stretching e esercizi senza istruzioni esclusi", 2, result.report.excludedByFilter)
        assertEquals(4, result.mediaManifest.size)
        assertEquals(
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/${FreeExerciseDb.COMMIT}/exercises/Leg_Extensions/0.jpg",
            result.mediaManifest.first { it.path == "exercises/Leg_Extensions/0.jpg" }.url,
        )
    }

    @Test
    fun translation_switchesContentToItalian() {
        val translation = TranslationEntry(
            name = "Croci con manubri",
            aliases = listOf("dumbbell fly"),
            instructions = listOf("Sdraiati sulla panca", "Apri le braccia"),
            keyPoints = listOf("Gomiti morbidi"),
            commonMistakes = listOf("Scendere troppo"),
            translatedBy = "claude-opus-5-5",
        )
        val result = CatalogBuilder().build(2, sources, inputs(translations = mapOf("Dumbbell Flyes" to translation)))
        val flyes = result.catalog.exercises.first { it.id == "dumbbell_flyes" }
        assertEquals("Croci con manubri", flyes.name)
        assertEquals(listOf("dumbbell fly"), flyes.aliases)
        assertEquals(listOf("Sdraiati sulla panca", "Apri le braccia"), flyes.instructions)
        assertEquals("it", flyes.contentLocale)
        assertEquals(2, result.report.italianContentCount)
    }

    @Test
    fun brokenCuratedLink_failsTheBuild() {
        try {
            CatalogBuilder().build(2, sources, inputs(links = mapOf("leg_extension" to "Does Not Exist")))
            fail("Il build doveva fallire")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("Does Not Exist"))
        }
    }

    @Test
    fun translatorRequest_isBuiltWithStructuredOutputAndFallback() {
        // Chiave fittizia: costruisce la richiesta senza alcuna chiamata di rete.
        val translator = ClaudeTranslator(client = AnthropicOkHttpClient.builder().apiKey("test-key").build())
        val params = translator.buildParams(sources.take(2), mapOf("Dumbbell Flyes" to "Croci su panca piana con manubri"))
        assertNotNull(params)
        val rendered = params.toString()
        assertTrue("Modello mancante: $rendered", "claude-opus-5-5" in rendered)
        assertTrue("Schema JSON senza i campi della risposta", "sourceName" in rendered && "commonMistakes" in rendered)
        assertTrue("Fallback lato server mancante", "server-side-fallback-2026-07-01" in rendered)
    }
}
