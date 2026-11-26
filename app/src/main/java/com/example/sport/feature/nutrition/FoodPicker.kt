package com.example.sport.feature.nutrition

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.sport.R
import com.example.sport.core.data.food.FoodFilter
import com.example.sport.core.data.food.FoodRepository
import com.example.sport.core.data.food.Nutrients
import com.example.sport.core.data.food.OnlineFood
import com.example.sport.core.data.food.OpenFoodFactsClient
import com.example.sport.core.data.food.isEditable
import com.example.sport.core.data.food.nutrients
import com.example.sport.core.data.food.per100g
import com.example.sport.core.data.food.searchFoods
import com.example.sport.core.data.food.sum
import com.example.sport.core.database.entity.FoodItemEntity
import com.example.sport.core.designsystem.component.SectionHeader
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.media.NetworkMonitor
import com.example.sport.core.model.FoodCategory
import com.example.sport.core.model.FoodSource
import com.example.sport.core.model.MealType
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import com.example.sport.navigation.FoodPickerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** Riga del selettore: piatto del catalogo, dell'utente o prodotto salvato. */
@Immutable
data class FoodRowUi(
    val id: String,
    val name: String,
    val brand: String?,
    val category: FoodCategory,
    val source: FoodSource,
    val kcalPer100g: Double,
    val editable: Boolean,
)

/** Stato della ricerca su Open Food Facts (solo su richiesta). */
sealed interface OnlineSearchUi {
    data object Idle : OnlineSearchUi
    data object Loading : OnlineSearchUi
    data class Results(val query: String, val foods: List<OnlineFood>) : OnlineSearchUi
    data object Failed : OnlineSearchUi
}

@Immutable
data class FoodPickerUiState(
    val date: LocalDate,
    val mealType: MealType,
    val results: List<FoodRowUi> = emptyList(),
    /** Totale del pasto e del giorno, aggiornati a ogni piatto aggiunto. */
    val mealKcal: Int = 0,
    val dayKcal: Int = 0,
    val isOnline: Boolean = false,
    val online: OnlineSearchUi = OnlineSearchUi.Idle,
)

sealed interface FoodPickerEvent {
    data class Added(val name: String, val grams: Double, val mealType: MealType) : FoodPickerEvent
}

@HiltViewModel
class FoodPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<FoodPickerRoute>()
    private val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    private val defaultMeal: MealType = MealType.entries.firstOrNull { it.name == route.mealType } ?: MealType.LUNCH

    val query = MutableStateFlow("")
    val filter = MutableStateFlow<FoodFilter>(FoodFilter.All)
    private val online = MutableStateFlow<OnlineSearchUi>(OnlineSearchUi.Idle)
    private val foods = foodRepository.observeFoods().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selection = MutableStateFlow<GramsTarget?>(null)

    /** Piatto scelto, aperto nel pannello grammi. */
    val selection: StateFlow<GramsTarget?> = _selection.asStateFlow()
    private var selectedFood: FoodItemEntity? = null

    private val eventChannel = Channel<FoodPickerEvent>(Channel.BUFFERED)
    val events: Flow<FoodPickerEvent> = eventChannel.receiveAsFlow()

    private val results = combine(foods, query, filter) { all, text, chosen ->
        searchFoods(all, text, chosen).take(MAX_RESULTS).map { it.toRow() }
    }

    private val totals = foodRepository.observeDiary(date)

    val uiState: StateFlow<FoodPickerUiState> = combine(results, totals, networkMonitor.isOnline, online) { rows, entries, isOnline, onlineState ->
        FoodPickerUiState(
            date = date,
            mealType = defaultMeal,
            results = rows,
            mealKcal = entries.filter { it.mealType == defaultMeal }.map { it.nutrients }.sum().kcal.roundToInt(),
            dayKcal = entries.map { it.nutrients }.sum().kcal.roundToInt(),
            isOnline = isOnline,
            online = onlineState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodPickerUiState(date, defaultMeal))

    fun onQueryChange(text: String) {
        query.value = text
        // I risultati online valgono per la ricerca che li ha prodotti.
        if ((online.value as? OnlineSearchUi.Results)?.query != text.trim()) online.value = OnlineSearchUi.Idle
    }

    fun onFilterChange(value: FoodFilter) {
        filter.value = value
    }

    fun onSelectFood(id: String) {
        val food = foods.value.firstOrNull { it.id == id } ?: return
        open(food)
    }

    /** Ricerca su Open Food Facts: parte solo dal pulsante (limite di 10 ricerche al minuto). */
    fun onSearchOnline() {
        val text = query.value.trim()
        if (text.length < OpenFoodFactsClient.MIN_QUERY_LENGTH || online.value == OnlineSearchUi.Loading) return
        online.value = OnlineSearchUi.Loading
        viewModelScope.launch {
            online.value = try {
                OnlineSearchUi.Results(text, foodRepository.searchOnline(text))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IOException) {
                Log.w(TAG, "Ricerca su Open Food Facts non riuscita", error)
                OnlineSearchUi.Failed
            }
        }
    }

    /** Prodotto online scelto: si salva (resta disponibile offline) e si apre il pannello grammi. */
    fun onSelectOnline(food: OnlineFood) {
        viewModelScope.launch { open(foodRepository.saveOnlineFood(food)) }
    }

    fun onDismissSelection() {
        _selection.value = null
        selectedFood = null
    }

    fun onConfirm(grams: Double, mealType: MealType) {
        val food = selectedFood ?: return
        onDismissSelection()
        viewModelScope.launch {
            foodRepository.addEntry(date, mealType, food, grams)
            eventChannel.send(FoodPickerEvent.Added(food.name, grams, mealType))
        }
    }

    private fun open(food: FoodItemEntity) {
        selectedFood = food
        _selection.value = GramsTarget(food.name, food.brand, food.per100g, food.defaultGrams, defaultMeal)
    }

    private fun FoodItemEntity.toRow() = FoodRowUi(
        id = id,
        name = name,
        brand = brand,
        category = category,
        source = source,
        kcalPer100g = kcalPer100g,
        editable = isEditable,
    )

    private companion object {
        const val TAG = "FoodPicker"

        /** Liste lunghe non servono: chi cerca restringe con una parola o un filtro. */
        const val MAX_RESULTS = 300
    }
}

