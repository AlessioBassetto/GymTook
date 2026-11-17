package com.example.sport.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.example.sport.R
import com.example.sport.core.data.CalorieRepository
import com.example.sport.core.database.entity.BodyProfileEntity
import com.example.sport.core.designsystem.component.FormDialog
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.BiologicalSex
import com.example.sport.core.ui.format.Formatters
import java.time.LocalDate

/** Profilo corporeo mostrato nel monitoraggio del peso. Tutti i campi sono facoltativi. */
@Immutable
data class BodyProfileUi(
    val heightCm: Float? = null,
    val birthYear: Int? = null,
    val sex: BiologicalSex? = null,
    /** Età di quest'anno, ricavata dall'anno di nascita. */
    val age: Int? = null,
) {
    val isEmpty: Boolean get() = heightCm == null && birthYear == null && sex == null

    companion object {
        fun of(entity: BodyProfileEntity?, today: LocalDate) = BodyProfileUi(
            heightCm = entity?.heightCm,
            birthYear = entity?.birthYear,
            sex = entity?.sex,
            age = entity?.birthYear?.let { today.year - it },
        )
    }
}

/** Valori pronti da salvare (null = campo lasciato vuoto). */
data class BodyProfileInput(val heightCm: Float?, val birthYear: Int?, val sex: BiologicalSex?)

/**
 * Testi dei campi → valori da salvare; null se un campo compilato non è valido.
 * L'altezza accetta la virgola ("180,5"); i campi vuoti restano vuoti.
 */
internal fun parseBodyProfile(height: String, birthYear: String, sex: BiologicalSex?, birthYears: IntRange): BodyProfileInput? {
    val parsedHeight = if (height.isBlank()) null else parseHeight(height) ?: return null
    val parsedYear = if (birthYear.isBlank()) null else parseBirthYear(birthYear, birthYears) ?: return null
    return BodyProfileInput(parsedHeight, parsedYear, sex)
}

private fun parseHeight(text: String): Float? =
    Formatters.parseDecimal(text)?.toFloat()?.takeIf { it in CalorieRepository.MIN_HEIGHT_CM..CalorieRepository.MAX_HEIGHT_CM }

private fun parseBirthYear(text: String, birthYears: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in birthYears }

@Composable
private fun sexLabel(sex: BiologicalSex?): String = stringResource(
    when (sex) {
        BiologicalSex.MALE -> R.string.body_profile_sex_male
        BiologicalSex.FEMALE -> R.string.body_profile_sex_female
        null -> R.string.body_profile_sex_unset
    },
)

/** Riga riassuntiva: "180 cm · 34 anni · Uomo" (solo i dati inseriti). */
@Composable
private fun profileSummary(profile: BodyProfileUi): String = listOfNotNull(
    profile.heightCm?.let { stringResource(R.string.body_profile_height_value, Formatters.oneDecimal(it.toDouble())) },
    profile.age?.let { stringResource(R.string.body_profile_age_value, it) },
    profile.sex?.let { sexLabel(it) },
).joinToString(" · ")

/** Card del profilo corporeo nella schermata del peso: altezza, età e sesso, con "Modifica". */
@Composable
internal fun BodyProfileCard(profile: BodyProfileUi, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = SportDimens.ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(start = SportDimens.CardPadding, top = SportDimens.SpacingM, bottom = SportDimens.SpacingM, end = SportDimens.SpacingS),
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs)) {
                Text(
                    stringResource(R.string.body_profile_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.isEmpty) {
                    Text(stringResource(R.string.body_profile_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(profileSummary(profile), style = MaterialTheme.typography.bodyLarge)
                }
            }
            val editDescription = stringResource(R.string.body_profile_edit_description)
            TextButton(
                onClick = onEdit,
                modifier = Modifier
                    .heightIn(min = SportDimens.TouchTargetMin)
                    .semantics { contentDescription = editDescription },
            ) {
                Text(stringResource(if (profile.isEmpty) R.string.body_profile_add else R.string.body_profile_edit))
            }
        }
    }
}

/**
 * Modifica del profilo corporeo. Ogni campo è facoltativo; quelli compilati devono essere
 * plausibili (altezza 100–250 cm, età 10–100 anni) perché entrano nella stima delle calorie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BodyProfileDialog(
    profile: BodyProfileUi,
    birthYears: IntRange,
    onSave: (BodyProfileInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var height by remember { mutableStateOf(profile.heightCm?.let { Formatters.editable(it.toDouble()) }.orEmpty()) }
    var birthYear by remember { mutableStateOf(profile.birthYear?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(profile.sex) }
    // Come nel foglio del peso: nessun allarme mentre si scrive ("18" verso "180"), solo premendo Salva.
    var showErrors by remember { mutableStateOf(false) }
    val input = parseBodyProfile(height, birthYear, sex, birthYears)
    val heightError = showErrors && height.isNotBlank() && parseHeight(height) == null
    val yearError = showErrors && birthYear.isNotBlank() && parseBirthYear(birthYear, birthYears) == null

    FormDialog(
        title = stringResource(R.string.body_profile_title),
        confirmLabel = stringResource(R.string.action_save),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = { if (input != null) onSave(input) else showErrors = true },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
            Text(
                stringResource(R.string.body_profile_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
                label = { Text(stringResource(R.string.body_profile_height_field)) },
                singleLine = true,
                isError = heightError,
                supportingText = if (heightError) {
                    {
                        Text(
                            stringResource(
                                R.string.body_profile_height_error,
                                CalorieRepository.MIN_HEIGHT_CM.toInt(),
                                CalorieRepository.MAX_HEIGHT_CM.toInt(),
                            ),
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = birthYear,
                onValueChange = { birthYear = it },
                label = { Text(stringResource(R.string.body_profile_birth_year_field)) },
                singleLine = true,
                isError = yearError,
                supportingText = if (yearError) {
                    { Text(stringResource(R.string.body_profile_birth_year_error, birthYears.first, birthYears.last)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.body_profile_sex_field),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // FlowRow: in un dialogo stretto "Non indicato" va a capo intero invece di spezzarsi.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                listOf(BiologicalSex.MALE, BiologicalSex.FEMALE, null).forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option },
                        label = { Text(sexLabel(option)) },
                    )
                }
            }
        }
    }
}
