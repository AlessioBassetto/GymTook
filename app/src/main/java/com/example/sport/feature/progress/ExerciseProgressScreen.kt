package com.example.sport.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.data.progress.RecordKind
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.ui.chart.ChartLine
import com.example.sport.core.ui.chart.ChartPoint
import com.example.sport.core.ui.chart.TrendChart
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.format.LocalWeightUnit
import java.time.LocalDate
import kotlin.math.roundToLong

@Immutable
data class ExerciseProgressActions(
    val onBack: () -> Unit,
    val onSelectMetric: (ProgressMetric) -> Unit,
    val onSelectRange: (TimeRange) -> Unit,
    val onOpenSession: (Long) -> Unit,
)

@Composable
fun ExerciseProgressScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: ExerciseProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseProgressContent(
        state = state,
        actions = ExerciseProgressActions(
            onBack = onBack,
            onSelectMetric = viewModel::onSelectMetric,
            onSelectRange = viewModel::onSelectRange,
            onOpenSession = onOpenSession,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseProgressContent(state: ExerciseProgressUiState, actions: ExerciseProgressActions) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    (state as? ExerciseProgressUiState.Content)?.name.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        when (state) {
            ExerciseProgressUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ExerciseProgressUiState.NotFound -> EmptyState(icon = Icons.Outlined.SearchOff, title = stringResource(R.string.exercise_not_found))
            is ExerciseProgressUiState.Content -> if (state.metric == null) {
                EmptyState(icon = Icons.Outlined.Insights, title = stringResource(R.string.exercise_progress_unsupported))
            } else {
                ExerciseProgressBody(state, state.metric, actions)
            }
        }
    }
}

@Composable
private fun ExerciseProgressBody(state: ExerciseProgressUiState.Content, metric: ProgressMetric, actions: ExerciseProgressActions) {
    LazyColumn(
        modifier = Modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = SportDimens.SpacingXl),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        if (state.metrics.size > 1) {
            item {
                ChoiceChips(
                    options = state.metrics,
                    selected = metric,
                    label = { stringResource(it.labelRes) },
                    onSelect = actions.onSelectMetric,
                )
            }
        }
        item { RangeChips(state.range, actions.onSelectRange) }
        item { MetricCard(state, metric) }
        if (state.records.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.exercise_progress_records),
                    modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
                )
            }
            items(state.records, key = { "record-${it.kind}" }) { record ->
                RecordTile(record, onClick = { actions.onOpenSession(record.sessionId) })
            }
        }
        if (state.sessions.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.exercise_progress_sessions),
                    modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
                )
            }
            items(state.sessions, key = { "session-${it.sessionId}" }) { session ->
                ExerciseSessionRow(session, onClick = { actions.onOpenSession(session.sessionId) })
            }
        }
    }
}

/** Valore della grandezza nel formato giusto: kg con decimale, volume intero, ripetizioni. */
@Composable
private fun metricValue(metric: ProgressMetric, value: Double, unit: WeightUnit = LocalWeightUnit.current): String = when (metric) {
    ProgressMetric.ONE_REP_MAX -> Formatters.estimateKg(value, unit)
    ProgressMetric.MAX_WEIGHT -> Formatters.weightKg(value, unit)
    ProgressMetric.VOLUME -> Formatters.wholeKg(value, unit)
    ProgressMetric.REPS -> stringResource(R.string.unit_reps, Formatters.integer(value))
}

/** Variazione con segno: "+5 kg", "−1.200 kg", "+12 rip.". */
@Composable
private fun metricChange(metric: ProgressMetric, delta: Double, unit: WeightUnit = LocalWeightUnit.current): String = when (metric) {
    ProgressMetric.REPS -> {
        val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
        stringResource(R.string.unit_reps, sign + Formatters.integer(kotlin.math.abs(delta)))
    }
    ProgressMetric.VOLUME -> {
        val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
        sign + Formatters.wholeKg(kotlin.math.abs(delta), unit)
    }
    else -> Formatters.signedKg(delta, unit)
}

@Composable
private fun MetricCard(state: ExerciseProgressUiState.Content, metric: ProgressMetric) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            if (state.points.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Insights,
                    title = stringResource(R.string.exercise_progress_empty_title),
                    message = stringResource(R.string.exercise_progress_empty_message),
                )
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingXl)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.exercise_progress_latest),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(metricValue(metric, state.latest ?: 0.0), style = SportNumberStyles.Field)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.exercise_progress_best),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(metricValue(metric, state.best ?: 0.0), style = SportNumberStyles.Compact)
                    state.change?.let { change ->
                        ChangeBadge(change, metricChange(metric, change), ChangeTone.POSITIVE_IS_GOOD)
                    }
                }
            }
            MetricChart(state.points, metric)
        }
    }
}

