package com.example.sport.feature.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.sport.R
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.update.DownloadFailure
import com.example.sport.core.update.UpdateController
import com.example.sport.core.update.UpdateUiState
import com.example.sport.core.ui.format.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject

/** Collega la regia dell'aggiornamento al guscio dell'app e fa partire il controllo all'avvio. */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val controller: UpdateController,
) : ViewModel() {

    val state: StateFlow<UpdateUiState> = controller.state

    init {
        controller.checkOnStartup()
    }

    fun onAccept() = controller.onAccept()

    fun onDismiss() = controller.onDismiss()

    fun installIntent(apk: File): Intent = controller.installIntent(apk)
}

@Immutable
data class UpdateActions(
    /** "Aggiorna ora" e "Riprova": avvia il download. */
    val onAccept: () -> Unit,
    /** "Più tardi", "Annulla", "Chiudi". */
    val onDismiss: () -> Unit,
    /** "Installa": apre l'installer di sistema sull'APK scaricato. */
    val onInstall: (File) -> Unit,
)

/**
 * Dialoghi dell'aggiornamento, sopra qualunque schermata. Compare solo se una nuova versione è
 * stata trovata: senza rete, o se il controllo fallisce, l'app si avvia come sempre.
 */
@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val install: (File) -> Unit = { apk -> context.launchInstaller(viewModel.installIntent(apk)) }

    // A download finito l'installer di sistema si apre da solo, una volta, e solo con l'app in
    // primo piano (da sfondo Android bloccherebbe l'avvio): se l'utente lo chiude, resta "Installa".
    val ready = state as? UpdateUiState.ReadyToInstall
    if (ready != null) {
        val lifecycleOwner = LocalLifecycleOwner.current
        var launched by rememberSaveable(ready.apk.path, ready.update.versionName) { mutableStateOf(false) }
        LaunchedEffect(ready.apk.path, ready.update.versionName) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!launched) {
                    launched = true
                    install(ready.apk)
                }
            }
        }
    }

    UpdateDialogs(state, UpdateActions(onAccept = viewModel::onAccept, onDismiss = viewModel::onDismiss, onInstall = install))
}

private fun Context.launchInstaller(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nessun installer di pacchetti (dispositivo molto personalizzato): il dialogo resta aperto.
    }
}

@Composable
internal fun UpdateDialogs(state: UpdateUiState, actions: UpdateActions) {
    when (state) {
        UpdateUiState.Hidden -> Unit
        is UpdateUiState.Available -> AvailableDialog(state, actions)
        is UpdateUiState.Downloading -> DownloadingDialog(state, actions)
        is UpdateUiState.ReadyToInstall -> ReadyDialog(state, actions)
        is UpdateUiState.Failed -> FailedDialog(state, actions)
    }
}

@Composable
private fun AvailableDialog(state: UpdateUiState.Available, actions: UpdateActions) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
                Text(
                    stringResource(R.string.update_available_versions, state.update.versionName, state.currentVersion),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.update.apkSizeBytes > 0) {
                    Text(
                        stringResource(R.string.update_download_size, Formatters.megabytes(state.update.apkSizeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.update.notes.isNotBlank()) {
                    // Note lunghe: scorrono dentro il dialogo, i pulsanti restano sempre visibili.
                    Text(
                        state.update.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = NOTES_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.onAccept) { Text(stringResource(R.string.update_now)) } },
        dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

@Composable
private fun DownloadingDialog(state: UpdateUiState.Downloading, actions: UpdateActions) {
    val fraction = state.fraction
    val progressText = if (fraction != null) {
        stringResource(
            R.string.update_download_progress,
            (fraction * PERCENT).toInt(),
            Formatters.megabytes(state.downloadedBytes),
            Formatters.megabytes(state.totalBytes),
        )
    } else {
        stringResource(R.string.update_download_progress_unknown, Formatters.megabytes(state.downloadedBytes))
    }
    AlertDialog(
        // Un tocco fuori dal dialogo non deve interrompere il download: si annulla solo col pulsante.
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.update_downloading_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SportDimens.SpacingM)) {
                Text(stringResource(R.string.update_downloading_version, state.update.versionName))
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(PROGRESS_HEIGHT))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(PROGRESS_HEIGHT))
                }
                Text(
                    progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.update_cancel)) } },
    )
}

@Composable
private fun ReadyDialog(state: UpdateUiState.ReadyToInstall, actions: UpdateActions) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.update_ready_title)) },
        text = { Text(stringResource(R.string.update_ready_message, state.update.versionName)) },
        confirmButton = { TextButton(onClick = { actions.onInstall(state.apk) }) { Text(stringResource(R.string.update_install)) } },
        dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

@Composable
private fun FailedDialog(state: UpdateUiState.Failed, actions: UpdateActions) {
    val message = when (state.reason) {
        DownloadFailure.NETWORK -> R.string.update_failed_network
        DownloadFailure.INVALID_FILE -> R.string.update_failed_invalid
        DownloadFailure.NO_SPACE -> R.string.update_failed_space
    }
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.update_failed_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = actions.onAccept) { Text(stringResource(R.string.update_retry)) } },
        dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.update_close)) } },
    )
}

private val NOTES_MAX_HEIGHT = 240.dp
private val PROGRESS_HEIGHT = 8.dp
private const val PERCENT = 100
