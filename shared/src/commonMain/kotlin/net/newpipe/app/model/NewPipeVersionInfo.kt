/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewPipeVersionResponse(
    val flavors: Flavors
)

@Serializable
data class Flavors(
    val newpipe: NewPipeVersionInfo
)

@Serializable
data class NewPipeVersionInfo(
    val version: String,
    @SerialName("version_code")
    val versionCode: Int,
    val apk: String
)
