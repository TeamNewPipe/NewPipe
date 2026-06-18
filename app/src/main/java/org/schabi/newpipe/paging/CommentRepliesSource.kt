package org.schabi.newpipe.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

class CommentRepliesSource(
    private val commentInfo: CommentsInfoItem
) : PagingSource<Page, CommentsInfoItem>() {
    private val service = NewPipe.getService(commentInfo.serviceId)

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, CommentsInfoItem> {
        return try {
            // params.key is null the first time load() is called, and we need to return the first page
            val repliesPage = params.key ?: commentInfo.replies

            val info = withContext(Dispatchers.IO) {
                CommentsInfo.getMoreItems(service, commentInfo.url, repliesPage)
            }

            LoadResult.Page(info.items, null, info.nextPage)
        } catch (e: Exception) {
            // Catches UnknownHostException (offline) or any extraction errors
            // and tells Paging3 to show the error state in the UI.
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Page, CommentsInfoItem>) = null
}
