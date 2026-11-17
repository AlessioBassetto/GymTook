package com.example.sport.tools.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TranslationTextTest {

    @Test
    fun parse_readsEveryFieldOfEachBlock() {
        val text = """
            # Lotto di prova
            ## Barbell Bench Press - Medium Grip
            N: Panca piana con bilanciere
            A: bench press | distensioni su panca
            I: Sdraiati sulla panca. | Stacca il bilanciere. | Scendi al petto e spingi.
            K: Scapole addotte | Piedi a terra
            E: Rimbalzare sul petto | Sollevare i glutei

            ## Crunches
            # en: Lie flat on your back...
            I: Sdraiati a terra con le ginocchia piegate. | Solleva le spalle contraendo l'addome.
            E: Tirare il collo con le mani
        """.trimIndent()

        val parsed = TranslationText.parse(text, translatedBy = "test")

        assertEquals(listOf("Barbell Bench Press - Medium Grip", "Crunches"), parsed.keys.toList())
        val bench = parsed.getValue("Barbell Bench Press - Medium Grip")
        assertEquals("Panca piana con bilanciere", bench.name)
        assertEquals(listOf("bench press", "distensioni su panca"), bench.aliases)
        assertEquals(3, bench.instructions.size)
        assertEquals(listOf("Scapole addotte", "Piedi a terra"), bench.keyPoints)
        assertFalse(bench.reviewed)
        assertEquals("test", bench.translatedBy)
        val crunch = parsed.getValue("Crunches")
        assertNull("Senza N: resta il nome di names_it.json", crunch.name)
        assertEquals(emptyList<String>(), crunch.keyPoints)
        assertEquals(listOf("Tirare il collo con le mani"), crunch.commonMistakes)
    }

    @Test
    fun parse_rejectsBlocksWithoutInstructionsAndUnknownLines() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationText.parse("## Crunches\nK: Solo punti chiave", "test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TranslationText.parse("## Crunches\nI: Passo\nX: campo sconosciuto", "test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TranslationText.parse("## Crunches\nI: Uno\n## Crunches\nI: Due", "test")
        }
    }
}
