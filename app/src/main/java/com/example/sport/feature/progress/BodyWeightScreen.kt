package com.example.sport.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.data.progress.WeightTrendPoint
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.component.StatValue
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles
import com.example.sport.core.ui.chart.ChartLine
import com.example.sport.core.ui.chart.ChartPoint
import com.example.sport.core.ui.chart.TrendChart
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.format.LocalWeightUnit
import java.time.LocalDate
import kotlin.math.roundToLong

@Immutable
data class BodyWeightActions(
    val onBack: () -> Unit,
    val onSelectRange: (TimeRange) -> Unit,
    val onAdd: () -> Unit,
    val onEdit: (LocalDate) -> Unit,
    val onDelete: (LocalDate) -> Unit,
    val onEditProfile: () -> Unit = {},
)

@Composable
fun BodyWeightScreen(
    onBack: () -> Unit,
    viewModel: BodyWeightViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entry by viewModel.entryEditor.state.collectAsStateWithLifecycle()
    val profileEditorOpen by viewModel.profileEditorOpen.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.weight_saved)
    val deletedMessage = stringResource(R.string.weight_deleted)
    val undoLabel = stringResource(R.string.weight_undo)
    val profileSavedMessage = stringResource(R.string.body_profile_saved)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BodyWeightEvent.Saved -> snackbarHostState.showSnackbar(savedMessage, duration = SnackbarDuration.Short)
                is BodyWeightEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete(event.entry)
                }
                BodyWeightEvent.ProfileSaved -> snackbarHostState.showSnackbar(profileSavedMessage, duration = SnackbarDuration.Short)
            }
        }
    }
    BodyWeightContent(
        state = state,
        actions = BodyWeightActions(
            onBack = onBack,
            onSelectRange = viewModel::onSelectRange,
            onAdd = viewModel::onAdd,
            onEdit = viewModel::onEdit,
            onDelete = viewModel::onDelete,
            onEditProfile = viewModel::onEditProfile,
        ),
        entry = entry,
        entryActions = remember(viewModel) { BodyWeightEntryActions.of(viewModel.entryEditor) },
        snackbarHostState = snackbarHostState,
    )
    if (profileEditorOpen) {
        BodyProfileDialog(
            profile = (state as? BodyWeightUiState.Content)?.profile ?: BodyProfileUi(),
            birthYears = viewModel.birthYears,
            onSave = viewModel::onSaveProfile,
            onDismiss = viewModel::onDismissProfile,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyWeightContent(
    state: BodyWeightUiState,
    actions: BodyWeightActions,
    entry: BodyWeightEntryUi? = null,
    entryActions: BodyWeightEntryActions? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_body_weight)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            when (state) {
                BodyWeightUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is BodyWeightUiState.Content -> if (state.hasAnyEntry) {
                    BodyWeightBody(state, actions)
                } else {
                    // Il profilo corporeo resta raggiungibile anche prima della prima pesata.
                    BodyProfileCard(state.profile, actions.onEditProfile, Modifier.padding(top = SportDimens.SpacingS))
                    EmptyState(
                        icon = Icons.Outlined.MonitorWeight,
                        title = stringResource(R.string.weight_empty_title),
                        message = stringResource(R.string.weight_empty_message),
                        action = {
                            PrimaryActionButton(
                                text = stringResource(R.string.progress_weigh_in),
                                onClick = actions.onAdd,
                                icon = Icons.Filled.Add,
                            )
                        },
                    )
                }
            }
        }
        if ((state as? BodyWeightUiState.Content)?.hasAnyEntry == true) {
            // Overload a contenuto: con `text`/`icon` l'etichetta non arriva all'accessibilità.
            ExtendedFloatingActionButton(
                onClick = actions.onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(SportDimens.ScreenPadding)
                    .heightIn(min = SportDimens.TouchTargetMin),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(SportDimens.SpacingS))
                Text(stringResource(R.string.progress_weigh_in))
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 80.dp),
        )
    }
    if (entry != null && entryActions != null) BodyWeightEntrySheet(entry, entryActions)
}

@Composable
private fun BodyWeightBody(state: BodyWeightUiState.Content, actions: BodyWeightActions) {
    LazyColumn(
        contentPadding = FabClearance,
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        item { WeightStats(state) }
        item { BodyProfileCard(state.profile, actions.onEditProfile) }
        item { RangeChips(state.range, actions.onSelectRange) }
        item { WeightChartCard(state) }
        if (state.points.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.weight_entries),
                    modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
                )
            }
            items(state.points.asReversed(), key = { it.date.toEpochDay() }) { point ->
                WeightEntryRow(point, onEdit = { actions.onEdit(point.date) }, onDelete = { actions.onDelete(point.date) })
            }
        }
    }
}

