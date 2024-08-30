package org.schabi.newpipe.ui.components.video.comment

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.ui.components.common.NoItemsMessage
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.md_theme_dark_primary
import org.schabi.newpipe.viewmodels.CommentsViewModel
import org.schabi.newpipe.viewmodels.util.Resource

@Composable
fun CommentSection(commentsViewModel: CommentsViewModel = viewModel()) {
    val state by commentsViewModel.uiState.collectAsStateWithLifecycle()
    CommentSection(state, commentsViewModel.comments)
}

@Composable
private fun CommentSection(
    uiState: Resource<CommentInfo>,
    commentsFlow: Flow<PagingData<CommentsInfoItem>>
) {
    val comments = commentsFlow.collectAsLazyPagingItems()
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val state = rememberLazyListState()

    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumnScrollbar(
            state = state,
            settings = ScrollbarSettings.Default.copy(
                thumbSelectedColor = md_theme_dark_primary,
                thumbUnselectedColor = Color.Red
            )
        ) {
            LazyColumn(
                modifier = Modifier.nestedScroll(nestedScrollInterop),
                state = state
            ) {
                when (uiState) {
                    is Resource.Loading -> {
                        item {
                            LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                        }
                    }

                    is Resource.Success -> {
                        val commentInfo = uiState.data
                        val count = commentInfo.commentCount

                        if (commentInfo.isCommentsDisabled) {
                            item {
                                NoItemsMessage(R.string.comments_are_disabled)
                            }
                        } else if (count == 0) {
                            item {
                                NoItemsMessage(R.string.no_comments)
                            }
                        } else {
                            item {
                                Text(
                                    modifier = Modifier.padding(start = 8.dp),
                                    text = pluralStringResource(R.plurals.comments, count, count),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            when (comments.loadState.refresh) {
                                is LoadState.Loading -> {
                                    item {
                                        LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                                    }
                                }

                                is LoadState.Error -> {
                                    item {
                                        NoItemsMessage(R.string.error_unable_to_load_comments)
                                    }
                                }

                                else -> {
                                    items(comments.itemCount) {
                                        Comment(comment = comments[it]!!)
                                    }
                                }
                            }
                        }
                    }

                    is Resource.Error -> {
                        item {
                            NoItemsMessage(R.string.error_unable_to_load_comments)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionLoadingPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(uiState = Resource.Loading, commentsFlow = flowOf())
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionSuccessPreview() {
    val comments = listOf(
        CommentsInfoItem(
            commentText = Description(
                "Comment 1\n\nThis line should be hidden by default.",
                Description.PLAIN_TEXT
            ),
            uploaderName = "Test",
            replies = Page(""),
            replyCount = 10
        )
    ) + (2..10).map {
        CommentsInfoItem(
            commentText = Description("Comment $it", Description.PLAIN_TEXT),
            uploaderName = "Test"
        )
    }

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(
                uiState = Resource.Success(
                    CommentInfo(
                        serviceId = 1, url = "", comments = comments, nextPage = null,
                        commentCount = 10, isCommentsDisabled = false
                    )
                ),
                commentsFlow = flowOf(PagingData.from(comments))
            )
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionErrorPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(uiState = Resource.Error(RuntimeException()), commentsFlow = flowOf())
        }
    }
}
