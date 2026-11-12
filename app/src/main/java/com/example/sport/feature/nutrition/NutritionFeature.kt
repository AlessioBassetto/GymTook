package com.example.sport.feature.nutrition

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.sport.R
import com.example.sport.core.data.DietPlanRepository
import com.example.sport.core.data.NutritionDay
import com.example.sport.core.data.NutritionRepository
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.component.SecondaryActionButton
import com.example.sport.core.designsystem.component.ScreenHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class MacroUi(
    @StringRes val labelRes: Int,
    val consumed: Double,
    val target: Double?,
    val unit: String,
) {
    val progress: Float get() = target?.takeIf { it > 0 }?.let { (consumed / it).toFloat().coerceIn(0f, 1f) } ?: 0f
}

@Immutable
data class MealUi(
    val id: Long,
    val customName: String?,
    @StringRes val typeLabelRes: Int,
    val time: String?,
    val items: String,
    val macros: String,
    val consumed: Boolean,
)

sealed interface NutritionUiState {
    data object Loading : NutritionUiState
    /** Nessun piano attivo; [hasPlans] = ce ne sono di non attivi da scegliere. */
    data class NoPlan(val hasPlans: Boolean) : NutritionUiState
    data class Content(
        val planId: Long,
        val dateLabel: String,
        val planName: String,
        val macros: List<MacroUi>,
        val meals: List<MealUi>,
    ) : NutritionUiState
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val dietPlanRepository: DietPlanRepository,
) : ViewModel() {

    val uiState: StateFlow<NutritionUiState> = combine(
        nutritionRepository.observeToday(),
        dietPlanRepository.observePlans(),
    ) { day, plans -> day?.toUiState() ?: NutritionUiState.NoPlan(hasPlans = plans.isNotEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionUiState.Loading)

    private val openPlanChannel = Channel<Long>(Channel.BUFFERED)

    /** Piano appena creato: si apre il suo editor. */
    val openPlan: Flow<Long> = openPlanChannel.receiveAsFlow()

    fun onCreatePlan(name: String) {
        viewModelScope.launch { openPlanChannel.send(dietPlanRepository.createPlan(name)) }
    }

    fun setConsumed(mealId: Long, consumed: Boolean) {
        viewModelScope.launch { nutritionRepository.setMealConsumed(mealId, consumed) }
    }

    private fun NutritionDay.toUiState(): NutritionUiState.Content {
        val target = plan.plan
        return NutritionUiState.Content(
            planId = target.id,
            dateLabel = Formatters.longDate(date),
            planName = target.name,
            macros = listOf(
                MacroUi(R.string.nutrition_calories, consumedCaloriesKcal, target.targetCaloriesKcal?.toDouble(), "kcal"),
                MacroUi(R.string.nutrition_protein, consumedProteinG, target.targetProteinG, "g"),
                MacroUi(R.string.nutrition_carbs, consumedCarbsG, target.targetCarbsG, "g"),
                MacroUi(R.string.nutrition_fat, consumedFatG, target.targetFatG, "g"),
            ),
            meals = plan.orderedMeals.map { meal ->
                MealUi(
                    id = meal.meal.id,
                    customName = meal.meal.name,
                    typeLabelRes = meal.meal.mealType.labelRes(),
                    time = meal.meal.timeOfDay?.let { "%02d:%02d".format(it.hour, it.minute) },
                    items = meal.orderedItems.joinToString(" · ") { item ->
                        listOfNotNull(item.name, item.quantity?.let { Formatters.number(it) + (item.unit?.let { u -> " $u" } ?: "") })
                            .joinToString(" ")
                    },
                    macros = listOf(meal.totalCaloriesKcal, meal.totalProteinG, meal.totalCarbsG, meal.totalFatG)
                        .map(Formatters::integer)
                        .let { (kcal, p, c, f) -> "$kcal kcal · P $p · C $c · G $f" },
                    consumed = meal.meal.id in consumedMealIds,
                )
            },
        )
    }
}

@Composable
fun NutritionScreen(
    onOpenPlans: () -> Unit = {},
    onEditPlan: (Long) -> Unit = {},
    viewModel: NutritionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openPlan.collect(onEditPlan) }
    NutritionContent(
        state = state,
        onSetConsumed = viewModel::setConsumed,
        onOpenPlans = onOpenPlans,
        onEditPlan = onEditPlan,
        onCreatePlan = viewModel::onCreatePlan,
    )
}

