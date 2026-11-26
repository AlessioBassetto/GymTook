package com.example.sport.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.R
import com.example.sport.core.data.food.FoodRepository
import com.example.sport.core.data.food.Nutrients
import com.example.sport.core.data.food.RecipeIngredientDraft
import com.example.sport.core.data.food.per100g
import com.example.sport.core.data.food.recipePer100g
import com.example.sport.core.data.food.searchFoods
import com.example.sport.core.database.entity.FoodItemEntity
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.FoodSource
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import com.example.sport.navigation.FoodEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ingrediente nella ricetta in modifica: grammi come testo (si scrive liberamente). */
@Immutable
data class IngredientUi(
    val foodId: String?,
    val name: String,
    val gramsText: String,
    val per100g: Nutrients,
) {
    val grams: Double? get() = parseNumber(gramsText)
}

/** Modulo dell'editor: valori per 100 g (piatto) oppure ingredienti (ricetta). */
@Immutable
data class FoodEditorForm(
    val isRecipe: Boolean,
    val name: String = "",
    val category: FoodCategory = FoodCategory.OTHER,
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val portion: String = "100",
    val ingredients: List<IngredientUi> = emptyList(),
    val finalWeight: String = "",
) {
    /** Calorie scritte, oppure calcolate dai macronutrienti (4/4/9) se il campo è vuoto. */
    val computedKcal: Double? get() = parseNumber(kcal) ?: macros()?.let { (p, c, f) -> Nutrients.kcalFromMacros(p, c, f) }

    private fun macros(): Triple<Double, Double, Double>? {
        val values = listOf(protein, carbs, fat).map { if (it.isBlank()) 0.0 else parseNumber(it) }
        if (values.any { it == null } || listOf(protein, carbs, fat).all { it.isBlank() }) return null
        return Triple(values[0]!!, values[1]!!, values[2]!!)
    }

    /** Valori per 100 g della ricetta, ricalcolati a ogni grammo cambiato. */
    val recipePer100g: Nutrients get() = recipePer100g(drafts(), parseNumber(finalWeight))

    val recipeTotalGrams: Double get() = ingredients.sumOf { it.grams ?: 0.0 }

    fun drafts(): List<RecipeIngredientDraft> =
        ingredients.mapNotNull { item -> item.grams?.takeIf { it > 0 }?.let { RecipeIngredientDraft(item.foodId, item.name, it, item.per100g) } }

    val canSave: Boolean
        get() = name.isNotBlank() && if (isRecipe) {
            drafts().isNotEmpty() && (finalWeight.isBlank() || (parseNumber(finalWeight) ?: 0.0) > 0)
        } else {
            val kcalValue = computedKcal
            val macroValues = listOf(protein, carbs, fat).map { if (it.isBlank()) 0.0 else parseNumber(it) }
            kcalValue != null && kcalValue in 0.0..FoodRepository.MAX_KCAL_PER_100G &&
                macroValues.none { it == null } && macroValues.sumOf { it ?: 0.0 } <= 100.5 &&
                (parseNumber(portion) ?: 0.0) in 1.0..FoodRepository.MAX_GRAMS
        }
}

/** "12,5" o "12.5" → 12.5; vuoto o non numerico → null. */
internal fun parseNumber(text: String): Double? =
    text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

@Immutable
data class FoodEditorUiState(
    val form: FoodEditorForm,
    val isNew: Boolean,
    val ingredientQuery: String = "",
    val suggestions: List<FoodItemEntity> = emptyList(),
)

sealed interface FoodEditorEvent {
    data object Closed : FoodEditorEvent
}

