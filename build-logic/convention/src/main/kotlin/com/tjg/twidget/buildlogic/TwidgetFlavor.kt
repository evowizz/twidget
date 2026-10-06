package com.tjg.twidget.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.kotlin.dsl.invoke

@Suppress("EnumEntryName")
enum class FlavorDimension {
    distribution,
}

/**
 * Where a build is distributed.
 *
 * Each flavor's `src/<flavor>/java/com/tjg/twidget/env/BuildVars.kt` sets the matching values
 * the app reads at runtime.
 */
@Suppress("EnumEntryName")
enum class TwidgetFlavor(val dimension: FlavorDimension) {
    github(FlavorDimension.distribution),
    play(FlavorDimension.distribution),
}

/** Registers every [FlavorDimension] and [TwidgetFlavor] on [commonExtension]. */
internal fun configureFlavors(commonExtension: CommonExtension) {
    commonExtension.apply {
        FlavorDimension.entries.forEach { flavorDimension ->
            flavorDimensions += flavorDimension.name
        }

        productFlavors {
            TwidgetFlavor.entries.forEach { flavor ->
                register(flavor.name) {
                    dimension = flavor.dimension.name
                }
            }
        }
    }
}