@Immutable
data class FoodPickerActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onFilterChange: (FoodFilter) -> Unit = {},
    val onSelectFood: (String) -> Unit = {},
    val onSearchOnline: () -> Unit = {},
    val onSelectOnline: (OnlineFood) -> Unit = {},
    val onNewFood: () -> Unit = {},
    val onNewRecipe: () -> Unit = {},
    val onEditFood: (String) -> Unit = {},
)

@Composable
fun FoodPickerScreen(
    onBack: () -> Unit,
    onNewFood: () -> Unit,
    onNewRecipe: () -> Unit,
    onEditFood: (String) -> Unit,
    viewModel: FoodPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val addedTemplate = stringResource(R.string.food_added)
    val mealNames = MealType.entries.associateWith { stringResource(it.labelRes()) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FoodPickerEvent.Added -> snackbarHostState.showSnackbar(
                    addedTemplate.format(event.name, Formatters.grams(event.grams), mealNames.getValue(event.mealType)),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
    FoodPickerContent(
        state = state,
        query = query,
        filter = filter,
        actions = FoodPickerActions(
            onBack = onBack,
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onSelectFood = viewModel::onSelectFood,
            onSearchOnline = viewModel::onSearchOnline,
            onSelectOnline = viewModel::onSelectOnline,
            onNewFood = onNewFood,
            onNewRecipe = onNewRecipe,
            onEditFood = onEditFood,
        ),
        snackbarHostState = snackbarHostState,
    )
    selection?.let { target ->
        GramsSheet(target = target, onConfirm = viewModel::onConfirm, onDismiss = viewModel::onDismissSelection)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodPickerContent(
    state: FoodPickerUiState,
    query: String,
    filter: FoodFilter,
    actions: FoodPickerActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val focusManager = LocalFocusManager.current
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val mealName = stringResource(state.mealType.labelRes())
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.food_picker_title, mealName), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.food_picker_totals, Formatters.kcal(state.mealKcal), Formatters.kcal(state.dayKcal)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.food_create_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.food_new_food)) },
                                onClick = {
                                    menuOpen = false
                                    actions.onNewFood()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.food_new_recipe)) },
                                onClick = {
                                    menuOpen = false
                                    actions.onNewRecipe()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            OutlinedTextField(
                value = query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SportDimens.ScreenPadding)
                    .heightIn(min = SportDimens.TouchTargetMin),
                placeholder = { Text(stringResource(R.string.food_search_hint)) },
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            FilterRow(filter, actions.onFilterChange)
            LazyColumn(contentPadding = PaddingValues(bottom = SportDimens.SpacingXl)) {
                items(state.results, key = { it.id }) { food ->
                    FoodRow(food, onClick = { actions.onSelectFood(food.id) }, onEdit = { actions.onEditFood(food.id) })
                }
                if (state.results.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.food_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(SportDimens.ScreenPadding),
                        )
                    }
                }
                item(key = "online") { OnlineSection(state, query, actions) }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FilterRow(selected: FoodFilter, onSelect: (FoodFilter) -> Unit) {
    val filters = listOf<FoodFilter>(FoodFilter.All, FoodFilter.Mine) + FoodCategory.entries.map { FoodFilter.Category(it) }
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
        horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        filters.forEach { filter ->
            val label = when (filter) {
                FoodFilter.All -> stringResource(R.string.food_filter_all)
                FoodFilter.Mine -> stringResource(R.string.food_filter_mine)
                is FoodFilter.Category -> stringResource(filter.category.labelRes())
            }
            FilterChip(selected = filter == selected, onClick = { onSelect(filter) }, label = { Text(label) })
        }
    }
}

