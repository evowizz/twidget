package com.tjg.twidget.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/**
 * Sets the compile SDK, min SDK and Java version on [commonExtension].
 *
 * AGP 9's built-in Kotlin takes its JVM target from [CommonExtension.compileOptions], so this
 * sets no Kotlin options.
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        compileSdk {
            version = release(37) {
                minorApiLevel = 2
            }
        }

        defaultConfig.apply {
            minSdk { version = release(26) }
        }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
