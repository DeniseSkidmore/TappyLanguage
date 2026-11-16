plugins {
    alias(libs.plugins.android.application) // AGP 9: built-in Kotlin, no kotlin-android plugin
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tappylanguage.game"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tappylanguage"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-prototype"
    }

    // Kotlin jvmTarget defaults to targetCompatibility under built-in Kotlin.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Prototype: audio comes straight from the gitignored data folder (added as an extra asset root).
    sourceSets.named("main") {
        assets.directories += "../../data/language/greek/commons"
    }

    androidResources {
        noCompress += listOf("ogg", "wav") // MediaPlayer needs uncompressed assets for openFd
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
}
