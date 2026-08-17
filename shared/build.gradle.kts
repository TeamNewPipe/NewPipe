/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.compose.multiplatform)
    alias(libs.plugins.koin)
    alias(libs.plugins.jetbrains.kotlinx.serialization)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.about.libraries)
}

// Better than adding a third-party dependency for something as simple as this
// https://stackoverflow.com/a/74771876/8446131
val buildConfigGenerator by tasks.registering(Sync::class) {
    val buildConfigPackage = NEWPIPE_APPLICATION_ID_NEW
    val rawClass = """
        package $buildConfigPackage

        object BuildConfig {
            const val VERSION_NAME = "$NEWPIPE_VERSION_NAME"
            const val APP_NAME = "NewPipe"
        }
    """.trimIndent()
    from(resources.text.fromString(rawClass)) {
        rename { "BuildConfig.kt" }
        into(buildConfigPackage.replace(".", "/"))
    }
    into(layout.buildDirectory.dir("generated/kotlin/"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(buildConfigGenerator)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-Xexplicit-backing-fields"
        )
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }

    android {
        namespace = NEWPIPE_APPLICATION_ID_NEW
        compileSdk {
            version = release(NEWPIPE_VERSION_SDK_COMPILE_MAJOR) {
                minorApiLevel = NEWPIPE_VERSION_SDK_COMPILE_MINOR
            }
        }
        minSdk {
            version = release(NEWPIPE_VERSION_SDK_MIN)
        }
        androidResources {
            enable = true
        }

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-proguard-rules.pro")
            }
        }

        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
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
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.java)
            }
        }
        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        commonMain {
            kotlin.srcDir(buildConfigGenerator.map { it.destinationDir })
            dependencies {
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.resources)
                implementation(libs.jetbrains.compose.preview)

                implementation(libs.jetbrains.lifecycle.viewmodel)

                // Use API as java compiler cannot see NavKey for some reason
                api(libs.jetbrains.navigation3.ui)
                implementation(libs.jetbrains.lifecycle.navigation3)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.koin.compose.navigation3)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.koin.annotations)

                implementation(libs.russhwolf.settings.core)
                implementation(libs.touchlab.kermit)

                implementation(libs.ktorfit.lib)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test.core)
            implementation(libs.jetbrains.compose.test.ui)
            implementation(libs.russhwolf.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.jetbrains.compose.preview)
            implementation(libs.androidx.activity)
            implementation(libs.androidx.preference)
            implementation(libs.androidx.browser)
            implementation(libs.ktor.client.okhttp)
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.compose.test.ui.manifest)
                implementation(libs.androidx.compose.test.ui.junit)

                // Needed because androidx.compose.test.ui.junit pulls an older dependency
                // which crashes on new Android versions
                implementation(libs.androidx.test.espresso.core)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.jetbrains.compose.tooling)
}

aboutLibraries {
    export {
        outputFile = file("src/iosMain/resources/aboutlibraries.json")
        prettyPrint = true
        variant = "metadataIosMain"
        excludeFields.addAll("organization", "scm", "funding")
    }
}
