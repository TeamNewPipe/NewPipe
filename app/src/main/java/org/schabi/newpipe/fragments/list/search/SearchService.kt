package org.schabi.newpipe.fragments.list.search

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.extractor.search.SearchInfo

fun getSearchResultStream(
    serviceId: Int, query: String, contentFilter: List<String>, sortFilter: String
): Flow<PagingData<SearchInfo>> {
    return Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = false),
        pagingSourceFactory = { SearchPagingSource(serviceId, query, contentFilter, sortFilter) }
    ).flow
}
