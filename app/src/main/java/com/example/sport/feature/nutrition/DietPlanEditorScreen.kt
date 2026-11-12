package com.example.sport.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sport.R
import com.example.sport.core.data.DietTargets
import com.example.sport.core.data.MealItemInput
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.component.StatValue
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.MealType
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import java.time.LocalTime

@Immutable
data class DietPlanEditorActions(
    val onBack: () -> Unit,
    val onRename: (String) -> Unit,
    val onTargets: (DietTargets) -> Unit,
    val onNotes: (String) -> Unit,
    val onSetActive: () -> Unit,
    val onDuplicate: (String) -> Unit,
    val onDelete: () -> Unit,
    val onAddMeal: (MealType, String, LocalTime?) -> Unit,
    val onUpdateMeal: (Long, MealType, String, LocalTime?) -> Unit,
    val onMoveMeal: (Long, Int) -> Unit,
    val onDeleteMeal: (Long) -> Unit,
    val onAddItem: (Long, MealItemInput) -> Unit,
    val onUpdateItem: (Long, MealItemInput) -> Unit,
    val onDeleteItem: (Long) -> Unit,
)

/** Finestra aperta sopra l'editor (al massimo una alla volta). */
private sealed interface EditorOverlay {
    data object Rename : EditorOverlay
    data object Targets : EditorOverlay
    data object Notes : EditorOverlay
    data object Duplicate : EditorOverlay
    data object DeletePlan : EditorOverlay
    data object NewMeal : EditorOverlay
    data class EditMeal(val meal: EditorMealUi) : EditorOverlay
    data class DeleteMeal(val meal: EditorMealUi) : EditorOverlay
    data class NewItem(val mealId: Long) : EditorOverlay
    data class EditItem(val item: EditorItemUi) : EditorOverlay
}

@Composable
fun DietPlanEditorScreen(
    onBack: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    viewModel: DietPlanEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DietPlanEditorEvent.OpenPlan -> onOpenPlan(event.planId)
                DietPlanEditorEvent.Deleted -> onBack()
            }
        }
    }
    DietPlanEditorContent(
        state = state,
        actions = DietPlanEditorActions(
            onBack = onBack,
            onRename = viewModel::onRename,
            onTargets = viewModel::onTargets,
            onNotes = viewModel::onNotes,
            onSetActive = viewModel::onSetActive,
            onDuplicate = viewModel::onDuplicate,
            onDelete = viewModel::onDelete,
            onAddMeal = viewModel::onAddMeal,
            onUpdateMeal = viewModel::onUpdateMeal,
            onMoveMeal = viewModel::onMoveMeal,
            onDeleteMeal = viewModel::onDeleteMeal,
            onAddItem = viewModel::onAddItem,
            onUpdateItem = viewModel::onUpdateItem,
            onDeleteItem = viewModel::onDeleteItem,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DietPlanEditorContent(state: DietPlanEditorUiState, actions: DietPlanEditorActions) {
    // Le finestre aperte non sopravvivono a una rotazione: contengono riferimenti a righe del piano.
    var overlay by remember { mutableStateOf<EditorOverlay?>(null) }
    val content = state as? DietPlanEditorUiState.Content
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(content?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                if (content != null) {
                    IconButton(onClick = { overlay = EditorOverlay.Rename }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.diet_rename))
                    }
                    PlanMenu(content.isActive, actions.onSetActive, onOverlay = { overlay = it })
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        when (state) {
            DietPlanEditorUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            DietPlanEditorUiState.NotFound -> EmptyState(icon = Icons.Outlined.Restaurant, title = stringResource(R.string.diet_not_found))
            is DietPlanEditorUiState.Content -> EditorBody(state, actions, onOverlay = { overlay = it })
        }
    }
    if (content != null) {
        overlay?.let { current -> EditorOverlayHost(current, content, actions, onDismiss = { overlay = null }) }
    }
}

@Composable
private fun PlanMenu(isActive: Boolean, onSetActive: () -> Unit, onOverlay: (EditorOverlay) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.diet_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!isActive) {
                DropdownMenuItem(text = { Text(stringResource(R.string.diet_set_active)) }, onClick = {
                    expanded = false
                    onSetActive()
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.diet_duplicate)) }, onClick = {
                expanded = false
                onOverlay(EditorOverlay.Duplicate)
            })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.diet_delete_plan), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onOverlay(EditorOverlay.DeletePlan)
                },
            )
        }
    }
}

