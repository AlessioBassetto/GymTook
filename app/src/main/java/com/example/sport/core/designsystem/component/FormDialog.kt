package com.example.sport.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Mostra i [FormDialog] dentro la schermata invece che in una finestra separata.
 *
 * Serve solo ai test JVM: in Robolectric un campo di testo dentro una finestra di dialogo impedisce
 * a Compose di arrivare a riposo e il test resta bloccato. In produzione vale sempre false.
 */
val LocalInlineDialogs = staticCompositionLocalOf { false }

/**
 * Dialogo con campi da compilare (nome di una scheda, di un giorno…), con l'aspetto di un
 * AlertDialog Material 3: titolo, contenuto, "Annulla" e conferma allineati a destra.
 */
@Composable
fun FormDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = @Composable {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                // Moduli lunghi (es. un alimento con i macro): scorre il contenuto, i pulsanti restano visibili.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
                }
            }
        }
    }
    if (LocalInlineDialogs.current) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { card() }
    } else {
        Dialog(onDismissRequest = onDismiss) { card() }
    }
}
