package com.example.sport.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Dove cercare gli aggiornamenti e con quale versione confrontarli.
 *
 * @param repository "proprietario/repository" su GitHub; vuoto = aggiornamento in-app assente.
 * @param apiBaseUrl `https://api.github.com` (un indirizzo http serve solo alle prove in locale).
 */
data class UpdateConfig(
    val repository: String,
    val apiBaseUrl: String,
    val currentVersionName: String,
    val currentVersionCode: Long,
) {
    val isEnabled: Boolean get() = repository.isNotBlank()

    val latestReleaseUrl: String get() = "${apiBaseUrl.trimEnd('/')}/repos/$repository/releases/latest"

    /** L'APK si scarica solo in HTTPS; in chiaro soltanto con un server di prova anch'esso in chiaro. */
    fun acceptsDownloadUrl(url: String): Boolean =
        url.startsWith("https://") || (apiBaseUrl.startsWith("http://") && url.startsWith("http://"))
}

/** C'è una rete con accesso a Internet? Se manca, il controllo non parte nemmeno. */
fun interface ConnectivityChecker {
    fun isOnline(): Boolean
}

class AndroidConnectivityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityChecker {
    override fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        // Basta una rete che dichiara Internet: se poi non risponde, la richiesta scade in silenzio.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

data class ApkInfo(val packageName: String, val versionCode: Long)

/** Legge package e versione di un APK su disco, senza installarlo. */
fun interface ApkInspector {
    fun inspect(apk: File): ApkInfo?
}

class AndroidApkInspector @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApkInspector {
    override fun inspect(apk: File): ApkInfo? {
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apk.path, 0)
        } ?: return null
        return ApkInfo(info.packageName, PackageInfoCompat.getLongVersionCode(info))
    }
}
