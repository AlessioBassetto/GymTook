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
import androidx.compose.material.icons.outlined.LocalFireDepartment
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
import com.example.sport.core.data.ExerciseSessionCalories
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
import kotlin.math.max
import kotlin.math.roundToInt

/** Una sessione di tapis roulant nelle statistiche: calorie della sessione e totale cumulativo. */
@Immutable
data class TreadmillSessionUi(
    val sessionId: Long,
    val date: LocalDate,
    /** null se all'epoca mancava una pesata (la stima non è possibile). */
    val kcal: Int?,
    /** Somma delle calorie dalla prima sessione del periodo fino a questa compresa. */
    val cumulativeKcal: Int,
)

/** Statistiche del tapis roulant: **solo calorie**, sessione per sessione e in totale. */
@Immutable
data class TreadmillCaloriesUi(val sessions: List<TreadmillSessionUi>) {
    private val estimated: List<Int> get() = sessions.mapNotNull { it.kcal }

    val totalKcal: Int get() = estimated.sum()
    val averageKcal: Int? get() = estimated.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val latest: TreadmillSessionUi? get() = sessions.lastOrNull()

    /** La sessione che ha bruciato di più nel periodo (evidenziata nell'elenco). */
    val bestSessionId: Long? get() = sessions.filter { it.kcal != null }.maxByOrNull { it.kcal ?: 0 }?.sessionId

    /** Ci sono sessioni ma nessuna stima: manca il peso corporeo. */
    val missingWeight: Boolean get() = sessions.isNotEmpty() && estimated.isEmpty()
}

/** Calorie per allenamento (dalla più vecchia) → modello della pagina con il totale cumulativo. */
internal fun treadmillCaloriesUi(sessions: List<ExerciseSessionCalories>, zone: ZoneId): TreadmillCaloriesUi {
    var running = 0
    return TreadmillCaloriesUi(
        sessions.map { session ->
            running += session.kcal ?: 0
            TreadmillSessionUi(
                sessionId = session.sessionId,
                date = session.performedAt.toLocalDate(zone),
                kcal = session.kcal,
                cumulativeKcal = running,
            )
        },
    )
}

/** Pagina del tapis roulant: periodo, riepilogo calorie, grafico calorie per sessione + cumulativo, sessioni. */
@Composable
internal fun TreadmillCaloriesBody(
    state: ExerciseProgressUiState.Content,
    treadmill: TreadmillCaloriesUi,
    actions: ExerciseProgressActions,
) {
    LazyColumn(
        modifier = Modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = SportDimens.SpacingXl),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        item { RangeChips(state.range, actions.onSelectRange) }
        when {
            treadmill.sessions.isEmpty() -> item { HintText(stringResource(R.string.treadmill_none_in_range)) }
            treadmill.missingWeight -> item { HintText(stringResource(R.string.treadmill_needs_weight)) }
            else -> {
                item { TreadmillSummaryCard(treadmill) }
                item { TreadmillChartCard(treadmill.sessions) }
            }
        }
        if (treadmill.sessions.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.exercise_progress_sessions),
                    modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
                )
            }
            items(treadmill.sessions.asReversed(), key = { "treadmill-${it.sessionId}" }) { session ->
                TreadmillSessionRow(
                    session = session,
                    isBest = session.sessionId == treadmill.bestSessionId && treadmill.sessions.size > 1,
                    onClick = { actions.onOpenSession(session.sessionId) },
                )
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
    )
}

@Composable
private fun TreadmillSummaryCard(treadmill: TreadmillCaloriesUi) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            Column {
                Text(
                    stringResource(R.string.treadmill_total_in_range),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Formatters.kcal(treadmill.totalKcal), style = MaterialTheme.typography.headlineMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingXl)) {
                treadmill.latest?.kcal?.let { StatValue(Formatters.kcal(it), stringResource(R.string.treadmill_latest)) }
                treadmill.averageKcal?.let { StatValue(Formatters.kcal(it), stringResource(R.string.treadmill_average)) }
                StatValue(treadmill.sessions.size.toString(), stringResource(R.string.treadmill_sessions))
            }
        }
    }
}

