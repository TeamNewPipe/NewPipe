package org.schabi.newpipe.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.ui.components.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo as ExtractorPlaylistInfo

class PlaylistItemsSource(
    private val playlistInfo: PlaylistInfo,
) : PagingSource<Page, StreamInfoItem>() {
    private val service = NewPipe.getService(playlistInfo.serviceId)

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, StreamInfoItem> {
        return params.key?.let {
            withContext(Dispatchers.IO) {
                val response = ExtractorPlaylistInfo
                    .getMoreItems(service, playlistInfo.url, playlistInfo.nextPage)
                LoadResult.Page(response.items, null, response.nextPage)
            }
        } ?: LoadResult.Page(playlistInfo.relatedItems, null, playlistInfo.nextPage)
    }

    override fun getRefreshKey(state: PagingState<Page, StreamInfoItem>) = null
}
