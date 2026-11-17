package com.example.sport.core.designsystem.input

import com.example.sport.core.designsystem.input.KeypadKey.Backspace
import com.example.sport.core.designsystem.input.KeypadKey.Clear
import com.example.sport.core.designsystem.input.KeypadKey.DecimalSeparator
import com.example.sport.core.designsystem.input.KeypadKey.Decrement
import com.example.sport.core.designsystem.input.KeypadKey.Digit
import com.example.sport.core.designsystem.input.KeypadKey.Increment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumericInputStateTest {

    private fun NumericInputState.type(vararg keys: KeypadKey) = keys.fold(this) { state, key -> state.reduce(key) }
    private fun digits(text: String) = text.map { if (it == ',') DecimalSeparator else Digit(it.digitToInt()) }.toTypedArray()

    @Test
    fun prefilledValue_isReplacedByFirstDigit_likeSelectedText() {
        val state = NumericInputState.of(80.0, NumericInputConfig.WeightKg)
        assertEquals("80", state.displayText)
        assertEquals("42,5", state.type(*digits("42,5")).displayText)
        assertEquals(42.5, state.type(*digits("42,5")).value!!, 0.0)
    }

    @Test
    fun backspace_editsPrefilledValueInsteadOfReplacingIt() {
        val state = NumericInputState.of(82.5, NumericInputConfig.WeightKg).type(Backspace, Digit(0))
        assertEquals("82,0", state.displayText)
        assertEquals(82.0, state.value!!, 0.0)
    }

    @Test
    fun steps_useTheConfiguredIncrement_andClampToRange() {
        val weight = NumericInputState.of(42.5, NumericInputConfig.WeightKg)
        assertEquals("45", weight.type(Increment).displayText)
        assertEquals("40", weight.type(Decrement).displayText)
        assertEquals("0", NumericInputState.of(1.0, NumericInputConfig.WeightKg).type(Decrement).displayText)
        assertEquals("2,5", NumericInputState.of(null, NumericInputConfig.WeightKg).type(Increment).displayText)

        val rpe = NumericInputState.of(9.5, NumericInputConfig.Rpe)
        assertEquals("10", rpe.type(Increment, Increment).displayText)
    }

    @Test
    fun afterAStep_typingReplacesAgain() {
        val state = NumericInputState.of(40.0, NumericInputConfig.WeightKg).type(Increment, Digit(5), Digit(0))
        assertEquals("50", state.displayText)
    }

    @Test
    fun integerFields_ignoreTheDecimalSeparator() {
        val reps = NumericInputState.of(8.0, NumericInputConfig.Reps).type(Digit(1), DecimalSeparator, Digit(2))
        assertEquals("12", reps.displayText)
    }

    @Test
    fun limits_rejectKeystrokesThatWouldBreakTheField() {
        val weight = NumericInputState.of(null, NumericInputConfig.WeightKg)
        assertEquals("Massimo due decimali", "12,25", weight.type(*digits("12,255")).displayText)
        assertEquals("Una sola virgola", "1,5", weight.type(Digit(1), DecimalSeparator, DecimalSeparator, Digit(5)).displayText)
        assertEquals("Oltre il massimo (2000 kg)", "200", weight.type(*digits("2005")).displayText)
        assertEquals("Niente zeri iniziali", "7", weight.type(Digit(0), Digit(7)).displayText)
        assertEquals("Virgola come primo tasto", "0,5", weight.type(DecimalSeparator, Digit(5)).displayText)

        val reps = NumericInputState.of(null, NumericInputConfig.Reps)
        assertEquals("Massimo 3 cifre", "999", reps.type(*digits("9999")).displayText)
    }

    @Test
    fun clear_emptiesTheField() {
        val state = NumericInputState.of(60.0, NumericInputConfig.WeightKg).type(Clear)
        assertEquals("", state.displayText)
        assertNull(state.value)
    }

    @Test
    fun canonical_dropsUselessZeros() {
        assertEquals("80", NumericInputState.canonical(80.0, NumericInputConfig.WeightKg))
        assertEquals("42.5", NumericInputState.canonical(42.50, NumericInputConfig.WeightKg))
        assertEquals("33.33", NumericInputState.canonical(33.333, NumericInputConfig.WeightKg))
    }

    @Test
    fun incline_stepsByHalfAPercent_andStopsAtTheTreadmillLimits() {
        val incline = NumericInputState.of(8.0, NumericInputConfig.InclinePercent)
        assertEquals("8,5", incline.type(Increment).displayText)
        assertEquals("7,5", incline.type(Decrement).displayText)
        assertEquals("40", NumericInputState.of(39.5, NumericInputConfig.InclinePercent).type(Increment, Increment).displayText)
        assertEquals("0", NumericInputState.of(0.0, NumericInputConfig.InclinePercent).type(Decrement).displayText)
        assertEquals("Un solo decimale", "12,5", NumericInputState.of(null, NumericInputConfig.InclinePercent).type(*digits("12,55")).displayText)
        assertEquals("Oltre il 40%", "4", NumericInputState.of(null, NumericInputConfig.InclinePercent).type(*digits("45")).displayText)
    }

    @Test
    fun clock_digitsEnterFromTheRight_likeAMicrowave() {
        val time = NumericInputState.of(null, NumericInputConfig.Clock).type(Digit(2), Digit(5), Digit(3), Digit(0))
        assertEquals("25:30", time.displayText)
        assertEquals(25 * 60 + 30.0, time.value!!, 0.0)
        assertEquals("0:05", NumericInputState.of(null, NumericInputConfig.Clock).type(Digit(5)).displayText)
        assertEquals("Niente virgola nel tempo", "1:00", NumericInputState.of(null, NumericInputConfig.Clock).type(Digit(1), DecimalSeparator, Digit(0), Digit(0)).displayText)
        assertEquals("Massimo 99:59", "9:99", NumericInputState.of(null, NumericInputConfig.Clock).type(*digits("9999")).displayText)
    }

    @Test
    fun clock_prefilledTimeIsReplaced_andStepsMoveByOneMinute() {
        val previous = NumericInputState.of(1500.0, NumericInputConfig.Clock)
        assertEquals("25:00", previous.displayText)
        assertEquals("26:00", previous.type(Increment).displayText)
        assertEquals(1440.0, previous.type(Decrement).value!!, 0.0)
        assertEquals("Il primo tasto sostituisce il tempo della volta scorsa", "0:03", previous.type(Digit(3)).displayText)
        assertEquals("Il backspace corregge invece di sostituire", "2:50", previous.type(Backspace).displayText)
        assertEquals("1:00", NumericInputState.of(null, NumericInputConfig.Clock).type(Increment).displayText)
        assertEquals("Non si scende sotto zero", "0:00", NumericInputState.of(30.0, NumericInputConfig.Clock).type(Decrement).displayText)
    }

    @Test
    fun clock_canonicalFormKeepsSecondsOnTwoDigits() {
        assertEquals("2500", NumericInputState.canonical(1500.0, NumericInputConfig.Clock))
        assertEquals("105", NumericInputState.canonical(65.0, NumericInputConfig.Clock))
        assertEquals("45", NumericInputState.canonical(45.0, NumericInputConfig.Clock))
        assertEquals(90L, clockSeconds("130"))
        assertEquals("1:30", clockDisplay("130"))
    }
}
