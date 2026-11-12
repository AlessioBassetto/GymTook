package com.example.sport.core.model

/**
 * Unità con cui l'utente legge e scrive i pesi. Nel database tutto resta in chilogrammi: l'unità
 * cambia solo la presentazione, quindi passare da kg a lb non tocca lo storico.
 */
enum class WeightUnit(val symbol: String, private val kilogramsPerUnit: Double) {
    KG("kg", 1.0),

    /** Libbra internazionale: esattamente 0,45359237 kg. */
    LB("lb", 0.45359237),
    ;

    fun fromKg(kilograms: Double): Double = kilograms / kilogramsPerUnit

    fun toKg(value: Double): Double = value * kilogramsPerUnit
}

/**
 * Scala dello sforzo percepito registrata nel logger: RPE (1–10, "quanto è stata dura") oppure
 * RIR (ripetizioni lasciate in riserva). Le due misure restano in colonne separate.
 */
enum class EffortScale {
    RPE,
    RIR,
}
