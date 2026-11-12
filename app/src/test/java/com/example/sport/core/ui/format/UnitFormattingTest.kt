package com.example.sport.core.ui.format

import com.example.sport.core.data.SetMetric
import com.example.sport.core.designsystem.input.NumericInputConfig
import com.example.sport.core.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitFormattingTest {

    @Test
    fun pound_isExactlyDefined_andRoundTrips() {
        assertEquals(0.45359237, WeightUnit.LB.toKg(1.0), 0.0)
        assertEquals(225.0, WeightUnit.LB.fromKg(WeightUnit.LB.toKg(225.0)), 1e-9)
        assertEquals(80.0, WeightUnit.KG.fromKg(80.0), 0.0)
    }

    @Test
    fun weights_areFormattedInTheChosenUnit() {
        assertEquals("100 kg", Formatters.weightKg(100.0))
        assertEquals("220,5 lb", Formatters.weightKg(100.0, WeightUnit.LB))
        assertEquals("Il peso corporeo ha sempre un decimale", "176,4 lb", Formatters.bodyWeightKg(80.0, WeightUnit.LB))
        assertEquals("81,0 kg", Formatters.bodyWeightKg(81.0))
        assertEquals("225 lb × 5", Formatters.setLabel(WeightUnit.LB.toKg(225.0), 5, WeightUnit.LB))
        assertEquals("−1,1 lb", Formatters.signedKg(-0.5, WeightUnit.LB))
        assertEquals("3.075 lb", Formatters.wholeKg(1395.0, WeightUnit.LB))
        assertEquals("—", Formatters.weightKg(null, WeightUnit.LB))
    }

    @Test
    fun metrics_convertOnlyTheWeight() {
        assertEquals(220.462, SetMetric.WEIGHT.toDisplay(100.0, WeightUnit.LB), 1e-3)
        assertEquals(100.0, SetMetric.WEIGHT.toStorage(SetMetric.WEIGHT.toDisplay(100.0, WeightUnit.LB), WeightUnit.LB), 1e-9)
        assertEquals(8.0, SetMetric.REPS.toDisplay(8.0, WeightUnit.LB), 0.0)
        assertEquals(NumericInputConfig.WeightLb, SetMetric.WEIGHT.inputConfig(WeightUnit.LB))
        assertEquals(NumericInputConfig.Reps, SetMetric.REPS.inputConfig(WeightUnit.LB))
    }

    @Test
    fun handTypedNumbers_acceptCommaOrDot() {
        assertEquals(42.5, Formatters.parseDecimal(" 42,5 ")!!, 0.0)
        assertEquals(42.5, Formatters.parseDecimal("42.5")!!, 0.0)
        assertNull(Formatters.parseDecimal(""))
        assertNull(Formatters.parseDecimal("1,2,3"))
        assertEquals("Senza separatore delle migliaia per la modifica", "1234,5", Formatters.editable(1234.5))
        assertEquals("80", Formatters.editable(80.0))
    }
}
