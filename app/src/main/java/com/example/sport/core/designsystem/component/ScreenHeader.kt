package com.example.sport.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.sport.core.designsystem.theme.SportDimens

/** Intestazione delle sezioni principali: titolo grande, sottotitolo, azioni opzionali. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = SportDimens.ScreenPadding,
                end = SportDimens.SpacingS,
                top = SportDimens.SpacingL,
                bottom = SportDimens.SpacingS,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions?.invoke()
    }
}