@Composable
private fun EditorBody(state: DietPlanEditorUiState.Content, actions: DietPlanEditorActions, onOverlay: (EditorOverlay) -> Unit) {
    LazyColumn(
        modifier = Modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(start = SportDimens.ScreenPadding, end = SportDimens.ScreenPadding, bottom = SportDimens.SpacingXl),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        item { ActiveBanner(state.isActive, actions.onSetActive) }
        item { TargetsCard(state, onClick = { onOverlay(EditorOverlay.Targets) }) }
        items(state.meals, key = { it.id }) { meal -> MealEditorCard(meal, actions, onOverlay) }
        item {
            SecondaryActionButton(
                text = stringResource(R.string.diet_add_meal),
                onClick = { onOverlay(EditorOverlay.NewMeal) },
                icon = Icons.Filled.Add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { NotesCard(state.notes, onClick = { onOverlay(EditorOverlay.Notes) }) }
    }
}

@Composable
private fun ActiveBanner(isActive: Boolean, onSetActive: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = SportDimens.TouchTargetMin).padding(start = SportDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (isActive) R.string.diet_active_hint else R.string.diet_inactive_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = SportDimens.SpacingS),
            )
            if (!isActive) {
                TextButton(onClick = onSetActive, modifier = Modifier.heightIn(min = SportDimens.TouchTargetMin)) {
                    Text(stringResource(R.string.diet_set_active))
                }
            }
        }
    }
}

/** Obiettivi e totale del piano a confronto: si vede subito se mancano proteine o avanzano calorie. */
@Composable
private fun TargetsCard(state: DietPlanEditorUiState.Content, onClick: () -> Unit) {
    val targets = state.targets
    val hasTargets = targets.caloriesKcal != null || targets.proteinG != null || targets.carbsG != null || targets.fatG != null
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.diet_targets_edit), onClick = onClick)
                .padding(SportDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.diet_targets), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasTargets) {
                MacroRow(
                    label = stringResource(R.string.diet_target_short),
                    kcal = targets.caloriesKcal?.toDouble(),
                    protein = targets.proteinG,
                    carbs = targets.carbsG,
                    fat = targets.fatG,
                )
            } else {
                Text(
                    stringResource(R.string.diet_targets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MacroRow(
                label = stringResource(R.string.diet_plan_total),
                kcal = state.totals.caloriesKcal,
                protein = state.totals.proteinG,
                carbs = state.totals.carbsG,
                fat = state.totals.fatG,
            )
        }
    }
}

@Composable
private fun MacroRow(label: String, kcal: Double?, protein: Double?, carbs: Double?, fat: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Colonne di larghezza fissa: obiettivo e totale del piano restano incolonnati.
        Row(modifier = Modifier.fillMaxWidth()) {
            StatValue(kcal?.let(Formatters::integer) ?: "—", "kcal", Modifier.weight(1f))
            StatValue(protein?.let(Formatters::integer) ?: "—", stringResource(R.string.nutrition_protein), Modifier.weight(1f))
            StatValue(carbs?.let(Formatters::integer) ?: "—", stringResource(R.string.nutrition_carbs), Modifier.weight(1f))
            StatValue(fat?.let(Formatters::integer) ?: "—", stringResource(R.string.nutrition_fat), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MealEditorCard(meal: EditorMealUi, actions: DietPlanEditorActions, onOverlay: (EditorOverlay) -> Unit) {
    val title = mealTitle(meal)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = SportDimens.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = SportDimens.SpacingS)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        macrosLine(meal.totals.caloriesKcal, meal.totals.proteinG, meal.totals.carbsG, meal.totals.fatG),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MealMenu(meal, title, actions, onOverlay)
            }
            if (meal.items.isEmpty()) {
                Text(
                    stringResource(R.string.diet_meal_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SportDimens.CardPadding),
                )
            }
            meal.items.forEach { item -> ItemRow(item, onClick = { onOverlay(EditorOverlay.EditItem(item)) }) }
            TextButton(
                onClick = { onOverlay(EditorOverlay.NewItem(meal.id)) },
                modifier = Modifier.padding(horizontal = SportDimens.SpacingS).heightIn(min = SportDimens.TouchTargetMin),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.diet_add_item), modifier = Modifier.padding(start = SportDimens.SpacingS))
            }
        }
    }
}

@Composable
private fun mealTitle(meal: EditorMealUi): String =
    listOfNotNull(meal.customName ?: stringResource(meal.type.labelRes()), meal.time?.let(Formatters::time)).joinToString(" · ")

@Composable
private fun macrosLine(kcal: Double?, protein: Double?, carbs: Double?, fat: Double?): String = stringResource(
    R.string.diet_macros_line,
    Formatters.integer(kcal ?: 0.0),
    Formatters.integer(protein ?: 0.0),
    Formatters.integer(carbs ?: 0.0),
    Formatters.integer(fat ?: 0.0),
)

