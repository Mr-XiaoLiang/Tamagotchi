plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lollipop.grokbot"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            optimization {
                enable = false
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
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}

// `GrokFrameDumpTest` writes the geometry the pixel comparison renders. Gradle does not forward
// `-D` from the command line into the test JVM, so the switch is passed through explicitly.
tasks.withType<Test>().configureEach {
    System.getProperty("pixdiff.dump")?.let { systemProperty("pixdiff.dump", it) }
}
