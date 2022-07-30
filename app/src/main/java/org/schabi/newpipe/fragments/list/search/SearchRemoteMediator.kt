package org.schabi.newpipe.fragments.list.search

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchInfo

@OptIn(ExperimentalPagingApi::class)
class SearchRemoteMediator(
    private val serviceId: Int,
    private val query: String,
    private val contentFilter: List<String>,
    private val sortFilter: String
) : RemoteMediator<Page, SearchInfo>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Page, SearchInfo>
    ): MediatorResult {
        TODO("Not yet implemented")
    }
}
