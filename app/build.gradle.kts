import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.zahakj.tandem"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "io.github.zahakj.tandem"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.1"
    }

    // Local release signing. CI (the release workflow) builds unsigned and signs with apksigner, so nothing here
    // applies there. Locally, a keystore.properties in the project root (storeFile, storePassword, keyAlias,
    // keyPassword; never committed) signs with the release key; without one, release builds are signed with the
    // debug key so `assembleRelease` gives an installable, fast APK for testing. See docs/PERFORMANCE.md.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    val onCi = System.getenv("CI") == "true"
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes (inlining, removing Compose's debug-only paths). That matters a lot for
            // Compose: a debug build runs unoptimized code and stutters whenever a new screen is composed.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                keystoreProps != null -> signingConfigs.getByName("release")
                onCi -> null
                else -> signingConfigs.getByName("debug")
            }
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Video wallpapers and video snaps (muted, looping, hardware decoded).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Optional: lets Tandem check whether an emulator is still running, so it can resume instead of restart.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Installs the baseline profile (src/main/baseline-prof.txt) on sideloaded installs, so ART compiles the hot
    // paths (Compose, the main screens) ahead of time instead of interpreting them on the first transitions.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Frame timing overlay (Settings › Data & about › Developer): logs janky frames with the current screen.
    implementation("androidx.metrics:metrics-performance:1.0.0")

    // JVM unit tests (./gradlew testDebugUnitTest). kxml2 is the XML pull parser Android itself uses.
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}
