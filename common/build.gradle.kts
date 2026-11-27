plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * Echo "common" module: the public API every Echo extension is compiled against.
 * The sources are a verbatim copy of upstream (brahmkshatriya/echo, `common/src/commonMain`)
 * so that extension APKs built for the phone app can be loaded unchanged on the watch.
 * Only the build script differs: a plain Android library instead of a KMP module.
 */
android {
    namespace = "echo.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("src/commonMain/resources/META-INF/proguard/consumer-proguard-rules.pro")
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/commonMain/kotlin")
            java.srcDirs("src/commonMain/kotlin")
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
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    api(libs.protobuf.java)
}
