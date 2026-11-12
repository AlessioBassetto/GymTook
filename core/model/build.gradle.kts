import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Modulo Kotlin puro (nessuna dipendenza Android): tassonomia, valori di dominio e formato del
// catalogo esercizi. È condiviso dall'app e dal tool di import del catalogo.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
