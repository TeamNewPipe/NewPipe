/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("org.sonarqube")
    checkstyle
}

val gitWorkingBranch = providers.exec {
    commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
}.standardOutput.asText.map { it.trim() }

android {
    compileSdk = 36
    namespace = "org.schabi.newpipe"

    defaultConfig {
        applicationId = "org.schabi.newpipe"
        resValue("string", "app_name", "NewPipe")
        minSdk = 21
        targetSdk = 35

        versionCode = System.getProperty("versionCodeOverride")?.toInt() ?: 1005

        versionName = "0.28.0"
        System.getProperty("versionNameSuffix")?.let { versionNameSuffix = it }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true

            // suffix the app id and the app name with git branch name
            val defaultBranches = listOf("master", "dev")
            val workingBranch = gitWorkingBranch.getOrElse("")
            val normalizedWorkingBranch = workingBranch
                .replaceFirst("^[^A-Za-z]+".toRegex(), "")
                .replace("[^0-9A-Za-z]+".toRegex(), "")

            if (normalizedWorkingBranch.isEmpty() || workingBranch in defaultBranches) {
                // default values when branch name could not be determined or is master or dev
                applicationIdSuffix = ".debug"
                resValue("string", "app_name", "NewPipe Debug")
            } else {
                applicationIdSuffix = ".debug.$normalizedWorkingBranch"
                resValue("string", "app_name", "NewPipe $workingBranch")
                setProperty("archivesBaseName", "NewPipe_$normalizedWorkingBranch")
            }
        }

        release {
            System.getProperty("packageSuffix")?.let { suffix ->
                applicationIdSuffix = suffix
                resValue("string", "app_name", "NewPipe $suffix")
                setProperty("archivesBaseName", "NewPipe_" + System.getProperty("packageSuffix"))
            }
            isMinifyEnabled = true
            isShrinkResources = false // disabled to fix F-Droid"s reproducible build
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            setProperty("archivesBaseName", "app")
        }
    }

    lint {
        checkReleaseBuilds = false
        // Or, if you prefer, you can continue to check for errors in release builds,
        // but continue the build even when errors are found:
        abortOnError = false
        // suppress false warning ("Resource IDs will be non-final in Android Gradle Plugin version
        // 5.0, avoid using them in switch case statements"), which affects only library projects
        disable += "NonConstantResourceId"
    }

    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        encoding = "utf-8"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            // remove two files which belong to jsoup
            // no idea how they ended up in the META-INF dir...
            excludes += setOf(
                "META-INF/README.md",
                "META-INF/CHANGES",
                "META-INF/COPYRIGHT" // "COPYRIGHT" belongs to RxJava...
            )
        }
    }
}

val checkstyleVersion = "10.12.1"

val androidxLifecycleVersion = "2.6.2"
val androidxRoomVersion = "2.6.1"
val androidxWorkVersion = "2.8.1"

val stateSaverVersion = "1.4.1"
val exoPlayerVersion = "2.18.7"
val googleAutoServiceVersion = "1.1.1"
val groupieVersion = "2.10.1"
val markwonVersion = "4.6.2"

val leakCanaryVersion = "2.12"
val stethoVersion = "1.6.0"

val ktlint by configurations.creating

