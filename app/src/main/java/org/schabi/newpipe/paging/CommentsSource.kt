package org.schabi.newpipe.paging

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.util.ExtractorHelper

class CommentsSource(
    private val serviceId: Int,
    private val url: String?,
    private val repliesPage: Page?
) : RxPagingSource<Page, CommentsInfoItem>() {
    override fun loadSingle(params: LoadParams<Page>): Single<LoadResult<Page, CommentsInfoItem>> {
        // repliesPage is non-null only when used to load the comment replies
        val nextKey = params.key ?: repliesPage

        return nextKey?.let {
            ExtractorHelper.getMoreCommentItems(serviceId, url, it)
                .subscribeOn(Schedulers.io())
                .map { LoadResult.Page(it.items, null, it.nextPage) }
        } ?: ExtractorHelper.getCommentsInfo(serviceId, url, false)
            .subscribeOn(Schedulers.io())
            .map {
                if (it.isCommentsDisabled) {
                    LoadResult.Error(CommentsDisabledException())
                } else {
                    LoadResult.Page(it.relatedItems, null, it.nextPage)
                }
            }
    }

    override fun getRefreshKey(state: PagingState<Page, CommentsInfoItem>) = null
}

class CommentsDisabledException : RuntimeException()
