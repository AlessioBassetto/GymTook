package com.example.sport.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sport.R
import com.example.sport.core.data.progress.InclineSession
import com.example.sport.core.data.progress.InclineTrend
import com.example.sport.core.data.progress.inclineTrend
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.component.StatValue
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.ui.chart.niceAxis
import com.example.sport.core.ui.format.Formatters
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Una sessione di tapis roulant nella pagina dei progressi, con il confronto con la precedente. */
@Immutable
data class InclineSessionUi(
    val sessionId: Long,
    val date: LocalDate,
    val totalSeconds: Int,
    val averageIncline: Double,
    val maxIncline: Double,
    val trend: InclineTrend,
    /** Differenza di tempo rispetto alla sessione precedente (null per la prima). */
    val secondsDelta: Int?,
    /** Differenza di pendenza media rispetto alla sessione precedente (null per la prima). */
    val inclineDelta: Double?,
)

/** Progressi di un esercizio INCLINE_TIME: sessioni del periodo in ordine cronologico. */
@Immutable
data class InclineProgressUi(val sessions: List<InclineSessionUi>) {
    val latest: InclineSessionUi? get() = sessions.lastOrNull()
    val longestSeconds: Int? get() = sessions.maxOfOrNull { it.totalSeconds }
    val steepest: Double? get() = sessions.maxOfOrNull { it.maxIncline }

    /** Sessioni migliorate rispetto alla precedente (stessa pendenza più a lungo, più pendenza a parità di tempo…). */
    val improvements: Int get() = sessions.count { it.trend.isImprovement }
}

/** Sessioni (dalla più vecchia) → modello della pagina, confrontando ciascuna con la precedente. */
internal fun inclineProgressUi(sessions: List<InclineSession>, zone: ZoneId): InclineProgressUi =
    InclineProgressUi(
        sessions.mapIndexed { index, session ->
            val previous = sessions.getOrNull(index - 1)
            InclineSessionUi(
                sessionId = session.sessionId,
                date = session.performedAt.toLocalDate(zone),
                totalSeconds = session.totalSeconds,
                averageIncline = session.averageIncline,
                maxIncline = session.maxIncline,
                trend = inclineTrend(previous, session),
                secondsDelta = previous?.let { session.totalSeconds - it.totalSeconds },
                inclineDelta = previous?.let { session.averageIncline - it.averageIncline },
            )
        },
    )

/** Contenuto della pagina per il tapis roulant: periodo, riepilogo, grafico comparativo, sessioni. */
@Composable
internal fun InclineProgressBody(
    state: ExerciseProgressUiState.Content,
    incline: InclineProgressUi,
    actions: ExerciseProgressActions,
) {
    LazyColumn(
        modifier = Modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = SportDimens.SpacingXl),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        item { RangeChips(state.range, actions.onSelectRange) }
        if (incline.sessions.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.incline_none_in_range),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
                )
            }
            return@LazyColumn
        }
        item { InclineSummaryCard(incline) }
        item { InclineChartCard(incline.sessions) }
        item {
            SectionHeader(
                stringResource(R.string.exercise_progress_sessions),
                modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
            )
        }
        items(incline.sessions.asReversed(), key = { "incline-${it.sessionId}" }) { session ->
            InclineSessionRow(session, onClick = { actions.onOpenSession(session.sessionId) })
        }
    }
}

@Composable
private fun InclineSummaryCard(incline: InclineProgressUi) {
    val latest = incline.latest ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            Column {
                Text(
                    stringResource(R.string.incline_latest),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.incline_session_value, Formatters.clock(latest.totalSeconds.toLong()), Formatters.incline(latest.averageIncline)),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingXl)) {
                incline.longestSeconds?.let { StatValue(Formatters.clock(it.toLong()), stringResource(R.string.incline_longest)) }
                incline.steepest?.let { StatValue(Formatters.incline(it), stringResource(R.string.incline_steepest)) }
                StatValue(
                    stringResource(R.string.incline_improvements_value, incline.improvements, (incline.sessions.size - 1).coerceAtLeast(0)),
                    stringResource(R.string.incline_improvements),
                )
            }
        }
    }
}

@Composable
private fun InclineChartCard(sessions: List<InclineSessionUi>) {
    val timeColor = MaterialTheme.colorScheme.secondary
    val inclineColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            Text(
                stringResource(R.string.incline_chart_title),
                style = MaterialTheme.typography.titleSmall,
            )
            InclineComparisonChart(sessions, timeColor = timeColor, inclineColor = inclineColor)
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingL)) {
                LegendSwatch(timeColor, stringResource(R.string.incline_legend_time), round = false)
                LegendSwatch(inclineColor, stringResource(R.string.incline_legend_incline), round = true)
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, round: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, if (round) CircleShape else RoundedCornerShape(2.dp)),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Grafico a doppia serie sessione per sessione: barre = tempo di corsa (asse sinistro, minuti),
 * linea con punti = pendenza media (asse destro, %). Tenendo premuto compare il dettaglio.
 */