@Composable
private fun WeightStats(state: BodyWeightUiState.Content) {
    val unit = LocalWeightUnit.current
    val latest = state.latest ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            Column {
                Text(
                    stringResource(R.string.weight_latest),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Formatters.bodyWeightKg(latest.weightKg, unit), style = SportNumberStyles.Field)
                Text(
                    stringResource(R.string.progress_recorded_on, Formatters.shortDate(latest.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingXl)) {
                StatValue(
                    value = state.latestAverageKg?.let { Formatters.bodyWeightKg(it, unit) } ?: "—",
                    label = stringResource(R.string.weight_legend_average),
                )
                Column {
                    val weekly = state.weeklyChange
                    if (weekly != null) {
                        ChangeBadge(weekly, Formatters.signedKg(weekly, unit), ChangeTone.NEUTRAL)
                    } else {
                        Text("—", style = SportNumberStyles.Compact)
                    }
                    Text(
                        stringResource(R.string.weight_week),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    val period = state.periodChange
                    if (period != null) {
                        ChangeBadge(period, Formatters.signedKg(period, unit), ChangeTone.NEUTRAL)
                    } else {
                        Text("—", style = SportNumberStyles.Compact)
                    }
                    Text(
                        stringResource(R.string.weight_change_period),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightChartCard(state: BodyWeightUiState.Content) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            if (state.points.isEmpty()) {
                Text(
                    stringResource(R.string.weight_none_in_range),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = SportDimens.SpacingXl),
                )
            } else {
                BodyWeightChart(state.points, height = 240.dp)
                ChartLegend()
            }
        }
    }
}

/** Pesate giornaliere (punti tenui) e media a 7 giorni (linea piena): la media è il dato da seguire. */
@Composable
internal fun BodyWeightChart(points: List<WeightTrendPoint>, height: Dp, modifier: Modifier = Modifier) {
    val unit = LocalWeightUnit.current
    val daily = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val average = MaterialTheme.colorScheme.primary
    // Il grafico lavora nell'unità mostrata: assi e valori premuti sono già in kg o lb.
    val lines = remember(points, daily, average, unit) {
        listOf(
            ChartLine(points.map { ChartPoint(it.date.toEpochDay().toDouble(), unit.fromKg(it.weightKg)) }, daily, thickness = 1.dp, showPoints = true),
            ChartLine(points.map { ChartPoint(it.date.toEpochDay().toDouble(), unit.fromKg(it.averageKg)) }, average, thickness = 3.dp),
        )
    }
    val shown: (Double) -> String = { "${Formatters.oneDecimal(it)} ${unit.symbol}" }
    val markerSingle = stringResource(R.string.weight_marker)
    val markerAverage = stringResource(R.string.weight_marker_average)
    val description = stringResource(
        R.string.progress_chart_weight_description,
        Formatters.bodyWeightKg(points.first().weightKg, unit),
        Formatters.bodyWeightKg(points.last().weightKg, unit),
        Formatters.bodyWeightKg(points.last().averageKg, unit),
    )
    TrendChart(
        lines = lines,
        xLabel = epochDayLabel(spanDays(points.map { it.date })),
        yLabel = { Formatters.oneDecimal(it) },
        markerLabel = { x, ys ->
            val day = Formatters.shortDate(LocalDate.ofEpochDay(x.roundToLong()))
            if (ys.size >= 2) {
                markerAverage.format(day, shown(ys[0]), shown(ys[1]))
            } else {
                markerSingle.format(day, ys.firstOrNull()?.let(shown) ?: "—")
            }
        },
        contentDescription = description,
        modifier = modifier,
        height = height,
    )
}

@Composable
private fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingL), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), stringResource(R.string.weight_legend_daily), dot = true)
        LegendItem(MaterialTheme.colorScheme.primary, stringResource(R.string.weight_legend_average), dot = false)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dot: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = color,
            shape = if (dot) CircleShape else MaterialTheme.shapes.extraSmall,
            modifier = if (dot) Modifier.size(8.dp) else Modifier.size(width = 16.dp, height = 3.dp),
        ) {}
        Spacer(Modifier.width(SportDimens.SpacingS))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeightEntryRow(point: WeightTrendPoint, onEdit: () -> Unit, onDelete: () -> Unit) {
    val unit = LocalWeightUnit.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .heightIn(min = SportDimens.TouchTargetPrimary)
            .padding(start = SportDimens.ScreenPadding, end = SportDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Formatters.longDate(point.date), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.progress_average_7d, Formatters.bodyWeightKg(point.averageKg, unit)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Formatters.bodyWeightKg(point.weightKg, unit), style = SportNumberStyles.Row)
        Spacer(Modifier.width(SportDimens.SpacingXs))
        IconButton(onClick = onDelete, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.weight_delete, Formatters.shortDate(point.date)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
