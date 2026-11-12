package com.example.sport.core.ui.chart

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

@Immutable
data class ChartPoint(val x: Double, val y: Double)

/** Una linea del grafico: [showPoints] per i dati reali, senza punti per medie e tendenze. */
@Immutable
data class ChartLine(
    val points: List<ChartPoint>,
    val color: Color,
    val thickness: Dp = 2.dp,
    val showPoints: Boolean = false,
)

/** Asse verticale "pulito": estremi e passo multipli di 1, 2, 2,5 o 5 × 10ⁿ. */
data class NiceAxis(val min: Double, val max: Double, val step: Double)

/**
 * Sceglie un intervallo leggibile che contiene [min]..[max] con circa [targetTicks] intervalli
 * (78,2–80,1 kg → 78–80,5 con passo 0,5; 98–131 kg → 90–140 con passo 10).
 */
fun niceAxis(min: Double, max: Double, targetTicks: Int = 4): NiceAxis {
    // Valore unico (una sola sessione, peso stabile): un margine per lato lo tiene al centro.
    val flatMargin = if (max > min) 0.0 else max(0.5, kotlin.math.abs(max) * 0.025)
    val low = min - flatMargin
    val high = max + flatMargin
    val span = high - low
    val rawStep = span / targetTicks
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rawStep }
    val niceMin = floor(low / step) * step
    var niceMax = ceil(high / step) * step
    if (niceMax <= niceMin) niceMax = niceMin + step
    return NiceAxis(niceMin, niceMax, step)
}

/**
 * Grafico a linee per l'andamento nel tempo (asse x in giorni, es. epoch-day). Adatta il contenuto
 * alla larghezza, senza scorrimento né zoom: è un riepilogo da guardare al volo. Tenendo premuto
 * compare il valore del punto.
 *
 * @param markerLabel testo del valore premuto: x e i valori y delle linee in quel punto.
 */
@Composable
fun TrendChart(
    lines: List<ChartLine>,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    markerLabel: (x: Double, ys: List<Double>) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    val visible = lines.filter { it.points.isNotEmpty() }
    if (visible.isEmpty()) return

    val xs = visible.flatMap { line -> line.points.map { it.x } }
    val ys = visible.flatMap { line -> line.points.map { it.y } }
    // Un solo giorno di dati: si allarga di un giorno per lato, altrimenti l'asse x è vuoto.
    val minX = xs.min().let { if (xs.max() == it) it - 1 else it }
    val maxX = xs.max().let { if (xs.min() == it) it + 1 else it }
    val axis = niceAxis(ys.min(), ys.max())
    val labelSpacing = max(1, ((maxX - minX) / X_LABELS).roundToInt())

    val currentXLabel by rememberUpdatedState(xLabel)
    val currentYLabel by rememberUpdatedState(yLabel)
    val currentMarkerLabel by rememberUpdatedState(markerLabel)

    val model = remember(visible) {
        CartesianChartModel(
            LineCartesianLayerModel.build {
                visible.forEach { line -> series(line.points.map { it.x }, line.points.map { it.y }) }
            },
        )
    }
    val vicoLines = visible.mapIndexed { index, line ->
        key(index) {
            val point = if (line.showPoints) {
                LineCartesianLayer.Point(rememberShapeComponent(Fill(line.color), CircleShape), size = 6.dp)
            } else {
                null
            }
            LineCartesianLayer.rememberLine(
                fill = remember(line.color) { LineCartesianLayer.LineFill.single(Fill(line.color)) },
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = line.thickness),
                pointProvider = point?.let { remember(it) { LineCartesianLayer.PointProvider.single(it) } },
            )
        }
    }
    val markerText = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
        padding = Insets(horizontal = 8.dp, vertical = 4.dp),
        background = rememberShapeComponent(Fill(MaterialTheme.colorScheme.surfaceContainerHighest), RoundedCornerShape(6.dp)),
    )

    ProvideVicoTheme(rememberM3VicoTheme()) {
        val chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(vicoLines),
                rangeProvider = remember(minX, maxX, axis) { CartesianLayerRangeProvider.fixed(minX, maxX, axis.min, axis.max) },
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentYLabel(value) } },
                itemPlacer = remember(axis) { VerticalAxis.ItemPlacer.step({ axis.step }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentXLabel(value) } },
                itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) },
            ),
            marker = rememberDefaultCartesianMarker(
                label = markerText,
                valueFormatter = remember {
                    DefaultCartesianMarker.ValueFormatter { _, targets ->
                        val target = targets.first()
                        val values = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().flatMap { it.points }.map { it.entry.y }
                        currentMarkerLabel(target.x, values)
                    }
                },
            ),
            // Un passo = un giorno: le date restano proporzionali anche con pause irregolari.
            getXStep = { _, _, _ -> 1.0 },
        )
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .semantics { this.contentDescription = contentDescription },
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

/** Etichette dell'asse x: circa cinque, quale che sia l'intervallo di date. */
private const val X_LABELS = 4
