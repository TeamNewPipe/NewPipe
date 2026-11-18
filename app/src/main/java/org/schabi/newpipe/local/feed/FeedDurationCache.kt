package org.schabi.newpipe.local.feed

import android.util.Log
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.App
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

internal object FeedDurationCache {
    @JvmStatic
    fun remember(info: StreamInfo) {
        if (info.serviceId != ServiceList.YouTube.serviceId ||
            info.streamType != StreamType.VIDEO_STREAM || info.duration <= 0
        ) {
            return
        }

        val context = try {
            App.instance
        } catch (_: UninitializedPropertyAccessException) {
            return
        }
        if (IncognitoMode.isEnabled(context)) return
        val profileId = ProfileManager.getActiveProfileId(context)
        val duration = info.duration
        val url = info.url
        val serviceId = info.serviceId
        Schedulers.io().scheduleDirect {
            if (IncognitoMode.isEnabled(context) || profileId != ProfileManager.getActiveProfileId(context)) {
                return@scheduleDirect
            }
            runCatching {
                NewPipeDatabase.getInstance(context).streamDAO()
                    .fillMissingFeedDuration(serviceId, url, duration, profileId)
            }.onFailure { Log.w("FeedDurationCache", "Unable to save resolved feed duration", it) }
        }
    }
}
