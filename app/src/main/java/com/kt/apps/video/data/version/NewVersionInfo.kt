package com.kt.apps.video.data.version

data class NewVersionInfo(
    val newestVersionCode: Long = 0,
    val newestVersionName: String = "",
    val minSupportedVersionCode: Long = 0,
    val downloadMd5: String = "",
    val downloadUrl: String = "",
    val outDateVersionTitle: String = "",
    val outDateVersionSubtitle: String = "",
    val outDateVersionAction: String = "",
    val unsupportedVersionTitle: String = "",
    val unsupportedVersionSubtitle: String = "",
    val unsupportedVersionAction: String = ""
)
