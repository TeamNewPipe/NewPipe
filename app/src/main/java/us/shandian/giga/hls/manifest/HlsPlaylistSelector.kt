package us.shandian.giga.hls.manifest

object HlsPlaylistSelector {
    fun selectVariant(playlist: HlsMasterPlaylist): HlsVariant? {
        return playlist.variants.maxByOrNull { variant ->
            variant.averageBandwidth ?: variant.bandwidth ?: 0L
        }
    }
}
