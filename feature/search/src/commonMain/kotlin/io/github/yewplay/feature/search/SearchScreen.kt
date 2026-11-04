/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpChannelRow
import io.github.yewplay.core.designsystem.YpFeaturedChannelCard
import io.github.yewplay.core.designsystem.YpPlaylistRow
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSubscribeLabels
import io.github.yewplay.core.designsystem.YpTextTabs
import io.github.yewplay.core.designsystem.YpVideoRow
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpListItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem

/** A suggestion below the search field: from the search history or from the service. */
data class SearchSuggestion(val query: String, val fromHistory: Boolean)

/** A notice of the service shown above the results (e.g. about a current event). */
data class SearchNotice(val title: String?, val text: String, val url: String?)

/** An error that replaces the results, with the actions it allows. */
data class SearchError(
    val message: String,
    /** "Report" or "Solve" (captcha); null when neither applies. */
    val actionLabel: String?,
    val canRetry: Boolean,
    val canOpenInBrowser: Boolean
)

/** Everything the search screen shows. */
data class SearchUiState(
    val query: String = "",
    val hint: String = "",
    val filters: List<String> = emptyList(),
    val selectedFilter: Int = 0,
    val showSuggestions: Boolean = true,
    val suggestions: List<SearchSuggestion> = emptyList(),
    /** Changes on every new search, so the list goes back to the top. */
    val searchId: Int = 0,
    val items: List<YpListItem> = emptyList(),
    val featureFirstChannel: Boolean = true,
    /** Subscription state of the channels in the results, by URL. */
    val subscribed: Map<String, Boolean> = emptyMap(),
    val notices: List<SearchNotice> = emptyList(),
    /** "Did you mean" or "showing results for" suggestion of the service. */
    val correction: String? = null,
    val isCorrected: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isEmpty: Boolean = false,
    val error: SearchError? = null,
    /** Changes whenever the field should take the focus and open the keyboard. */
    val focusRequest: Int = 0
)

/** Texts of the screen, from the app's strings. */
data class SearchStrings(
    val cancel: String,
    val clear: String,
    val retry: String,
    val openInBrowser: String,
    val noResults: String,
    val noResultsDetail: String,
    val subscribe: YpSubscribeLabels,
    /** Formats with the suggestion in place of "%1\$s" or "%s". */
    val didYouMean: String,
    val showingResultsFor: String
)

/** Icons of the screen, from the app's drawables. */
data class SearchIcons(
    val search: Painter,
    val clear: Painter,
    val history: Painter,
    val insert: Painter
)

/** What the screen asks the app to do. */
data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onSubmit: (String) -> Unit,
    val onCancel: () -> Unit,
    val onClear: () -> Unit,
    val onFieldFocused: () -> Unit,
    val onSuggestionClick: (SearchSuggestion) -> Unit,
    val onSuggestionInsert: (SearchSuggestion) -> Unit,
    val onSuggestionLongClick: (SearchSuggestion) -> Unit,
    val onFilterSelect: (Int) -> Unit,
    val onItemClick: (YpListItem) -> Unit,
    val onItemLongClick: (YpListItem) -> Unit,
    val onToggleSubscription: (YpChannelItem) -> Unit,
    val onLoadMore: () -> Unit,
    val onCorrectionClick: () -> Unit,
    val onCorrectionLongClick: () -> Unit,
    val onNoticeClick: (SearchNotice) -> Unit,
    val onRetry: () -> Unit,
    val onErrorAction: () -> Unit,
    val onOpenInBrowser: () -> Unit
)