@HiltViewModel
class FoodEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<FoodEditorRoute>()
    private val form = MutableStateFlow(FoodEditorForm(isRecipe = route.recipe))
    val ingredientQuery = MutableStateFlow("")
    private val foods = foodRepository.observeFoods().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<FoodEditorUiState> = combine(form, ingredientQuery, foods) { current, query, all ->
        FoodEditorUiState(
            form = current,
            isNew = route.foodId == null,
            ingredientQuery = query,
            suggestions = if (query.isBlank()) emptyList() else searchFoods(all, query).filter { it.id != route.foodId }.take(MAX_SUGGESTIONS),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodEditorUiState(form.value, isNew = route.foodId == null))

    private val eventChannel = Channel<FoodEditorEvent>(Channel.BUFFERED)
    val events: Flow<FoodEditorEvent> = eventChannel.receiveAsFlow()

    init {
        route.foodId?.let { id ->
            viewModelScope.launch {
                val food = foodRepository.food(id) ?: return@launch
                form.value = if (food.source == FoodSource.RECIPE) {
                    FoodEditorForm(
                        isRecipe = true,
                        name = food.name,
                        category = food.category,
                        ingredients = foodRepository.recipeIngredients(id).map {
                            IngredientUi(it.foodId, it.name, Formatters.editable(it.grams), it.per100g)
                        },
                        finalWeight = "",
                    )
                } else {
                    FoodEditorForm(
                        isRecipe = false,
                        name = food.name,
                        category = food.category,
                        kcal = Formatters.editable(roundOne(food.kcalPer100g)),
                        protein = Formatters.editable(roundOne(food.proteinPer100g)),
                        carbs = Formatters.editable(roundOne(food.carbsPer100g)),
                        fat = Formatters.editable(roundOne(food.fatPer100g)),
                        portion = Formatters.editable(food.defaultGrams),
                    )
                }
            }
        }
    }

    fun onFormChange(transform: (FoodEditorForm) -> FoodEditorForm) = form.update(transform)

    fun onAddIngredient(food: FoodItemEntity) {
        form.update {
            it.copy(ingredients = it.ingredients + IngredientUi(food.id, food.name, Formatters.editable(food.defaultGrams), food.per100g))
        }
        ingredientQuery.value = ""
    }

    fun onIngredientGrams(index: Int, text: String) = form.update { current ->
        current.copy(ingredients = current.ingredients.mapIndexed { i, item -> if (i == index) item.copy(gramsText = text) else item })
    }

    fun onRemoveIngredient(index: Int) = form.update { current ->
        current.copy(ingredients = current.ingredients.filterIndexed { i, _ -> i != index })
    }

    fun onSave() {
        val current = form.value
        if (!current.canSave) return
        viewModelScope.launch {
            if (current.isRecipe) {
                foodRepository.saveRecipe(route.foodId, current.name, current.category, current.drafts(), parseNumber(current.finalWeight))
            } else {
                foodRepository.saveCustomFood(
                    id = route.foodId,
                    name = current.name,
                    category = current.category,
                    kcalPer100g = parseNumber(current.kcal),
                    proteinPer100g = parseNumber(current.protein) ?: 0.0,
                    carbsPer100g = parseNumber(current.carbs) ?: 0.0,
                    fatPer100g = parseNumber(current.fat) ?: 0.0,
                    defaultGrams = parseNumber(current.portion) ?: 100.0,
                )
            }
            eventChannel.send(FoodEditorEvent.Closed)
        }
    }

    fun onDelete() {
        val id = route.foodId ?: return
        viewModelScope.launch {
            foodRepository.deleteUserFood(id)
            eventChannel.send(FoodEditorEvent.Closed)
        }
    }

    private companion object {
        const val MAX_SUGGESTIONS = 8
    }
}

