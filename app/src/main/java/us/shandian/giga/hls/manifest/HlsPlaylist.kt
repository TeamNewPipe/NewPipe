package us.shandian.giga.hls.manifest

sealed class HlsPlaylist(
    open val sourceUrl: String,
)

data class HlsMasterPlaylist(
    override val sourceUrl: String,
    val variants: List<HlsVariant>,
) : HlsPlaylist(sourceUrl)

data class HlsMediaPlaylist(
    override val sourceUrl: String,
    val targetDurationSeconds: Double?,
    val mediaSequence: Long,
    val segments: List<HlsSegment>,
    val isEndList: Boolean,
    val initSegment: HlsInitSegment?,
    val hasEncryption: Boolean,
    val hasUnsupportedEncryption: Boolean,
    val hasDiscontinuity: Boolean,
) : HlsPlaylist(sourceUrl)

data class HlsVariant(
    val url: String,
    val bandwidth: Long?,
    val averageBandwidth: Long?,
    val codecs: String?,
    val resolution: String?,
    val frameRate: Double?,
    val audioGroupId: String?,
)

data class HlsSegment(
    val url: String,
    val durationSeconds: Double?,
    val title: String?,
    val byteRange: HlsByteRange?,
    val discontinuity: Boolean,
    val encryptionKey: HlsEncryptionKey?,
)

data class HlsInitSegment(
    val url: String,
    val byteRange: HlsByteRange?,
    val encryptionKey: HlsEncryptionKey?,
)

data class HlsEncryptionKey(
    val method: String,
    val url: String?,
    val iv: String?,
)

data class HlsByteRange(
    val length: Long,
    val offset: Long?,
)
