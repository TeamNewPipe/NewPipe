/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// YewPlay: contracts between the screens and the data. The :app module implements them on top
// of NewPipe's database and services, so the screens never see the database directly.
plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
}
