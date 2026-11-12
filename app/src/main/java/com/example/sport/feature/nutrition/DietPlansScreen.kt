package com.example.sport.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.sport.R
import com.example.sport.core.data.DietPlanRepository
import com.example.sport.core.designsystem.component.EmptyState
import com.example.sport.core.designsystem.component.PrimaryActionButton
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.format.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class DietPlanRowUi(val id: Long, val name: String, val isActive: Boolean, val targetKcal: Int?)

sealed interface DietPlansUiState {
    data object Loading : DietPlansUiState
    data class Content(val plans: List<DietPlanRowUi>) : DietPlansUiState
}

@HiltViewModel
class DietPlansViewModel @Inject constructor(
    private val repository: DietPlanRepository,
) : ViewModel() {

    val uiState: StateFlow<DietPlansUiState> = repository.observePlans()
        .map { plans -> DietPlansUiState.Content(plans.map { DietPlanRowUi(it.id, it.name, it.isActive, it.targetCaloriesKcal) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietPlansUiState.Loading)

    private val openPlanChannel = Channel<Long>(Channel.BUFFERED)

    /** Piano appena creato: si apre subito il suo editor. */
    val openPlan: Flow<Long> = openPlanChannel.receiveAsFlow()

    fun onCreate(name: String) {
        viewModelScope.launch { openPlanChannel.send(repository.createPlan(name)) }
    }
}

@Composable
fun DietPlansScreen(
    onBack: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    viewModel: DietPlansViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openPlan.collect(onOpenPlan) }
    DietPlansContent(state, onBack, onOpenPlan, viewModel::onCreate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DietPlansContent(
    state: DietPlansUiState,
    onBack: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.diet_plans)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            when (state) {
                DietPlansUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is DietPlansUiState.Content -> if (state.plans.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Restaurant,
                        title = stringResource(R.string.diet_plans_empty_title),
                        message = stringResource(R.string.diet_plans_empty_message),
                        action = {
                            PrimaryActionButton(text = stringResource(R.string.diet_create_plan), onClick = { creating = true }, icon = Icons.Filled.Add)
                        },
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = SportDimens.ScreenPadding, end = SportDimens.ScreenPadding, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
                    ) {
                        items(state.plans, key = { it.id }) { plan -> PlanRow(plan, onClick = { onOpenPlan(plan.id) }) }
                    }
                }
            }
        }
        if ((state as? DietPlansUiState.Content)?.plans?.isNotEmpty() == true) {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(SportDimens.ScreenPadding),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(SportDimens.SpacingS))
                Text(stringResource(R.string.diet_new_plan))
            }
        }
    }
    if (creating) {
        PlanNameDialog(
            title = stringResource(R.string.diet_new_plan),
            initial = stringResource(R.string.diet_plan_default_name),
            confirmLabel = stringResource(R.string.diet_create),
            onConfirm = onCreate,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun PlanRow(plan: DietPlanRowUi, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (plan.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = SportDimens.TouchTargetPrimary)
                .padding(horizontal = SportDimens.CardPadding, vertical = SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium)
                plan.targetKcal?.let {
                    Text(
                        "${Formatters.integer(it.toDouble())} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (plan.isActive) {
                Text(
                    stringResource(R.string.diet_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
