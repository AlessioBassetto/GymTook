package com.example.sport.core.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class NiceAxisTest {

    @Test
    fun bodyWeightRange_usesHalfKilos() {
        assertEquals(NiceAxis(78.0, 80.5, 0.5), niceAxis(78.2, 80.1))
    }

    @Test
    fun loadRange_usesTens() {
        assertEquals(NiceAxis(90.0, 140.0, 10.0), niceAxis(98.0, 131.0))
    }

    @Test
    fun singleValue_staysInTheMiddle() {
        val axis = niceAxis(76.0, 76.0)
        assertEquals(NiceAxis(74.0, 78.0, 1.0), axis)
        assertEquals("76 kg al centro dell'asse", 76.0, (axis.min + axis.max) / 2, 0.0)
    }
}