/** The Search destination (proposal, p. 13): big field with Cancel, type tabs and results. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    strings: SearchStrings,
    icons: SearchIcons,
    actions: SearchActions
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(state, strings, icons, actions)
        if (state.showSuggestions) {
            SuggestionList(state.suggestions, icons, actions)
        } else {
            if (state.filters.size >= 2) {
                YpTextTabs(
                    labels = state.filters,
                    selectedIndex = state.selectedFilter,
                    onSelect = actions.onFilterSelect
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.error != null -> ErrorState(state.error, strings, actions)
                    state.isLoading -> LoadingState()
                    state.isEmpty -> EmptyState(strings)
                    else -> Results(state, strings, actions)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    state: SearchUiState,
    strings: SearchStrings,
    icons: SearchIcons,
    actions: SearchActions
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember {
        mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length)))
    }
    if (value.text != state.query) {
        // changed by the app (suggestion, correction, clear): cursor at the end
        value = TextFieldValue(state.query, TextRange(state.query.length))
    }

    LaunchedEffect(state.focusRequest) {
        if (state.focusRequest > 0) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(state.showSuggestions) {
        if (!state.showSuggestions) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = YpScreenMargin, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainer)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = icons.search,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        text = state.hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused) actions.onFieldFocused() },
                    value = value,
                    onValueChange = {
                        value = it
                        if (it.text != state.query) {
                            actions.onQueryChange(it.text)
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        actions.onSubmit(value.text.trim())
                    })
                )
            }
            if (value.text.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = actions.onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = icons.clear,
                        contentDescription = strings.clear,
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }
        TextButton(onClick = actions.onCancel) {
            Text(
                text = strings.cancel,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionList(
    suggestions: List<SearchSuggestion>,
    icons: SearchIcons,
    actions: SearchActions
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(suggestions, key = { (if (it.fromHistory) "h:" else "r:") + it.query }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { actions.onSuggestionClick(item) },
                        onLongClick = { actions.onSuggestionLongClick(item) }
                    )
                    .padding(start = YpScreenMargin),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = if (item.fromHistory) icons.history else icons.search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    text = item.query,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { actions.onSuggestionInsert(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = icons.insert,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Results(state: SearchUiState, strings: SearchStrings, actions: SearchActions) {
    val listState = rememberSaveable(state.searchId, saver = LazyListState.Saver) {
        LazyListState()
    }
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size, state.hasMore, state.isLoadingMore) {
        if (nearEnd && state.hasMore && !state.isLoadingMore) {
            actions.onLoadMore()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        state.correction?.let { correction ->
            item(key = "correction") {
                Correction(correction, state.isCorrected, strings, actions)
            }
        }
        items(state.notices) { notice -> Notice(notice) { actions.onNoticeClick(notice) } }
        itemsIndexed(state.items, key = { index, item -> "$index:${item.url}" }) { index, item ->
            when (item) {
                is YpStreamItem -> YpVideoRow(
                    item = item,
                    onClick = { actions.onItemClick(item) },
                    onLongClick = { actions.onItemLongClick(item) }
                )
                is YpChannelItem -> if (index == 0 && state.featureFirstChannel) {
                    YpFeaturedChannelCard(
                        item = item,
                        subscribed = state.subscribed[item.url],
                        labels = strings.subscribe,
                        onClick = { actions.onItemClick(item) },
                        onToggleSubscription = { actions.onToggleSubscription(item) }
                    )
                } else {
                    YpChannelRow(
                        item = item,
                        subscribed = state.subscribed[item.url],
                        labels = strings.subscribe,
                        onClick = { actions.onItemClick(item) },
                        onToggleSubscription = { actions.onToggleSubscription(item) }
                    )
                }
                is YpPlaylistItem -> YpPlaylistRow(
                    item = item,
                    onClick = { actions.onItemClick(item) }
                )
            }
        }
        if (state.isLoadingMore) {
            item(key = "more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Correction(
    correction: String,
    isCorrected: Boolean,
    strings: SearchStrings,
    actions: SearchActions
) {
    val format = if (isCorrected) strings.showingResultsFor else strings.didYouMean
    val placeholder = listOf("%1\$s", "%s").firstOrNull { format.contains(it) }
    val text = buildAnnotatedString {
        if (placeholder == null) {
            append(format)
            append(' ')
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(correction) }
        } else {
            append(format.substringBefore(placeholder))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(correction) }
            append(format.substringAfter(placeholder))
        }
    }
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = actions.onCorrectionClick,
                onLongClick = actions.onCorrectionLongClick
            )
            .padding(horizontal = YpScreenMargin, vertical = 12.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Notice(notice: SearchNotice, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YpScreenMargin, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = notice.url != null, onClick = onClick)
            .padding(14.dp)
    ) {
        notice.title?.let {
            Text(text = it, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = notice.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Blocks in the shape of the results while they load (proposal, p. 17). */
@Composable
private fun LoadingState() {
    val block = MaterialTheme.colorScheme.surfaceContainer
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(7) {
            Row(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(block)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonLine(1f)
                    SkeletonLine(0.7f)
                    SkeletonLine(0.45f)
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
private fun EmptyState(strings: SearchStrings) {
    CenteredMessage(title = strings.noResults, detail = strings.noResultsDetail) {}
}

@Composable
private fun ErrorState(error: SearchError, strings: SearchStrings, actions: SearchActions) {
    CenteredMessage(title = error.message, detail = null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (error.canRetry) {
                PillButton(strings.retry, primary = true, onClick = actions.onRetry)
            }
            error.actionLabel?.let { PillButton(it, primary = !error.canRetry, actions.onErrorAction) }
        }
        if (error.canOpenInBrowser) {
            TextButton(onClick = actions.onOpenInBrowser) {
                Text(
                    text = strings.openInBrowser,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    detail: String?,
    buttons: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))
        buttons()
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) colors.onPrimary else colors.onSurface
        )
    }
}
