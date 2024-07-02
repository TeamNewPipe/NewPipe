package org.schabi.newpipe.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.util.NO_SERVICE_ID

class CommentsSource(
    serviceId: Int,
    private val url: String?,
    private val repliesPage: Page?
) : PagingSource<Page, CommentsInfoItem>() {
    init {
        require(serviceId != NO_SERVICE_ID) { "serviceId is NO_SERVICE_ID" }
    }
    private val service = NewPipe.getService(serviceId)

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, CommentsInfoItem> {
        // repliesPage is non-null only when used to load the comment replies
        val nextKey = params.key ?: repliesPage

        return withContext(Dispatchers.IO) {
            nextKey?.let {
                val info = CommentsInfo.getMoreItems(service, url, it)
                LoadResult.Page(info.items, null, info.nextPage)
            } ?: run {
                val info = CommentsInfo.getInfo(service, url)
                if (info.isCommentsDisabled) {
                    LoadResult.Error(CommentsDisabledException())
                } else {
                    LoadResult.Page(info.relatedItems, null, info.nextPage)
                }
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Page, CommentsInfoItem>) = null
}

class CommentsDisabledException : RuntimeException()
