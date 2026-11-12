package com.example.sport.core.media

import com.example.sport.core.model.catalog.isSafeRelativeMediaPath
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Configurazione della CDN media. Il catalogo contiene solo path relativi
 * (`exercises/Barbell_Curl/0.jpg`): cambiare CDN significa cambiare solo questa base URL.
 */
class MediaConfig(baseUrl: String) {

    private val base: HttpUrl? = baseUrl.takeIf { it.isNotBlank() }?.let {
        val normalized = if (it.endsWith("/")) it else "$it/"
        requireNotNull(normalized.toHttpUrlOrNull()) { "MEDIA_BASE_URL non valida: $it" }
    }

    /** False in build senza CDN configurata: l'app mostra solo contenuti già scaricati. */
    val isRemoteEnabled: Boolean get() = base != null

    /** URL assoluto con ogni segmento correttamente codificato; null se remoto disattivato o path non sicuro. */
    fun urlFor(path: String): String? {
        val baseUrl = base ?: return null
        if (!isSafeRelativeMediaPath(path)) return null
        return baseUrl.newBuilder()
            .apply { path.split('/').forEach { addPathSegment(it) } }
            .build()
            .toString()
    }
}
