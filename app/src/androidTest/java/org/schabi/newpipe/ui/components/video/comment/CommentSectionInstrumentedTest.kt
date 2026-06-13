package org.schabi.newpipe.ui.components.video.comment

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import java.net.UnknownHostException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.components.common.ErrorPanel
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.ui.emptystate.EmptyStateComposable
import org.schabi.newpipe.ui.emptystate.EmptyStateSpec
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.viewmodels.util.Resource

class CommentSectionInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val uiStateFlow = MutableStateFlow<Resource<CommentInfo>>(Resource.Loading)
    private val pagingFlow = MutableStateFlow(PagingData.empty<CommentsInfoItem>())
    private fun string(@StringRes resId: Int) = composeRule.activity.getString(resId)

    @Before
    fun setUp() {
        composeRule.setContent {
            AppTheme {
                TestCommentSection(uiStateFlow = uiStateFlow, commentsFlow = pagingFlow)
            }
        }
    }

    private fun successState(commentCount: Int) = Resource.Success(
        CommentInfo(
            serviceId = 0,
            url = "",
            comments = emptyList(),
            nextPage = null,
            commentCount = commentCount,
            isCommentsDisabled = false
        )
    )

    @Test
    fun commentListLoadsAndScrolls() {
        val comments = (1..25).map { index ->
            CommentsInfoItem(
                commentText = Description("Comment $index", Description.PLAIN_TEXT),
                uploaderName = "Uploader $index",
                replies = Page(""),
                replyCount = 0
            )
        }
        uiStateFlow.value = successState(comments.size)
        pagingFlow.value = PagingData.from(comments)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Comment 1").assertIsDisplayed()
        composeRule.onNodeWithTag("comment_list")
            .performScrollToNode(hasText("Comment 25"))
        composeRule.onNodeWithText("Comment 25").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pagingErrorShowsErrorPanelAndAllowsRetry() {
        uiStateFlow.value = successState(10)
        pagingFlow.value = PagingData.from(
            data = emptyList(),
            sourceLoadStates = LoadStates(
                refresh = LoadState.Error(ReCaptchaException("captcha required", "https://example.com")),
                prepend = LoadState.NotLoading(true),
                append = LoadState.NotLoading(true)
            )
        )
        composeRule.waitForIdle()

        val solveMatcher = hasText(string(R.string.recaptcha_solve), ignoreCase = true)
            .and(hasClickAction())
        val retryMatcher = hasText(string(R.string.retry), ignoreCase = true)
            .and(hasClickAction())

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(solveMatcher).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(retryMatcher).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(retryMatcher)
            .performScrollTo()
            .performClick()

        val recoveredComment = CommentsInfoItem(
            commentText = Description("Recovered comment", Description.PLAIN_TEXT),
            uploaderName = "Uploader",
            replies = Page(""),
            replyCount = 0
        )

        uiStateFlow.value = successState(1)
        pagingFlow.value = PagingData.from(
            data = listOf(recoveredComment),
            sourceLoadStates = LoadStates(
                refresh = LoadState.NotLoading(false),
                prepend = LoadState.NotLoading(true),
                append = LoadState.NotLoading(true)
            )
        )
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Recovered comment"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Recovered comment").assertIsDisplayed()

        composeRule.onNode(solveMatcher).assertDoesNotExist()
        composeRule.onNode(retryMatcher).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun resourceErrorShowsErrorPanelAndRetry() {
        uiStateFlow.value = Resource.Error(UnknownHostException("offline"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.network_error)).assertIsDisplayed()
        val retryMatcher = hasText(string(R.string.retry), ignoreCase = true)
            .and(hasClickAction())
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(retryMatcher).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(retryMatcher)
            .performScrollTo()
            .performClick()

        val recoveredComment = CommentsInfoItem(
            commentText = Description("Recovered comment", Description.PLAIN_TEXT),
            uploaderName = "Uploader",
            replies = Page(""),
            replyCount = 0
        )

        uiStateFlow.value = successState(1)
        pagingFlow.value = PagingData.from(
            data = listOf(recoveredComment),
            sourceLoadStates = LoadStates(
                refresh = LoadState.NotLoading(false),
                prepend = LoadState.NotLoading(true),
                append = LoadState.NotLoading(true)
            )
        )
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Recovered comment"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Recovered comment").assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.network_error))
            .assertDoesNotExist()
        composeRule.onNode(retryMatcher).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun retryAfterErrorRecoversList() {
        uiStateFlow.value = Resource.Error(RuntimeException("boom"))
        composeRule.waitForIdle()

        val retryMatcher = hasText(string(R.string.retry), ignoreCase = true)
            .and(hasClickAction())
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(retryMatcher).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(retryMatcher)
            .performScrollTo()
            .performClick()

        val firstComment = CommentsInfoItem(
            commentText = Description("First comment", Description.PLAIN_TEXT),
            uploaderName = "Uploader",
            replies = Page(""),
            replyCount = 0
        )

        uiStateFlow.value = successState(1)
        pagingFlow.value = PagingData.from(
            data = listOf(firstComment),
            sourceLoadStates = LoadStates(
                refresh = LoadState.NotLoading(false),
                prepend = LoadState.NotLoading(true),
                append = LoadState.NotLoading(true)
            )
        )
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("First comment"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("First comment").assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.network_error))
            .assertDoesNotExist()
        composeRule.onNode(retryMatcher).assertDoesNotExist()
    }
}

@Composable
private fun TestCommentSection(
    uiStateFlow: StateFlow<Resource<CommentInfo>>,
    commentsFlow: Flow<PagingData<CommentsInfoItem>>
) {
    val uiState by uiStateFlow.collectAsState()
    val comments = commentsFlow.collectAsLazyPagingItems()
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val listState = rememberLazyListState()
    val COMMENT_LIST_TAG = "comment_list"

    LazyColumnThemedScrollbar(state = listState) {
        LazyColumn(
            modifier = Modifier
                .testTag(COMMENT_LIST_TAG)
                .nestedScroll(nestedScrollInterop),
            state = listState
        ) {
            when (uiState) {
                is Resource.Loading -> item {
                    LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                }

                is Resource.Success -> {
                    val commentInfo = (uiState as Resource.Success<CommentInfo>).data
                    val count = commentInfo.commentCount

                    when {
                        commentInfo.isCommentsDisabled -> item {
                            EmptyStateComposable(
                                spec = EmptyStateSpec.DisabledComments,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 128.dp)
                            )
                        }

                        count == 0 -> item {
                            EmptyStateComposable(
                                spec = EmptyStateSpec.NoComments,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 128.dp)
                            )
                        }

                        else -> {
                            if (count >= 0) {
                                item {
                                    Text(
                                        modifier = Modifier
                                            .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                                        text = pluralStringResource(R.plurals.comments, count, count),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            when (val refresh = comments.loadState.refresh) {
                                is LoadState.Loading -> item {
                                    LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                                }

                                is LoadState.Error -> item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        ErrorPanel(
                                            errorInfo = ErrorInfo(
                                                throwable = refresh.error,
                                                userAction = UserAction.REQUESTED_COMMENTS,
                                                request = "comments"
                                            ),
                                            onRetry = { comments.retry() },
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                else -> items(comments.itemCount) { index ->
                                    Comment(comment = comments[index]!!) {}
                                }
                            }
                        }
                    }
                }

                is Resource.Error -> item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorPanel(
                            errorInfo = ErrorInfo(
                                throwable = (uiState as Resource.Error).throwable,
                                userAction = UserAction.REQUESTED_COMMENTS,
                                request = "comments"
                            ),
                            onRetry = { comments.retry() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}
