import java.util.Properties

val DEV_MEDIA_BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

// Firma di rilascio: keystore.properties nella radice del progetto (mai nel controllo di versione),
// vedi docs/rilascio.md. Senza il file la build release resta non firmata.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Aggiornamento in-app dalle GitHub Releases (docs/aggiornamenti.md): "proprietario/repository".
// Senza la proprietà la funzione non esiste nell'app: né controllo né permesso di installazione.
// Le build per il Play Store vanno fatte così, perché lo Store vieta l'auto-aggiornamento.
val updateRepository = providers.gradleProperty("sport.update.repository").getOrElse("").trim()
require(updateRepository.isEmpty() || Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(updateRepository)) {
    "sport.update.repository deve avere la forma proprietario/repository (trovato: \"$updateRepository\")"
}

android {
    namespace = "com.example.sport"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Identificativo sul Play Store: definitivo dopo la prima pubblicazione. Il package del codice
        // (namespace) resta com.example.sport: non è visibile agli utenti.
        applicationId = providers.gradleProperty("sport.applicationId").getOrElse("it.allenamento.sport")
        // API 26+: java.time nativo (Instant/LocalDate nelle entità Room) senza desugaring.
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("sport.versionCode").getOrElse("1").toInt()
        versionName = providers.gradleProperty("sport.versionName").getOrElse("1.0.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Base URL della CDN media. In sviluppo punta al mirror pubblico di free-exercise-db
        // (commit fissato, stesso del catalogo); sovrascrivibile con -Psport.mediaBaseUrl=...
        buildConfigField(
            "String",
            "MEDIA_BASE_URL",
            "\"${providers.gradleProperty("sport.mediaBaseUrl").getOrElse(DEV_MEDIA_BASE_URL)}\"",
        )

        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")
        // API di GitHub; sovrascrivibile solo per provare l'aggiornamento con un server locale.
        buildConfigField(
            "String",
            "UPDATE_API_BASE_URL",
            "\"${providers.gradleProperty("sport.update.apiBaseUrl").getOrElse("https://api.github.com")}\"",
        )
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug e release convivono sullo stesso telefono.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // In produzione i media devono stare sulla propria CDN (vedi tools/catalog-importer):
            // senza -Psport.mediaBaseUrl.release l'app funziona offline senza immagini remote.
            buildConfigField(
                "String",
                "MEDIA_BASE_URL",
                "\"${providers.gradleProperty("sport.mediaBaseUrl.release").getOrElse("")}\"",
            )
            // R8 completo (codice e risorse). Configurazione classica e non la DSL optimization: con
            // quest'ultima l'ottimizzazione limitata ad androidx/kotlin (packageScope) faceva andare in crash
            // l'app all'avvio (IllegalAccessError) e il plugin del baseline profile non riusciva a
            // disattivare R8 nella sua variante (profilo con nomi offuscati, inutilizzabile).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric legge gli asset reali (catalogo esercizi) nei test JVM.
            isIncludeAndroidResources = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        // REQUEST_INSTALL_PACKAGES entra nel manifest solo se l'aggiornamento in-app è configurato.
        if (updateRepository.isNotEmpty()) {
            variant.sources.manifests.addStaticManifestFile("src/selfupdate/AndroidManifest.xml")
        }
    }
}

baselineProfile {
    // Il profilo si rigenera a mano (serve un emulatore): ./gradlew :app:generateReleaseBaselineProfile
    automaticGenerationDuringBuild = false
    saveInSrc = true
    dexLayoutOptimization = true
}

room {
    // Schemi JSON versionati: base per le Migration e per i test di migrazione.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // AndroidX + Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(project(":core:model"))

    // Data layer
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Media: immagini con cache su disco, download offline in background
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.reorderable)
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.datastore.preferences)
    // Installa il baseline profile anche fuori dal Play Store (sideload, test interni).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Unit test (JVM + Robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented test
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
