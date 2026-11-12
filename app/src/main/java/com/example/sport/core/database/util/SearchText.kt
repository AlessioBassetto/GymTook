package com.example.sport.core.database.util

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizzazione del testo per la ricerca esercizi: minuscolo, senza accenti,
 * punteggiatura trasformata in spazi. "Alzate Laterali (Manubri)" → "alzate laterali manubri".
 * La stessa funzione è applicata sia alla colonna `search_text` sia alla query dell'utente.
 */
object SearchText {

    private val combiningMarks = "\\p{Mn}+".toRegex()
    private val nonAlphanumeric = "[^\\p{L}\\p{N}]+".toRegex()

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(nonAlphanumeric, " ")
            .trim()

    /** Costruisce la colonna di ricerca a partire da tutti i nomi noti dell'esercizio. */
    fun build(name: String, nameEn: String?, aliases: List<String>): String =
        (listOf(name) + listOfNotNull(nameEn) + aliases)
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(separator = " | ")

    /** Parole della query, normalizzate e senza duplicati. */
    fun tokenize(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }.distinct()

    /** Escape dei caratteri speciali di LIKE; da usare con `ESCAPE '\'`. */
    fun escapeLike(token: String): String =
        token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
