package com.example.sport.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.component.ScreenHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.Equipment
import com.example.sport.core.model.ExerciseCategory
import com.example.sport.core.model.Muscle
import com.example.sport.core.model.MuscleGroup
import com.example.sport.core.ui.exercise.ExerciseListItem
import com.example.sport.core.ui.labels.labelRes

/** Azioni della libreria (separate dal ViewModel per i test di interfaccia). */
internal data class LibraryActions(
    val onQueryChange: (String) -> Unit,
    val onGroupSelected: (MuscleGroup?) -> Unit,
    val onToggleFavorites: () -> Unit,
    val onToggleEquipment: (Equipment) -> Unit,
    val onToggleCategory: (ExerciseCategory) -> Unit,
    val onToggleMuscle: (Muscle) -> Unit,
    val onIncludeSecondaryChange: (Boolean) -> Unit,
    val onOnlyCustomChange: (Boolean) -> Unit,
    val onClearAdvanced: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onCreateExercise: () -> Unit,
)

@Composable
fun ExerciseListScreen(
    onOpenExercise: (String) -> Unit,
    onCreateExercise: () -> Unit,
    viewModel: ExerciseListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    ExerciseListContent(
        state = state,
        query = query,
        filters = filters,
        actions = LibraryActions(
            onQueryChange = viewModel::onQueryChange,
            onGroupSelected = viewModel::onGroupSelected,
            onToggleFavorites = viewModel::onToggleFavorites,
            onToggleEquipment = viewModel::onToggleEquipment,
            onToggleCategory = viewModel::onToggleCategory,
            onToggleMuscle = viewModel::onToggleMuscle,
            onIncludeSecondaryChange = viewModel::onIncludeSecondaryChange,
            onOnlyCustomChange = viewModel::onOnlyCustomChange,
            onClearAdvanced = viewModel::onClearAdvanced,
            onOpenExercise = onOpenExercise,
            onCreateExercise = onCreateExercise,
        ),
    )
}

@Composable
internal fun ExerciseListContent(
    state: ExerciseListUiState,
    query: String,
    filters: LibraryFilters,
    actions: LibraryActions,
) {
    val focusManager = LocalFocusManager.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.nav_exercises),
                subtitle = if (state.isLoading) null else {
                    pluralStringResource(R.plurals.exercises_count, state.exercises.size, state.exercises.size)
                },
            )
            OutlinedTextField(
                value = query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SportDimens.ScreenPadding)
                    .heightIn(min = SportDimens.TouchTargetMin),
                placeholder = { Text(stringResource(R.string.exercises_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.exercises_clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                // I risultati si aggiornano mentre si digita: "Cerca" serve solo a chiudere la tastiera.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            QuickFilterRow(filters, actions, onOpenFilters = { showFilters = true })
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.exercises.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.exercises_empty_title),
                    message = stringResource(R.string.exercises_empty_message),
                )
                else -> LazyColumn(
                    // Spazio in fondo per non coprire l'ultima riga con il pulsante "Nuovo esercizio".
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
                ) {
                    items(state.exercises, key = { it.id }) { exercise ->
                        ExerciseListItem(exercise, onClick = { actions.onOpenExercise(exercise.id) })
                    }
                }
            }
        }
        // Variante con contenuto esplicito: sempre estesa, l'etichetta è visibile anche durante lo scroll.
        ExtendedFloatingActionButton(
            onClick = actions.onCreateExercise,
            modifier = Modifier.align(Alignment.BottomEnd).padding(SportDimens.ScreenPadding),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(SportDimens.SpacingS))
            Text(stringResource(R.string.exercises_new))
        }
    }
    if (showFilters) {
        FilterSheet(
            filters = filters,
            resultCount = state.exercises.size,
            actions = actions,
            onDismiss = { showFilters = false },
        )
    }
}

/** Riga di chip: pannello filtri, preferiti e gruppo muscolare a portata di pollice. */
@Composable
private fun QuickFilterRow(filters: LibraryFilters, actions: LibraryActions, onOpenFilters: () -> Unit) {
    LazyRow(
        modifier = Modifier.testTag(QUICK_FILTERS_TAG),
        contentPadding = PaddingValues(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
        horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        item {
            FilterChip(
                selected = filters.advancedCount > 0,
                onClick = onOpenFilters,
                label = {
                    Text(
                        if (filters.advancedCount > 0) {
                            stringResource(R.string.exercises_filters_count, filters.advancedCount)
                        } else {
                            stringResource(R.string.exercises_filters)
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.padding(2.dp)) },
            )
        }
        item {
            FilterChip(
                selected = filters.onlyFavorites,
                onClick = actions.onToggleFavorites,
                label = { Text(stringResource(R.string.exercises_favorites)) },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.padding(2.dp)) },
            )
        }
        item {
            FilterChip(
                selected = filters.group == null,
                onClick = { actions.onGroupSelected(null) },
                label = { Text(stringResource(R.string.exercises_filter_all)) },
            )
        }
        items(MuscleGroup.entries) { group ->
            FilterChip(
                selected = group == filters.group,
                onClick = { actions.onGroupSelected(if (group == filters.group) null else group) },
                label = { Text(stringResource(group.labelRes())) },
            )
        }
    }
}

internal const val QUICK_FILTERS_TAG = "quick_filters"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: LibraryFilters,
    resultCount: Int,
    actions: LibraryActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SportDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.exercises_filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = actions.onClearAdvanced, enabled = filters.advancedCount > 0) {
                        Text(stringResource(R.string.exercises_filters_reset))
                    }
                }
                FilterSection(stringResource(R.string.exercises_filter_equipment)) {
                    Equipment.entries.forEach { equipment ->
                        SheetChip(stringResource(equipment.labelRes()), equipment in filters.equipment) { actions.onToggleEquipment(equipment) }
                    }
                }
                FilterSection(stringResource(R.string.exercises_filter_category)) {
                    ExerciseCategory.entries.forEach { category ->
                        SheetChip(stringResource(category.labelRes()), category in filters.categories) { actions.onToggleCategory(category) }
                    }
                }
                FilterSection(stringResource(R.string.exercises_filter_muscles)) {
                    Muscle.entries.forEach { muscle ->
                        SheetChip(stringResource(muscle.labelRes()), muscle in filters.muscles) { actions.onToggleMuscle(muscle) }
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.exercises_filter_include_secondary),
                    subtitle = stringResource(R.string.exercises_filter_include_secondary_hint),
                    checked = filters.includeSecondary,
                    onCheckedChange = actions.onIncludeSecondaryChange,
                )
                SwitchRow(
                    title = stringResource(R.string.exercises_filter_only_custom),
                    subtitle = null,
                    checked = filters.onlyCustom,
                    onCheckedChange = actions.onOnlyCustomChange,
                )
            }
            PrimaryActionButton(
                text = pluralStringResource(R.plurals.exercises_show_results, resultCount, resultCount),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(SportDimens.ScreenPadding),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
        ) { content() }
    }
}

@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // Tutta la riga è toccabile: bersaglio grande, l'interruttore è solo un indicatore.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SportDimens.TouchTargetMin)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
