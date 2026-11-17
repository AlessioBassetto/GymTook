package com.example.sport.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.ScreenHeader
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.component.StatValue
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.format.LocalWeightUnit

@Immutable
data class ProgressActions(
    val onWeighIn: () -> Unit,
    val onOpenBodyWeight: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onOpenSession: (Long) -> Unit,
    val onDeleteSession: (Long) -> Unit = {},
)

@Composable
fun ProgressScreen(
    onOpenBodyWeight: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entry by viewModel.entryEditor.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.weight_saved)
    LaunchedEffect(viewModel) {
        viewModel.weightSaved.collect { snackbarHostState.showSnackbar(savedMessage, duration = SnackbarDuration.Short) }
    }
    val deletedMessage = stringResource(R.string.session_deleted)
    LaunchedEffect(viewModel) {
        viewModel.sessionDeleted.collect { snackbarHostState.showSnackbar(deletedMessage, duration = SnackbarDuration.Short) }
    }
    ProgressContent(
        state = state,
        actions = ProgressActions(
            onWeighIn = viewModel::onWeighIn,
            onOpenBodyWeight = onOpenBodyWeight,
            onOpenExercise = onOpenExercise,
            onOpenSession = onOpenSession,
            onDeleteSession = viewModel::onDeleteSession,
        ),
        entry = entry,
        entryActions = remember(viewModel) { BodyWeightEntryActions.of(viewModel.entryEditor) },
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun ProgressContent(
    state: ProgressUiState,
    actions: ProgressActions,
    entry: BodyWeightEntryUi? = null,
    entryActions: BodyWeightEntryActions? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showAllTrends by rememberSaveable { mutableStateOf(false) }
    var showAllRecords by rememberSaveable { mutableStateOf(false) }
    var deletingSession by remember { mutableStateOf<SessionHistoryUi?>(null) }
    deletingSession?.let { session ->
        DestructiveConfirmDialog(
            title = stringResource(R.string.session_delete_title, Formatters.shortDate(session.date)),
            message = stringResource(R.string.session_delete_message),
            confirmLabel = stringResource(R.string.session_delete_confirm),
            onConfirm = {
                deletingSession = null
                actions.onDeleteSession(session.id)
            },
            onDismiss = { deletingSession = null },
        )
    }
    Box(Modifier.fillMaxSize()) {
        when (state) {
            ProgressUiState.Loading -> Column(Modifier.fillMaxSize()) {
                ScreenHeader(stringResource(R.string.nav_progress))
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is ProgressUiState.Content -> LazyColumn(
                contentPadding = PaddingValues(bottom = SportDimens.SpacingXl),
                verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
            ) {
                item { ScreenHeader(stringResource(R.string.nav_progress)) }
                item { BodyWeightCard(state.bodyWeight, actions) }

                item { SectionTitle(stringResource(R.string.progress_exercises)) }
                if (state.trends.isEmpty()) {
                    item { HintText(stringResource(R.string.progress_exercises_empty)) }
                } else {
                    expandableItems(
                        all = state.trends,
                        expanded = showAllTrends,
                        onToggle = { showAllTrends = !showAllTrends },
                        key = { "trend-${it.exerciseId}" },
                    ) { TrendRow(it, onClick = { actions.onOpenExercise(it.exerciseId) }) }
                }

                if (state.records.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.progress_records)) }
                    expandableItems(
                        all = state.records,
                        expanded = showAllRecords,
                        onToggle = { showAllRecords = !showAllRecords },
                        key = { "record-${it.exerciseId}" },
                    ) { RecordRow(it, onClick = { actions.onOpenExercise(it.exerciseId) }) }
                }

                item { SectionTitle(stringResource(R.string.progress_history)) }
                if (state.sessions.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Insights,
                            title = stringResource(R.string.progress_empty_title),
                            message = stringResource(R.string.progress_empty_message),
                        )
                    }
                } else {
                    state.calorieTotals?.let { totals -> item(key = "calorie-totals") { CalorieTotalsCard(totals) } }
                    items(state.sessions, key = { "session-${it.id}" }) { session ->
                        SessionCard(
                            session = session,
                            onClick = { actions.onOpenSession(session.id) },
                            onDelete = { deletingSession = session },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    if (entry != null && entryActions != null) BodyWeightEntrySheet(entry, entryActions)
}

/** Le prime [ProgressViewModel.PREVIEW_ROWS] righe, più "Mostra tutti (N)" / "Mostra meno". */
private fun <T> LazyListScope.expandableItems(
    all: List<T>,
    expanded: Boolean,
    onToggle: () -> Unit,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    val visible = if (expanded) all else all.take(ProgressViewModel.PREVIEW_ROWS)
    items(visible, key = key) { row(it) }
    if (all.size > ProgressViewModel.PREVIEW_ROWS) {
        item {
            TextButton(
                onClick = onToggle,
                modifier = Modifier
                    .padding(horizontal = SportDimens.SpacingS)
                    .heightIn(min = SportDimens.TouchTargetMin),
            ) {
                Text(
                    if (expanded) stringResource(R.string.progress_show_less) else stringResource(R.string.progress_show_all, all.size),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    SectionHeader(
        title,
        modifier = Modifier.padding(start = SportDimens.ScreenPadding, end = SportDimens.ScreenPadding, top = SportDimens.SpacingM),
    )
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
private fun BodyWeightCard(summary: WeightSummaryUi?, actions: ProgressActions) {
    val unit = LocalWeightUnit.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.progress_body_weight),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary == null) {
                        Text(stringResource(R.string.progress_body_weight_none), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(Formatters.bodyWeightKg(summary.latestKg, unit), style = SportNumberStyles.Field)
                        Text(
                            stringResource(R.string.progress_recorded_on, Formatters.shortDate(summary.latestDate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (summary != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        summary.averageKg?.let {
                            Text(
                                stringResource(R.string.progress_average_7d, Formatters.bodyWeightKg(it, unit)),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        summary.weeklyChange?.let { change ->
                            ChangeBadge(
                                delta = change,
                                text = stringResource(R.string.progress_weekly_change, Formatters.signedKg(change, unit)),
                                tone = ChangeTone.NEUTRAL,
                            )
                        }
                    }
                }
            }
            if (summary != null && summary.points.size >= 2) {
                BodyWeightChart(summary.points, height = 140.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS), verticalAlignment = Alignment.CenterVertically) {
                SecondaryActionButton(
                    text = stringResource(R.string.progress_weigh_in),
                    onClick = actions.onWeighIn,
                    icon = Icons.Filled.Add,
                    modifier = Modifier.weight(1f),
                )
                // Senza pesate la schermata del peso serve comunque per il profilo corporeo (altezza, età, sesso).
                TextButton(onClick = actions.onOpenBodyWeight, modifier = Modifier.heightIn(min = SportDimens.TouchTargetMin)) {
                    Text(stringResource(if (summary != null) R.string.progress_body_weight_open else R.string.body_profile_title))
                }
            }
        }
    }
}

@Composable
private fun TrendRow(trend: ExerciseTrendUi, onClick: () -> Unit) {
    val unit = LocalWeightUnit.current
    val openDescription = stringResource(R.string.progress_open_exercise, trend.name)
    ListCard(onClick = onClick, description = openDescription) {
        Column(Modifier.weight(1f)) {
            Text(trend.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.progress_sessions_count, trend.sessionCount, trend.sessionCount) +
                    " · " + stringResource(R.string.progress_last_performed, Formatters.dayMonth(trend.lastDate)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Colonna stretta (etichetta piccola sopra il valore): il nome dell'esercizio resta leggibile.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(R.string.progress_record_one_rep_max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(trend.latestOneRepMaxKg?.let { Formatters.estimateKg(it, unit) } ?: "—", style = SportNumberStyles.Compact)
            trend.oneRepMaxChangeKg?.let { change ->
                ChangeBadge(change, Formatters.signedKg(change, unit), ChangeTone.POSITIVE_IS_GOOD)
            }
        }
    }
}

@Composable
private fun RecordRow(record: ExerciseRecordUi, onClick: () -> Unit) {
    val unit = LocalWeightUnit.current
    val openDescription = stringResource(R.string.progress_open_exercise, record.name)
    ListCard(onClick = onClick, description = openDescription) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SportTheme.extendedColors.record)
        Spacer(Modifier.width(SportDimens.SpacingM))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatters.dayMonth(record.latestDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.progress_record_heaviest) + " " + Formatters.setLabel(record.heaviestWeightKg, record.heaviestReps, unit),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                listOfNotNull(
                    record.oneRepMaxKg?.let { stringResource(R.string.progress_one_rep_max, Formatters.estimateKg(it, unit)) },
                    stringResource(R.string.session_exercise_volume, Formatters.wholeKg(record.bestVolumeKg, unit)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionCard(session: SessionHistoryUi, onClick: () -> Unit, onDelete: () -> Unit) {
    val unit = LocalWeightUnit.current
    val description = stringResource(R.string.session_open_description, session.title, Formatters.shortDate(session.date))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .clickable(onClickLabel = description, onClick = onClick)
                .padding(start = SportDimens.CardPadding, bottom = SportDimens.CardPadding, top = SportDimens.SpacingXs),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    Formatters.shortDate(session.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Allenamento registrato per errore: si elimina dallo storico senza aprirlo.
                IconButton(onClick = onDelete, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.session_delete_description, session.title, Formatters.shortDate(session.date)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingL)) {
                StatValue(session.duration, stringResource(R.string.progress_duration))
                StatValue(session.sets.toString(), stringResource(R.string.progress_sets))
                StatValue(Formatters.weightKg(session.volumeKg, unit), stringResource(R.string.progress_volume))
                StatValue(session.caloriesBurned?.let(Formatters::kcal) ?: "—", stringResource(R.string.progress_calories))
            }
        }
    }
}

/** Riga toccabile a tutta larghezza (min 64dp) per esercizi e record. */
@Composable
private fun ListCard(onClick: () -> Unit, description: String, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = description, onClick = onClick)
                .heightIn(min = SportDimens.TouchTargetPrimary)
                .padding(horizontal = SportDimens.CardPadding, vertical = SportDimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
            content = content,
        )
    }
}

/** Totali calorici sopra lo storico: settimana in corso e ultimi 30 giorni. */
@Composable
private fun CalorieTotalsCard(totals: CalorieTotalsUi) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            Text(
                stringResource(R.string.calories_totals_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totals.hasEstimates) {
                Row(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingXl)) {
                    StatValue(Formatters.kcal(totals.weekKcal), stringResource(R.string.calories_this_week))
                    StatValue(Formatters.kcal(totals.monthKcal), stringResource(R.string.calories_last_30_days))
                }
            } else {
                Text(stringResource(R.string.calories_missing_weight), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
