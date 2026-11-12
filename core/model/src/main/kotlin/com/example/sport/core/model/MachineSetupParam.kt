package com.example.sport.core.model

import kotlinx.serialization.Serializable

/**
 * Singola voce di regolazione di un macchinario, es. `Altezza sedile = 4`,
 * `Inclinazione schienale = foro 2`, `Blocco gambe = 3`.
 *
 * Il valore è testo libero per supportare qualunque notazione (numeri, lettere, "2/3", "45°").
 * Una lista di parametri è persistita come JSON in una singola colonna: i parametri vengono
 * sempre letti e scritti insieme, quindi non serve una tabella dedicata.
 */
@Serializable
data class MachineSetupParam(
    val name: String,
    val value: String,
)

/**
 * Pulisce l'input dell'utente prima del salvataggio: spazi rimossi, voci senza nome o senza
 * valore scartate, nomi duplicati (case-insensitive) ridotti all'ultima occorrenza inserita.
 */
fun List<MachineSetupParam>.normalized(): List<MachineSetupParam> =
    map { MachineSetupParam(name = it.name.trim(), value = it.value.trim()) }
        .filter { it.name.isNotEmpty() && it.value.isNotEmpty() }
        .asReversed()
        .distinctBy { it.name.lowercase() }
        .asReversed()
