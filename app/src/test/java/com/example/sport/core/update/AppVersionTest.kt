package com.example.sport.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    private fun version(text: String) = AppVersion.parse(text)!!

    @Test
    fun tagsAndVersionNames_areParsedTheSameWay() {
        assertEquals(version("1.2.3"), version("v1.2.3"))
        assertEquals("Il suffisso della build di debug non conta", version("1.2.3"), version("1.2.3-debug"))
        assertEquals(version("1.2"), version("1.2.0"))
        assertEquals(version("1.2").hashCode(), version("1.2.0").hashCode())
        assertEquals("2.0.1", version(" V2.0.1+45 ").toString())
        assertNull(AppVersion.parse("nightly"))
        assertNull(AppVersion.parse(""))
        assertNull("Numeri fuori scala: nessun confronto possibile", AppVersion.parse("99999999999.1"))
    }

    @Test
    fun comparison_isNumeric_notAlphabetical() {
        assertTrue(version("1.10.0") > version("1.9.9"))
        assertTrue(version("2.0") > version("1.99.99"))
        assertTrue(version("1.0.1") > version("1.0"))
        assertFalse(version("1.0.0") > version("v1.0"))
        assertTrue(version("1.2.3") < version("1.2.4"))
    }

    @Test
    fun releaseNotes_becomePlainText() {
        val markdown = "## Novità\r\n\r\n- **Aggiornamenti** dall'app\n* Fix del `timer`\n\n\n\nDettagli nel [changelog](https://example.com/changelog)."
        assertEquals(
            "Novità\n\n• Aggiornamenti dall'app\n• Fix del timer\n\nDettagli nel changelog.",
            plainReleaseNotes(markdown),
        )
        assertEquals("", plainReleaseNotes("   \n"))
    }

    @Test
    fun downloads_requireHttps_unlessTheApiItselfIsALocalTestServer() {
        val production = UpdateConfig("owner/app", "https://api.github.com", "1.0.0", 1)
        assertTrue(production.acceptsDownloadUrl("https://github.com/owner/app/releases/download/v1.1.0/app.apk"))
        assertFalse(production.acceptsDownloadUrl("http://github.com/owner/app/releases/download/v1.1.0/app.apk"))
        assertFalse(production.acceptsDownloadUrl("file:///sdcard/app.apk"))
        assertEquals("https://api.github.com/repos/owner/app/releases/latest", production.latestReleaseUrl)

        val local = UpdateConfig("owner/app", "http://localhost:8080/", "1.0.0", 1)
        assertTrue(local.acceptsDownloadUrl("http://localhost:8080/download/app.apk"))
        assertEquals("http://localhost:8080/repos/owner/app/releases/latest", local.latestReleaseUrl)
        assertFalse(UpdateConfig("", "https://api.github.com", "1.0.0", 1).isEnabled)
    }
}
