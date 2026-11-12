package com.example.sport.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sport.core.backup.BackupFile
import com.example.sport.core.backup.BackupRepository
import com.example.sport.core.backup.BackupSummary
import com.example.sport.core.backup.InvalidBackupException
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.WeightUnit
import com.example.sport.core.settings.SettingsRepository
import com.example.sport.core.settings.UserSettings
import com.example.sport.core.update.ManualCheckResult
import com.example.sport.core.update.UpdateController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Backup letto e controllato, in attesa della conferma ("sostituisce tutti i dati"). */
data class PendingRestore(val file: BackupFile, val summary: BackupSummary, val exportedOn: LocalDate?)

sealed interface SettingsEvent {
    data class Exported(val summary: BackupSummary) : SettingsEvent
    data class Restored(val summary: BackupSummary) : SettingsEvent
    data object ExportFailed : SettingsEvent
    data object RestoreFailed : SettingsEvent
    data object InvalidFile : SettingsEvent

    /** Esito di "Controlla aggiornamenti" quando non c'è nulla da proporre. */
    data class UpdateCheck(val result: ManualCheckResult) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val contentResolver: ContentResolver,
    private val clock: Clock,
    private val updateController: UpdateController,
) : ViewModel() {

    /** False nelle build senza aggiornamento in-app (es. Play Store): il pulsante non compare. */
    val updatesEnabled: Boolean get() = updateController.isEnabled

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _busy = MutableStateFlow(false)

    /** Esportazione o ripristino in corso: i pulsanti si disattivano. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = _pendingRestore.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    fun onWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { settingsRepository.setWeightUnit(unit) }
    }

    fun onEffortScale(scale: EffortScale) {
        viewModelScope.launch { settingsRepository.setEffortScale(scale) }
    }

    /**
     * Controllo manuale: se c'è una versione nuova compare il dialogo di aggiornamento (lo mostra
     * il guscio dell'app); altrimenti un messaggio dice perché non c'è nulla da fare.
     */
    fun onCheckUpdates() = runBusy {
        val result = updateController.checkNow()
        if (result != ManualCheckResult.FOUND) eventChannel.send(SettingsEvent.UpdateCheck(result))
    }

    /** Nome proposto al selettore di sistema: sport-backup-2026-11-11.json. */
    fun suggestedFileName(): String = "sport-backup-${LocalDate.now(clock)}.json"

    fun onExport(destination: Uri) = runBusy {
        val event = try {
            val content = backupRepository.export()
            contentResolver.openOutputStream(destination, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("Destinazione non scrivibile")
            SettingsEvent.Exported(backupRepository.summaryOf(backupRepository.parse(content)))
        } catch (_: IOException) {
            SettingsEvent.ExportFailed
        } catch (_: SecurityException) {
            SettingsEvent.ExportFailed
        }
        eventChannel.send(event)
    }

    /** Legge il file scelto e chiede conferma mostrando cosa contiene. */
    fun onRestoreFilePicked(source: Uri) = runBusy {
        try {
            val content = contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IOException("File non leggibile")
            val file = backupRepository.parse(content)
            val exportedOn = runCatching { java.time.Instant.parse(file.exportedAt).atZone(clock.zone).toLocalDate() }.getOrNull()
            _pendingRestore.value = PendingRestore(file, backupRepository.summaryOf(file), exportedOn)
        } catch (_: InvalidBackupException) {
            eventChannel.send(SettingsEvent.InvalidFile)
        } catch (_: IOException) {
            eventChannel.send(SettingsEvent.InvalidFile)
        } catch (_: SecurityException) {
            eventChannel.send(SettingsEvent.InvalidFile)
        }
    }

    fun onConfirmRestore() {
        val pending = _pendingRestore.value ?: return
        _pendingRestore.value = null
        runBusy {
            val event = try {
                SettingsEvent.Restored(backupRepository.restore(pending.file))
            } catch (_: InvalidBackupException) {
                SettingsEvent.RestoreFailed
            }
            eventChannel.send(event)
        }
    }

    fun onCancelRestore() {
        _pendingRestore.value = null
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}
