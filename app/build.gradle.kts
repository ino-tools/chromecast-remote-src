plugins {
    id("com.android.application")
}

android {
    namespace = "com.ino.remotehome"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ino.remotehome"
        minSdk = 26
        targetSdk = 34
        versionCode = 2026111306
        versionName = "2026.11.13.6"
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("fixed.keystore")
            storePassword = "remotehome"
            keyAlias = "remote"
            keyPassword = "remotehome"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