checkstyle {
    configDirectory = rootProject.file("checkstyle")
    isIgnoreFailures = false
    isShowViolations = true
    toolVersion = checkstyleVersion
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
    args = listOf("src/**/*.kt")
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.register<JavaExec>("formatKtlint") {
    inputs.files(inputFiles)
    outputs.dir(outputDir)
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.getByName("ktlint")
    args = listOf("-F", "src/**/*.kt")
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

afterEvaluate {
    tasks.named("preDebugBuild").configure {
        if (!System.getProperties().containsKey("skipFormatKtlint")) {
            dependsOn("formatKtlint")
        }
        dependsOn("runCheckstyle", "runKtlint")
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
    /** Desugaring **/
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")

    /** NewPipe libraries **/
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    // WORKAROUND: if you get errors with the NewPipeExtractor dependency, replace `v0.24.3` with
    // the corresponding commit hash, since JitPack sometimes deletes artifacts.
    // If there’s already a git hash, just add more of it to the end (or remove a letter)
    // to cause jitpack to regenerate the artifact.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:0023b22095a2d62a60cdfc87f4b5cd85c8b266c3")
    implementation("com.github.TeamNewPipe:NoNonsense-FilePicker:5.0.0")

    /** Checkstyle **/
    checkstyle("com.puppycrawl.tools:checkstyle:$checkstyleVersion")
    ktlint("com.pinterest:ktlint:0.45.2")

    /** AndroidX **/
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$androidxLifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$androidxLifecycleVersion")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.room:room-runtime:$androidxRoomVersion")
    implementation("androidx.room:room-rxjava3:$androidxRoomVersion")
    kapt("androidx.room:room-compiler:$androidxRoomVersion")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // Newer version specified to prevent accessibility regressions with RecyclerView, see:
    // https://developer.android.com/jetpack/androidx/releases/viewpager2#1.1.0-alpha01
    implementation("androidx.viewpager2:viewpager2:1.1.0-beta02")
    implementation("androidx.work:work-runtime-ktx:$androidxWorkVersion")
    implementation("androidx.work:work-rxjava3:$androidxWorkVersion")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.webkit:webkit:1.9.0")

    /** Third-party libraries **/
    implementation("com.github.livefront:bridge:v2.0.2")
    implementation("com.evernote:android-state:$stateSaverVersion")
    kapt("com.evernote:android-state-processor:$stateSaverVersion")

    // HTML parser
    implementation("org.jsoup:jsoup:1.17.2")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Media player
    implementation("com.google.android.exoplayer:exoplayer-core:$exoPlayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-dash:$exoPlayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-database:$exoPlayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-datasource:$exoPlayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-hls:$exoPlayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-smoothstreaming:$exoPlayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-ui:$exoPlayerVersion")
    implementation("com.google.android.exoplayer:extension-mediasession:$exoPlayerVersion")

    // Metadata generator for service descriptors
    compileOnly("com.google.auto.service:auto-service-annotations:$googleAutoServiceVersion")
    kapt("com.google.auto.service:auto-service:$googleAutoServiceVersion")

    // Manager for complex RecyclerView layouts
    implementation("com.github.lisawray.groupie:groupie:$groupieVersion")
    implementation("com.github.lisawray.groupie:groupie-viewbinding:$groupieVersion")

    // Image loading
    //noinspection NewerVersionAvailable,GradleDependency --> 2.8 is the last version, not 2.71828!
    implementation("com.squareup.picasso:picasso:2.8")

    // Markdown library for Android
    implementation("io.noties.markwon:core:$markwonVersion")
    implementation("io.noties.markwon:linkify:$markwonVersion")

    // Crash reporting
    implementation("ch.acra:acra-core:5.11.3")

    // Properly restarting
    implementation("com.jakewharton:process-phoenix:2.1.2")

    // Reactive extensions for Java VM
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    // RxJava binding APIs for Android UI widgets
    implementation("com.jakewharton.rxbinding4:rxbinding:4.0.0")

    // Date and time formatting
    implementation("org.ocpsoft.prettytime:prettytime:5.0.8.Final")

    /** Debugging **/
    // Memory leak detection
    debugImplementation("com.squareup.leakcanary:leakcanary-object-watcher-android:$leakCanaryVersion")
    debugImplementation("com.squareup.leakcanary:plumber-android:$leakCanaryVersion")
    debugImplementation("com.squareup.leakcanary:leakcanary-android-core:$leakCanaryVersion")
    // Debug bridge for Android
    debugImplementation("com.facebook.stetho:stetho:$stethoVersion")
    debugImplementation("com.facebook.stetho:stetho-okhttp3:$stethoVersion")

    /** Testing **/
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.6.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:$androidxRoomVersion")
    androidTestImplementation("org.assertj:assertj-core:3.24.2")
}
