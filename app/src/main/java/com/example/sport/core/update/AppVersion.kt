package com.example.sport.core.update

import kotlin.math.max

/**
 * Versione numerica confrontabile ("1.2.3"). Accetta il prefisso dei tag di GitHub ("v1.2.3") e
 * ignora i suffissi ("1.2.3-debug", "1.2.3+45"): contano solo i numeri separati da punti.
 * "1.2" e "1.2.0" sono la stessa versione.
 */
class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until max(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString(): String = parts.joinToString(".")

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+(?:\.\d+)*)""")

        /** null se il testo non comincia con un numero di versione (es. tag "nightly"). */
        fun parse(text: String): AppVersion? {
            val numbers = PATTERN.find(text.trim())?.groupValues?.get(1) ?: return null
            val parts = numbers.split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(parts)
        }
    }
}

/**
 * Note di rilascio leggibili in un dialogo di solo testo: GitHub le restituisce in Markdown.
 * Titoli senza cancelletti, elenchi con il pallino, niente grassetti, link ridotti al loro testo.
 */
fun plainReleaseNotes(markdown: String): String = markdown
    .replace("\r\n", "\n")
    .lines()
    .joinToString("\n") { line ->
        line.replace(HEADING, "")
            .replace(BULLET, "$1• ")
            .replace(LINK, "$1")
            .replace(EMPHASIS, "")
            .trimEnd()
    }
    .replace(BLANK_RUN, "\n\n")
    .trim()

private val HEADING = Regex("""^\s{0,3}#{1,6}\s+""")
private val BULLET = Regex("""^(\s*)[-*+]\s+""")
private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
private val EMPHASIS = Regex("""\*\*|__|`""")
private val BLANK_RUN = Regex("""\n{3,}""")
