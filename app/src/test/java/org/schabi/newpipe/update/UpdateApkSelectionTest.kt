package org.schabi.newpipe.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateApkSelectionTest {
    private val armName = "wizestream_v1.51.0.apk"
    private val x86Name = "wizestream_v1.51.0_x86_64.apk"
    private val armUrl = "https://example.invalid/releases/arm.apk"
    private val x86Url = "https://example.invalid/releases/x86_64.apk"
    private val armDigest = "ab".repeat(32)
    private val x86Digest = "cd".repeat(32)
    private val armAsset = asset(armName, armUrl, 12345L, "sha256:$armDigest")
    private val x86Asset = asset(x86Name, x86Url, 23456L, "sha256:$x86Digest")

    @Test
    fun armDevicesChooseTheArmBuildAndItsMetadataInEitherAssetOrder() {
        val devices = listOf(
            listOf("arm64-v8a"),
            listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
            listOf("armeabi-v7a", "armeabi")
        )
        for (abis in devices) {
            for (assets in listOf(listOf(armAsset, x86Asset), listOf(x86Asset, armAsset))) {
                val release = parse(abis, assets)
                assertEquals(armName, release.apkName)
                assertEquals(armUrl, release.apkUrl)
                assertEquals(12345L, release.apkSize)
                assertEquals(armDigest, release.apkSha256)
            }
        }
    }

    @Test
    fun x86DevicesChooseTheNativeBuildAndItsMetadataInEitherAssetOrder() {
        for (abis in listOf(listOf("x86_64"), listOf("x86_64", "x86"))) {
            for (assets in listOf(listOf(armAsset, x86Asset), listOf(x86Asset, armAsset))) {
                val release = parse(abis, assets)
                assertEquals(x86Name, release.apkName)
                assertEquals(x86Url, release.apkUrl)
                assertEquals(23456L, release.apkSize)
                assertEquals(x86Digest, release.apkSha256)
            }
        }
    }

    @Test
    fun nativeX86TakesPriorityOverAdvertisedArmTranslation() {
        val devices = listOf(
            listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a"),
            listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        )
        for (abis in devices) {
            for (assets in listOf(listOf(armAsset, x86Asset), listOf(x86Asset, armAsset))) {
                assertEquals(x86Name, parse(abis, assets).apkName)
            }
        }
    }

    @Test
    fun missingNativeBuildKeepsTheReleasePageWithoutSubstitutingArmTranslation() {
        val devices = listOf(
            listOf("x86_64"),
            listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")
        )
        for (abis in devices) {
            val release = parse(abis, listOf(armAsset))
            assertNoDownload(release)
            assertEquals("v1.51.0", release.version)
            assertEquals("Release notes", release.body)
            assertEquals(
                "https://github.com/wizdom13/WizeStream/releases/tag/v1.51.0",
                release.htmlUrl
            )
            assertEquals(
                release,
                WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(release))
            )
        }
    }

    @Test
    fun unsupportedDevicesAndMissingArmBuildHaveNoAutomaticDownload() {
        for (abis in listOf(emptyList(), listOf("x86"), listOf("riscv64"), listOf("armeabi"))) {
            assertNoDownload(parse(abis, listOf(armAsset, x86Asset)))
        }
        for (abis in listOf(listOf("arm64-v8a"), listOf("armeabi-v7a"))) {
            assertNoDownload(parse(abis, listOf(x86Asset)))
            assertNoDownload(parse(abis, emptyList()))
        }
    }

    @Test
    fun legacyUnsuffixedSingleAssetIsAnArmBuildRatherThanAnAssumedUniversalBuild() {
        val legacy = asset("wizestream_v1.0.0.apk", armUrl, 12345L, "sha256:$armDigest")
        val arm = parse(listOf("armeabi-v7a"), listOf(legacy), "v1.0.0")
        assertEquals("wizestream_v1.0.0.apk", arm.apkName)
        assertEquals(armUrl, arm.apkUrl)
        assertNoDownload(parse(listOf("x86_64"), listOf(legacy), "v1.0.0"))
    }

    @Test
    fun unknownApksAndAssetsForAnotherVersionCannotBecomeUpdates() {
        val unknown = listOf(
            "WizeStream.apk",
            "wizestream_v1.51.0_universal.apk",
            "wizestream_v1.51.0_x86.apk",
            "wizestream_v1.51.0_debug.apk",
            "wizestream_v9.0.0.apk",
            "wizestream_v9.0.0_x86_64.apk",
            "wizestream_v1.51.0.apk.asc"
        ).map { asset(it, armUrl, 12345L, "sha256:$armDigest") }
        for (abis in listOf(listOf("arm64-v8a"), listOf("x86_64"))) {
            assertNoDownload(parse(abis, unknown))
        }
        assertEquals(armName, parse(listOf("arm64-v8a"), unknown + armAsset).apkName)
        assertEquals(x86Name, parse(listOf("x86_64"), unknown + x86Asset).apkName)
    }

    @Test
    fun x86ChecksumValidationStillRejectsMissingOrMalformedDigest() {
        for (digest in listOf("", "sha512:${"ab".repeat(64)}", "sha256:not-a-checksum")) {
            val x86 = asset(x86Name, x86Url, 23456L, digest)
            val release = parse(listOf("x86_64"), listOf(armAsset, x86))
            assertEquals(x86Name, release.apkName)
            assertEquals(23456L, release.apkSize)
            assertNull(release.apkSha256)
        }
    }

    private fun assertNoDownload(release: WizeStreamUpdateRepository.Release) {
        assertNull(release.apkName)
        assertNull(release.apkUrl)
        assertNull(release.apkSize)
        assertNull(release.apkSha256)
    }

    private fun parse(
        abis: List<String>,
        assets: List<String>,
        version: String = "v1.51.0"
    ): WizeStreamUpdateRepository.Release {
        val json = """
            [{
              "tag_name": "$version",
              "name": "WizeStream $version",
              "html_url": "https://github.com/wizdom13/WizeStream/releases/tag/$version",
              "body": "Release notes",
              "assets": [${assets.joinToString(",")}]
            }]
        """.trimIndent()
        return WizeStreamUpdateRepository.parseReleases(json, abis).single()
    }

    private fun asset(name: String, url: String, size: Long, digest: String): String {
        return """
            {
              "name": "$name",
              "browser_download_url": "$url",
              "size": $size,
              "digest": "$digest"
            }
        """.trimIndent()
    }
}
