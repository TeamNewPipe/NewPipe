package org.schabi.newpipe.ui.components.video

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.flowOf
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.ui.components.items.ItemList
import org.schabi.newpipe.ui.components.items.Playlist
import org.schabi.newpipe.ui.components.items.Stream
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NO_SERVICE_ID
import java.util.concurrent.TimeUnit

@Composable
fun RelatedItems(info: StreamInfo) {
    val context = LocalContext.current
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val key = stringResource(R.string.auto_queue_key)
    // TODO: AndroidX DataStore might be a better option.
    var isAutoQueueEnabled by rememberSaveable {
        mutableStateOf(sharedPreferences.getBoolean(key, false))
    }
    val displayItems = info.relatedItems.mapNotNull {
        when (it) {
            is StreamInfoItem -> Stream(it)
            is PlaylistInfoItem -> Playlist(it)
            else -> null
        }
    }

    ItemList(
        items = flowOf(PagingData.from(displayItems)).collectAsLazyPagingItems(),
        mode = ItemViewMode.LIST,
        listHeader = {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.auto_queue_description))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.auto_queue_toggle))
                    Switch(
                        checked = isAutoQueueEnabled,
                        onCheckedChange = {
                            isAutoQueueEnabled = it
                            sharedPreferences.edit {
                                putBoolean(key, it)
                            }
                        }
                    )
                }
            }
        }
    )
}

private fun StreamInfoItem(
    serviceId: Int = NO_SERVICE_ID,
    url: String = "",
    name: String = "Stream",
    streamType: StreamType,
    uploaderName: String? = "Uploader",
    uploaderUrl: String? = null,
    uploaderAvatars: List<Image> = emptyList(),
    duration: Long = TimeUnit.HOURS.toSeconds(1),
    viewCount: Long = 10,
    textualUploadDate: String = "1 month ago"
) = StreamInfoItem(serviceId, url, name, streamType).apply {
    this.uploaderName = uploaderName
    this.uploaderUrl = uploaderUrl
    this.uploaderAvatars = uploaderAvatars
    this.duration = duration
    this.viewCount = viewCount
    this.textualUploadDate = textualUploadDate
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RelatedItemsPreview() {
    val info = StreamInfo(NO_SERVICE_ID, "", "", StreamType.VIDEO_STREAM, "", "", 0)
    info.relatedItems = listOf(
        StreamInfoItem(streamType = StreamType.NONE),
        StreamInfoItem(streamType = StreamType.LIVE_STREAM),
        StreamInfoItem(streamType = StreamType.AUDIO_LIVE_STREAM)
    )

    AppTheme {
        Surface {
            RelatedItems(info)
        }
    }
}