@Composable
internal fun InclineComparisonChart(sessions: List<InclineSessionUi>, timeColor: Color, inclineColor: Color, modifier: Modifier = Modifier) {
    if (sessions.isEmpty()) return
    val xs = sessions.indices.map { it.toDouble() }
    val minutes = sessions.map { it.totalSeconds / 60.0 }
    val inclines = sessions.map { it.averageIncline }
    val timeAxis = niceAxis(0.0, max(minutes.max(), 1.0))
    val inclineAxis = niceAxis(0.0, max(inclines.max(), 1.0))
    val labelSpacing = max(1, (sessions.size / X_LABELS.toDouble()).roundToInt())
    val minX = if (sessions.size == 1) -1.0 else 0.0
    val maxX = if (sessions.size == 1) 1.0 else (sessions.size - 1).toDouble()

    val dates = sessions.map { Formatters.dayMonth(it.date) }
    val markerTexts = sessions.map { session ->
        "${Formatters.shortDate(session.date)} · ${Formatters.clock(session.totalSeconds.toLong())} · ${Formatters.incline(session.averageIncline)}"
    }
    val currentDates by rememberUpdatedState(dates)
    val currentMarkers by rememberUpdatedState(markerTexts)
    val description = stringResource(
        R.string.incline_chart_description,
        sessions.size,
        Formatters.clock(sessions.last().totalSeconds.toLong()),
        Formatters.incline(sessions.last().averageIncline),
    )

    val model = remember(sessions) {
        CartesianChartModel(
            ColumnCartesianLayerModel.build { series(xs, minutes) },
            LineCartesianLayerModel.build { series(xs, inclines) },
        )
    }
    val column = rememberLineComponent(
        fill = Fill(timeColor),
        thickness = 14.dp,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
    )
    val line = LineCartesianLayer.rememberLine(
        fill = remember(inclineColor) { LineCartesianLayer.LineFill.single(Fill(inclineColor)) },
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.5.dp),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(Fill(inclineColor), CircleShape), size = 7.dp),
        ),
    )
    val markerText = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
        padding = Insets(horizontal = 8.dp, vertical = 4.dp),
        background = rememberShapeComponent(Fill(MaterialTheme.colorScheme.surfaceContainerHighest), RoundedCornerShape(6.dp)),
    )
    ProvideVicoTheme(rememberM3VicoTheme()) {
        val chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
                rangeProvider = remember(timeAxis, minX, maxX) { CartesianLayerRangeProvider.fixed(minX, maxX, timeAxis.min, timeAxis.max) },
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(line),
                rangeProvider = remember(inclineAxis, minX, maxX) { CartesianLayerRangeProvider.fixed(minX, maxX, inclineAxis.min, inclineAxis.max) },
                verticalAxisPosition = Axis.Position.Vertical.End,
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> "${Formatters.number(value)}′" } },
                itemPlacer = remember(timeAxis) { VerticalAxis.ItemPlacer.step({ timeAxis.step }) },
            ),
            endAxis = VerticalAxis.rememberEnd(
                // Una sola griglia (quella dei minuti): due scale diverse darebbero linee sfalsate.
                guideline = null,
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> Formatters.incline(value) } },
                itemPlacer = remember(inclineAxis) { VerticalAxis.ItemPlacer.step({ inclineAxis.step }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentDates.getOrNull(value.roundToInt()).orEmpty() } },
                itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) },
            ),
            marker = rememberDefaultCartesianMarker(
                label = markerText,
                valueFormatter = remember {
                    DefaultCartesianMarker.ValueFormatter { _, targets ->
                        currentMarkers.getOrNull(targets.first().x.roundToInt()).orEmpty()
                    }
                },
            ),
            // Una sessione = un passo: il confronto è sessione per sessione, non per giorno.
            getXStep = { _, _, _ -> 1.0 },
        )
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics { contentDescription = description },
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

@Composable
private fun InclineSessionRow(session: InclineSessionUi, onClick: () -> Unit) {
    val improvement = session.trend.isImprovement
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = SportDimens.TouchTargetPrimary)
                .padding(horizontal = SportDimens.CardPadding, vertical = SportDimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
                Text(Formatters.shortDate(session.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.incline_session_value, Formatters.clock(session.totalSeconds.toLong()), Formatters.incline(session.averageIncline)),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (session.maxIncline > session.averageIncline + 0.25) {
                    Text(
                        stringResource(R.string.incline_session_max, Formatters.incline(session.maxIncline)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    trendLabel(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (improvement) SportTheme.extendedColors.record else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (improvement) {
                Icon(
                    Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = stringResource(R.string.incline_improved),
                    tint = SportTheme.extendedColors.record,
                )
            }
        }
    }
}

/** Frase del confronto con la sessione precedente ("Stessa pendenza, +2:00", "+1% a parità di durata"). */
@Composable
private fun trendLabel(session: InclineSessionUi): String {
    val time = signedClock(session.secondsDelta ?: 0)
    val slope = signedIncline(session.inclineDelta ?: 0.0)
    return when (session.trend) {
        InclineTrend.FIRST -> stringResource(R.string.incline_trend_first)
        InclineTrend.LONGER_SAME_INCLINE -> stringResource(R.string.incline_trend_longer_same, time)
        InclineTrend.STEEPER_SAME_TIME -> stringResource(R.string.incline_trend_steeper_same, slope)
        InclineTrend.STEEPER_AND_LONGER -> stringResource(R.string.incline_trend_steeper_longer, slope, time)
        InclineTrend.SAME -> stringResource(R.string.incline_trend_same)
        InclineTrend.STEEPER, InclineTrend.LONGER, InclineTrend.LOWER -> stringResource(R.string.incline_trend_change, slope, time)
    }
}

/** +2:00 / −0:45 (segno meno tipografico). */
private fun signedClock(seconds: Int): String = (if (seconds < 0) "−" else "+") + Formatters.clock(abs(seconds).toLong())

/** +1% / −0,5%. */
private fun signedIncline(delta: Double): String = (if (delta < 0) "−" else "+") + Formatters.incline(abs(delta))

/** Etichette dell'asse x: circa cinque date. */
private const val X_LABELS = 5
