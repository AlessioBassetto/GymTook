package com.example.sport.core.designsystem.input

import androidx.compose.runtime.Immutable
import java.math.BigDecimal
import java.math.RoundingMode

/** Regole di un campo numerico del logger. */
@Immutable
data class NumericInputConfig(
    val allowDecimal: Boolean,
    val maxIntegerDigits: Int,
    val maxDecimalDigits: Int,
    /** Incremento dei tasti +/- (es. 2,5 kg = la coppia di dischi più piccola comune). */
    val step: Double,
    val min: Double,
    val max: Double,
) {
    init {
        require(min <= max) { "min deve essere <= max" }
        require(step > 0) { "step deve essere > 0" }
    }

    companion object {
        val WeightKg = NumericInputConfig(allowDecimal = true, maxIntegerDigits = 4, maxDecimalDigits = 2, step = 2.5, min = 0.0, max = 2000.0)

        /** Libbre: la coppia di dischi più piccola comune è 2 × 2,5 lb, si sale di 5. */
        val WeightLb = NumericInputConfig(allowDecimal = true, maxIntegerDigits = 4, maxDecimalDigits = 1, step = 5.0, min = 0.0, max = 4400.0)
        val Reps = NumericInputConfig(allowDecimal = false, maxIntegerDigits = 3, maxDecimalDigits = 0, step = 1.0, min = 0.0, max = 999.0)
        val Rpe = NumericInputConfig(allowDecimal = true, maxIntegerDigits = 2, maxDecimalDigits = 1, step = 0.5, min = 1.0, max = 10.0)
        val Rir = NumericInputConfig(allowDecimal = false, maxIntegerDigits = 2, maxDecimalDigits = 0, step = 1.0, min = 0.0, max = 10.0)
        val Seconds = NumericInputConfig(allowDecimal = false, maxIntegerDigits = 4, maxDecimalDigits = 0, step = 15.0, min = 0.0, max = 5999.0)
        val Minutes = NumericInputConfig(allowDecimal = false, maxIntegerDigits = 3, maxDecimalDigits = 0, step = 1.0, min = 0.0, max = 600.0)
        val DistanceKm = NumericInputConfig(allowDecimal = true, maxIntegerDigits = 3, maxDecimalDigits = 2, step = 0.5, min = 0.0, max = 300.0)

        /** Peso corporeo: la bilancia di casa arriva al decimo, si corregge di 0,1 kg. */
        val BodyWeightKg = NumericInputConfig(allowDecimal = true, maxIntegerDigits = 3, maxDecimalDigits = 1, step = 0.1, min = 20.0, max = 400.0)

        /** Peso corporeo in libbre: 44,1–881,8 lb, cioè dentro i limiti di 20–400 kg. */
        val BodyWeightLb = NumericInputConfig(allowDecimal = true, maxIntegerDigits = 3, maxDecimalDigits = 1, step = 0.2, min = 44.1, max = 881.8)
    }
}

/** Tasti del tastierino. */
sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey {
        init {
            require(value in 0..9) { "Cifra non valida: $value" }
        }
    }

    data object DecimalSeparator : KeypadKey
    data object Backspace : KeypadKey
    data object Increment : KeypadKey
    data object Decrement : KeypadKey
    data object Clear : KeypadKey
}

/**
 * Stato immutabile di un campo numerico editato col tastierino dell'app (mai la tastiera di sistema:
 * copre metà schermo e cambia layout tra un telefono e l'altro).
 *
 * Comportamento "zero friction": un campo pre-compilato (es. 80 kg della volta scorsa) viene
 * sostituito dalla prima cifra digitata, come se il testo fosse selezionato; +/- lavorano sul valore.
 * Il testo interno usa il punto, quello mostrato la virgola (formato italiano).
 */
@Immutable
data class NumericInputState(
    val text: String,
    val config: NumericInputConfig,
    val replaceOnNextInput: Boolean = true,
) {
    val value: Double? get() = text.toDoubleOrNull()

    val displayText: String get() = text.replace('.', ',')

    fun reduce(key: KeypadKey): NumericInputState = when (key) {
        is KeypadKey.Digit -> appendDigit(key.value)
        KeypadKey.DecimalSeparator -> appendDecimalSeparator()
        KeypadKey.Backspace -> copy(text = text.dropLast(1), replaceOnNextInput = false)
        KeypadKey.Clear -> copy(text = "", replaceOnNextInput = false)
        KeypadKey.Increment -> stepBy(config.step)
        KeypadKey.Decrement -> stepBy(-config.step)
    }

    private fun appendDigit(digit: Int): NumericInputState {
        val base = if (replaceOnNextInput) "" else text
        val candidate = if (base == "0") digit.toString() else base + digit
        return if (isAcceptable(candidate)) copy(text = candidate, replaceOnNextInput = false) else this
    }

    private fun appendDecimalSeparator(): NumericInputState {
        if (!config.allowDecimal) return this
        val base = if (replaceOnNextInput) "" else text
        if (base.contains('.')) return this
        return copy(text = if (base.isEmpty()) "0." else "$base.", replaceOnNextInput = false)
    }

    private fun stepBy(delta: Double): NumericInputState {
        val next = ((value ?: config.min) + delta).coerceIn(config.min, config.max)
        return copy(text = canonical(next, config), replaceOnNextInput = true)
    }

    private fun isAcceptable(candidate: String): Boolean {
        val integerPart = candidate.substringBefore('.')
        val decimalPart = candidate.substringAfter('.', missingDelimiterValue = "")
        if (integerPart.length > config.maxIntegerDigits) return false
        if (decimalPart.length > config.maxDecimalDigits) return false
        val parsed = candidate.toDoubleOrNull() ?: return false
        return parsed <= config.max
    }

    companion object {
        /** Campo pre-compilato con [value] (null = vuoto): la prima cifra digitata lo sostituisce. */
        fun of(value: Double?, config: NumericInputConfig): NumericInputState =
            NumericInputState(text = value?.let { canonical(it, config) }.orEmpty(), config = config)

        /** Rappresentazione senza zeri superflui: 80.0 → "80", 42.50 → "42.5". */
        fun canonical(value: Double, config: NumericInputConfig): String =
            BigDecimal.valueOf(value)
                .setScale(config.maxDecimalDigits, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
    }
}
