package org.schabi.newpipe.fragments.list.search

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.util.ExtractorHelper

class SearchPagingSource(
    private val serviceId: Int,
    private val query: String,
    private val contentFilter: List<String>,
    private val sortFilter: String,
) : PagingSource<Page, SearchInfo>() {
    override fun getRefreshKey(state: PagingState<Page, SearchInfo>): Page? {
        TODO("Not yet implemented")
    }

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, SearchInfo> {
        TODO("Not yet implemented")
    }
}
