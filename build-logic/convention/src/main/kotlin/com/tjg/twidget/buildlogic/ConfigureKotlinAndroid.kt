package com.tjg.twidget.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/**
 * Configure base Kotlin with Android options. Kotlin support is built into
 * AGP 9 and takes its JVM target from [CommonExtension.compileOptions].
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
