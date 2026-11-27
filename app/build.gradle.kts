plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val gitHash = execute("git", "rev-parse", "HEAD").take(7).ifEmpty { "local" }
val gitCount = execute("git", "rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val version = "1.0.$gitCount"

android {
    // Same namespace as the phone app: the `R`/`BuildConfig` references of the ported
    // extension & playback code stay untouched, and extension APKs keep working.
    namespace = "dev.brahmkshatriya.echo"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.brahmkshatriya.echo.wear"
        // Wear OS 3+ (Galaxy Watch 4 and newer). Target device: Galaxy Watch 6 Classic,
        // Wear OS 6 / One UI Watch 8 (API 36).
        minSdk = 30
        targetSdk = 36
        versionCode = gitCount
        versionName = "v${version}_$gitHash($gitCount)-wear"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            // Signed with the debug key so the APK can be side-loaded on the watch with
            // `adb install` without any extra setup.
            signingConfig = signingConfigs.getByName("debug")
            resValue("string", "app_name", "Echo Nightly")
        }
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Echo Debug")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":common"))

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.serialization.json)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Compose (through the BOM) + Wear Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.wear.tooling.preview)

    // Wear OS platform helpers (ambient mode, ongoing activity on the watch face)
    implementation(libs.wear)
    implementation(libs.wear.ongoing)
    implementation(libs.wear.input)

    // Media3 playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)

    // DI & images
    implementation(libs.okhttp)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}

fun execute(vararg command: String): String = runCatching {
    providers.exec { commandLine(*command) }.standardOutput.asText.get().trim()
}.getOrDefault("")