@Composable
private fun MealMenu(meal: EditorMealUi, title: String, actions: DietPlanEditorActions, onOverlay: (EditorOverlay) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.diet_meal_options, title))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.diet_meal_edit)) }, onClick = {
                expanded = false
                onOverlay(EditorOverlay.EditMeal(meal))
            })
            if (!meal.isFirst) {
                DropdownMenuItem(text = { Text(stringResource(R.string.diet_meal_move_up)) }, onClick = {
                    expanded = false
                    actions.onMoveMeal(meal.id, -1)
                })
            }
            if (!meal.isLast) {
                DropdownMenuItem(text = { Text(stringResource(R.string.diet_meal_move_down)) }, onClick = {
                    expanded = false
                    actions.onMoveMeal(meal.id, 1)
                })
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.diet_meal_delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onOverlay(EditorOverlay.DeleteMeal(meal))
                },
            )
        }
    }
}

@Composable
private fun ItemRow(item: EditorItemUi, onClick: () -> Unit) {
    val input = item.input
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.diet_item_edit), onClick = onClick)
            .heightIn(min = SportDimens.TouchTargetMin)
            .padding(horizontal = SportDimens.CardPadding, vertical = SportDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(input.name, quantityLabel(input)).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                macrosLine(input.caloriesKcal, input.proteinG, input.carbsG, input.fatG),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            input.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun quantityLabel(input: MealItemInput): String? =
    input.quantity?.let { Formatters.number(it) + (input.unit?.let { unit -> " $unit" } ?: "") }

@Composable
private fun NotesCard(notes: String?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
        SectionHeader(stringResource(R.string.diet_notes))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(
                notes ?: stringResource(R.string.diet_notes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = if (notes == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .heightIn(min = SportDimens.TouchTargetMin)
                    .padding(SportDimens.CardPadding),
            )
        }
    }
}

@Composable
private fun EditorOverlayHost(
    overlay: EditorOverlay,
    plan: DietPlanEditorUiState.Content,
    actions: DietPlanEditorActions,
    onDismiss: () -> Unit,
) {
    when (overlay) {
        EditorOverlay.Rename -> PlanNameDialog(
            title = stringResource(R.string.diet_rename),
            initial = plan.name,
            confirmLabel = stringResource(R.string.diet_save),
            onConfirm = actions.onRename,
            onDismiss = onDismiss,
        )
        EditorOverlay.Duplicate -> PlanNameDialog(
            title = stringResource(R.string.diet_duplicate_title),
            initial = stringResource(R.string.diet_copy_name, plan.name),
            confirmLabel = stringResource(R.string.diet_duplicate),
            onConfirm = actions.onDuplicate,
            onDismiss = onDismiss,
        )
        EditorOverlay.Targets -> TargetsDialog(plan.targets, actions.onTargets, onDismiss)
        EditorOverlay.Notes -> NotesDialog(plan.notes, actions.onNotes, onDismiss)
        EditorOverlay.NewMeal -> MealDialog(
            title = stringResource(R.string.diet_meal_new),
            initialType = MealType.OTHER,
            initialName = null,
            initialTime = null,
            onConfirm = actions.onAddMeal,
            onDismiss = onDismiss,
        )
        is EditorOverlay.EditMeal -> MealDialog(
            title = stringResource(R.string.diet_meal_edit),
            initialType = overlay.meal.type,
            initialName = overlay.meal.customName,
            initialTime = overlay.meal.time,
            onConfirm = { type, name, time -> actions.onUpdateMeal(overlay.meal.id, type, name, time) },
            onDismiss = onDismiss,
        )
        is EditorOverlay.DeleteMeal -> ConfirmDialog(
            title = stringResource(R.string.diet_delete_title, overlay.meal.customName ?: stringResource(overlay.meal.type.labelRes())),
            message = stringResource(R.string.diet_meal_delete_message),
            onConfirm = { actions.onDeleteMeal(overlay.meal.id) },
            onDismiss = onDismiss,
        )
        EditorOverlay.DeletePlan -> ConfirmDialog(
            title = stringResource(R.string.diet_delete_title, plan.name),
            message = stringResource(R.string.diet_delete_plan_message),
            onConfirm = actions.onDelete,
            onDismiss = onDismiss,
        )
        is EditorOverlay.NewItem -> MealItemDialog(
            title = stringResource(R.string.diet_item_new),
            initial = null,
            onConfirm = { actions.onAddItem(overlay.mealId, it) },
            onDismiss = onDismiss,
        )
        is EditorOverlay.EditItem -> MealItemDialog(
            title = stringResource(R.string.diet_item_edit),
            initial = overlay.item.input,
            onConfirm = { actions.onUpdateItem(overlay.item.id, it) },
            onDismiss = onDismiss,
            onDelete = { actions.onDeleteItem(overlay.item.id) },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.diet_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.diet_cancel)) } },
    )
}