@Composable
private fun FoodRow(food: FoodRowUi, onClick: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = SportDimens.TouchTargetPrimary)
            .padding(start = SportDimens.ScreenPadding, end = SportDimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = SportDimens.SpacingS)) {
            Text(food.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val origin = when (food.source) {
                FoodSource.CATALOG -> stringResource(food.category.labelRes())
                FoodSource.CUSTOM -> stringResource(R.string.food_source_custom)
                FoodSource.RECIPE -> stringResource(R.string.food_source_recipe)
                FoodSource.OPEN_FOOD_FACTS -> stringResource(R.string.food_source_off)
            }
            Text(
                listOfNotNull(food.brand, origin).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.food_kcal_per_100g, Formatters.integer(food.kcalPer100g)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (food.editable) {
            IconButton(onClick = onEdit, modifier = Modifier.size(SportDimens.TouchTargetMin)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.food_edit_named, food.name))
            }
        }
    }
}

/** In fondo ai risultati: il piatto non c'è? Si cerca su Open Food Facts (con rete) o se ne crea uno. */
@Composable
private fun OnlineSection(state: FoodPickerUiState, query: String, actions: FoodPickerActions) {
    val trimmed = query.trim()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = SportDimens.SpacingM),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        when (val online = state.online) {
            is OnlineSearchUi.Results -> {
                SectionHeader(
                    stringResource(R.string.food_online_results, online.query),
                    modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
                )
                if (online.foods.isEmpty()) {
                    HintText(stringResource(R.string.food_online_none))
                }
                online.foods.forEach { food -> OnlineFoodRow(food, onClick = { actions.onSelectOnline(food) }) }
                HintText(stringResource(R.string.food_online_attribution))
            }
            OnlineSearchUi.Loading -> Row(
                modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
            ) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(R.string.food_online_loading), style = MaterialTheme.typography.bodyMedium)
            }
            OnlineSearchUi.Failed -> HintText(stringResource(R.string.food_online_failed))
            OnlineSearchUi.Idle -> Unit
        }
        val canSearchOnline = state.isOnline && trimmed.length >= OpenFoodFactsClient.MIN_QUERY_LENGTH &&
            (state.online as? OnlineSearchUi.Results)?.query != trimmed && state.online != OnlineSearchUi.Loading
        if (canSearchOnline) {
            OutlinedButton(onClick = actions.onSearchOnline, modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding)) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                Text(stringResource(R.string.food_online_search, trimmed), modifier = Modifier.padding(start = SportDimens.SpacingS))
            }
        } else if (!state.isOnline && trimmed.length >= OpenFoodFactsClient.MIN_QUERY_LENGTH) {
            HintText(stringResource(R.string.food_online_offline))
        }
        Row(
            modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
        ) {
            OutlinedButton(onClick = actions.onNewFood) { Text(stringResource(R.string.food_new_food)) }
            OutlinedButton(onClick = actions.onNewRecipe) { Text(stringResource(R.string.food_new_recipe)) }
        }
    }
}

@Composable
private fun OnlineFoodRow(food: OnlineFood, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SportDimens.TouchTargetPrimary)
                .padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                food.brand?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.food_kcal_per_100g, Formatters.integer(food.per100g.kcal)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
    )
}

/** Valori di un piatto in una riga leggibile (usato anche dall'editor). */
internal fun Nutrients.kcalText(): String = Formatters.kcal(kcal.roundToInt())
