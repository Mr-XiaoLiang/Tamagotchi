plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lollipop.tamagotchi"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.lollipop.tamagotchi"
        minSdk = 30
        targetSdk = 37
        versionCode = 1_00_00
        versionName = "1.0.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 本地试用：用 SDK 自带的 debug 密钥给 release 包签名，便于 adb install。
            // 正式发布请改为独立 release keystore（在 signingConfigs 中配置）。
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.material3)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    androidTestImplementation(libs.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.orgjson)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}