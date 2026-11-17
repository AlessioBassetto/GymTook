package com.example.sport.feature.exercises

import org.junit.Assert.assertEquals
import org.junit.Test

/** Scelta del media della scheda esercizio: mai una schermata d'errore, sempre un ripiego visivo. */
class CatalogMediaTest {

    private val mp4 = "https://media.example.org/exercises/squat.mp4"
    private val gif = "https://media.example.org/exercises/squat.gif"

    @Test
    fun cachedOrLocalVideo_playsInTheLoop() {
        assertEquals(CatalogMedia.VIDEO, catalogMediaFor(mp4, videoFailed = false, onlineOnly = false, hasFrames = true))
        assertEquals(CatalogMedia.VIDEO, catalogMediaFor("file:///data/video.mp4", videoFailed = false, onlineOnly = false, hasFrames = false))
    }

    @Test
    fun gifsAndAnimatedWebp_goThroughCoil() {
        assertEquals(CatalogMedia.ANIMATED_IMAGE, catalogMediaFor(gif, videoFailed = false, onlineOnly = false, hasFrames = true))
        assertEquals(CatalogMedia.ANIMATED_IMAGE, catalogMediaFor("file:///android_asset/catalog/squat.webp", videoFailed = false, onlineOnly = false, hasFrames = false))
    }

    @Test
    fun offlineWithoutCache_showsTheStaticFallbackWithTheBadge() {
        assertEquals(CatalogMedia.FRAMES_VIDEO_ONLINE, catalogMediaFor(mp4, videoFailed = false, onlineOnly = true, hasFrames = true))
        assertEquals(CatalogMedia.ANATOMY_VIDEO_ONLINE, catalogMediaFor(mp4, videoFailed = false, onlineOnly = true, hasFrames = false))
    }

    @Test
    fun aBrokenVideo_fallsBackToTheFrames_orToTheMuscleMapBelow() {
        assertEquals(CatalogMedia.FRAMES, catalogMediaFor(mp4, videoFailed = true, onlineOnly = false, hasFrames = true))
        assertEquals(CatalogMedia.NONE, catalogMediaFor(mp4, videoFailed = true, onlineOnly = false, hasFrames = false))
        assertEquals(CatalogMedia.FRAMES, catalogMediaFor(null, videoFailed = false, onlineOnly = false, hasFrames = true))
        assertEquals(CatalogMedia.NONE, catalogMediaFor(null, videoFailed = false, onlineOnly = true, hasFrames = false))
    }
}
