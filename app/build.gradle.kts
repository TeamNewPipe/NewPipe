/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.android.build.api.dsl.ApplicationExtension
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.jetbrains.kotlinx.serialization)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.about.libraries)
    alias(libs.plugins.koin)
    checkstyle
}

val gitWorkingBranch = providers.exec {
    commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
}.standardOutput.asText.map { it.trim() }
val defaultBranches = listOf("master", "dev")
val workingBranch = gitWorkingBranch.getOrElse("")
val normalizedWorkingBranch = workingBranch
    .replaceFirst("^[^A-Za-z]+".toRegex(), "")
    .replace("[^0-9A-Za-z]+".toRegex(), "")

kotlin {
    jvmToolchain(21)
}

configure<ApplicationExtension> {
    compileSdk {
        version = release(NEWPIPE_VERSION_SDK_COMPILE_MAJOR) {
            minorApiLevel = NEWPIPE_VERSION_SDK_COMPILE_MINOR
        }
    }
    namespace = NEWPIPE_APPLICATION_ID_OLD

    defaultConfig {
        applicationId = NEWPIPE_APPLICATION_ID_OLD
        resValue("string", "app_name", "NewPipe")
        minSdk {
            version = release(NEWPIPE_VERSION_SDK_MIN)
        }
        targetSdk {
            version = release(NEWPIPE_VERSION_SDK_TARGET)
        }

        versionCode = System.getProperty("versionCodeOverride")?.toInt() ?: NEWPIPE_VERSION_CODE

        versionName = NEWPIPE_VERSION_NAME
        System.getProperty("versionNameSuffix")?.let { versionNameSuffix = it }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true

            // suffix the app id and the app name with git branch name
            if (normalizedWorkingBranch.isEmpty() || workingBranch in defaultBranches) {
                applicationIdSuffix = ".debug"
                resValue("string", "app_name", "NewPipe Debug")
            } else {
                applicationIdSuffix = ".debug.$normalizedWorkingBranch"
                resValue("string", "app_name", "NewPipe $workingBranch")
            }
        }

        release {
            System.getProperty("packageSuffix")?.let { suffix ->
                applicationIdSuffix = suffix
                resValue("string", "app_name", "NewPipe $suffix")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        register("continuous") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            isDefault = true

            // suffix the app id and the app name with git branch name
            if (normalizedWorkingBranch.isEmpty() || workingBranch in defaultBranches) {
                applicationIdSuffix = ".continuous"
                resValue("string", "app_name", "NewPipe Continuous")
            } else {
                applicationIdSuffix = ".continuous.$normalizedWorkingBranch"
                resValue("string", "app_name", "NewPipe $workingBranch")
            }

            // Disable baseline profiles to fix INSTALL_BASELINE_PROFILE_FAILED
            @Suppress("UnstableApiUsage")
            experimentalProperties["android.experimental.art-profile.enable"] = false
            @Suppress("UnstableApiUsage")
            experimentalProperties["android.experimental.baseline-profile.enable"] = false
        }
    }

    lint {
        lintConfig = file("lint.xml")
        // Continue the debug build even when errors are found
        abortOnError = false
    }

    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
        encoding = "utf-8"
    }

    sourceSets {
        getByName("androidTest") {
            assets.directories += "$projectDir/schemas"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        viewBinding = false
        buildConfig = true
        resValues = true
        compose = true
    }

    packaging {
        resources {
            // remove two files which belong to jsoup
            // no idea how they ended up in the META-INF dir...
            excludes += setOf(
                "META-INF/README.md",
                "META-INF/CHANGES",
                "META-INF/COPYRIGHT", // "COPYRIGHT" belongs to RxJava...
                "**/baseline-prof.txt",
                "**/baseline.prof",
                "**/baseline.profm"
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


// Custom dependency configuration for ktlint
val ktlint by configurations.creating

checkstyle {
    configDirectory = rootProject.file("checkstyle")
    isIgnoreFailures = false
    isShowViolations = true
    toolVersion = libs.versions.checkstyle.get()
}

tasks.register<Checkstyle>("runCheckstyle") {
    source("src")
    include("**/*.java")
    exclude("**/gen/**")
    exclude("**/R.java")
    exclude("**/BuildConfig.java")
    exclude("main/java/us/shandian/giga/**")

    classpath = configurations.getByName("checkstyle")

    isShowViolations = true

    reports {
        xml.required = true
        html.required = true
    }
}

val outputDir = project.layout.buildDirectory.dir("reports/ktlint/")
val inputFiles = fileTree("src") { include("**/*.kt") }

tasks.register<JavaExec>("runKtlint") {
    inputs.files(inputFiles)
    outputs.dir(outputDir)
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.getByName("ktlint")
    args = listOf("--editorconfig=../.editorconfig", "src/**/*.kt")
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.register<JavaExec>("formatKtlint") {
    inputs.files(inputFiles)
    outputs.dir(outputDir)
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.getByName("ktlint")
    args = listOf("--editorconfig=../.editorconfig", "-F", "src/**/*.kt")
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.register<CheckDependenciesOrder>("checkDependenciesOrder") {
    tomlFile = layout.projectDirectory.file("../gradle/libs.versions.toml")
}

afterEvaluate {
    tasks.named("preDebugBuild").configure {
        /*if (!System.getProperties().containsKey("skipFormatKtlint")) {
            dependsOn("formatKtlint")
        }
        dependsOn("runCheckstyle", "runKtlint", "checkDependenciesOrder")*/
    }
}

sonar {
    properties {
        property("sonar.projectKey", "TeamNewPipe_NewPipe")
        property("sonar.organization", "teamnewpipe")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

dependencies {
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    // Desugaring
    coreLibraryDesugaring(libs.android.desugar)

    // NewPipe libraries
    implementation(projects.shared)

    implementation(libs.newpipe.extractor)


    // Checkstyle
    checkstyle(libs.puppycrawl.checkstyle)
    ktlint(libs.pinterest.ktlint)

    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.nononsenseapps.filepicker)

    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.media)
    implementation(libs.androidx.preference)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.ktx)
    implementation(libs.google.android.material)
    implementation(libs.androidx.webkit)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Third-party libraries
    implementation(libs.livefront.bridge)
    implementation(libs.evernote.statesaver.core)
    kapt(libs.evernote.statesaver.compiler)

    // HTML parser
    implementation(libs.ksoup)

    // HTTP client
    implementation(libs.squareup.okhttp)

    // Media player
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.ui)



    // Compose
    implementation(libs.androidx.activity)
    implementation(libs.jetbrains.compose.ui)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.runtime)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.jetbrains.navigation3.ui)
    implementation(libs.jetbrains.lifecycle.navigation3)
    implementation(libs.koin.compose.navigation3)
    implementation(libs.koin.compose.viewmodel)
    implementation("androidx.compose.material:material-icons-extended:1.7.0")

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Markdown library for Android
    implementation(libs.noties.markwon.core)
    implementation(libs.noties.markwon.linkify)

    // Crash reporting
    implementation(libs.acra.core)
    compileOnly(libs.google.autoservice.annotations)
    ksp(libs.zacsweers.autoservice.compiler)

    // Properly restarting
    implementation(libs.jakewharton.phoenix)

    // Date and time formatting
    implementation(libs.ocpsoft.prettytime)

    // Debugging and memory leak detection
    debugImplementation(libs.squareup.leakcanary.watcher)
    debugImplementation(libs.squareup.leakcanary.plumber)
    debugImplementation(libs.squareup.leakcanary.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.assertj.core)
    androidTestImplementation(libs.androidx.compose.test.ui.junit)
    debugImplementation(libs.androidx.compose.test.ui.manifest)
}

aboutLibraries {
    collect {
        configPath = file("../config/aboutlibraries")
    }
    export {
        outputFile = file("../shared/src/androidMain/assets/aboutlibraries.json")
        prettyPrint = true
        excludeFields.addAll("organization", "scm", "funding")
    }
    library {
        exclusionPatterns = listOf(
            Pattern.compile("^com\\.github\\.TeamNewPipe:NewPipeExtractor$"),
            Pattern.compile("^com\\.evernote:android-state$")
        )
    }
}

// Workaround for INSTALL_BASELINE_PROFILE_FAILED: disable all art/baseline profile tasks
tasks.matching {
    it.name.contains("ArtProfile", ignoreCase = true) ||
            it.name.contains("BaselineProfile", ignoreCase = true)
}.configureEach {
    enabled = false
}
