package com.example.sport.feature.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.sport.R
import com.example.sport.core.data.ProgressRepository
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.format.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

/** Periodo mostrato da grafici e liste dei progressi. */
enum class TimeRange(@StringRes val labelRes: Int, private val months: Long?) {
    MONTH(R.string.range_1m, 1),
    QUARTER(R.string.range_3m, 3),
    HALF_YEAR(R.string.range_6m, 6),
    YEAR(R.string.range_1y, 12),
    ALL(R.string.range_all, null),
    ;

    /** Primo giorno incluso: "1 mese" il 10 novembre parte dal 10 ottobre. */
    fun startDate(today: LocalDate): LocalDate = months?.let { today.minusMonths(it) } ?: ProgressRepository.EARLIEST_DATE
}

/** Una riga di chip a scelta singola, scorrevole se non entra nello schermo. */
@Composable
internal fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SportDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
internal fun RangeChips(selected: TimeRange, onSelect: (TimeRange) -> Unit, modifier: Modifier = Modifier) {
    ChoiceChips(
        options = TimeRange.entries,
        selected = selected,
        label = { stringResource(it.labelRes) },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/** Come leggere una variazione: per la forza salire è un progresso, per il peso dipende dall'obiettivo. */
internal enum class ChangeTone { POSITIVE_IS_GOOD, NEUTRAL }

/**
 * Freccia e valore della variazione ("↗ +2,5 kg"). Il colore segnala un progresso solo quando il
 * verso ha un significato univoco ([ChangeTone.POSITIVE_IS_GOOD]); il lettore di schermo legge
 * "in aumento di 2,5 kg".
 */
@Composable
internal fun ChangeBadge(delta: Double, text: String, tone: ChangeTone, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rising = delta > CHANGE_EPSILON
    val falling = delta < -CHANGE_EPSILON
    val color: Color = when {
        tone == ChangeTone.NEUTRAL || !(rising || falling) -> colors.onSurfaceVariant
        rising -> colors.primary
        else -> colors.error
    }
    val description = when {
        rising -> stringResource(R.string.progress_trend_up, text.trimStart('+'))
        falling -> stringResource(R.string.progress_trend_down, text.trimStart('−', '-'))
        else -> text
    }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                rising -> Icons.AutoMirrored.Filled.TrendingUp
                falling -> Icons.AutoMirrored.Filled.TrendingDown
                else -> Icons.AutoMirrored.Filled.TrendingFlat
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(SportDimens.SpacingXs))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Sotto questa soglia la variazione si considera nulla (arrotondamento al decimo di kg). */
private const val CHANGE_EPSILON = 0.05

/**
 * Etichetta dell'asse x (epoch-day): giorno e mese, oppure mese e anno quando il grafico copre
 * più di un anno e il giorno non aiuta più.
 */
internal fun epochDayLabel(spanDays: Long): (Double) -> String = { x ->
    val date = LocalDate.ofEpochDay(x.roundToLong())
    if (spanDays > LONG_SPAN_DAYS) Formatters.monthYear(date) else Formatters.dayMonth(date)
}

private const val LONG_SPAN_DAYS = 330L

/** Giorni coperti da una serie di date (0 se vuota). */
internal fun spanDays(dates: List<LocalDate>): Long =
    if (dates.size < 2) 0 else abs(dates.last().toEpochDay() - dates.first().toEpochDay())

/** Spazio sotto le liste con un pulsante flottante: l'ultima riga resta raggiungibile. */
internal val FabClearance = PaddingValues(bottom = 96.dp)

/** Giorno di calendario di un istante (LocalDate.ofInstant richiederebbe API 34). */
internal fun Instant.toLocalDate(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()
