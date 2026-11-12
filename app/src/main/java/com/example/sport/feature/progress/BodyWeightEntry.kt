package com.example.sport.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sport.R
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.designsystem.component.LocalInlineDialogs
import com.example.sport.core.designsystem.component.NumericKeypad
import com.example.sport.core.designsystem.input.KeypadKey
import com.example.sport.core.designsystem.input.NumericInputConfig
import com.example.sport.core.designsystem.input.NumericInputState
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.ui.format.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Stato del foglio "Registra peso". */
@Immutable
data class BodyWeightEntryUi(
    val date: LocalDate,
    val today: LocalDate,
    val input: NumericInputState,
    /** Giorni proposti che hanno già una pesata (salvando la si sostituisce). */
    val recordedDates: Set<LocalDate>,
    /** Unità del valore digitato: si salva sempre in kg. */
    val unit: WeightUnit = WeightUnit.KG,
    /**
     * "Salva" premuto con un valore vuoto o fuori scala. L'errore compare solo allora: mentre si
     * digita "8" per arrivare a "80" il valore è momentaneamente fuori scala, ma non è uno sbaglio.
     */
    val showsRangeError: Boolean = false,
) {
    /** Gli ultimi sette giorni dal più recente, più il giorno scelto se è più vecchio. */
    val days: List<LocalDate>
        get() = ((0 until ENTRY_DAYS).map { today.minusDays(it.toLong()) } + date).distinct().sortedDescending()

    val value: Double? get() = input.value

    val isValid: Boolean
        get() = valueKg?.let { it in ProgressRepository.MIN_BODY_WEIGHT_KG..ProgressRepository.MAX_BODY_WEIGHT_KG } == true

    /** Valore digitato convertito in kg (come nel database). */
    val valueKg: Double? get() = value?.let(unit::toKg)

    val replacesExisting: Boolean get() = date in recordedDates

    companion object {
        const val ENTRY_DAYS = 7
    }
}

/**
 * Logica del foglio di pesata, condivisa dalla panoramica e dallo storico del peso: si apre già
 * compilato con l'ultima pesata (di solito si cambia solo il decimale) e su "oggi".
 */
class BodyWeightEntryEditor(
    private val repository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<BodyWeightEntryUi?>(null)
    val state: StateFlow<BodyWeightEntryUi?> = _state.asStateFlow()

    private val savedChannel = Channel<LocalDate>(Channel.BUFFERED)

    /** Giorno di ogni pesata salvata: le schermate mostrano la conferma. */
    val saved: Flow<LocalDate> = savedChannel.receiveAsFlow()

    /** Apre il foglio su [date] (null = oggi), compilato con la pesata di quel giorno o l'ultima. */
    fun open(date: LocalDate? = null) {
        scope.launch {
            val today = repository.today()
            val target = date?.coerceAtMost(today) ?: today
            val sameDay = repository.bodyWeightOn(target)
            val unit = settingsRepository.current().weightUnit
            val prefill = (sameDay ?: repository.latestBodyWeight())?.weightKg?.let(unit::fromKg)
            val recorded = repository.bodyWeightsBetween(today.minusDays(BodyWeightEntryUi.ENTRY_DAYS - 1L), today)
                .map { it.date }
                .toSet()
            _state.value = BodyWeightEntryUi(
                date = target,
                today = today,
                input = NumericInputState.of(prefill, bodyWeightConfig(unit)),
                recordedDates = recorded + listOfNotNull(sameDay?.date),
                unit = unit,
            )
        }
    }

    fun onKey(key: KeypadKey) {
        _state.update { current -> current?.copy(input = current.input.reduce(key), showsRangeError = false) }
    }

    /** Cambia giorno: se quel giorno ha già una pesata la mostra, altrimenti tiene il valore digitato. */
    fun onSelectDate(date: LocalDate) {
        scope.launch {
            val existing = repository.bodyWeightOn(date)
            _state.update { current ->
                current?.copy(
                    date = date,
                    input = existing?.let { NumericInputState.of(current.unit.fromKg(it.weightKg), bodyWeightConfig(current.unit)) } ?: current.input,
                )
            }
        }
    }

    fun save() {
        val current = _state.value ?: return
        val weight = current.valueKg?.takeIf { current.isValid }
        if (weight == null) {
            _state.value = current.copy(showsRangeError = true)
            return
        }
        scope.launch {
            repository.saveBodyWeight(current.date, weight)
            _state.value = null
            savedChannel.send(current.date)
        }
    }

    fun dismiss() {
        _state.value = null
    }
}

