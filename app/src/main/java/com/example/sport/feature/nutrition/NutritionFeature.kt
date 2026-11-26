package com.example.sport.feature.nutrition

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.sport.R
import com.example.sport.core.data.DietPlanRepository
import com.example.sport.core.data.NutritionDay
import com.example.sport.core.data.NutritionRepository
import com.example.sport.core.data.food.DIARY_MEALS
import com.example.sport.core.data.food.FoodRepository
import com.example.sport.core.data.food.Nutrients
import com.example.sport.core.data.food.nutrients
import com.example.sport.core.data.food.per100g
import com.example.sport.core.data.food.sum
import com.example.sport.core.database.entity.MealEntryEntity
import com.example.sport.core.designsystem.component.ScreenHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.MealType
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

@Immutable
data class MacroUi(
    @StringRes val labelRes: Int,
    val consumed: Double,
    val target: Double?,
    val unit: String,
) {
    val progress: Float get() = target?.takeIf { it > 0 }?.let { (consumed / it).toFloat().coerceIn(0f, 1f) } ?: 0f
}

/** Piatto nel diario: le calorie si ricalcolano dai grammi. */
@Immutable
data class DiaryEntryUi(
    val id: Long,
    val name: String,
    val brand: String?,
    val grams: Double,
    val mealType: MealType,
    val per100g: Nutrients,
) {
    val nutrients: Nutrients get() = per100g.forGrams(grams)
}

/** Pasto del piano alimentare attivo, da spuntare come prima. */
@Immutable
data class PlanMealUi(
    val id: Long,
    val customName: String?,
    @StringRes val typeLabelRes: Int,
    val time: String?,
    val items: String,
    val macros: String,
    val consumed: Boolean,
)

/** Un pasto della giornata: piatti del diario, pasti del piano e totale calorico. */
@Immutable
data class DiaryMealUi(
    val type: MealType,
    val entries: List<DiaryEntryUi>,
    val planMeals: List<PlanMealUi>,
    val kcal: Int,
)

sealed interface NutritionUiState {
    data object Loading : NutritionUiState

    data class Content(
        val date: LocalDate,
        val today: LocalDate,
        /** Piano attivo (obiettivi e pasti da spuntare); null = solo diario. */
        val planId: Long?,
        val planName: String?,
        val macros: List<MacroUi>,
        val meals: List<DiaryMealUi>,
    ) : NutritionUiState {
        val isToday: Boolean get() = date == today
    }
}

sealed interface NutritionEvent {
    /** Piatto eliminato con un tocco: la barra in basso offre "Annulla". */
    data class EntryDeleted(val entry: MealEntryEntity) : NutritionEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val dietPlanRepository: DietPlanRepository,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val date = MutableStateFlow(foodRepository.today())

