package com.example.sport.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Percorso critico dell'app per il baseline profile: avvio a freddo su "Oggi", libreria esercizi
 * con scorrimento (la lista più lunga), progressi, dieta e schede. Le classi toccate qui vengono
 * compilate in anticipo: avvio e primo scroll senza scatti anche sui telefoni lenti.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("Argomento targetAppId mancante"),
        includeInStartupProfile = true,
    ) {
        pressHome()
        // am start -W invece di startActivityAndWait: sugli emulatori senza finestra (CI) la libreria
        // non legge i frame di avvio e fallisce; qui basta attendere la prima schermata.
        device.executeShellCommand("am start -W -n $packageName/com.example.sport.MainActivity")
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT_MS)
        device.wait(Until.hasObject(By.text("Oggi")), TIMEOUT_MS)

        openSection("Esercizi")
        flingList()
        openSection("Progressi")
        flingList()
        openSection("Dieta")
        openSection("Schede")
        openSection("Oggi")
    }

    /** Tocca una voce della barra di navigazione inferiore (l'ultima con quel testo: la barra sta in fondo). */
    private fun MacrobenchmarkScope.openSection(label: String) {
        try {
            device.findObjects(By.text(label)).maxByOrNull { it.visibleBounds.top }?.click()
        } catch (_: StaleObjectException) {
            device.findObjects(By.text(label)).maxByOrNull { it.visibleBounds.top }?.click()
        }
        device.waitForIdle()
    }

    /**
     * Scorre la lista principale in basso e poi in alto. Si cerca ogni volta la lista più alta (le
     * righe di chip sono scorrevoli in orizzontale) e un oggetto diventato obsoleto per una
     * ricomposizione si ignora: il profilo raccoglie comunque il codice dello scorrimento.
     */
    private fun MacrobenchmarkScope.flingList() {
        listOf(Direction.DOWN, Direction.UP).forEach { direction ->
            try {
                device.findObjects(By.scrollable(true))
                    .maxByOrNull { it.visibleBounds.height() }
                    ?.apply { setGestureMargin(device.displayWidth / 5) }
                    ?.fling(direction)
            } catch (_: StaleObjectException) {
                // La lista è stata ricomposta durante il gesto.
            }
            device.waitForIdle()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
