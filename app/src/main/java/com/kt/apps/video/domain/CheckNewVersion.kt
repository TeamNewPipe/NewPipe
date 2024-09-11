package com.kt.apps.video.domain

import java.io.File

sealed interface CheckNewVersion {
    data class HintNewVersion(
        val title: CharSequence,
        val subtitle: CharSequence,
        val action: CharSequence,
        val apkFile: File,
        val newVersionCode: Long,
    ) : CheckNewVersion

    data class UnsupportedVersion(
        val title: CharSequence,
        val subtitle: CharSequence,
        val action: CharSequence,
        val apkFile: File
    ) : CheckNewVersion

    data object Gone : CheckNewVersion
}
