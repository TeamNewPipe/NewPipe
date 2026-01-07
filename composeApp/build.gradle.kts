/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.compose.multiplatform)
    alias(libs.plugins.jetbrains.compose.hotreload)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "net.newpipe.app"
        compileSdk = 36
        minSdk = 21
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.jetbrains.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jetbrains.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.newpipe.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "net.newpipe.app"
            packageVersion = "1.0.0"
        }
    }
}
