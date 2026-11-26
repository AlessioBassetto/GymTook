package com.example.sport.feature.nutrition

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.sport.R
import com.example.sport.core.data.food.DIARY_MEALS
import com.example.sport.core.data.food.Nutrients
import com.example.sport.core.designsystem.component.LocalInlineDialogs
import com.example.sport.core.designsystem.component.NumericKeypad
import com.example.sport.core.designsystem.input.KeypadKey
import com.example.sport.core.designsystem.input.NumericInputConfig
import com.example.sport.core.designsystem.input.NumericInputState
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.designsystem.theme.SportNumberStyles
import com.example.sport.core.model.MealType
import com.example.sport.core.ui.format.Formatters
import com.example.sport.core.ui.labels.labelRes
import kotlin.math.roundToInt

/** Piatto da aggiungere al diario (o voce del diario da correggere) nel pannello grammi. */
@Immutable
data class GramsTarget(
    val name: String,
    val brand: String?,
    val per100g: Nutrients,
    val initialGrams: Double,
    val mealType: MealType,
    /** Correzione di una voce già nel diario: compare "Elimina" e il pulsante dice "Salva". */
    val isEdit: Boolean = false,
)

/**
 * Grammatura liberamente regolabile con calcolo istantaneo: Calorie = kcal per 100 g × grammi / 100,
 * e lo stesso per i macronutrienti. Grammi dal tastierino dell'app, con −10 / +10 / +50 g e le
 * porzioni standard (50, 100, 150, 200 g più la porzione tipica del piatto).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GramsSheet(
    target: GramsTarget,
    onConfirm: (grams: Double, mealType: MealType) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    if (LocalInlineDialogs.current) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
                GramsSheetContent(target, onConfirm, onDelete)
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            GramsSheetContent(target, onConfirm, onDelete)
        }
    }
}

@Composable
internal fun GramsSheetContent(
    target: GramsTarget,
    onConfirm: (grams: Double, mealType: MealType) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var input by remember(target) { mutableStateOf(NumericInputState.of(target.initialGrams.coerceAtLeast(0.0), NumericInputConfig.Grams)) }
    var mealType by remember(target) { mutableStateOf(target.mealType) }
    val grams = input.value ?: 0.0
    val portion = target.per100g.forGrams(grams)
    fun setGrams(value: Double) {
        input = NumericInputState.of(value.coerceIn(NumericInputConfig.Grams.min, NumericInputConfig.Grams.max), NumericInputConfig.Grams)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
    ) {
        Column(Modifier.padding(horizontal = SportDimens.ScreenPadding)) {
            Text(target.name, style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(
                    target.brand,
                    stringResource(R.string.food_per_100g, Formatters.integer(target.per100g.kcal), macrosLine(target.per100g)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = SportDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
        ) {
            DIARY_MEALS.forEach { meal ->
                FilterChip(selected = meal == mealType, onClick = { mealType = meal }, label = { Text(stringResource(meal.labelRes())) })
            }
        }
        val gramsLabel = stringResource(R.string.food_grams_label)
        val kcalText = Formatters.kcal(portion.kcal.roundToInt())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SportDimens.ScreenPadding)
                .semantics {
                    contentDescription = "$gramsLabel: ${input.displayText.ifEmpty { "0" }} g, $kcalText"
                    liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                input.displayText.ifEmpty { "0" },
                style = SportNumberStyles.Hero,
                color = if (input.text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                " g",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = SportDimens.SpacingM),
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = SportDimens.SpacingS)) {
                Text(kcalText, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(macrosLine(portion), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = SportDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS),
        ) {
            listOf(-10.0, 10.0, 50.0).forEach { delta ->
                val label = (if (delta < 0) "−" else "+") + Formatters.integer(kotlin.math.abs(delta)) + " g"
                FilledTonalButton(onClick = { setGrams(grams + delta) }, modifier = Modifier.heightIn(min = SportDimens.TouchTargetMin)) {
                    Text(label)
                }
            }
            Spacer(Modifier.width(SportDimens.SpacingXs))
            portionsFor(target).forEach { portionGrams ->
                FilterChip(
                    selected = grams == portionGrams,
                    onClick = { setGrams(portionGrams) },
                    label = { Text(Formatters.grams(portionGrams)) },
                )
            }
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.padding(horizontal = SportDimens.SpacingS)) {
                Text(stringResource(R.string.food_entry_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        val doneLabel = if (target.isEdit) {
            stringResource(R.string.action_save)
        } else {
            // Il pasto è nei chip qui sopra: sul tasto basta il verbo (un testo lungo andrebbe a capo).
            stringResource(R.string.food_add)
        }
        NumericKeypad(
            onKey = { key: KeypadKey -> input = input.reduce(key) },
            onDone = { if (grams > 0) onConfirm(grams, mealType) },
            stepLabel = Formatters.integer(NumericInputConfig.Grams.step),
            allowDecimal = false,
            doneLabel = doneLabel,
            modifier = Modifier.heightIn(min = 0.dp),
        )
    }
}

/** Porzioni standard più quella tipica del piatto, in ordine crescente. */
private fun portionsFor(target: GramsTarget): List<Double> =
    (STANDARD_PORTIONS + target.initialGrams.takeIf { it > 0 && !target.isEdit }).filterNotNull().distinct().sorted()

private val STANDARD_PORTIONS = listOf(50.0, 100.0, 150.0, 200.0)

/** "P 5,9 g · C 31 g · G 3,2 g" (G = grassi). */
@Composable
internal fun macrosLine(nutrients: Nutrients): String = stringResource(
    R.string.food_macros_line,
    Formatters.oneDecimal(nutrients.proteinG),
    Formatters.oneDecimal(nutrients.carbsG),
    Formatters.oneDecimal(nutrients.fatG),
)
