plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.callzik.dotifywidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.callzik.dotifywidget"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // Один и тот же ключ в репозитории, чтобы новые версии ставились поверх старых
    signingConfigs {
        create("shared") {
            storeFile = file("signing.keystore")
            storePassword = "dotifywidget"
            keyAlias = "dotify"
            keyPassword = "dotifywidget"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.palette:palette:1.0.0")
}
