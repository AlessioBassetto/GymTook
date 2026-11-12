package com.example.sport.core.ui.anatomy

import com.example.sport.core.model.Muscle

// A livello di file (non nell'object): devono esistere prima che l'object inizializzi i path.
private val PATH_TOKEN = Regex("[A-Za-z]|-?\\d+(?:\\.\\d+)?")
private val SUPPORTED_PATH_COMMANDS = setOf("M", "L", "Q", "Z")

/** Vista della figura anatomica. */
enum class BodyView { FRONT, BACK }

/**
 * Area colorabile della figura, in formato SVG path data (solo comandi assoluti M, L, Q, Z).
 * Le due metà sono parti separate: specchiare inverte il verso del tracciato, e in un unico path
 * le zone sovrapposte si annullerebbero (riga vuota al centro della figura).
 */
data class MuscleRegionShape(val muscle: Muscle, val parts: List<String>)

/**
 * Geometria vettoriale della mappa muscolare: due figure (fronte e retro) in un riquadro
 * 120 × 260 ciascuna. Peso nullo nell'APK (nessuna immagine) e colori decisi a runtime.
 *
 * Le regioni sono disegnate solo sulla metà destra della figura e specchiate rispetto a x = 60:
 * la mappa è simmetrica per costruzione. Ogni regione corrisponde a una costante di [Muscle],
 * quindi la mappa resta allineata alla tassonomia dei filtri e del catalogo.
 */
object MuscleMapGeometry {
    const val FIGURE_WIDTH = 120f
    const val FIGURE_HEIGHT = 260f
    const val FIGURE_GAP = 10f
    const val TOTAL_WIDTH = FIGURE_WIDTH * 2 + FIGURE_GAP

    /** Muscoli senza area propria: colorano l'intera sagoma. */
    val wholeBodyMuscles: Set<Muscle> = setOf(Muscle.CARDIO, Muscle.FULL_BODY)

    /** Sagoma del corpo (le due metà si sovrappongono di 1 unità per non mostrare cuciture). */
    val silhouetteParts: List<String> = symmetric(
        "M59.5 4 Q74 4 74 22 Q74 37 66 40 L66.5 47 Q78 49 87 52 Q99 55 100 66 L101 80 " +
            "Q103 94 101 106 L104 130 Q106 146 104 158 L106 167 Q103 173 98 168 L97 158 " +
            "Q94 142 92 128 L89 108 Q86 97 86 86 L82 112 Q80 124 83 133 Q89 148 85 170 " +
            "Q84 186 82 197 Q87 216 81 240 L83 250 Q84 256 77 256 L70 256 Q68 248 69 240 " +
            "Q66 218 67 198 Q63 176 62 150 L61 141 L59.5 141 Z",
    )

    private val forearm = "M90 108 L99 107 Q103 124 102 147 L96 148 Q91 128 90 108 Z"

    private val front: List<MuscleRegionShape> = listOf(
        center(Muscle.NECK, "M55 39 L65 39 L66 47 Q60 49 54 47 Z"),
        mirrored(Muscle.TRAPS, "M66 46 Q76 48 84 53 L71 54 Q67 51 66 46 Z"),
        mirrored(Muscle.CHEST_UPPER, "M61 55 Q72 52 84 56 Q86 61 84 65 L61 65 Z"),
        mirrored(Muscle.CHEST_MIDDLE, "M61 65 L84 65 Q86 73 81 79 L61 79 Z"),
        mirrored(Muscle.CHEST_LOWER, "M61 79 L81 79 Q76 88 61 89 Z"),
        mirrored(Muscle.SHOULDERS_FRONT, "M84 54 Q88 51 92 54 L91 74 Q87 70 85 64 Q86 59 84 54 Z"),
        mirrored(Muscle.SHOULDERS_SIDE, "M92 54 Q99 57 100 66 L99 77 L91 74 Z"),
        mirrored(Muscle.BICEPS, "M89 78 L98 79 Q101 92 98 104 L90 104 Q87 91 89 78 Z"),
        mirrored(Muscle.FOREARMS, forearm),
        mirrored(Muscle.ABS_UPPER, "M61 91 L71 90 L71.5 112 L61 112 Z"),
        mirrored(Muscle.ABS_LOWER, "M61 113 L71.5 113 Q71 126 61 133 Z"),
        mirrored(Muscle.OBLIQUES, "M72.5 89 L80 83 Q83 100 79 121 L73 118 Q72.5 104 72.5 89 Z"),
        mirrored(Muscle.ABDUCTORS, "M80 125 L84 132 Q86 145 83 158 Q80 142 80 125 Z"),
        mirrored(Muscle.QUADRICEPS, "M63 140 Q72 132 80 134 Q84 150 81 176 L79 191 L69 192 Q63 168 63 140 Z"),
        mirrored(Muscle.ADDUCTORS, "M61 140 L63 141 Q63 165 68 187 L63 182 Q61 163 61 140 Z"),
        mirrored(Muscle.CALVES, "M78 203 L82 200 Q85 218 80 236 Q80 218 78 203 Z"),
    )