@Composable
private fun TreadmillChartCard(sessions: List<TreadmillSessionUi>) {
    val sessionColor = MaterialTheme.colorScheme.primary
    val cumulativeColor = MaterialTheme.colorScheme.secondary
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            Text(stringResource(R.string.treadmill_chart_title), style = MaterialTheme.typography.titleSmall)
            TreadmillCaloriesChart(sessions, sessionColor = sessionColor, cumulativeColor = cumulativeColor)
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingL)) {
                LegendSwatch(sessionColor, stringResource(R.string.treadmill_legend_session), round = false)
                LegendSwatch(cumulativeColor, stringResource(R.string.treadmill_legend_cumulative), round = true)
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
 * Calorie sessione per sessione: barre = kcal della sessione (asse sinistro), linea con punti =
 * totale cumulativo del periodo (asse destro). Tenendo premuto compare il dettaglio.
 */
@Composable
internal fun TreadmillCaloriesChart(
    sessions: List<TreadmillSessionUi>,
    sessionColor: Color,
    cumulativeColor: Color,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return
    val xs = sessions.indices.map { it.toDouble() }
    val perSession = sessions.map { (it.kcal ?: 0).toDouble() }
    val cumulative = sessions.map { it.cumulativeKcal.toDouble() }
    val sessionAxis = niceAxis(0.0, max(perSession.max(), 1.0))
    val cumulativeAxis = niceAxis(0.0, max(cumulative.max(), 1.0))
    val labelSpacing = max(1, (sessions.size / X_LABELS.toDouble()).roundToInt())
    val minX = if (sessions.size == 1) -1.0 else 0.0
    val maxX = if (sessions.size == 1) 1.0 else (sessions.size - 1).toDouble()

    val dates = sessions.map { Formatters.dayMonth(it.date) }
    val markerTexts = sessions.map { session ->
        val kcal = session.kcal?.let(Formatters::kcal) ?: "—"
        "${Formatters.shortDate(session.date)} · $kcal · ${Formatters.kcal(session.cumulativeKcal)}"
    }
    val currentDates by rememberUpdatedState(dates)
    val currentMarkers by rememberUpdatedState(markerTexts)
    val description = stringResource(
        R.string.treadmill_chart_description,
        sessions.size,
        sessions.last().kcal?.let(Formatters::kcal) ?: "—",
        Formatters.kcal(sessions.last().cumulativeKcal),
    )

    val model = remember(sessions) {
        CartesianChartModel(
            ColumnCartesianLayerModel.build { series(xs, perSession) },
            LineCartesianLayerModel.build { series(xs, cumulative) },
        )
    }
    val column = rememberLineComponent(
        fill = Fill(sessionColor),
        thickness = 14.dp,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
    )
    val line = LineCartesianLayer.rememberLine(
        fill = remember(cumulativeColor) { LineCartesianLayer.LineFill.single(Fill(cumulativeColor)) },
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.5.dp),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(Fill(cumulativeColor), CircleShape), size = 7.dp),
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
                rangeProvider = remember(sessionAxis, minX, maxX) { CartesianLayerRangeProvider.fixed(minX, maxX, sessionAxis.min, sessionAxis.max) },
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(line),
                rangeProvider = remember(cumulativeAxis, minX, maxX) { CartesianLayerRangeProvider.fixed(minX, maxX, cumulativeAxis.min, cumulativeAxis.max) },
                verticalAxisPosition = Axis.Position.Vertical.End,
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> Formatters.integer(value) } },
                itemPlacer = remember(sessionAxis) { VerticalAxis.ItemPlacer.step({ sessionAxis.step }) },
            ),
            endAxis = VerticalAxis.rememberEnd(
                // Una sola griglia (quella della sessione): due scale diverse darebbero linee sfalsate.
                guideline = null,
                valueFormatter = remember { CartesianValueFormatter { _, value, _ -> Formatters.integer(value) } },
                itemPlacer = remember(cumulativeAxis) { VerticalAxis.ItemPlacer.step({ cumulativeAxis.step }) },
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
private fun TreadmillSessionRow(session: TreadmillSessionUi, isBest: Boolean, onClick: () -> Unit) {
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
                    session.kcal?.let(Formatters::kcal) ?: stringResource(R.string.treadmill_session_no_estimate),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.treadmill_session_cumulative, Formatters.kcal(session.cumulativeKcal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isBest) {
                Icon(
                    Icons.Outlined.LocalFireDepartment,
                    contentDescription = stringResource(R.string.treadmill_best_session),
                    tint = SportTheme.extendedColors.record,
                )
            }
        }
    }
}

/** Etichette dell'asse x: circa cinque date. */
private const val X_LABELS = 5
