package org.schabi.newpipe.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.update.WizeStreamUpdateRepository.VersionComparison

class WizeStreamUpdateRepositoryTest {
    @Test
    fun compareInstalledToLatestDetectsNewerMinorRelease() {
        assertEquals(
            VersionComparison.NEWER,
            WizeStreamUpdateRepository.compareInstalledToLatest("1.0.0", "1.1.0")
        )
    }

    @Test
    fun compareInstalledToLatestTreatsMatchingVPrefixedTagAsCurrent() {
        assertEquals(
            VersionComparison.SAME_OR_OLDER,
            WizeStreamUpdateRepository.compareInstalledToLatest("1.1.1", "v1.1.1")
        )
    }

    @Test
    fun compareInstalledToLatestMigratesLegacyMSeriesToOnePointZero() {
        assertEquals(
            VersionComparison.NEWER,
            WizeStreamUpdateRepository.compareInstalledToLatest("0.28.8-m14", "v1.0.0")
        )
    }

    @Test
    fun compareInstalledToLatestPreservesLegacyMSeriesOrdering() {
        assertEquals(
            VersionComparison.NEWER,
            WizeStreamUpdateRepository.compareInstalledToLatest("0.28.8", "0.28.8-m14")
        )
        assertEquals(
            VersionComparison.SAME_OR_OLDER,
            WizeStreamUpdateRepository.compareInstalledToLatest("0.28.8-m14", "0.28.8")
        )
    }

    @Test
    fun compareInstalledToLatestUsesSemanticPrereleasePrecedence() {
        assertEquals(
            VersionComparison.NEWER,
            WizeStreamUpdateRepository.compareInstalledToLatest(
                "1.1.0-beta.2",
                "1.1.0-beta.11"
            )
        )
        assertEquals(
            VersionComparison.NEWER,
            WizeStreamUpdateRepository.compareInstalledToLatest("1.1.0-beta.11", "1.1.0")
        )
        assertEquals(
            VersionComparison.SAME_OR_OLDER,
            WizeStreamUpdateRepository.compareInstalledToLatest("1.1.0", "1.1.0-rc.1")
        )
    }

    @Test
    fun compareInstalledToLatestFailsSafelyForUnknownInstalledVersion() {
        assertEquals(
            VersionComparison.UNKNOWN,
            WizeStreamUpdateRepository.compareInstalledToLatest("wizestream-dev", "v1.0.0")
        )
        assertEquals(
            VersionComparison.UNKNOWN,
            WizeStreamUpdateRepository.compareInstalledToLatest("1.0.0", "v1.01.0")
        )
    }

    @Test
    fun selectLatestCandidateReleaseIgnoresDrafts() {
        val draft = release("1.1.0", "2026-02-01T00:00:00Z", draft = true)
        val published = release("1.0.0", "2026-01-01T00:00:00Z")

        assertEquals(
            published,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(draft, published))
        )
    }

    @Test
    fun selectLatestCandidateReleaseIncludesPrereleases() {
        val stable = release("1.0.0", "2026-01-01T00:00:00Z")
        val prerelease = release("1.1.0-beta.1", "2026-01-02T00:00:00Z", prerelease = true)

        assertEquals(
            prerelease,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(stable, prerelease))
        )
    }

    @Test
    fun selectLatestCandidateReleaseUsesHighestSemanticVersion() {
        val higherVersion = release("1.1.0", "2026-01-01T00:00:00Z")
        val laterBackport = release("1.0.1", "2026-01-02T00:00:00Z")

        assertEquals(
            higherVersion,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(
                listOf(higherVersion, laterBackport)
            )
        )
    }

    @Test
    fun selectLatestCandidateReleaseUsesPublishTimeToBreakVersionTies() {
        val older = release("1.1.0", "2026-01-01T00:00:00Z")
        val newer = release("v1.1.0", "2026-01-02T00:00:00Z")

        assertEquals(
            newer,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(older, newer))
        )
    }

    @Test
    fun selectLatestCandidateReleaseHandlesEmptyListSafely() {
        assertNull(WizeStreamUpdateRepository.selectLatestCandidateRelease(emptyList()))
    }

    @Test
    fun selectLatestCandidateReleaseCanSelectPrereleaseNewerThanInstalled() {
        val prerelease = release("1.1.0-beta.1", "2026-01-02T00:00:00Z", prerelease = true)

        assertEquals(
            prerelease,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(prerelease))
        )
        assertEquals(
            VersionComparison.NEWER,
            WizeStreamUpdateRepository.compareInstalledToLatest("1.0.0", prerelease.version)
        )
    }

    @Test
    fun selectLatestCandidateReleaseIgnoresMalformedVersions() {
        val malformed = release("latest", "2026-02-01T00:00:00Z")
        val semantic = release("1.0.0", "2026-01-01T00:00:00Z")

        assertEquals(
            semantic,
            WizeStreamUpdateRepository.selectLatestCandidateRelease(listOf(malformed, semantic))
        )
    }

    @Test
    fun parseReleasesReadsGitHubSha256Digest() {
        val releases = WizeStreamUpdateRepository.parseReleases(
            releaseJson("sha256:${"ab".repeat(32)}"),
            listOf("arm64-v8a")
        )

        assertEquals("ab".repeat(32), releases.single().apkSha256)
        assertEquals(12_345L, releases.single().apkSize)
    }

    @Test
    fun parseReleasesRejectsUnsupportedOrMalformedDigest() {
        val unsupported = WizeStreamUpdateRepository.parseReleases(
            releaseJson("sha512:${"ab".repeat(64)}"),
            listOf("armeabi-v7a")
        )
        val malformed = WizeStreamUpdateRepository.parseReleases(
            releaseJson("sha256:not-a-checksum"),
            listOf("arm64-v8a")
        )

        assertNull(unsupported.single().apkSha256)
        assertNull(malformed.single().apkSha256)
    }

    private fun releaseJson(digest: String): String {
        return """
            [
              {
                "tag_name": "v1.0.0",
                "name": "WizeStream 1.0.0",
                "html_url": "https://github.com/wizdom13/WizeStream/releases/tag/v1.0.0",
                "body": "Release notes",
                "published_at": "2026-07-21T00:00:00Z",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "wizestream_v1.0.0.apk",
                    "browser_download_url": "https://example.invalid/WizeStream.apk",
                    "size": 12345,
                    "digest": "$digest"
                  }
                ]
              }
            ]
        """.trimIndent()
    }

    private fun release(
        version: String,
        publishedAt: String,
        draft: Boolean = false,
        prerelease: Boolean = false
    ) = WizeStreamUpdateRepository.Release(
        version = version,
        title = version,
        htmlUrl = "https://github.com/wizdom13/WizeStream/releases/tag/$version",
        body = "Release notes",
        publishedAt = publishedAt,
        draft = draft,
        prerelease = prerelease,
        apkUrl = null
    )
}
