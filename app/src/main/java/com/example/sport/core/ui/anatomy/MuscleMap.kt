package com.example.sport.core.ui.anatomy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.sport.core.model.Muscle
import kotlin.math.min

/** Colori della mappa: target principale pieno, sinergici attenuati, resto quasi invisibile. */
@Immutable
data class MuscleMapColors(
    val primary: Color,
    val secondary: Color,
    val inactive: Color,
    val body: Color,
)

object MuscleMapDefaults {
    @Composable
    fun colors(
        primary: Color = MaterialTheme.colorScheme.primary,
        secondary: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        inactive: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        body: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    ) = MuscleMapColors(primary, secondary, inactive, body)
}

private class CompiledRegion(val muscle: Muscle, val path: Path)

private class CompiledMap(
    val silhouette: Path,
    val regions: Map<BodyView, List<CompiledRegion>>,
)

/**
 * Le metà di ogni forma vengono unite in un unico tracciato: con colori semitrasparenti, due
 * metà sovrapposte disegnate separatamente raddoppierebbero l'opacità lungo l'asse centrale.
 */
private fun parseUnion(parts: List<String>): Path = parts
    .map { PathParser().parsePathString(it).toPath() }
    .reduce { union, part -> Path.combine(PathOperation.Union, union, part) }

private fun compile(): CompiledMap = CompiledMap(
    silhouette = parseUnion(MuscleMapGeometry.silhouetteParts),
    regions = BodyView.entries.associateWith { view ->
        MuscleMapGeometry.regions(view).map { CompiledRegion(it.muscle, parseUnion(it.parts)) }
    },
)

/**
 * Mappa anatomica fronte/retro con i muscoli dell'esercizio evidenziati.
 * Vettoriale (nessun asset), scalabile a qualsiasi dimensione, colori dal tema.
 *
 * @param contentDescription descrizione per TalkBack (es. "Muscoli: petto alto; secondari: tricipiti").
 */
@Composable
fun MuscleMap(
    primary: Set<Muscle>,
    secondary: Set<Muscle>,
    modifier: Modifier = Modifier,
    colors: MuscleMapColors = MuscleMapDefaults.colors(),
    contentDescription: String? = null,
) {
    val compiled = remember { compile() }
    val wholeBody = MuscleMapGeometry.wholeBodyMuscles
    val bodyColor = when {
        primary.any { it in wholeBody } -> colors.secondary
        secondary.any { it in wholeBody } -> colors.inactive
        else -> colors.body
    }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(
        modifier = modifier
            .aspectRatio(MuscleMapGeometry.TOTAL_WIDTH / MuscleMapGeometry.FIGURE_HEIGHT)
            .then(semanticsModifier),
    ) {
        val factor = min(size.width / MuscleMapGeometry.TOTAL_WIDTH, size.height / MuscleMapGeometry.FIGURE_HEIGHT)
        scale(factor, pivot = Offset.Zero) {
            BodyView.entries.forEach { view ->
                translate(left = MuscleMapGeometry.offsetX(view)) {
                    drawPath(compiled.silhouette, bodyColor)
                    compiled.regions.getValue(view).forEach { region ->
                        val color = when (region.muscle) {
                            in primary -> colors.primary
                            in secondary -> colors.secondary
                            else -> colors.inactive
                        }
                        drawPath(region.path, color)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 320)
@Composable
private fun MuscleMapPreview() {
    MaterialTheme {
        MuscleMap(
            primary = setOf(Muscle.CHEST_UPPER),
            secondary = setOf(Muscle.SHOULDERS_FRONT, Muscle.TRICEPS),
        )
    }
}
