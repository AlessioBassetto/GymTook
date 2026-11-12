package com.example.sport.core.ui.anatomy

import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MuscleMapGeometryTest {

    private val numbers = Regex("-?\\d+(?:\\.\\d+)?")

    @Test
    fun everyMuscleRegion_isDrawnInAtLeastOneView() {
        val drawn = BodyView.entries.flatMap { view -> MuscleMapGeometry.regions(view).map { it.muscle } }.toSet()
        val expected = Muscle.entries.toSet() - MuscleMapGeometry.wholeBodyMuscles
        assertEquals(expected, drawn)
    }

    @Test
    fun mirror_reflectsXAroundCenterAndKeepsY() {
        assertEquals("M 59 55 L 36 56.5 Q 30 10 60 20 Z", MuscleMapGeometry.mirror("M61 55 L84 56.5 Q90 10 60 20 Z"))
    }

    @Test
    fun allShapes_stayInsideTheFigureBox() {
        val shapes = MuscleMapGeometry.silhouetteParts +
            BodyView.entries.flatMap { view -> MuscleMapGeometry.regions(view).flatMap { it.parts } }
        shapes.forEach { path ->
            val values = numbers.findAll(path).map { it.value.toFloat() }.toList()
            values.chunked(2).forEach { (x, y) ->
                assertTrue("x fuori figura in $path", x in 0f..MuscleMapGeometry.FIGURE_WIDTH)
                assertTrue("y fuori figura in $path", y in 0f..MuscleMapGeometry.FIGURE_HEIGHT)
            }
        }
    }

    /**
     * Esporta due anteprime SVG in app/build/reports/muscle-map per la revisione visiva:
     * un esempio reale (panca inclinata) e una tavola con ogni gruppo muscolare colorato.
     */
    @Test
    fun exportSvgPreviews() {
        val outDir = File("build/reports/muscle-map").apply { mkdirs() }
        File(outDir, "incline-press.svg").writeText(
            renderSvg { muscle ->
                when (muscle) {
                    Muscle.CHEST_UPPER -> "#E8FF3A"
                    Muscle.SHOULDERS_FRONT, Muscle.TRICEPS -> "#7A8A2E"
                    else -> "#2A2D33"
                }
            },
        )
        val palette = listOf(
            "#E57373", "#F06292", "#BA68C8", "#9575CD", "#7986CB", "#64B5F6", "#4FC3F7", "#4DD0E1", "#4DB6AC",
            "#81C784", "#AED581", "#DCE775", "#FFF176", "#FFD54F", "#FFB74D", "#FF8A65", "#A1887F", "#90A4AE",
        )
        File(outDir, "all-groups.svg").writeText(
            renderSvg { muscle -> palette[MuscleGroup.entries.indexOf(muscle.group) % palette.size] },
        )
        assertTrue(File(outDir, "all-groups.svg").length() > 0)
    }

    private fun renderSvg(colorOf: (Muscle) -> String): String = buildString {
        val width = MuscleMapGeometry.TOTAL_WIDTH
        val height = MuscleMapGeometry.FIGURE_HEIGHT
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-5 -5 ${width + 10} ${height + 10}\" ")
        append("width=\"${(width + 10) * 2.5}\" height=\"${(height + 10) * 2.5}\" style=\"background:#121212\">")
        BodyView.entries.forEach { view ->
            append("<g transform=\"translate(${MuscleMapGeometry.offsetX(view)} 0)\">")
            MuscleMapGeometry.silhouetteParts.forEach { append("<path d=\"$it\" fill=\"#1E2024\"/>") }
            MuscleMapGeometry.regions(view).forEach { region ->
                region.parts.forEach { part ->
                    append("<path d=\"$part\" fill=\"${colorOf(region.muscle)}\"><title>${region.muscle}</title></path>")
                }
            }
            append("</g>")
        }
        append("</svg>")
    }
}
