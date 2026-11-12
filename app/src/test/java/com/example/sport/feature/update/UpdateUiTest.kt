package com.example.sport.feature.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.update.AvailableUpdate
import com.example.sport.core.update.DownloadFailure
import com.example.sport.core.update.UpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Dialoghi dell'aggiornamento in-app: proposta, avanzamento, installazione, errore (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class UpdateUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val update = AvailableUpdate(
        versionName = "1.1.0",
        notes = "Novità\n• Grafici più veloci\n• Corretto il timer",
        apkName = "Sport-1.1.0.apk",
        apkUrl = "https://example.com/Sport-1.1.0.apk",
        apkSizeBytes = 7_130_316,
    )
    private val actions = UpdateActions(
        onAccept = { calls += "accept" },
        onDismiss = { calls += "dismiss" },
        onInstall = { calls += "install:${it.name}" },
    )
    private var state: UpdateUiState by mutableStateOf(UpdateUiState.Hidden)

    private fun show(initial: UpdateUiState) {
        state = initial
        composeRule.setContent { SportTheme { UpdateDialogs(state, actions) } }
    }

    @Test
    fun available_showsVersionNotesAndTheTwoChoices() {
        show(UpdateUiState.Available(update, currentVersion = "1.0.0"))

        composeRule.onNodeWithText("Aggiornamento disponibile").assertIsDisplayed()
        composeRule.onNodeWithText("Versione 1.1.0 (installata: 1.0.0)").assertIsDisplayed()
        composeRule.onNodeWithText("Download di 6,8 MB").assertIsDisplayed()
        composeRule.onNodeWithText("Novità\n• Grafici più veloci\n• Corretto il timer").assertIsDisplayed()

        composeRule.onNodeWithText("Più tardi").performClick()
        composeRule.onNodeWithText("Aggiorna ora").performClick()
        assertEquals(listOf("dismiss", "accept"), calls)
    }

    @Test
    fun hidden_showsNothing() {
        show(UpdateUiState.Hidden)
        composeRule.onNodeWithText("Aggiornamento disponibile").assertDoesNotExist()
    }

    @Test
    fun downloading_showsProgress_andCanBeCancelled() {
        show(UpdateUiState.Downloading(update, downloadedBytes = 3_565_158, totalBytes = 7_130_316))

        composeRule.onNodeWithText("Download in corso…").assertIsDisplayed()
        composeRule.onNodeWithText("Scarico la versione 1.1.0.").assertIsDisplayed()
        composeRule.onNodeWithText("50% · 3,4 MB di 6,8 MB").assertIsDisplayed()

        // Dimensione sconosciuta: barra indeterminata e solo i megabyte scaricati.
        state = UpdateUiState.Downloading(update, downloadedBytes = 1_048_576, totalBytes = 0)
        composeRule.onNodeWithText("1 MB scaricati").assertIsDisplayed()

        composeRule.onNodeWithText("Annulla").performClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun ready_offersInstall_andFailureOffersRetry() {
        show(UpdateUiState.ReadyToInstall(update, File("update.apk")))
        composeRule.onNodeWithText("Aggiornamento pronto").assertIsDisplayed()
        composeRule.onNodeWithText("Installa").performClick()

        state = UpdateUiState.Failed(update, DownloadFailure.NETWORK)
        composeRule.onNodeWithText("Aggiornamento non riuscito").assertIsDisplayed()
        composeRule.onNodeWithText("Il download si è interrotto. Controlla la connessione e riprova: i tuoi dati non sono stati toccati.").assertIsDisplayed()
        composeRule.onNodeWithText("Riprova").performClick()

        state = UpdateUiState.Failed(update, DownloadFailure.INVALID_FILE)
        composeRule.onNodeWithText("Il file scaricato non è un aggiornamento valido di questa app e non è stato installato.").assertIsDisplayed()
        composeRule.onNodeWithText("Chiudi").performClick()

        assertEquals(listOf("install:update.apk", "accept", "dismiss"), calls)
    }
}
