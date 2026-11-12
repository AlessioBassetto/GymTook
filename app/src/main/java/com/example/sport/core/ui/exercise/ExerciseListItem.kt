package com.example.sport.core.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sport.R
import com.example.sport.core.database.entity.ExerciseEntity
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.labels.labelRes
import com.example.sport.core.ui.media.ExerciseThumbnail

/** Riga esercizio per liste e selettori. */
@Immutable
data class ExerciseRowUi(
    val id: String,
    val name: String,
    val muscleLabelRes: Int,
    val equipmentLabelRes: Int,
    val thumbnail: Any?,
    val isFavorite: Boolean,
    val isCustom: Boolean = false,
) {
    companion object {
        fun from(exercise: ExerciseEntity, thumbnail: Any?) = ExerciseRowUi(
            id = exercise.id,
            name = exercise.name,
            muscleLabelRes = exercise.primaryMuscle.labelRes(),
            equipmentLabelRes = exercise.equipment.labelRes(),
            thumbnail = thumbnail,
            isFavorite = exercise.isFavorite,
            isCustom = exercise.isCustom,
        )
    }
}

@Composable
fun ExerciseListItem(
    exercise: ExerciseRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = SportDimens.ScreenPadding, vertical = SportDimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingM),
        ) {
            ExerciseThumbnail(model = exercise.thumbnail)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingS)) {
                    if (exercise.isCustom) CustomBadge()
                    Text(
                        text = stringResource(exercise.muscleLabelRes) + " · " + stringResource(exercise.equipmentLabelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (exercise.isFavorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.exercise_favorite_remove),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** Etichetta "Superserie A": stessa grafica nel builder e nel logger. */
@Composable
fun SupersetBadge(text: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Etichetta degli esercizi creati dall'utente. */
@Composable
fun CustomBadge(modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Text(
            stringResource(R.string.exercise_custom_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
