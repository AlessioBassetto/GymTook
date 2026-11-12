import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Tool da riga di comando (JVM) che genera il catalogo esercizi dell'app.
// Non è una dipendenza dell'app: nulla di questo modulo finisce nell'APK.
//
//   ./gradlew :tools:catalog-importer:run --args="build --version 2"
//   ./gradlew :tools:catalog-importer:run --args="translate --limit 40"
//   ./gradlew :tools:catalog-importer:run --args="mirror-media --out D:/cdn/media"
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
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

application {
    mainClass.set("com.example.sport.tools.catalog.MainKt")
}

tasks.named<JavaExec>("run") {
    // I path del tool sono relativi alla radice del repository.
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.anthropic.java)
    testImplementation(libs.junit)
}
