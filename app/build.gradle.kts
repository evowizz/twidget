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

fun propertyOrEnv(propKey: String, envKey: String): String =
    providers.gradleProperty(propKey).orElse(providers.environmentVariable(envKey)).getOrElse("")
val bufferOAuthClientId = propertyOrEnv("bufferOAuthClientId", "BUFFER_OAUTH_CLIENT_ID")
val cloudinaryCloudName = propertyOrEnv("cloudinaryCloudName", "CLOUDINARY_CLOUD_NAME")
val cloudinaryUploadPreset = propertyOrEnv("cloudinaryUploadPreset", "CLOUDINARY_UPLOAD_PRESET")

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
            matchingFallbacks += listOf("release")
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
    // The version catalog pins these strictly. Its SESL9 note explains why.
    implementation(libs.bundles.sesl9)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