@Composable
internal fun NutritionContent(
    state: NutritionUiState,
    onSetConsumed: (Long, Boolean) -> Unit,
    onOpenPlans: () -> Unit = {},
    onEditPlan: (Long) -> Unit = {},
    onCreatePlan: (String) -> Unit = {},
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val plansAction: @Composable () -> Unit = {
        IconButton(onClick = onOpenPlans, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = stringResource(R.string.diet_plans_open))
        }
    }
    when (state) {
        NutritionUiState.Loading -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.nav_nutrition))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is NutritionUiState.NoPlan -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.nav_nutrition), actions = if (state.hasPlans) plansAction else null)
            EmptyState(
                icon = Icons.Outlined.Restaurant,
                title = stringResource(R.string.nutrition_empty_title),
                message = stringResource(R.string.nutrition_empty_message),
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                        PrimaryActionButton(text = stringResource(R.string.diet_create_plan), onClick = { creating = true }, icon = Icons.Filled.Add)
                        if (state.hasPlans) {
                            SecondaryActionButton(
                                text = stringResource(R.string.diet_plans_open),
                                onClick = onOpenPlans,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
            )
        }
        is NutritionUiState.Content -> LazyColumn(
            contentPadding = PaddingValues(bottom = SportDimens.SpacingXl),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.nav_nutrition),
                    subtitle = stringResource(R.string.nutrition_today, state.dateLabel) + " · " + state.planName,
                    actions = {
                        IconButton(onClick = { onEditPlan(state.planId) }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.diet_edit_plan))
                        }
                        plansAction()
                    },
                )
            }
            item { MacroSummary(state.macros) }
            items(state.meals, key = { it.id }) { meal ->
                MealCard(meal, onToggle = { onSetConsumed(meal.id, !meal.consumed) })
            }
        }
    }
    if (creating) {
        PlanNameDialog(
            title = stringResource(R.string.diet_new_plan),
            initial = stringResource(R.string.diet_plan_default_name),
            confirmLabel = stringResource(R.string.diet_create),
            onConfirm = onCreatePlan,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun MacroSummary(macros: List<MacroUi>) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(SportDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            macros.forEach { macro ->
                Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
                    Row {
                        Text(stringResource(macro.labelRes), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(
                                R.string.nutrition_progress,
                                Formatters.integer(macro.consumed),
                                macro.target?.let { Formatters.integer(it) + " " + macro.unit } ?: macro.unit,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { macro.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
    }
}

@Composable
private fun MealCard(meal: MealUi, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.medium,
        color = if (meal.consumed) colors.primaryContainer else colors.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = SportDimens.CardPadding, top = SportDimens.SpacingS, bottom = SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
                Text(
                    text = listOfNotNull(meal.customName ?: stringResource(meal.typeLabelRes), meal.time).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (meal.items.isNotEmpty()) {
                    Text(meal.items, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Text(meal.macros, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            IconToggleButton(
                checked = meal.consumed,
                onCheckedChange = {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onToggle()
                },
                modifier = Modifier.size(SportDimens.TouchTargetPrimary),
            ) {
                Icon(
                    imageVector = if (meal.consumed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = stringResource(
                        if (meal.consumed) R.string.nutrition_mark_not_consumed else R.string.nutrition_mark_consumed,
                    ),
                    tint = if (meal.consumed) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}
