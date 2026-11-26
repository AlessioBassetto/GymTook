package com.example.sport.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Regolazione dedicata del tapis roulant: due voci fisse della "Regolazione macchinario",
 * **Pendenza (%)** e **Velocità (km/h)**, salvate come le altre voci ([MachineSetupParam]) così
 * restano nei profili, nel backup e nello storico delle regolazioni.
 *
 * I valori sono scritti in formato italiano ("6,5"); in lettura si accetta anche il punto.
 */
object TreadmillSettings {
    const val INCLINE = "Pendenza (%)"
    const val SPEED = "Velocità (km/h)"

    /** Le due voci, nell'ordine in cui compaiono nell'editor. */
    val PARAM_NAMES: List<String> = listOf(INCLINE, SPEED)

    const val MIN_INCLINE = 0.0

    /** Pendenza massima dei tapis da camminata in salita. */
    const val MAX_INCLINE = 40.0
    const val INCLINE_STEP = 0.5

    const val MIN_SPEED = 0.0

    /** Velocità massima dei tapis roulant da palestra (con margine). */
    const val MAX_SPEED = 30.0
    const val SPEED_FINE_STEP = 0.1
    const val SPEED_COARSE_STEP = 0.5

    fun isTreadmillParam(name: String): Boolean = PARAM_NAMES.any { it.equals(name.trim(), ignoreCase = true) }

    /** "6,5" o "6.5" → 6.5; testo non numerico → null. */
    fun parse(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** 6.5 → "6,5", 6.0 → "6", 5.55 → "5,6" (un decimale). */
    fun format(value: Double): String =
        BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    fun clampIncline(value: Double): Double = value.coerceIn(MIN_INCLINE, MAX_INCLINE)

    fun clampSpeed(value: Double): Double = value.coerceIn(MIN_SPEED, MAX_SPEED)

    /** Pendenza salvata nel profilo (null se assente o non numerica). */
    fun incline(params: List<MachineSetupParam>): Double? = valueOf(params, INCLINE)?.let(::clampIncline)

    /** Velocità salvata nel profilo (null se assente o non numerica). */
    fun speed(params: List<MachineSetupParam>): Double? = valueOf(params, SPEED)?.let(::clampSpeed)

    /**
     * Profilo con l'ultima combinazione usata: le due voci vengono sostituite (o aggiunte in testa)
     * e le altre voci dell'utente restano com'erano. Un valore null lascia la voce invariata.
     */
    fun withValues(params: List<MachineSetupParam>, incline: Double?, speed: Double?): List<MachineSetupParam> {
        val updates = buildMap {
            incline?.let { put(INCLINE, format(clampIncline(it))) }
            speed?.let { put(SPEED, format(clampSpeed(it))) }
        }
        if (updates.isEmpty()) return params
        val replaced = params.map { param ->
            val key = updates.keys.firstOrNull { it.equals(param.name.trim(), ignoreCase = true) }
            if (key != null) MachineSetupParam(key, updates.getValue(key)) else param
        }
        val missing = PARAM_NAMES.filter { name -> name in updates && replaced.none { it.name.trim().equals(name, ignoreCase = true) } }
            .map { MachineSetupParam(it, updates.getValue(it)) }
        return missing + replaced
    }

    private fun valueOf(params: List<MachineSetupParam>, name: String): Double? =
        params.lastOrNull { it.name.trim().equals(name, ignoreCase = true) }?.value?.let(::parse)
}
