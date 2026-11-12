package com.example.sport

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.example.sport.core.designsystem.theme.SportTheme
import com.example.sport.core.media.VideoDataSources
import com.example.sport.core.ui.media.LocalVideoDataSources
import com.example.sport.navigation.SportApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Incrementato a ogni apertura dalla notifica del recupero: il guscio porta al logger. */
    private val openWorkoutSignal = mutableIntStateOf(0)

    /** Cache dei video di catalogo, condivisa da tutti i player. */
    @Inject
    lateinit var videoDataSources: VideoDataSources

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barre di sistema trasparenti con icone chiare: il tema dell'app è scuro di default.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val signal by openWorkoutSignal
            CompositionLocalProvider(LocalVideoDataSources provides videoDataSources) {
                SportTheme {
                    SportApp(openWorkoutSignal = signal)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true) {
            openWorkoutSignal.intValue += 1
            intent.removeExtra(EXTRA_OPEN_WORKOUT)
        }
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "open_workout"
    }
}
