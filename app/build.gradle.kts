/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.jetbrains.kotlinx.serialization)
    alias(libs.plugins.squareup.wire)
    checkstyle
}

val releaseStoreFile = System.getenv("WIZESTREAM_RELEASE_STORE_FILE")
    ?: System.getenv("NEWPIPE_MATERIAL_RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("WIZESTREAM_RELEASE_STORE_PASSWORD")
    ?: System.getenv("NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("WIZESTREAM_RELEASE_KEY_ALIAS")
    ?: System.getenv("NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("WIZESTREAM_RELEASE_KEY_PASSWORD")
    ?: System.getenv("NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val releaseAbi = providers.gradleProperty("releaseAbi").orElse("arm").get()
val releaseAbiFilters = when (releaseAbi) {
    "arm" -> setOf("arm64-v8a", "armeabi-v7a")

    "x86_64" -> setOf("x86_64")

    else -> throw GradleException(
        "Unsupported releaseAbi '$releaseAbi'. Expected 'arm' or 'x86_64'."
    )
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // TODO: Drop annotation default target when it is stable
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property"
        )
    }
}

configure<ApplicationExtension> {
    buildToolsVersion = "36.1.0"
    ndkVersion = "28.2.13676358"

    compileSdk {
        version = release(ANDROID_COMPILE_SDK_MAJOR) {
            minorApiLevel = ANDROID_COMPILE_SDK_MINOR
        }
    }
    namespace = UPSTREAM_NEWPIPE_NAMESPACE

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = WIZESTREAM_APPLICATION_ID
        resValue("string", "app_name", "WizeStream")
        minSdk {
            version = release(ANDROID_MIN_SDK)
        }
        targetSdk {
            version = release(ANDROID_TARGET_SDK)
        }

        versionCode = System.getProperty("versionCodeOverride")?.toInt()
            ?: WIZESTREAM_VERSION_CODE

        versionName = WIZESTREAM_VERSION_NAME
        System.getProperty("versionNameSuffix")?.let { versionNameSuffix = it }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasReleaseSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "WizeStream Debug")
        }

        release {
            ndk {
                abiFilters += releaseAbiFilters
            }
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        lintConfig = file("lint.xml")
        baseline = file("lint-baseline.xml")
        // Treat newly reported lint errors as CI failures.
        abortOnError = true
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
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            // remove two files which belong to jsoup
            // no idea how they ended up in the META-INF dir...
            excludes += setOf(
                "META-INF/README.md",
                "META-INF/CHANGES",
                // "COPYRIGHT" belongs to RxJava...
                "META-INF/COPYRIGHT",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room writes one schema file per database version, so new schemas must not be exported concurrently.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
    mustRunAfter("kspDebugKotlin")
}

wire {
    java {
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
    exclude("main/java/org/schabi/newpipe/extractor/**")
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
        if (!System.getProperties().containsKey("skipFormatKtlint")) {
            dependsOn("formatKtlint")
        }
        dependsOn("runCheckstyle", "runKtlint", "checkDependenciesOrder")
    }
}

dependencies {
    // Desugaring
    coreLibraryDesugaring(libs.android.desugar)

    // Bounded linear-time expressions for user-defined content filters
    implementation(libs.re2j)

    // NewPipe libraries
    implementation(libs.newpipe.nanojson)
    implementation("com.github.evermind-zz:hlsdownloader:1.0.0")
    implementation(libs.newpipe.filepicker)

    // Integrated extractor sources
    implementation("org.apache.commons:commons-lang3:3.8.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.brotli:dec:0.1.1")
    implementation("org.java-websocket:Java-WebSocket:1.4.1")
    implementation("org.mozilla:rhino:1.7.13")
    implementation("org.nibor.autolink:autolink:0.10.0")
    implementation("com.google.protobuf:protobuf-java:3.11.0")
    implementation("com.github.spotbugs:spotbugs-annotations:4.8.3")
    compileOnly("org.json:json:20231013")

    // Open casting support for FCast and Chromecast-compatible receivers
    implementation("org.fcast:sender-sdk:0.6.1")

    // JNA 5.17 fixes Android 16 KB page-size support in libjnidispatch.so.
    implementation("net.java.dev.jna:jna:5.17.0@aar")

    // Checkstyle
    checkstyle(libs.puppycrawl.checkstyle)
    ktlint(libs.pinterest.ktlint)

    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.media)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.rxjava3)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.rxjava3)
    implementation(libs.google.android.material)
    implementation(libs.androidx.webkit)

    // Coroutines interop
    implementation(libs.kotlinx.coroutines.rx3)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Third-party libraries
    implementation(libs.livefront.bridge)
    implementation(libs.evernote.statesaver.core)
    implementation(libs.ntbl.lame)
    kapt(libs.evernote.statesaver.compiler)

    // HTML parser
    implementation(libs.jsoup)

    // End-to-end encrypted peer-to-peer device synchronization
    implementation(libs.jvm.libp2p) {
        exclude(group = "io.netty", module = "netty-codec-native-quic")
        exclude(group = "io.netty", module = "netty-tcnative-boringssl-static")
    }

    // Keep jvm-libp2p's Bouncy Castle modules aligned with the integrated extractor.
    constraints {
        implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
        implementation("org.bouncycastle:bcprov-jdk18on:1.85")
        implementation("org.bouncycastle:bcutil-jdk18on:1.85")
    }

    // HTTP client
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.brotli)

    // Media player
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.ui)

    // Manager for complex RecyclerView layouts
    implementation(libs.lisawray.groupie.core)
    implementation(libs.lisawray.groupie.viewbinding)

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

    // Reactive extensions
    implementation(libs.reactivex.rxjava)
    implementation(libs.reactivex.rxandroid)
    // RxJava binding APIs for Android UI widgets
    implementation(libs.jakewharton.rxbinding)

    // Date and time formatting
    implementation(libs.ocpsoft.prettytime)

    // QR code generation and scanning for device pairing
    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)

    // Debugging and memory leak detection
    debugImplementation(libs.squareup.leakcanary.watcher)
    debugImplementation(libs.squareup.leakcanary.plumber)
    debugImplementation(libs.squareup.leakcanary.core)
    // Debug bridge for Android
    debugImplementation(libs.facebook.stetho.core)
    debugImplementation(libs.facebook.stetho.okhttp3)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.13.4")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.assertj.core)
}