@Immutable
data class BodyWeightEntryActions(
    val onKey: (KeypadKey) -> Unit,
    val onSelectDate: (LocalDate) -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        fun of(editor: BodyWeightEntryEditor) = BodyWeightEntryActions(
            onKey = editor::onKey,
            onSelectDate = editor::onSelectDate,
            onSave = editor::save,
            onDismiss = editor::dismiss,
        )
    }
}

/** Foglio dal basso con giorno, valore in grande e tastierino dell'app (mai la tastiera di sistema). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightEntrySheet(state: BodyWeightEntryUi, actions: BodyWeightEntryActions) {
    if (LocalInlineDialogs.current) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
                BodyWeightEntryContent(state, actions)
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = actions.onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            BodyWeightEntryContent(state, actions)
        }
    }
}

@Composable
internal fun BodyWeightEntryContent(state: BodyWeightEntryUi, actions: BodyWeightEntryActions) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
    ) {
        Text(
            stringResource(R.string.progress_weigh_in),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = SportDimens.ScreenPadding),
        )
        EntryDayChips(state, actions.onSelectDate)
        val weightLabel = stringResource(R.string.weight_value)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SportDimens.ScreenPadding)
                .semantics {
                    contentDescription = "$weightLabel: ${state.input.displayText.ifEmpty { "—" }} ${state.unit.symbol}"
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                state.input.displayText.ifEmpty { "—" },
                style = SportNumberStyles.Hero,
                color = if (state.input.text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(SportDimens.SpacingS))
            Text(
                state.unit.symbol,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = SportDimens.SpacingM),
            )
        }
        val hint = when {
            state.showsRangeError -> stringResource(
                R.string.weight_out_of_range,
                Formatters.weightKg(ProgressRepository.MIN_BODY_WEIGHT_KG, state.unit),
                Formatters.weightKg(ProgressRepository.MAX_BODY_WEIGHT_KG, state.unit),
            )
            state.replacesExisting -> stringResource(R.string.weight_replaces)
            else -> null
        }
        Text(
            hint.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.showsRangeError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp)
                .padding(horizontal = SportDimens.ScreenPadding),
            textAlign = TextAlign.Center,
        )
        NumericKeypad(
            onKey = actions.onKey,
            onDone = actions.onSave,
            stepLabel = Formatters.number(state.input.config.step),
            allowDecimal = true,
            doneLabel = stringResource(R.string.weight_save),
        )
    }
}

@Composable
private fun EntryDayChips(state: BodyWeightEntryUi, onSelect: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SportDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        state.days.forEach { day ->
            val label = when (day) {
                state.today -> stringResource(R.string.weight_today)
                state.today.minusDays(1) -> stringResource(R.string.weight_yesterday)
                else -> Formatters.shortDate(day)
            }
            val recorded = day in state.recordedDates
            val description = if (recorded) stringResource(R.string.weight_day_recorded, label) else label
            FilterChip(
                selected = day == state.date,
                onClick = { onSelect(day) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                trailingIcon = if (recorded) {
                    { Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

/** Tastierino del peso corporeo nell'unità scelta. */
internal fun bodyWeightConfig(unit: WeightUnit): NumericInputConfig = when (unit) {
    WeightUnit.KG -> NumericInputConfig.BodyWeightKg
    WeightUnit.LB -> NumericInputConfig.BodyWeightLb
}
