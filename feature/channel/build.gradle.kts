/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// YewPlay: the "Channel" screen (proposal, page 13), in Compose.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "io.github.yewplay.feature.channel"
        compileSdk {
            version = release(NEWPIPE_VERSION_SDK_COMPILE_MAJOR) {
                minorApiLevel = NEWPIPE_VERSION_SDK_COMPILE_MINOR
            }
        }
        minSdk {
            version = release(NEWPIPE_VERSION_SDK_MIN)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.designsystem)
                implementation(libs.coil.compose)
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)
            }
        }
    }
}
