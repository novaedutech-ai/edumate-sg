import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Developer settings: put overrides in local.properties (not committed), e.g.
//   edumate.apiBaseUrl=http://127.0.0.1:8080/   (real phone over USB with `adb reverse`)
//   edumate.useMockAi=true                     (skip the backend; offline templates only)
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun devSetting(name: String, default: String): String =
    (project.findProperty(name) as String?) ?: localProps.getProperty(name) ?: default

android {
    namespace = "sg.edumate.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "sg.edumate.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"
        testInstrumentationRunner = "sg.edumate.app.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }

        // No API keys ship in the app: AI calls go through the EduMate backend.
        buildConfigField("String", "LIVE_WS_URL", "\"wss://live.edumate.example.sg/v1/sessions\"")
    }

    buildTypes {
        debug {
            // Default: the backend running on your PC, reached from the emulator via 10.0.2.2.
            // If it isn't running, the app falls back to offline templates.
            buildConfigField("String", "API_BASE_URL", "\"${devSetting("edumate.apiBaseUrl", "http://10.0.2.2:8080/")}\"")
            buildConfigField("boolean", "USE_MOCK_AI", devSetting("edumate.useMockAi", "false"))
            buildConfigField("boolean", "USE_MOCK_LIVE", "true") // live sessions stay simulated until the live service exists
            buildConfigField("String", "DEV_TOKEN", "\"${devSetting("edumate.devToken", "")}\"")
        }
        release {
            // Production backend, hosted in asia-southeast1 (Singapore).
            buildConfigField("String", "API_BASE_URL", "\"https://api.edumate.example.sg/\"")
            buildConfigField("boolean", "USE_MOCK_AI", "false")
            buildConfigField("boolean", "USE_MOCK_LIVE", "false")
            buildConfigField("String", "DEV_TOKEN", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.nav)
    implementation(libs.compose.material3.windowsize)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)

    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.biometric)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