    val uiState: StateFlow<NutritionUiState> = date.flatMapLatest { day ->
        combine(nutritionRepository.observeDay(day), foodRepository.observeDiary(day)) { plan, entries ->
            buildState(day, plan, entries)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionUiState.Loading)

    private val _editing = MutableStateFlow<DiaryEntryUi?>(null)

    /** Voce del diario aperta nel pannello grammi. */
    val editing: StateFlow<DiaryEntryUi?> = _editing.asStateFlow()

    private val eventChannel = Channel<NutritionEvent>(Channel.BUFFERED)
    val events: Flow<NutritionEvent> = eventChannel.receiveAsFlow()

    private val openPlanChannel = Channel<Long>(Channel.BUFFERED)

    /** Piano appena creato: si apre il suo editor. */
    val openPlan: Flow<Long> = openPlanChannel.receiveAsFlow()

    fun selectedDate(): LocalDate = date.value

    fun onPreviousDay() {
        date.value = date.value.minusDays(1)
    }

    fun onNextDay() {
        date.value = date.value.plusDays(1)
    }

    fun onToday() {
        date.value = foodRepository.today()
    }

    fun onCreatePlan(name: String) {
        viewModelScope.launch { openPlanChannel.send(dietPlanRepository.createPlan(name)) }
    }

    fun setConsumed(mealId: Long, consumed: Boolean) {
        viewModelScope.launch { nutritionRepository.setMealConsumed(mealId, consumed, date.value) }
    }

    fun onEditEntry(entry: DiaryEntryUi) {
        _editing.value = entry
    }

    fun onDismissEdit() {
        _editing.value = null
    }

    fun onSaveEntry(grams: Double, mealType: MealType) {
        val entry = _editing.value ?: return
        _editing.value = null
        viewModelScope.launch { foodRepository.updateEntry(entry.id, grams, mealType) }
    }

    fun onDeleteEntry(id: Long) {
        if (_editing.value?.id == id) _editing.value = null
        viewModelScope.launch {
            foodRepository.deleteEntry(id)?.let { eventChannel.send(NutritionEvent.EntryDeleted(it)) }
        }
    }

    fun onUndoDelete(entry: MealEntryEntity) {
        viewModelScope.launch { foodRepository.restoreEntry(entry) }
    }

    private fun buildState(day: LocalDate, plan: NutritionDay?, entries: List<MealEntryEntity>): NutritionUiState.Content {
        val diary = entries.map { it.toUi() }
        val planMeals = plan?.plan?.orderedMeals.orEmpty().map { meal ->
            meal.meal.mealType to PlanMealUi(
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
                consumed = meal.meal.id in plan?.consumedMealIds.orEmpty(),
            )
        }
        // I cinque pasti sempre; "Altro pasto" solo se il piano o il diario lo usano.
        val types = DIARY_MEALS + listOf(MealType.OTHER).filter { other ->
            diary.any { it.mealType == other } || planMeals.any { it.first == other }
        }
        val meals = types.map { type ->
            val typeEntries = diary.filter { it.mealType == type }
            val typePlan = planMeals.filter { it.first == type }.map { it.second }
            val planKcal = plan?.plan?.meals.orEmpty()
                .filter { it.meal.mealType == type && it.meal.id in plan?.consumedMealIds.orEmpty() }
                .sumOf { it.totalCaloriesKcal }
            DiaryMealUi(
                type = type,
                entries = typeEntries,
                planMeals = typePlan,
                kcal = (typeEntries.map { it.nutrients }.sum().kcal + planKcal).roundToInt(),
            )
        }
        // Totali del giorno: piatti del diario + pasti del piano spuntati.
        val diaryTotal = diary.map { it.nutrients }.sum()
        val target = plan?.plan?.plan
        return NutritionUiState.Content(
            date = day,
            today = foodRepository.today(),
            planId = target?.id,
            planName = target?.name,
            macros = listOf(
                MacroUi(R.string.nutrition_calories, diaryTotal.kcal + (plan?.consumedCaloriesKcal ?: 0.0), target?.targetCaloriesKcal?.toDouble(), "kcal"),
                MacroUi(R.string.nutrition_protein, diaryTotal.proteinG + (plan?.consumedProteinG ?: 0.0), target?.targetProteinG, "g"),
                MacroUi(R.string.nutrition_carbs, diaryTotal.carbsG + (plan?.consumedCarbsG ?: 0.0), target?.targetCarbsG, "g"),
                MacroUi(R.string.nutrition_fat, diaryTotal.fatG + (plan?.consumedFatG ?: 0.0), target?.targetFatG, "g"),
            ),
            meals = meals,
        )
    }

    private fun MealEntryEntity.toUi() = DiaryEntryUi(
        id = id,
        name = name,
        brand = brand,
        grams = grams,
        mealType = mealType,
        per100g = per100g,
    )
}

/** Azioni della schermata Dieta (una sola classe: i test le sostituiscono in blocco). */
@Immutable
data class NutritionActions(
    val onPreviousDay: () -> Unit = {},
    val onNextDay: () -> Unit = {},
    val onToday: () -> Unit = {},
    val onAddFood: (MealType) -> Unit = {},
    val onEditEntry: (DiaryEntryUi) -> Unit = {},
    val onDeleteEntry: (Long) -> Unit = {},
    val onSetConsumed: (Long, Boolean) -> Unit = { _, _ -> },
    val onOpenPlans: () -> Unit = {},
    val onEditPlan: (Long) -> Unit = {},
    val onCreatePlan: (String) -> Unit = {},
)

@Composable
fun NutritionScreen(
    onOpenPlans: () -> Unit = {},
    onEditPlan: (Long) -> Unit = {},
    onAddFood: (LocalDate, MealType) -> Unit = { _, _ -> },
    viewModel: NutritionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.food_entry_deleted)
    val undoLabel = stringResource(R.string.weight_undo)
    LaunchedEffect(viewModel) { viewModel.openPlan.collect(onEditPlan) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NutritionEvent.EntryDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        "$deletedMessage: ${event.entry.name}",
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete(event.entry)
                }
            }
        }
    }
    NutritionContent(
        state = state,
        actions = NutritionActions(
            onPreviousDay = viewModel::onPreviousDay,
            onNextDay = viewModel::onNextDay,
            onToday = viewModel::onToday,
            onAddFood = { meal -> onAddFood(viewModel.selectedDate(), meal) },
            onEditEntry = viewModel::onEditEntry,
            onDeleteEntry = viewModel::onDeleteEntry,
            onSetConsumed = viewModel::setConsumed,
            onOpenPlans = onOpenPlans,
            onEditPlan = onEditPlan,
            onCreatePlan = viewModel::onCreatePlan,
        ),
        snackbarHostState = snackbarHostState,
    )
    editing?.let { entry ->
        GramsSheet(
            target = GramsTarget(entry.name, entry.brand, entry.per100g, entry.grams, entry.mealType, isEdit = true),
            onConfirm = viewModel::onSaveEntry,
            onDismiss = viewModel::onDismissEdit,
            onDelete = { viewModel.onDeleteEntry(entry.id) },
        )
    }
}

