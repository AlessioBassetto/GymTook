package com.example.sport.core.ui.media

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.sport.R
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.model.Muscle
import com.example.sport.core.ui.anatomy.MuscleMap
import com.example.sport.core.ui.time.rememberIsVisible
import kotlinx.coroutines.delay

/**
 * Miniatura esercizio. [model] è ciò che restituisce `MediaRepository.resolve` (File locale o URL):
 * Coil la prende dalla cache su disco anche offline. Senza immagine (o se non si carica) resta visibile
 * il ripiego locale: la mappa anatomica del [muscle] principale, oppure un'icona neutra. Mai un riquadro vuoto.
 */
@Composable
fun ExerciseThumbnail(
    model: Any?,
    modifier: Modifier = Modifier,
    size: Dp = SportDimens.ThumbnailSize,
    muscle: Muscle? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (muscle != null) {
            MuscleMap(primary = setOf(muscle), secondary = emptySet(), modifier = Modifier.fillMaxSize().padding(3.dp))
        } else {
            Icon(
                Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * "Mini-video" ottenuto dai fotogrammi dell'esercizio (posizione iniziale ↔ finale) in dissolvenza
 * continua. È il fallback leggero quando l'esercizio non ha un video loop dedicato.
 */
@Composable
fun FrameLoop(
    frames: List<Any>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    frameDurationMillis: Long = 1_200,
    /** Mostrato se i fotogrammi non si caricano (offline e mai scaricati): mai un riquadro vuoto. */
    fallback: (@Composable () -> Unit)? = null,
) {
    var loaded by remember(frames) { mutableStateOf(false) }
    var failed by remember(frames) { mutableStateOf(false) }
    if (failed && !loaded && fallback != null) {
        fallback()
    } else {
        FrameCrossfade(frames, contentDescription, modifier, frameDurationMillis, onLoaded = { loaded = true }, onFailed = { if (!loaded) failed = true })
    }
}

@Composable
private fun FrameCrossfade(
    frames: List<Any>,
    contentDescription: String?,
    modifier: Modifier,
    frameDurationMillis: Long,
    onLoaded: () -> Unit,
    onFailed: () -> Unit,
) {
    var index by remember(frames) { mutableIntStateOf(0) }
    // Si ferma a schermo spento o con l'app in background: nessun risveglio inutile della CPU.
    val visible = rememberIsVisible()
    if (frames.size > 1 && visible) {
        LaunchedEffect(frames) {
            while (true) {
                delay(frameDurationMillis)
                index = (index + 1) % frames.size
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Proporzione dei fotogrammi del dataset: niente bande vuote sopra e sotto.
            .aspectRatio(3f / 2f)
            .clip(MaterialTheme.shapes.large)
            // I fotogrammi del dataset hanno fondo bianco: un contenitore chiaro evita bordi neri.
            .background(Color.White),
    ) {
        Crossfade(targetState = index, animationSpec = tween(durationMillis = 450), label = "frame-loop") { current ->
            AsyncImage(
                model = frames.getOrNull(current),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { onLoaded() },
                onError = { onFailed() },
            )
        }
    }
}

/** URL o file di una GIF/WebP animata: si mostra con Coil invece che con il player video. */
fun isAnimatedImage(uri: String): Boolean {
    val path = uri.substringBefore('?').lowercase()
    return path.endsWith(".gif") || path.endsWith(".webp")
}

/**
 * Dimostrazione animata da GIF/WebP (catalogo o file locale), in loop come il video: Coil la decodifica
 * e la tiene nella cache su disco. Se non si carica chiama [onError] e la scheda passa al ripiego.
 */
@Composable
fun AnimatedImageLoop(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(MaterialTheme.shapes.large)
            .background(Color.White),
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onError = { onError() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Mappa anatomica al posto della dimostrazione: è locale e vettoriale, quindi sempre disponibile,
 * anche offline e per gli esercizi senza immagini.
 */
@Composable
fun AnatomyPreview(
    primary: Set<Muscle>,
    secondary: Set<Muscle>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(SportDimens.SpacingM),
        contentAlignment = Alignment.Center,
    ) {
        // La figura ha proporzioni sue: si adatta all'altezza del riquadro 3:2 (centrata), senza tagli.
        MuscleMap(primary = primary, secondary = secondary, contentDescription = contentDescription, modifier = Modifier.fillMaxHeight())
    }
}

/**
 * Badge discreto sopra fotogrammi o mappa: il video c'è ma serve la rete (non è ancora in cache).
 * Nessun messaggio d'errore: si capisce che basta tornare online.
 */
@Composable
fun OnlineVideoBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SportDimens.SpacingM, vertical = SportDimens.SpacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportDimens.SpacingXs),
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.exercise_video_online_only), style = MaterialTheme.typography.labelMedium)
        }
    }
}
