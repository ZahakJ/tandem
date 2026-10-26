import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Discord status (DiscordPresence.kt). Discord's Social SDK is proprietary, so it is never in the repository: a builder
// who has it puts discord_partner_sdk.aar under third_party/discord/ and the application ID in local.properties
// (`discord.appId`, or a `discord.appId` Gradle property); see docs/guide/building.md. Without both, the app builds
// as before: a no-op backend (src/nodiscord) and no Discord setting.
val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) } }
val discordAppId = (localProps.getProperty("discord.appId") ?: providers.gradleProperty("discord.appId").orNull)
    ?.trim()?.takeIf { it.matches(Regex("[0-9]+")) }
val discordAar = rootProject.file("third_party/discord").takeIf { it.isDirectory }?.walkTopDown()
    ?.filter { it.isFile && it.name == "discord_partner_sdk.aar" }?.sortedBy { "debug" in it.path }?.firstOrNull()

val sdkDir: String? = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: localProps.getProperty("sdk.dir")

/** The newest NDK or CMake ([what]: "ndk", "cmake") installed in the Android SDK, so a read-only SDK (Nix) needs no download. */
fun newestInSdk(what: String): String? {
    val sdk = sdkDir ?: return null
    val parts = { v: String -> v.split('.').map { it.toIntOrNull() ?: 0 } }
    return File(sdk, what).listFiles()?.filter { it.isDirectory }?.map { it.name }
        ?.maxWithOrNull { a, b -> parts(a).zip(parts(b)).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0 }
}

val discordNdk = if (discordAar != null) newestInSdk("ndk") else null
val withDiscord = discordAar != null && discordAppId != null && discordNdk != null
if (discordAar != null && !withDiscord) {
    logger.warn("Discord SDK found but " + (if (discordAppId == null) "no discord.appId" else "no NDK installed") + ": building without Discord")
}

// Controller passthrough helper (src/padlink/padlink.c, PadLink.kt): a small arm64 executable built with the NDK's
// clang. It goes into the APK as a native library (lib*.so, so the installer unpacks it, executable, into Tandem's
// library folder, where Shizuku or su can run it). Without an NDK the app builds without it and the feature stays
// hidden.
val padlinkClang: File? = newestInSdk("ndk")?.let { v ->
    val os = System.getProperty("os.name").lowercase()
    val host = when { "linux" in os -> "linux-x86_64"; "mac" in os -> "darwin-x86_64"; else -> "windows-x86_64" }
    File(sdkDir, "ndk/$v/toolchains/llvm/prebuilt/$host/bin/aarch64-linux-android30-clang" + if ("windows" in os) ".cmd" else "")
}?.takeIf { it.isFile }
val padlinkLibs = layout.buildDirectory.dir("padlink/jniLibs")
val buildPadLink = padlinkClang?.let { clang ->
    tasks.register<Exec>("buildPadLink") {
        val src = file("src/padlink/padlink.c")
        val out = padlinkLibs.map { it.file("arm64-v8a/libpadlink.so") }
        inputs.file(src)
        outputs.file(out)
        doFirst { out.get().asFile.parentFile.mkdirs() }
        commandLine(clang.path, "-O2", "-Wall", "-Wextra", "-fPIE", "-pie", "-s", "-o", out.get().asFile.path, src.path)
    }
}
if (buildPadLink == null) logger.warn("No NDK found: building without the controller passthrough helper")

android {
    namespace = "io.github.zahakj.tandem"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "io.github.zahakj.tandem"
        minSdk = 30
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.0"
        buildConfigField("long", "DISCORD_APP_ID", "${if (withDiscord) discordAppId else "0"}L")
        if (withDiscord) {
            // Where Discord sends the authorization back: discord-<application ID>:/authorize/callback.
            manifestPlaceholders["discordScheme"] = "discord-$discordAppId"
            // The SDK's native library is about 9 MB per ABI; Tandem's handhelds are 64-bit ARM.
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    if (withDiscord) {
        ndkVersion = discordNdk!!
        externalNativeBuild {
            cmake {
                path = file("src/discord/cpp/CMakeLists.txt")
                newestInSdk("cmake")?.let { version = it }
            }
        }
    }
    sourceSets {
        getByName("main").java.srcDir(if (withDiscord) "src/discord/java" else "src/nodiscord/java")
        // Build-type manifests merge over the main one: the SDK's sign-in activity and its redirect, only with the SDK.
        if (withDiscord) listOf("debug", "release").forEach { getByName(it).manifest.srcFile("src/discord/AndroidManifest.xml") }
        if (buildPadLink != null) getByName("main").jniLibs.srcDir(padlinkLibs)
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
            if (withDiscord) proguardFiles("src/discord/proguard-rules.pro")
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
    // Native libraries are unpacked on install, so the passthrough helper can be run as a program.
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures {
        compose = true
        buildConfig = true
        prefab = withDiscord
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    // Video wallpapers and video snaps (muted, looping, hardware decoded).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Optional: lets Tandem check whether an emulator is still running, so it can resume instead of restart.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Installs the baseline profile (src/main/baseline-prof.txt) on sideloaded installs, so ART compiles the hot
    // paths (Compose, the main screens) ahead of time instead of interpreting them on the first transitions.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    // Frame timing overlay (Settings › Data & about › Developer): logs janky frames with the current screen.
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")

    if (withDiscord) {
        implementation(files(discordAar!!))
        // The SDK's sign-in opens a Custom Tab when the Discord app isn't installed.
        implementation("androidx.browser:browser:1.8.0")
    }

    // JVM unit tests (./gradlew testDebugUnitTest). kxml2 is the XML pull parser Android itself uses.
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}

if (buildPadLink != null) {
    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach { dependsOn(buildPadLink) }
}