@Composable
internal fun NutritionContent(
    state: NutritionUiState,
    actions: NutritionActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        when (state) {
            NutritionUiState.Loading -> Column(Modifier.fillMaxSize()) {
                ScreenHeader(stringResource(R.string.nav_nutrition))
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is NutritionUiState.Content -> LazyColumn(
                contentPadding = PaddingValues(bottom = SportDimens.SpacingXl),
                verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
            ) {
                item {
                    ScreenHeader(
                        title = stringResource(R.string.nav_nutrition),
                        subtitle = state.planName,
                        actions = {
                            if (state.planId != null) {
                                IconButton(onClick = { actions.onEditPlan(state.planId) }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.diet_edit_plan))
                                }
                            }
                            IconButton(onClick = actions.onOpenPlans, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                                Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = stringResource(R.string.diet_plans_open))
                            }
                        },
                    )
                }
                item { DateBar(state, actions) }
                item { MacroSummary(state.macros, hasTargets = state.planId != null, onSetTargets = { creating = true }) }
                items(state.meals, key = { it.type.name }) { meal -> MealSection(meal, actions) }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (creating) {
        PlanNameDialog(
            title = stringResource(R.string.diet_new_plan),
            initial = stringResource(R.string.diet_plan_default_name),
            confirmLabel = stringResource(R.string.diet_create),
            onConfirm = actions.onCreatePlan,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun DateBar(state: NutritionUiState.Content, actions: NutritionActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onPreviousDay, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.diary_previous_day))
        }
        Text(
            text = if (state.isToday) stringResource(R.string.nutrition_today, Formatters.longDate(state.date)) else Formatters.longDate(state.date),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (!state.isToday) {
            TextButton(onClick = actions.onToday) { Text(stringResource(R.string.diary_today)) }
        }
        IconButton(onClick = actions.onNextDay, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.diary_next_day))
        }
    }
}

@Composable
private fun MacroSummary(macros: List<MacroUi>, hasTargets: Boolean, onSetTargets: () -> Unit) {
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
                            text = macro.target?.let { target ->
                                stringResource(R.string.nutrition_progress, Formatters.integer(macro.consumed), Formatters.integer(target) + " " + macro.unit)
                            } ?: (Formatters.integer(macro.consumed) + " " + macro.unit),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (hasTargets) {
                        LinearProgressIndicator(
                            progress = { macro.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
            }
            if (!hasTargets) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.diary_no_targets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSetTargets) { Text(stringResource(R.string.diary_set_targets)) }
                }
            }
        }
    }
}

@Composable
private fun MealSection(meal: DiaryMealUi, actions: NutritionActions) {
    val mealName = stringResource(meal.type.labelRes())
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = SportDimens.SpacingS)) {
            Row(
                modifier = Modifier.padding(horizontal = SportDimens.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(mealName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Formatters.kcal(meal.kcal), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            meal.entries.forEach { entry -> DiaryEntryRow(entry, actions) }
            meal.planMeals.forEach { planMeal ->
                HorizontalDivider(Modifier.padding(horizontal = SportDimens.CardPadding))
                PlanMealRow(planMeal, onToggle = { actions.onSetConsumed(planMeal.id, !planMeal.consumed) })
            }
            TextButton(
                onClick = { actions.onAddFood(meal.type) },
                modifier = Modifier.padding(horizontal = SportDimens.SpacingS),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.diary_add_to_meal, mealName))
            }
        }
    }
}

@Composable
private fun DiaryEntryRow(entry: DiaryEntryUi, actions: NutritionActions) {
    val nutrients = entry.nutrients
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { actions.onEditEntry(entry) }
            .heightIn(min = SportDimens.TouchTargetPrimary)
            .padding(start = SportDimens.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(entry.brand, Formatters.grams(entry.grams), macrosLine(nutrients)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Formatters.kcal(nutrients.kcal.roundToInt()), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { actions.onDeleteEntry(entry.id) }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.food_entry_delete_named, entry.name))
        }
    }
}

@Composable
private fun PlanMealRow(meal: PlanMealUi, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = SportDimens.CardPadding, top = SportDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
            Text(
                text = stringResource(
                    R.string.diary_from_plan,
                    listOfNotNull(meal.customName ?: stringResource(meal.typeLabelRes), meal.time).joinToString(" · "),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (meal.consumed) colors.primary else colors.onSurface,
            )
            if (meal.items.isNotEmpty()) Text(meal.items, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
