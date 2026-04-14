plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val apiBaseUrl = providers.gradleProperty("API_BASE_URL")
    .orElse("http://10.0.2.2:8080")

android {
    namespace = "com.example.augmentedreality"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.augmentedreality"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.get()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        mlModelBinding = false
    }
}

// Automatically set up adb reverse tunnel whenever the app is installed over USB.
// This lets the physical device reach the Ktor server on this Mac without WiFi.
tasks.matching { it.name.startsWith("install") }.configureEach {
    doFirst {
        val adb = "${System.getenv("HOME")}/Library/Android/sdk/platform-tools/adb"
        try {
            exec {
                commandLine(adb, "reverse", "tcp:8080", "tcp:8080")
                isIgnoreExitValue = true
            }
        } catch (_: Exception) {
            // adb not found or no device — silently skip, install will report device errors itself
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)


    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.unit)
    implementation(libs.litert.metadata)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // CameraX core + lifecycle
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

// CameraX Viewfinder (Compose) + CameraView/Controller
    implementation(libs.androidx.camera.compose)             // was camera-viewfinder*
    implementation(libs.androidx.camera.viewfinder.compose)

// Coroutines (used for the analyzer executor)
    implementation(libs.kotlinx.coroutines.android)



    // Task library (often used by generated bindings for detection/classification)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")


    // Coroutines Guava (if you use .await() on CameraX futures)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    implementation("io.ktor:ktor-client-android:3.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("io.ktor:ktor-client-logging:3.1.2")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.6.0")

}