    private val back: List<MuscleRegionShape> = listOf(
        center(Muscle.NECK, "M55 36 L65 36 L66 45 L54 45 Z"),
        mirrored(Muscle.UPPER_BACK, "M72 59 L86 57 Q88 70 82 82 L66 84 Z"),
        mirrored(Muscle.TRAPS, "M59.5 44 L66 43 Q76 47 85 53 L72 58 L65 80 L59.5 90 Z"),
        mirrored(Muscle.SHOULDERS_REAR, "M85 54 Q93 52 97 58 L95 72 Q90 66 85 61 Z"),
        mirrored(Muscle.SHOULDERS_SIDE, "M97 58 Q100 62 100 67 L98 77 L95 72 Z"),
        mirrored(Muscle.TRICEPS, "M89 76 L99 78 Q102 92 99 104 L91 104 Q87 91 89 76 Z"),
        mirrored(Muscle.FOREARMS, forearm),
        mirrored(Muscle.LATS, "M66 86 L82 83 Q88 94 83 110 Q74 116 67 120 Q64 104 66 86 Z"),
        mirrored(Muscle.LOWER_BACK, "M60.5 100 L65 92 L67 121 Q66 128 60.5 131 Z"),
        mirrored(Muscle.GLUTES, "M61 133 Q72 126 82 132 Q88 146 82 158 Q72 163 61 158 Z"),
        mirrored(Muscle.ABDUCTORS, "M76 125 Q84 126 86 134 L84 140 Q80 131 76 125 Z"),
        mirrored(Muscle.HAMSTRINGS, "M63 162 Q72 165 82 161 Q83 180 79 195 L68 196 Q63 180 63 162 Z"),
        mirrored(Muscle.ADDUCTORS, "M61 160 L63 162 Q63 180 67 192 L62 188 Q61 175 61 160 Z"),
        mirrored(Muscle.CALVES, "M68 202 Q74 198 81 201 Q86 216 79 234 L72 235 Q66 218 68 202 Z"),
    )

    /** Regioni nell'ordine di disegno (le successive stanno sopra). */
    fun regions(view: BodyView): List<MuscleRegionShape> = when (view) {
        BodyView.FRONT -> front
        BodyView.BACK -> back
    }

    /** Offset orizzontale della figura nel riquadro complessivo. */
    fun offsetX(view: BodyView): Float = when (view) {
        BodyView.FRONT -> 0f
        BodyView.BACK -> FIGURE_WIDTH + FIGURE_GAP
    }

    /** Specchia un path rispetto all'asse verticale x = 60. */
    internal fun mirror(pathData: String): String {
        val tokens = PATH_TOKEN.findAll(pathData).map { it.value }.toList()
        val out = StringBuilder()
        var coordinateIndex = 0
        tokens.forEach { token ->
            if (token[0].isLetter()) {
                require(token in SUPPORTED_PATH_COMMANDS) { "Comando path non supportato: $token" }
                coordinateIndex = 0
                out.append(token).append(' ')
            } else {
                val value = token.toFloat()
                val mirrored = if (coordinateIndex % 2 == 0) FIGURE_WIDTH - value else value
                out.append(format(mirrored)).append(' ')
                coordinateIndex++
            }
        }
        return out.toString().trim()
    }

    private fun mirrored(muscle: Muscle, rightHalf: String) = MuscleRegionShape(muscle, symmetric(rightHalf))

    private fun center(muscle: Muscle, pathData: String) = MuscleRegionShape(muscle, listOf(pathData))

    private fun symmetric(rightHalf: String) = listOf(rightHalf, mirror(rightHalf))

    private fun format(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
}
