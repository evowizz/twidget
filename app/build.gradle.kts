import java.util.Properties

plugins {
    alias(libs.plugins.twidget.android.application)
}

// Release signing config. Local production credentials live outside the
// checkout by default, under ~/.config/twidget/keystore.properties. CI uses
// RELEASE_* environment variables. Absent either, release builds stay
// unsigned and debug builds keep using the checked-in debug key.
val signingPropertiesFile = providers.gradleProperty("twidgetSigningProperties")
    .orNull
    ?.let { rootProject.file(it) }
    ?: File(System.getProperty("user.home"), ".config/twidget/keystore.properties")
val keystoreProperties = Properties().apply {
    signingPropertiesFile.takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)
val releaseStoreFile: String? = signingValue("storeFile", "RELEASE_STORE_FILE")
val signDebugWithRelease = providers.gradleProperty("signDebugWithRelease")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false
require(!signDebugWithRelease || releaseStoreFile != null) {
    "-PsignDebugWithRelease=true requires the release signing credentials"
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val baseVersionName = versionProperties.getProperty("versionName")
    ?.takeIf { it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) }
    ?: error("version.properties must contain a semantic version such as versionName=1.0.0")
val (versionMajor, versionMinor, versionPatch) = baseVersionName.split('.').map(String::toInt)
require(versionMinor < 1_000 && versionPatch < 1_000) {
    "Android version codes require version minor and patch components below 1000"
}

data class CommandResult(val exitCode: Int, val output: String)

// Configuration-cache safe: Gradle tracks the output and reconfigures when it changes.
fun git(vararg args: String): CommandResult = runCatching {
    val execution = providers.exec {
        commandLine("git", *args)
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }
    CommandResult(execution.result.get().exitValue, execution.standardOutput.asText.get().trim())
}.getOrElse { CommandResult(-1, "") }

// Debug builds use the commit distance from the base-version change in their
// version name so every build remains identifiable. Their version code uses a
// fixed slot above every beta, allowing trusted debug APKs to replace betas.
// Beta releases have their own sequence, supplied by the pre-release workflow,
// and reset to 1 for each base version.
val debugNumber = providers.gradleProperty("prereleaseNumber").orNull?.toIntOrNull()
    ?: run {
        val versionFileStatus = git("status", "--porcelain", "--", "version.properties")
        val versionCommit = git("log", "-1", "--format=%H", "--", "version.properties")
        if (versionFileStatus.output.isNotBlank() || versionCommit.exitCode != 0 || versionCommit.output.isBlank()) {
            1
        } else {
            git("rev-list", "--count", "${versionCommit.output}..HEAD")
                .output.toIntOrNull()?.plus(1) ?: 1
        }
    }
val betaNumber = providers.gradleProperty("betaNumber").orNull?.toIntOrNull() ?: 1
fun propertyOrEnv(propKey: String, envKey: String): String =
    providers.gradleProperty(propKey).orElse(providers.environmentVariable(envKey)).getOrElse("")
val bufferOAuthClientId = propertyOrEnv("bufferOAuthClientId", "BUFFER_OAUTH_CLIENT_ID")
val cloudinaryCloudName = propertyOrEnv("cloudinaryCloudName", "CLOUDINARY_CLOUD_NAME")
val cloudinaryUploadPreset = propertyOrEnv("cloudinaryUploadPreset", "CLOUDINARY_UPLOAD_PRESET")
require(debugNumber > 0) { "prereleaseNumber must be greater than zero" }
require(betaNumber > 0) { "betaNumber must be greater than zero" }
require(betaNumber <= 19) {
    "Beta build number $betaNumber exceeds this version's Play Store slot range; bump versionName"
}

// Reserve 100 monotonically ordered Play Store version-code slots for each
// semantic version: beta 80-98, trusted debug 98, and stable 99. A 100-code
// migration offset moves betas above the 1.3.0 stable code (100300099)
// already uploaded to Play. Keep this offset for future versions so
// beta < debug < stable and upgrades to the next version remain ordered.
// Validate the final value against Play's 2,100,000,000 ceiling.
val versionCodeBase =
    versionMajor * 100_000_000 + versionMinor * 100_000 + versionPatch * 100 + 100
val stableVersionCode = versionCodeBase + 99
require(versionMajor in 0..20 && stableVersionCode <= 2_100_000_000) {
    "versionName $baseVersionName cannot be represented as a Play Store version code"
}

android {
    namespace = "com.tjg.twidget"

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    // Widgets choose their language independently of the app/device locale.
    // Every Play install therefore needs every supported translation offline.
    bundle {
        language {
            enableSplit = false
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "IN_APP_UPDATES", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "IN_APP_UPDATES", "false")
        }
    }

    defaultConfig {
        applicationId = "com.tjg.twidget"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = stableVersionCode
        versionName = baseVersionName
        resValue("string", "buffer_oauth_client_id", bufferOAuthClientId)
        resValue("string", "cloudinary_cloud_name", cloudinaryCloudName)
        resValue("string", "cloudinary_upload_preset", cloudinaryUploadPreset)
        resValue(
            "string",
            "buffer_oauth_redirect_uri",
            "https://thatjoshguy67.github.io/twidget/oauth/buffer/",
        )
    }

    signingConfigs {
        // Default for contributors and pull requests. Trusted builds can opt
        // into the production certificate with -PsignDebugWithRelease=true so
        // they install over beta/stable builds without changing the debug
        // version suffix.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = File(releaseStoreFile).let { path ->
                    if (path.isAbsolute) path else signingPropertiesFile.parentFile.resolve(path)
                }
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug.$debugNumber"
            if (signDebugWithRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        create("beta") {
            initWith(getByName("release"))
            versionNameSuffix = "-beta.$betaNumber"
            matchingFallbacks += listOf("release")
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val versionCode = when (variant.buildType) {
            "debug" -> versionCodeBase + 98
            "beta" -> versionCodeBase + 79 + betaNumber
            else -> stableVersionCode
        }
        variant.outputs.forEach { output ->
            output.versionCode.set(versionCode)
        }
    }
}

configurations.configureEach {
    exclude(group = "androidx.core", module = "core")
    exclude(group = "androidx.core", module = "core-ktx")
    exclude(group = "androidx.customview", module = "customview")
    exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
    exclude(group = "androidx.viewpager2", module = "viewpager2")
    exclude(group = "androidx.viewpager", module = "viewpager")
    exclude(group = "androidx.appcompat", module = "appcompat")
    // Play Services pulls stock Fragment, but One UI Design supplies the SESL
    // implementation under the same AndroidX package names.
    exclude(group = "androidx.fragment", module = "fragment")
    // SESL9 Core and Fragment include their Kotlin extensions.
    exclude(group = "sesl.androidx.core", module = "core-ktx")
    exclude(group = "androidx.fragment", module = "fragment-ktx")
    exclude(group = "androidx.preference", module = "preference")
    exclude(group = "androidx.recyclerview", module = "recyclerview")
    exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
    exclude(group = "androidx.slidingpanelayout", module = "slidingpanelayout")
    exclude(group = "com.google.android.material", module = "material")
}

dependencies {
    implementation(libs.oneui.design)
    implementation(libs.lottie)
    implementation(libs.androidx.work.runtime)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.oneui.icons)
    // Strictly pinned in the version catalog, see the SESL9 comment there.
    implementation(libs.bundles.sesl9)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
