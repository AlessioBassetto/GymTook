package com.example.sport.feature.exercises

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.sport.core.designsystem.theme.SportDimens
import com.example.sport.core.ui.media.AnatomyPreview
import com.example.sport.core.ui.media.AnimatedImageLoop
import com.example.sport.core.ui.media.FrameLoop
import com.example.sport.core.ui.media.OnlineVideoBadge
import com.example.sport.core.ui.media.VideoLoop
import com.example.sport.core.ui.media.isAnimatedImage

/** Cosa mostra la scheda esercizio al posto del video personale. */
internal enum class CatalogMedia {
    /** Video loop (Media3/ExoPlayer, dalla cache o da file locale). */
    VIDEO,

    /** GIF/WebP animata (Coil). */
    ANIMATED_IMAGE,

    /** Fotogrammi in dissolvenza: nessun video, o video illeggibile. */
    FRAMES,

    /** Video non raggiungibile offline: fotogrammi con il badge "Video disponibile online". */
    FRAMES_VIDEO_ONLINE,

    /** Video non raggiungibile offline e nessun fotogramma: mappa anatomica con il badge. */
    ANATOMY_VIDEO_ONLINE,

    /** Nessun media di catalogo (es. esercizio personalizzato): la mappa muscolare è più in basso. */
    NONE,
}

/**
 * Scelta pura (testabile) del media di catalogo. Offline e senza copia in cache il video non si
 * prova nemmeno ad aprire: si mostra subito il ripiego statico con il badge, mai un errore.
 */
internal fun catalogMediaFor(video: String?, videoFailed: Boolean, onlineOnly: Boolean, hasFrames: Boolean): CatalogMedia = when {
    video != null && onlineOnly -> if (hasFrames) CatalogMedia.FRAMES_VIDEO_ONLINE else CatalogMedia.ANATOMY_VIDEO_ONLINE
    video != null && !videoFailed -> if (isAnimatedImage(video)) CatalogMedia.ANIMATED_IMAGE else CatalogMedia.VIDEO
    hasFrames -> CatalogMedia.FRAMES
    else -> CatalogMedia.NONE
}

@Composable
internal fun CatalogMediaView(
    exercise: ExerciseDetailUi,
    media: CatalogMedia,
    description: String,
    framesDescription: String,
    onVideoError: (String) -> Unit,
) {
    val video = exercise.catalogVideo
    val anatomy: @Composable () -> Unit = { ExerciseAnatomy(exercise, framesDescription) }
    when (media) {
        CatalogMedia.VIDEO -> if (video != null) VideoLoop(uri = video, contentDescription = description, onError = { onVideoError(video) })
        CatalogMedia.ANIMATED_IMAGE -> if (video != null) AnimatedImageLoop(model = video, contentDescription = description, onError = { onVideoError(video) })
        // Fotogrammi mai scaricati e niente rete: la mappa anatomica al posto di un riquadro vuoto.
        CatalogMedia.FRAMES -> FrameLoop(frames = exercise.frames, contentDescription = framesDescription, fallback = anatomy)
        CatalogMedia.FRAMES_VIDEO_ONLINE -> Box {
            FrameLoop(frames = exercise.frames, contentDescription = framesDescription, fallback = anatomy)
            OnlineVideoBadge(Modifier.align(Alignment.TopStart).padding(SportDimens.SpacingS))
        }
        CatalogMedia.ANATOMY_VIDEO_ONLINE -> Box {
            anatomy()
            OnlineVideoBadge(Modifier.align(Alignment.TopStart).padding(SportDimens.SpacingS))
        }
        CatalogMedia.NONE -> Unit
    }
}

/** Mappa anatomica dell'esercizio nello spazio del media (ripiego statico offline). */
@Composable
internal fun ExerciseAnatomy(exercise: ExerciseDetailUi, contentDescription: String) {
    AnatomyPreview(
        primary = exercise.primaryMuscles.toSet(),
        secondary = exercise.secondaryMuscles.toSet(),
        contentDescription = contentDescription,
        modifier = Modifier.testTag(EXERCISE_ANATOMY_TAG),
    )
}

internal const val EXERCISE_ANATOMY_TAG = "exercise-media-anatomy"
