package com.example.sport.core.ui.media

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import com.example.sport.R
import com.example.sport.core.media.VideoDataSources
import com.example.sport.core.ui.time.rememberIsVisible

/** Sorgenti dati del player (cache CDN); fornite dall'Activity, assenti nei test e nelle anteprime. */
val LocalVideoDataSources = staticCompositionLocalOf<VideoDataSources?> { null }

/**
 * Video breve in loop, senza audio, per studiare l'esecuzione.
 *
 * - Parte da solo e si ferma quando la schermata non è visibile (schermo spento, app in background).
 * - Tocco sul video: pausa / ripresa. Pulsante "0,5×": rallentatore per la tecnica.
 * - Il riquadro segue le proporzioni del video, limitate tra 3:4 (verticale) e 16:9.
 * - Se il decoder hardware fallisce riprova con quello software; se il video resta illeggibile
 *   chiama [onError] e la scheda torna ai fotogrammi.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoLoop(
    uri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val dataSources = LocalVideoDataSources.current
    val currentOnError by rememberUpdatedState(onError)
    val visible = rememberIsVisible()
    var userPaused by rememberSaveable(uri) { mutableStateOf(false) }
    var slowMotion by rememberSaveable(uri) { mutableStateOf(false) }
    // Alcuni decoder hardware falliscono su video registrati da altri telefoni: un secondo
    // tentativo con i soli decoder software prima di arrendersi.
    var softwareDecoding by rememberSaveable(uri) { mutableStateOf(false) }

    val player = remember(uri, softwareDecoding) {
        val factory = dataSources?.factoryFor(uri) ?: DefaultDataSource.Factory(context)
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        if (softwareDecoding) renderers.setMediaCodecSelector(SoftwareOnlyCodecSelector)
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val decoderFailure = (error as? ExoPlaybackException)?.type == ExoPlaybackException.TYPE_RENDERER
                if (decoderFailure && !softwareDecoding) softwareDecoding = true else currentOnError()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, visible, userPaused) { player.playWhenReady = visible && !userPaused }
    LaunchedEffect(player, slowMotion) { player.setPlaybackSpeed(if (slowMotion) SLOW_SPEED else 1f) }

    val presentation = rememberPresentationState(player)
    val videoSize = presentation.videoSizeDp
    val aspect = if (videoSize != null && videoSize.width > 0f && videoSize.height > 0f) {
        (videoSize.width / videoSize.height).coerceIn(MIN_ASPECT, MAX_ASPECT)
    } else {
        DEFAULT_ASPECT
    }
    val pausedLabel = stringResource(R.string.exercise_video_play)
    val playingLabel = stringResource(R.string.exercise_video_pause)

    // Riquadro centrato: un video verticale resta entro l'altezza massima (più stretto della
    // pagina) invece di forzare larghezza piena e altezza massima insieme, che sono incompatibili.
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        VideoFrame(
            aspect = aspect,
            modifier = Modifier
                .clickable(onClickLabel = if (userPaused) pausedLabel else playingLabel) { userPaused = !userPaused }
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = if (userPaused) pausedLabel else playingLabel
                },
        ) {
            VideoContent(player, userPaused, slowMotion, onToggleSpeed = { slowMotion = !slowMotion })
        }
    }
}

@Composable
private fun VideoFrame(aspect: Float, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(max = MAX_HEIGHT)
            .aspectRatio(aspect)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black)
            .then(modifier),
        content = content,
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.VideoContent(player: Player, userPaused: Boolean, slowMotion: Boolean, onToggleSpeed: () -> Unit) {
    val speedLabel = if (slowMotion) "0,5×" else "1×"
    val speedDescription = stringResource(R.string.exercise_video_speed, speedLabel)
    ContentFrame(
        player = player,
        modifier = Modifier.fillMaxSize(),
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = ContentScale.Fit,
    )
    if (userPaused) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.Center)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.padding(12.dp).size(40.dp))
        }
    }
    FilledTonalButton(
        onClick = onToggleSpeed,
        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
    ) {
        Text(speedLabel, modifier = Modifier.semantics { contentDescription = speedDescription })
    }
}

/** Solo decoder software: più lenti ma indipendenti dai driver video del telefono. */
@OptIn(UnstableApi::class)
private val SoftwareOnlyCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
    MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        .filter { it.softwareOnly }
}

private val MAX_HEIGHT = 480.dp
private const val SLOW_SPEED = 0.5f
private const val MIN_ASPECT = 3f / 4f
private const val MAX_ASPECT = 16f / 9f
private const val DEFAULT_ASPECT = 4f / 3f
