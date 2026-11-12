package com.example.sport.core.ui.format

import com.example.sport.core.model.WeightUnit
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Formattazione italiana di numeri, durate e date. Pura (nessun Context): testabile sulla JVM e
 * riusabile da ViewModel e Composable. L'unità di peso (kg/lb) arriva dalle impostazioni
 * ([LocalWeightUnit] nelle schermate).
 */
object Formatters {
    private val locale: Locale = Locale.ITALIAN
    private val symbols = DecimalFormatSymbols(Locale.ITALY)

    // Arrotondamento "da scuola" (10,5 → 11), come il tastierino; DecimalFormat di default arrotonda al pari.
    private val upToTwoDecimals = decimalFormat("#,##0.##")
    private val upToOneDecimal = decimalFormat("#,##0.#")
    private val fixedOneDecimal = decimalFormat("#,##0.0")
    private val integer = decimalFormat("#,##0")
    private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM", locale)
    private val monthYearFormat = DateTimeFormatter.ofPattern("MMM yy", locale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)

    /** Numero da modificare in un campo di testo: senza separatore delle migliaia (1234,5; 80). */
    fun editable(value: Double?): String =
        value?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',') }.orEmpty()

    /** Interpreta un numero scritto a mano: virgola o punto decimale, spazi ignorati; vuoto o non valido → null. */
    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** 42.5 → "42,5"; 80.0 → "80"; 1234.5 → "1.234,5". */
    fun number(value: Double): String = synchronized(upToTwoDecimals) { upToTwoDecimals.format(value) }

    fun oneDecimal(value: Double): String = synchronized(upToOneDecimal) { upToOneDecimal.format(value) }

    fun integer(value: Double): String = synchronized(integer) { integer.format(value) }

    private fun decimalFormat(pattern: String) = DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }

    // Pesi: il valore arriva sempre in kg (come nel database) e viene convertito nell'[WeightUnit]
    // scelta. In kg si tengono due decimali (dischi da 1,25), in libbre uno (225 lb, 93,7 lb).

    /** Solo il numero del peso nell'unità: 42.5 kg → "42,5" (kg) o "93,7" (lb). */
    fun weightNumber(valueKg: Double, unit: WeightUnit = WeightUnit.KG): String = when (unit) {
        WeightUnit.KG -> number(valueKg)
        WeightUnit.LB -> oneDecimal(unit.fromKg(valueKg))
    }

    /** Peso con unità ("42,5 kg", "93,7 lb"); null → trattino. */
    fun weightKg(value: Double?, unit: WeightUnit = WeightUnit.KG): String =
        value?.let { "${weightNumber(it, unit)} ${unit.symbol}" } ?: "—"

    /** Peso corporeo e sue medie: sempre un decimale, come la bilancia ("79,8 kg", "81,0 kg", "176,0 lb"). */
    fun bodyWeightKg(value: Double, unit: WeightUnit = WeightUnit.KG): String =
        "${synchronized(fixedOneDecimal) { fixedOneDecimal.format(unit.fromKg(value)) }} ${unit.symbol}"

    /** Stime (1RM): un decimale solo se serve ("116,7 kg", "76 kg"). */
    fun estimateKg(value: Double, unit: WeightUnit = WeightUnit.KG): String = "${oneDecimal(unit.fromKg(value))} ${unit.symbol}"

    /** Variazione con segno tipografico: −0,4 kg, +1,2 kg, 0 kg. */
    fun signedKg(delta: Double, unit: WeightUnit = WeightUnit.KG): String {
        val rounded = Math.round(unit.fromKg(delta) * 10) / 10.0
        return when {
            rounded > 0 -> "+${oneDecimal(rounded)} ${unit.symbol}"
            rounded < 0 -> "−${oneDecimal(-rounded)} ${unit.symbol}"
            else -> "0 ${unit.symbol}"
        }
    }

    /** Valore grande senza decimali superflui per volumi e massimali ("1.395 kg"). */
    fun wholeKg(value: Double, unit: WeightUnit = WeightUnit.KG): String = "${integer(unit.fromKg(value))} ${unit.symbol}"

    /** Serie in forma compatta: 100 kg × 5 (220,5 lb × 5). */
    fun setLabel(weightKg: Double, reps: Int, unit: WeightUnit = WeightUnit.KG): String =
        "${weightNumber(weightKg, unit)} ${unit.symbol} × $reps"

    /** "3 nov": etichette degli assi dei grafici. */
    fun dayMonth(date: LocalDate): String = date.format(dayMonthFormat)

    /** "nov 26": assi dei grafici che coprono più di un anno. */
    fun monthYear(date: LocalDate): String = date.format(monthYearFormat)

    /** "07:05". */
    fun time(value: LocalTime): String = value.format(timeFormat)

    /** Dimensione di un file in megabyte: 7_130_316 byte → "6,8 MB". */
    fun megabytes(bytes: Long): String = "${oneDecimal(bytes / BYTES_PER_MEGABYTE)} MB"

    private const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0

    /** Range di ripetizioni: (8, 12) → "8–12", (10, 10) → "10", (null, null) → "—". */
    fun repRange(min: Int?, max: Int?): String = when {
        min != null && max != null && min != max -> "$min–$max"
        min != null -> min.toString()
        max != null -> max.toString()
        else -> "—"
    }

    /** Durata compatta: 90 → "1:30"; 3725 → "1:02:05". */
    fun duration(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
    }

    /** Durata leggibile: 3_900 s → "1 h 5 min"; 2_700 s → "45 min". */
    fun durationHuman(duration: Duration): String {
        val minutes = duration.toMinutes().coerceAtLeast(0)
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }

    /** "Lunedì 9 novembre". */
    fun longDate(date: LocalDate): String = date.format(longDate).replaceFirstChar { it.titlecase(locale) }

    /** "lun 9 nov". */
    fun shortDate(date: LocalDate): String = date.format(shortDate)

    /** "Mercoledì". */
    fun dayOfWeek(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    /** Iniziale per il calendario compatto: L M M G V S D. */
    fun dayOfWeekInitial(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
}
