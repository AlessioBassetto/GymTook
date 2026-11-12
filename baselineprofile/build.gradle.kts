// Generatore del baseline profile: percorre l'app come un utente (avvio, libreria, progressi, dieta)
// e registra le classi e i metodi usati, che il Play Store compila in anticipo.
// Uso, con un emulatore o un telefono collegato: ./gradlew :app:generateReleaseBaselineProfile
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.sport.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // BaselineProfileRule richiede Android 9+ sul dispositivo che genera il profilo.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["targetAppId"] =
            providers.gradleProperty("sport.applicationId").getOrElse("it.allenamento.sport")
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}
