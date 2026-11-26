package com.example.sport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreadmillSettingsTest {

    @Test
    fun values_areReadInItalianOrWithTheDot_andClamped() {
        val params = listOf(MachineSetupParam("Pendenza (%)", "6,5"), MachineSetupParam("velocità (km/h)", "5.5"))
        assertEquals(6.5, TreadmillSettings.incline(params)!!, 0.0)
        assertEquals(5.5, TreadmillSettings.speed(params)!!, 0.0)
        assertEquals(40.0, TreadmillSettings.incline(listOf(MachineSetupParam(TreadmillSettings.INCLINE, "55")))!!, 0.0)
        assertNull(TreadmillSettings.speed(listOf(MachineSetupParam(TreadmillSettings.SPEED, "veloce"))))
        assertNull(TreadmillSettings.incline(emptyList()))
    }

    @Test
    fun format_usesOneItalianDecimal() {
        assertEquals("6", TreadmillSettings.format(6.0))
        assertEquals("6,5", TreadmillSettings.format(6.5))
        assertEquals("5,6", TreadmillSettings.format(5.55))
    }

    @Test
    fun withValues_replacesTheTwoEntries_andKeepsTheOthers() {
        val saved = listOf(MachineSetupParam("Note cinghia", "tesa"), MachineSetupParam(TreadmillSettings.SPEED, "5"))
        val updated = TreadmillSettings.withValues(saved, incline = 8.0, speed = 5.5)
        assertEquals(
            listOf(
                MachineSetupParam(TreadmillSettings.INCLINE, "8"),
                MachineSetupParam("Note cinghia", "tesa"),
                MachineSetupParam(TreadmillSettings.SPEED, "5,5"),
            ),
            updated,
        )
        assertEquals("Niente da aggiornare", saved, TreadmillSettings.withValues(saved, null, null))
        assertTrue(TreadmillSettings.isTreadmillParam(" pendenza (%) "))
    }
}
