package com.example.sport.core.ui.media

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.time.rememberIsVisible
import kotlinx.coroutines.delay

/**
 * Miniatura esercizio. [model] è ciò che restituisce `MediaRepository.resolve` (File locale o URL):
 * Coil gestisce cache e decodifica. Senza immagine mostra un'icona neutra, mai un riquadro vuoto.
 */
@Composable
fun ExerciseThumbnail(
    model: Any?,
    modifier: Modifier = Modifier,
    size: Dp = SportDimens.ThumbnailSize,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            )
        }
    }
}