@Immutable
data class FoodEditorActions(
    val onBack: () -> Unit = {},
    val onFormChange: ((FoodEditorForm) -> FoodEditorForm) -> Unit = {},
    val onIngredientQuery: (String) -> Unit = {},
    val onAddIngredient: (FoodItemEntity) -> Unit = {},
    val onIngredientGrams: (Int, String) -> Unit = { _, _ -> },
    val onRemoveIngredient: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

@Composable
fun FoodEditorScreen(onBack: () -> Unit, viewModel: FoodEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.events.collect { onBack() } }
    FoodEditorContent(
        state = state,
        actions = FoodEditorActions(
            onBack = onBack,
            onFormChange = viewModel::onFormChange,
            onIngredientQuery = { viewModel.ingredientQuery.value = it },
            onAddIngredient = viewModel::onAddIngredient,
            onIngredientGrams = viewModel::onIngredientGrams,
            onRemoveIngredient = viewModel::onRemoveIngredient,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FoodEditorContent(state: FoodEditorUiState, actions: FoodEditorActions) {
    val form = state.form
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        when {
                            form.isRecipe && state.isNew -> R.string.food_editor_new_recipe
                            form.isRecipe -> R.string.food_editor_edit_recipe
                            state.isNew -> R.string.food_editor_new_food
                            else -> R.string.food_editor_edit_food
                        },
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                TextButton(onClick = actions.onSave, enabled = form.canSave) { Text(stringResource(R.string.action_save)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
            verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { text -> actions.onFormChange { it.copy(name = text) } },
                label = { Text(stringResource(R.string.food_editor_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.food_editor_category), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                FoodCategory.entries.forEach { category ->
                    FilterChip(
                        selected = form.category == category,
                        onClick = { actions.onFormChange { it.copy(category = category) } },
                        label = { Text(stringResource(category.labelRes())) },
                    )
                }
            }
            if (form.isRecipe) RecipeFields(state, actions) else ValueFields(form, actions)
            if (!state.isNew) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(
                        stringResource(if (form.isRecipe) R.string.food_editor_delete_recipe else R.string.food_editor_delete_food),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Button(onClick = actions.onSave, enabled = form.canSave, modifier = Modifier.fillMaxWidth().heightIn(min = SportDimens.TouchTargetPrimary)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.food_editor_delete_title, form.name)) },
            text = { Text(stringResource(R.string.food_editor_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ValueFields(form: FoodEditorForm, actions: FoodEditorActions) {
    SectionHeader(stringResource(R.string.food_editor_values_per_100g))
    NumberInput(form.protein, stringResource(R.string.food_editor_protein)) { text -> actions.onFormChange { it.copy(protein = text) } }
    NumberInput(form.carbs, stringResource(R.string.food_editor_carbs)) { text -> actions.onFormChange { it.copy(carbs = text) } }
    NumberInput(form.fat, stringResource(R.string.food_editor_fat)) { text -> actions.onFormChange { it.copy(fat = text) } }
    NumberInput(form.kcal, stringResource(R.string.food_editor_kcal)) { text -> actions.onFormChange { it.copy(kcal = text) } }
    if (form.kcal.isBlank()) {
        form.computedKcal?.let {
            Text(
                stringResource(R.string.food_editor_kcal_computed, Formatters.integer(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    NumberInput(form.portion, stringResource(R.string.food_editor_portion)) { text -> actions.onFormChange { it.copy(portion = text) } }
}

@Composable
private fun RecipeFields(state: FoodEditorUiState, actions: FoodEditorActions) {
    val form = state.form
    SectionHeader(stringResource(R.string.food_editor_ingredients))
    form.ingredients.forEachIndexed { index, ingredient ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
            Column(Modifier.weight(1f)) {
                Text(ingredient.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (ingredient.grams?.let { ingredient.per100g.forGrams(it) } ?: Nutrients.ZERO).kcalText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = ingredient.gramsText,
                onValueChange = { actions.onIngredientGrams(index, it) },
                singleLine = true,
                suffix = { Text("g") },
                isError = ingredient.grams == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text(stringResource(R.string.food_grams_label)) },
                modifier = Modifier.width(120.dp),
            )
            IconButton(onClick = { actions.onRemoveIngredient(index) }, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.food_editor_remove_ingredient, ingredient.name))
            }
        }
    }
    OutlinedTextField(
        value = state.ingredientQuery,
        onValueChange = actions.onIngredientQuery,
        label = { Text(stringResource(R.string.food_editor_add_ingredient)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    state.suggestions.forEach { food ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.onAddIngredient(food) }
                    .heightIn(min = SportDimens.TouchTargetMin)
                    .padding(horizontal = SportDimens.SpacingM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.food_kcal_per_100g, Formatters.integer(food.kcalPer100g)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    NumberInput(form.finalWeight, stringResource(R.string.food_editor_final_weight)) { text -> actions.onFormChange { it.copy(finalWeight = text) } }
    Text(
        stringResource(R.string.food_editor_final_weight_hint, Formatters.grams(form.recipeTotalGrams)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (form.drafts().isNotEmpty()) {
        val per100 = form.recipePer100g
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(SportDimens.CardPadding)) {
                Text(stringResource(R.string.food_editor_recipe_per_100g), style = MaterialTheme.typography.labelLarge)
                Text(per100.kcalText(), style = MaterialTheme.typography.titleLarge)
                Text(macrosLine(per100), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NumberInput(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onChange(typed.filter { it.isDigit() || it == ',' || it == '.' }.take(7)) },
        label = { Text(label) },
        singleLine = true,
        isError = value.isNotBlank() && parseNumber(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** I valori mostrati nell'editor si arrotondano al decimo (una ricetta può dare 129,99999 kcal). */
private fun roundOne(value: Double): Double = Math.round(value * 10) / 10.0
