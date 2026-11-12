package com.example.sport.core.update

import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.sport.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Cosa mostra l'app a proposito dell'aggiornamento (un dialogo alla volta, o niente). */
sealed interface UpdateUiState {
    data object Hidden : UpdateUiState

    /** Nuova versione trovata: si chiede all'utente se aggiornare. Nulla è stato scaricato. */
    data class Available(val update: AvailableUpdate, val currentVersion: String) : UpdateUiState

    data class Downloading(val update: AvailableUpdate, val downloadedBytes: Long, val totalBytes: Long) : UpdateUiState {
        /** Avanzamento 0–1, null finché la dimensione non è nota. */
        val fraction: Float?
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    /** APK scaricato e controllato: manca solo la conferma nell'installer di sistema. */
    data class ReadyToInstall(val update: AvailableUpdate, val apk: File) : UpdateUiState

    data class Failed(val update: AvailableUpdate, val reason: DownloadFailure) : UpdateUiState
}

/** Risposta a "Controlla aggiornamenti" nelle impostazioni (lì l'esito va detto, anche se negativo). */
enum class ManualCheckResult { FOUND, UP_TO_DATE, OFFLINE, FAILED }

/** Momento dell'ultimo controllo automatico riuscito, per non interrogare GitHub a ogni avvio. */
@Singleton
class UpdatePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun lastAutomaticCheck(): Instant? = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .first()[LAST_CHECK]
        ?.let(Instant::ofEpochMilli)

    suspend fun setLastAutomaticCheck(instant: Instant) {
        dataStore.edit { it[LAST_CHECK] = instant.toEpochMilli() }
    }

    private companion object {
        val LAST_CHECK = longPreferencesKey("update_last_automatic_check")
    }
}

/**
 * Regia dell'aggiornamento in-app, unica per tutto il processo (il download prosegue cambiando
 * schermata).
 *
 * - All'avvio controlla in silenzio, al massimo una volta ogni [AUTOMATIC_CHECK_INTERVAL]: così
 *   "Più tardi" non ripropone il dialogo a ogni apertura e GitHub non viene interrogato inutilmente.
 *   Offline, timeout ed errori non producono alcun messaggio.
 * - Scarica solo dopo [onAccept]; "Più tardi" e "Annulla" non lasciano file.
 */
@Singleton
class UpdateController @Inject constructor(
    private val manager: GitHubUpdateManager,
    private val preferences: UpdatePreferences,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /** False nelle build senza repository configurato (es. Play Store): nessuna voce nell'interfaccia. */
    val isEnabled: Boolean get() = manager.isEnabled

    /** Controllo silenzioso all'avvio dell'app. */
    fun checkOnStartup(): Job? {
        if (!isEnabled) return null
        return scope.launch {
            if (_state.value != UpdateUiState.Hidden) return@launch
            // Avvio pulito: un APK rimasto in cache è già stato installato o è stato abbandonato.
            manager.clearDownloads()
            val last = preferences.lastAutomaticCheck()
            val now = Instant.now(clock)
            if (last != null && Duration.between(last, now) < AUTOMATIC_CHECK_INTERVAL && !last.isAfter(now)) return@launch
            when (val result = manager.check()) {
                is UpdateCheck.Available -> {
                    preferences.setLastAutomaticCheck(now)
                    offer(result.update)
                }
                UpdateCheck.UpToDate -> preferences.setLastAutomaticCheck(now)
                // Offline o errore: nessun messaggio, si riprova al prossimo avvio.
                UpdateCheck.Offline, UpdateCheck.Failed, UpdateCheck.Disabled -> Unit
            }
        }
    }

    /** Controllo richiesto dall'utente: ignora l'intervallo e restituisce sempre un esito. */
    suspend fun checkNow(): ManualCheckResult = when (val result = manager.check()) {
        is UpdateCheck.Available -> {
            offer(result.update)
            ManualCheckResult.FOUND
        }
        UpdateCheck.UpToDate -> ManualCheckResult.UP_TO_DATE
        UpdateCheck.Offline -> ManualCheckResult.OFFLINE
        UpdateCheck.Failed, UpdateCheck.Disabled -> ManualCheckResult.FAILED
    }

    private fun offer(update: AvailableUpdate) {
        // Un download in corso o un APK pronto non vengono sostituiti dalla proposta.
        _state.update { current ->
            if (current is UpdateUiState.Hidden || current is UpdateUiState.Available) {
                UpdateUiState.Available(update, manager.currentVersionName)
            } else {
                current
            }
        }
    }

    /** "Aggiorna ora" (o "Riprova" dopo un errore): parte il download. */
    fun onAccept() {
        val update = when (val current = _state.value) {
            is UpdateUiState.Available -> current.update
            is UpdateUiState.Failed -> current.update
            else -> return
        }
        _state.value = UpdateUiState.Downloading(update, downloadedBytes = 0, totalBytes = update.apkSizeBytes)
        downloadJob?.cancel()
        downloadJob = scope.launch {
            manager.download(update).collect { step ->
                _state.value = when (step) {
                    is DownloadState.Progress -> UpdateUiState.Downloading(update, step.downloadedBytes, step.totalBytes)
                    is DownloadState.Completed -> UpdateUiState.ReadyToInstall(update, step.apk)
                    is DownloadState.Failed -> UpdateUiState.Failed(update, step.reason)
                }
            }
        }
    }

    /** "Più tardi", "Annulla" o "Chiudi": si torna all'app senza tenere nulla. */
    fun onDismiss() {
        downloadJob?.cancel()
        downloadJob = null
        val wasInstalling = _state.value is UpdateUiState.ReadyToInstall
        _state.value = UpdateUiState.Hidden
        // L'APK pronto resta in cache finché l'installer può ancora leggerlo: si pulisce al prossimo avvio.
        if (!wasInstalling) scope.launch { manager.clearDownloads() }
    }

    fun installIntent(apk: File): Intent = manager.installIntent(apk)

    companion object {
        val AUTOMATIC_CHECK_INTERVAL: Duration = Duration.ofHours(12)
    }
}
