package org.schabi.newpipe.ui.components.video.comment

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.paging.CommentRepliesSource
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.ui.emptystate.EmptyStateComposable
import org.schabi.newpipe.ui.emptystate.EmptyStateSpec
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun CommentRepliesDialog(
    parentComment: CommentsInfoItem,
    onDismissRequest: () -> Unit,
    onCommentAuthorOpened: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val commentsFlow = remember {
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            CommentRepliesSource(parentComment)
        }
            .flow
            .cachedIn(coroutineScope)
    }

    CommentRepliesDialog(parentComment, commentsFlow, onDismissRequest, onCommentAuthorOpened)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentRepliesDialog(
    parentComment: CommentsInfoItem,
    commentsFlow: Flow<PagingData<CommentsInfoItem>>,
    onDismissRequest: () -> Unit,
    onCommentAuthorOpened: () -> Unit,
) {
    val comments = commentsFlow.collectAsLazyPagingItems()
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val listState = rememberLazyListState()

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val nestedOnCommentAuthorOpened: () -> Unit = {
        // also partialExpand any parent dialog
        onCommentAuthorOpened()
        coroutineScope.launch {
            sheetState.partialExpand()
        }
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
    ) {
        CompositionLocalProvider(
            // contentColorFor(MaterialTheme.colorScheme.containerColor), i.e. ModalBottomSheet's
            // default background color, does not resolve correctly, so need to manually set the
            // content color for MaterialTheme.colorScheme.background instead
            LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.background)
        ) {
            LazyColumnThemedScrollbar(state = listState) {
                LazyColumn(
                    modifier = Modifier.nestedScroll(nestedScrollInterop),
                    state = listState
                ) {
                    item {
                        CommentRepliesHeader(
                            comment = parentComment,
                            onCommentAuthorOpened = nestedOnCommentAuthorOpened,
                        )
                        HorizontalDivider(
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }

                    if (parentComment.replyCount >= 0) {
                        item {
                            Text(
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 4.dp
                                ),
                                text = pluralStringResource(
                                    R.plurals.replies,
                                    parentComment.replyCount,
                                    parentComment.replyCount,
                                ),
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    if (comments.itemCount == 0) {
                        item {
                            val refresh = comments.loadState.refresh
                            if (refresh is LoadState.Loading) {
                                LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                            } else if (refresh is LoadState.Error) {
                                // TODO use error panel instead
                                EmptyStateComposable(
                                    EmptyStateSpec.DisabledComments.copy(
                                        descriptionText = {
                                            stringResource(R.string.error_unable_to_load_comments)
                                        }
                                    )
                                )
                            } else {
                                EmptyStateComposable(EmptyStateSpec.NoComments)
                            }
                        }
                    } else {
                        items(comments.itemCount) {
                            Comment(
                                comment = comments[it]!!,
                                onCommentAuthorOpened = nestedOnCommentAuthorOpened,
                            )
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
private fun CommentRepliesDialogPreview() {
    val comment = CommentsInfoItem(
        commentText = Description("Hello world!", Description.PLAIN_TEXT),
        uploaderName = "Test",
        likeCount = 100,
        isPinned = true,
        isHeartedByUploader = true
    )
    val replies = (1..10).map { i ->
        CommentsInfoItem(
            commentText = Description(
                "Reply $i: ${LoremIpsum(i * i).values.first()}",
                Description.PLAIN_TEXT,
            ),
            uploaderName = LoremIpsum(11 - i).values.first()
        )
    }
    val flow = flowOf(PagingData.from(replies))

    AppTheme {
        CommentRepliesDialog(comment, flow, onDismissRequest = {}, onCommentAuthorOpened = {})
    }
}
