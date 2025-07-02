package org.schabi.newpipe.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.ui.components.video.comment.CommentInfo
import java.io.IOException

class CommentsSource(private val commentInfo: CommentInfo) : PagingSource<Page, CommentsInfoItem>() {
    private val service = NewPipe.getService(commentInfo.serviceId)

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, CommentsInfoItem> {
        // params.key is null the first time the load() function is called, so we need to return the
        // first batch of already-loaded comments
        return LoadResult.Error(IOException("💥 forced test error"))
        if (params.key == null) {
            return LoadResult.Page(commentInfo.comments, null, commentInfo.nextPage)
        } else {
            val info = withContext(Dispatchers.IO) {
                CommentsInfo.getMoreItems(service, commentInfo.url, params.key)
            }

            return LoadResult.Page(info.items, null, info.nextPage)
        }
    }

    override fun getRefreshKey(state: PagingState<Page, CommentsInfoItem>) = null
}
