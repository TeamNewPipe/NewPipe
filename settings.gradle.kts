/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.settings") version "9.2.1"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "WizeStream"

android {
    execution {
        profiles {
            create("reproducibleRelease") {
                r8 {
                    runInSeparateProcess = true
                    jvmOptions.addAll(
                        listOf(
                            "-Xmx2048m",
                            "-XX:ActiveProcessorCount=1",
                            "-Dfile.encoding=UTF-8",
                            "-Duser.language=en",
                            "-Duser.country=US",
                            "-Duser.timezone=UTC"
                        )
                    )
                }
            }
        }
        defaultProfile = "reproducibleRelease"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Shared GitHub runner IPs can hit Maven Central's rate limit before compilation.
        // Use the same public Central mirror as the CI Robolectric SDK downloads.
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        maven(url = "https://dl.cloudsmith.io/public/consensys/maven/maven/")
        maven(url = "https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/")
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org")
    }
}
include(":app") // androidApp
