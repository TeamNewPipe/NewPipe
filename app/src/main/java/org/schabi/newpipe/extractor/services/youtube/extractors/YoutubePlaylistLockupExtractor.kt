package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.localization.TimeAgoParser

internal class YoutubePlaylistLockupExtractor(
    lockup: JsonObject,
    timeAgoParser: TimeAgoParser?,
    private val albumUploaderName: String?,
    private val albumUploaderUrl: String?
) : YoutubeLockupStreamInfoItemExtractor(lockup, timeAgoParser) {
    override fun getUploaderName(): String = try {
        super.getUploaderName()
    } catch (error: ParsingException) {
        albumUploaderName ?: throw error
    }

    override fun getUploaderUrl(): String = try {
        super.getUploaderUrl()
    } catch (error: ParsingException) {
        albumUploaderUrl ?: throw error
    }
}