@Composable
private fun MetricChart(points: List<MetricPoint>, metric: ProgressMetric) {
    val unit = LocalWeightUnit.current
    val color = MaterialTheme.colorScheme.primary
    // Assi e valori premuti nell'unità mostrata (le ripetizioni non si convertono).
    val shownValue: (Double) -> Double = { if (metric == ProgressMetric.REPS) it else unit.fromKg(it) }
    val lines = remember(points, color, unit) {
        listOf(ChartLine(points.map { ChartPoint(it.date.toEpochDay().toDouble(), shownValue(it.value)) }, color, thickness = 3.dp, showPoints = true))
    }
    val metricLabel = stringResource(metric.labelRes)
    val formatted = points.map { metricValue(metric, it.value) }
    val repsUnit = stringResource(R.string.unit_reps, "%s")
    val markerFormat = stringResource(R.string.exercise_progress_marker)
    TrendChart(
        lines = lines,
        xLabel = epochDayLabel(spanDays(points.map { it.date })),
        yLabel = { value ->
            when (metric) {
                ProgressMetric.REPS -> Formatters.integer(value)
                ProgressMetric.VOLUME -> Formatters.integer(value)
                else -> Formatters.number(value)
            }
        },
        markerLabel = { x, ys ->
            val day = Formatters.shortDate(LocalDate.ofEpochDay(x.roundToLong()))
            val value = ys.firstOrNull()?.let { y ->
                when (metric) {
                    ProgressMetric.REPS -> repsUnit.format(Formatters.integer(y))
                    ProgressMetric.VOLUME -> "${Formatters.integer(y)} ${unit.symbol}"
                    ProgressMetric.ONE_REP_MAX, ProgressMetric.MAX_WEIGHT -> "${Formatters.oneDecimal(y)} ${unit.symbol}"
                }
            } ?: "—"
            markerFormat.format(day, value)
        },
        contentDescription = stringResource(R.string.exercise_progress_chart_description, metricLabel, formatted.first(), formatted.last()),
        height = 240.dp,
    )
}

@Composable
private fun RecordTile(record: RecordTileUi, onClick: () -> Unit) {
    val unit = LocalWeightUnit.current
    val (label, value) = when (record.kind) {
        RecordKind.HEAVIEST_SET -> stringResource(R.string.progress_record_heaviest) to
            Formatters.setLabel(record.weightKg ?: record.valueKg, record.reps ?: 0, unit)
        RecordKind.ESTIMATED_ONE_REP_MAX -> stringResource(R.string.progress_record_one_rep_max) to
            Formatters.estimateKg(record.valueKg, unit) + (record.weightKg?.let { w -> " (" + Formatters.setLabel(w, record.reps ?: 0, unit) + ")" } ?: "")
        RecordKind.SESSION_VOLUME -> stringResource(R.string.progress_record_volume) to Formatters.wholeKg(record.valueKg, unit)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.medium,
        color = SportTheme.extendedColors.recordContainer,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = SportDimens.TouchTargetPrimary)
                .padding(horizontal = SportDimens.CardPadding, vertical = SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SportTheme.extendedColors.record)
            Spacer(Modifier.width(SportDimens.SpacingM))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(value, style = SportNumberStyles.Compact)
            }
            Text(Formatters.shortDate(record.date), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ExerciseSessionRow(session: ExerciseSessionUi, onClick: () -> Unit) {
    val unit = LocalWeightUnit.current
    val details = listOfNotNull(
        session.maxWeightKg?.let { stringResource(R.string.metric_max_weight) + " " + Formatters.weightKg(it, unit) },
        session.oneRepMaxKg?.let { stringResource(R.string.progress_one_rep_max, Formatters.estimateKg(it, unit)) },
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = SportDimens.TouchTargetPrimary)
            .padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Formatters.longDate(session.date), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    R.string.exercise_progress_session_line,
                    session.setCount,
                    stringResource(R.string.unit_reps, session.totalReps.toString()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        session.volumeKg?.let { Text(Formatters.wholeKg(it, unit), style = SportNumberStyles.Compact) }
    }
}
