package org.schabi.newpipe.fragments.list.comments

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.util.ExtractorHelper

class CommentRepliesSource(
    private val commentsInfoItem: CommentsInfoItem,
) : RxPagingSource<Page, CommentsInfoItem>() {
    override fun loadSingle(params: LoadParams<Page>): Single<LoadResult<Page, CommentsInfoItem>> {
        val nextPage = params.key ?: commentsInfoItem.replies
        return ExtractorHelper.getMoreCommentItems(commentsInfoItem.serviceId, commentsInfoItem.url, nextPage)
            .subscribeOn(Schedulers.io())
            .map { LoadResult.Page(it.items, null, it.nextPage) }
    }

    override fun getRefreshKey(state: PagingState<Page, CommentsInfoItem>) = null
}
