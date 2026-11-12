package com.example.sport.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.data.template.RoutineTemplate
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.ScreenHeader
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.format.Formatters
import java.time.DayOfWeek

@Composable
fun RoutinesScreen(
    onOpenRoutine: (Long) -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RoutinesEvent.TemplateCreated -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.routines_template_created, event.routineName),
                        actionLabel = context.getString(R.string.routines_open),
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenRoutine(event.routineId)
                }
                is RoutinesEvent.RoutineCreated -> onOpenRoutine(event.routineId)
            }
        }
    }
    val firstDayName = stringResource(R.string.builder_day_default, "A")
    Box(Modifier.fillMaxSize()) {
        RoutinesContent(
            state = state,
            onUseTemplate = viewModel::createFromTemplate,
            onSetActive = viewModel::setActive,
            onOpenRoutine = onOpenRoutine,
            onCreateRoutine = { name -> viewModel.createRoutine(name, firstDayName) },
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun RoutinesContent(
    state: RoutinesUiState,
    onUseTemplate: (RoutineTemplate) -> Unit,
    onSetActive: (Long) -> Unit,
    onOpenRoutine: (Long) -> Unit = {},
    onCreateRoutine: (String) -> Unit = {},
) {
    var showNewRoutine by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        RoutinesList(state, onUseTemplate, onSetActive, onOpenRoutine)
        ExtendedFloatingActionButton(
            onClick = { showNewRoutine = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(SportDimens.ScreenPadding),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(SportDimens.SpacingS))
            Text(stringResource(R.string.routines_new))
        }
    }
    if (showNewRoutine) {
        TextInputDialog(
            title = stringResource(R.string.routines_new),
            label = stringResource(R.string.routines_name_label),
            initial = stringResource(R.string.routines_default_name),
            onConfirm = onCreateRoutine,
            onDismiss = { showNewRoutine = false },
        )
    }
}

@Composable
private fun RoutinesList(
    state: RoutinesUiState,
    onUseTemplate: (RoutineTemplate) -> Unit,
    onSetActive: (Long) -> Unit,
    onOpenRoutine: (Long) -> Unit,
) {
    when (state) {
        RoutinesUiState.Loading -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = stringResource(R.string.nav_routines))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is RoutinesUiState.Content -> LazyColumn(
            // Spazio in fondo per il pulsante "Nuova scheda".
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            item { ScreenHeader(title = stringResource(R.string.nav_routines)) }
            if (state.routines.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.CalendarMonth,
                        title = stringResource(R.string.routines_empty_title),
                        message = stringResource(R.string.routines_empty_message),
                    )
                }
            } else {
                item { Header(stringResource(R.string.routines_my_routines)) }
                items(state.routines, key = { it.id }) { routine ->
                    RoutineCard(routine, onSetActive = { onSetActive(routine.id) }, onOpen = { onOpenRoutine(routine.id) })
                }
            }
            item { Header(stringResource(R.string.routines_templates_header)) }
            items(state.templates, key = { it.template.id }) { template ->
                TemplateCard(template, onUse = { onUseTemplate(template.template) })
            }
        }
    }
}

@Composable
private fun Header(title: String) {
    SectionHeader(title = title, modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding))
}

@Composable
private fun RoutineCard(routine: RoutineUi, onSetActive: () -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val openDescription = stringResource(R.string.routines_open_description, routine.name)
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SportDimens.ScreenPadding)
            .semantics { onClick(label = openDescription, action = null) },
        shape = MaterialTheme.shapes.large,
        color = if (routine.isActive) colors.primaryContainer else colors.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleLarge)
                    val splitLabel = stringResource(routine.splitLabelRes)
                    val daysLabel = pluralStringResource(R.plurals.routines_days_count, routine.dayNames.size, routine.dayNames.size)
                    Text(
                        // Le schede create da modello si chiamano come lo split: evita "PPL · PPL".
                        text = if (splitLabel.equals(routine.name, ignoreCase = true)) daysLabel else "$splitLabel · $daysLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (routine.isActive) {
                    Surface(shape = MaterialTheme.shapes.small, color = colors.primary) {
                        Text(
                            text = stringResource(R.string.routines_active_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimary,
                            modifier = Modifier.padding(horizontal = SportDimens.SpacingS, vertical = SportDimens.SpacingXs),
                        )
                    }
                }
            }
            routine.parentName?.let {
                Text(stringResource(R.string.routines_variant_of, it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text(routine.dayNames.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            WeekStrip(routine.scheduledDays)
            if (!routine.isActive) {
                TextButton(onClick = onSetActive) { Text(stringResource(R.string.routines_set_active)) }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: TemplateUi, onUse: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            Text(template.template.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(template.levelLabelRes) + " · " + pluralStringResource(
                    R.plurals.routines_days_per_week,
                    template.template.sessionsPerWeek,
                    template.template.sessionsPerWeek,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                template.template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(template.dayNames.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            WeekStrip(template.scheduledDays)
            SecondaryActionButton(
                text = stringResource(R.string.routines_template_use),
                onClick = onUse,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Calendario compatto L M M G V S D con i giorni di allenamento evidenziati. */
@Composable
private fun WeekStrip(scheduled: Set<DayOfWeek>) {
    val colors = MaterialTheme.colorScheme
    val description = DayOfWeek.entries.filter { it in scheduled }.joinToString { Formatters.dayOfWeek(it) }
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val active = day in scheduled
            Surface(shape = CircleShape, color = if (active) colors.primary else colors.surfaceContainerHigh) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = Formatters.dayOfWeekInitial(day),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) colors.onPrimary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
