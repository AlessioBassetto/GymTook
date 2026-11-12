package com.example.sport.core.ui.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * true mentre la schermata è visibile all'utente (lifecycle almeno STARTED).
 * A schermo spento o con l'app in background diventa false.
 */
@Composable
fun rememberIsVisible(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}

/**
 * Orologio per i cronometri a schermo: si aggiorna ogni [periodMillis] solo mentre la schermata è
 * visibile. A schermo spento non sveglia la CPU; al ritorno si riallinea subito all'ora corrente.
 */
@Composable
fun rememberTickingNow(periodMillis: Long, now: () -> Instant): State<Instant> {
    val visible = rememberIsVisible()
    val currentNow by rememberUpdatedState(now)
    return produceState(initialValue = currentNow(), visible, periodMillis) {
        value = currentNow()
        if (!visible) return@produceState
        while (true) {
            delay(periodMillis)
            value = currentNow()
        }
    }
}
