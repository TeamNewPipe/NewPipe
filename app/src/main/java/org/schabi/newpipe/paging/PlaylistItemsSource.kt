package org.schabi.newpipe.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.ui.components.playlist.PlaylistScreenInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo as ExtractorPlaylistInfo

class PlaylistItemsSource(
    private val playlist: PlaylistScreenInfo,
) : PagingSource<Page, StreamInfoItem>() {
    private val service = NewPipe.getService(playlist.serviceId)

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, StreamInfoItem> {
        return params.key?.let {
            withContext(Dispatchers.IO) {
                val response = ExtractorPlaylistInfo.getMoreItems(service, playlist.url, it)
                LoadResult.Page(response.items, null, response.nextPage)
            }
        } ?: LoadResult.Page(playlist.relatedItems, null, playlist.nextPage)
    }

    override fun getRefreshKey(state: PagingState<Page, StreamInfoItem>) = null
}
