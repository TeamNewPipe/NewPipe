/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.channel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.designsystem.YpAvatar
import io.github.yewplay.core.designsystem.YpChannelRow
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpErrorState
import io.github.yewplay.core.designsystem.YpLoadingMore
import io.github.yewplay.core.designsystem.YpMessageState
import io.github.yewplay.core.designsystem.YpPlaylistControlIcons
import io.github.yewplay.core.designsystem.YpPlaylistControlLabels
import io.github.yewplay.core.designsystem.YpPlaylistControls
import io.github.yewplay.core.designsystem.YpPlaylistRow
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSkeletonRow
import io.github.yewplay.core.designsystem.YpSubscribeLabels
import io.github.yewplay.core.designsystem.YpTextTabs
import io.github.yewplay.core.designsystem.YpVideoRow
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpError
import io.github.yewplay.core.model.YpListItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem

/** The top of the channel: banner, avatar, numbers and description. */
data class ChannelHeader(
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    /** "141K subscribers"; may be empty. */
    val details: String,
    val description: String?,
    /** "Created by X", for channels that belong to another one. */
    val parentText: String?,
    val parentAvatarUrl: String?
)

/** A tab of the channel (Videos, Live, Playlists, ..., About). */
data class ChannelTab(val label: String, val isStreams: Boolean, val isAbout: Boolean)

/** The items of a channel tab. */
data class ChannelTabContent(
    val items: List<YpListItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isEmpty: Boolean = false,
    val error: YpError? = null
)

/** The "About" tab. */
data class ChannelAbout(
    val description: String?,
    /** "Subscribers" and the full count, when known. */
    val subscribers: Pair<String, String>?,
    val tags: List<String>,
    /** "Avatar" / "Banner" and the URL of the largest image. */
    val images: List<Pair<String, String>>
)

/** Everything the channel screen shows. */
data class ChannelUiState(
    /** Known before loading (the name the channel was opened with). */
    val title: String = "",
    val header: ChannelHeader? = null,
    val isLoading: Boolean = true,
    val error: YpError? = null,
    val contentNotSupported: Boolean = false,
    /** Null until the subscription state is known. */
    val subscribed: Boolean? = null,
    /** The bell, shown when subscribed. */
    val notifyVisible: Boolean = false,
    /** False when new-video notifications are turned off in the settings. */
    val notifyEnabled: Boolean = true,
    val notifyOn: Boolean = false,
    val hasRss: Boolean = false,
    val tabs: List<ChannelTab> = emptyList(),
    val selectedTab: Int = 0,
    val tabContent: Map<Int, ChannelTabContent> = emptyMap(),
    val about: ChannelAbout? = null,
    /** False when the channel is a tab of the home page, which has its own bar. */
    val showBack: Boolean = true
)

/** Texts of the screen, from the app's strings. */
data class ChannelStrings(
    val subscribe: YpSubscribeLabels,
    val notifications: String,
    val rss: String,
    val share: String,
    val openInBrowser: String,
    val settings: String,
    val back: String,
    val search: String,
    val error: YpErrorLabels,
    val noVideos: String,
    val emptyDetail: String,
    val contentNotSupported: String,
    val tags: String,
    val controls: YpPlaylistControlLabels
)

/** Icons of the screen, from the app's drawables. */
data class ChannelIcons(
    val back: Painter,
    val search: Painter,
    val more: Painter,
    val check: Painter,
    val bell: Painter,
    val rss: Painter,
    val controls: YpPlaylistControlIcons
)

/** What the screen asks the app to do. */
data class ChannelActions(
    val onBack: () -> Unit,
    val onSearch: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInBrowser: () -> Unit,
    val onSettings: () -> Unit,
    val onToggleSubscription: () -> Unit,
    val onToggleNotify: () -> Unit,
    val onRss: () -> Unit,
    val onParentClick: () -> Unit,
    val onTabSelect: (Int) -> Unit,
    val onItemClick: (YpListItem) -> Unit,
    val onItemMenu: (YpListItem) -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryTab: () -> Unit,
    val onErrorAction: () -> Unit,
    val onErrorOpenInBrowser: () -> Unit,
    val onPlay: () -> Unit,
    val onShuffle: () -> Unit,
    val onListen: () -> Unit,
    val onPlayLongClick: () -> Unit,
    val onListenLongClick: () -> Unit,
    val onTagClick: (String) -> Unit,
    val onLinkClick: (String) -> Unit
)

private val BannerHeight = 150.dp

/** The channel (proposal, p. 13): banner, Subscribed/bell/RSS, tabs and a compact list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelScreen(
    state: ChannelUiState,
    strings: ChannelStrings,
    icons: ChannelIcons,
    actions: ChannelActions
) {
    val listState = rememberLazyListState()
    val scrolled by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val content = state.tabContent[state.selectedTab]
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, content?.items?.size, content?.hasMore, content?.isLoadingMore) {
        if (nearEnd && content != null && content.hasMore && !content.isLoadingMore) {
            actions.onLoadMore()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val error = state.error
        if (error != null) {
            YpErrorState(
                modifier = Modifier.padding(top = 56.dp),
                error = error,
                labels = strings.error,
                onRetry = actions.onRetry,
                onAction = actions.onErrorAction,
                onOpenInBrowser = actions.onErrorOpenInBrowser
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(key = "header") { Header(state, strings, icons, actions) }
                if (state.contentNotSupported) {
                    item(key = "unsupported") {
                        YpMessageState(title = strings.contentNotSupported, detail = null)
                    }
                } else if (state.tabs.isNotEmpty()) {
                    stickyHeader(key = "tabs") {
                        YpTextTabs(
                            modifier = Modifier.background(MaterialTheme.colorScheme.background),
                            labels = state.tabs.map { it.label },
                            selectedIndex = state.selectedTab,
                            onSelect = actions.onTabSelect
                        )
                    }
                    val tab = state.tabs.getOrNull(state.selectedTab)
                    if (tab?.isAbout == true) {
                        state.about?.let { about(it, strings, actions) }
                    } else if (tab != null) {
                        tabItems(tab, content ?: ChannelTabContent(), strings, icons, actions)
                    }
                } else if (state.isLoading) {
                    items(6) { YpSkeletonRow() }
                }
            }
        }
        TopBar(state, scrolled, strings, icons, actions)
    }
}

@Composable
private fun TopBar(
    state: ChannelUiState,
    scrolled: Boolean,
    strings: ChannelStrings,
    icons: ChannelIcons,
    actions: ChannelActions
) {
    val colors = MaterialTheme.colorScheme
    val overBanner = !scrolled && state.header?.bannerUrl != null && state.error == null
    val tint = if (overBanner) Color.White else colors.onBackground
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (scrolled) colors.background else Color.Transparent)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.showBack) {
            BarIcon(icons.back, strings.back, tint, actions.onBack)
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            text = if (scrolled) state.header?.name ?: state.title else "",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (state.showBack) {
            BarIcon(icons.search, strings.search, tint, actions.onSearch)
        }
        Box {
            BarIcon(icons.more, null, tint) { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf(
                    strings.share to actions.onShare,
                    strings.openInBrowser to actions.onOpenInBrowser,
                    strings.settings to actions.onSettings
                ).forEach { (label, onClick) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BarIcon(icon: Painter, description: String?, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            painter = icon,
            contentDescription = description,
            tint = tint
        )
    }
}

@Composable
private fun Header(
    state: ChannelUiState,
    strings: ChannelStrings,
    icons: ChannelIcons,
    actions: ChannelActions
) {
    val colors = MaterialTheme.colorScheme
    val header = state.header
    val bannerUrl = header?.bannerUrl
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bannerUrl != null) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BannerHeight),
                    model = bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
                // dark at the top for the icons, fading into the page at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BannerHeight)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                0.4f to Color.Transparent,
                                1f to colors.background
                            )
                        )
                )
            }
            Row(
                modifier = Modifier
                    .padding(
                        start = YpScreenMargin,
                        end = YpScreenMargin,
                        top = if (bannerUrl != null) BannerHeight - 44.dp else 64.dp
                    ),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                YpAvatar(url = header?.avatarUrl, size = 76)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = header?.name ?: state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!header?.details.isNullOrEmpty()) {
                        Text(
                            text = header?.details.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        header?.description?.let {
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 12.dp),
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val parentText = header?.parentText
        if (parentText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions.onParentClick)
                    .padding(horizontal = YpScreenMargin, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                YpAvatar(url = header?.parentAvatarUrl, size = 28)
                Text(text = parentText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.subscribed != null) {
            Actions(state, strings, icons, actions)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Actions(
    state: ChannelUiState,
    strings: ChannelStrings,
    icons: ChannelIcons,
    actions: ChannelActions
) {
    val colors = MaterialTheme.colorScheme
    val subscribed = state.subscribed == true
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YpScreenMargin, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(shape)
                .background(if (subscribed) colors.surfaceContainerHighest else colors.primary)
                .clickable(onClick = actions.onToggleSubscription),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subscribed) {
                Icon(
                    modifier = Modifier.size(19.dp),
                    painter = icons.check,
                    contentDescription = null,
                    tint = colors.onSurface
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (subscribed) strings.subscribe.subscribed else strings.subscribe.subscribe,
                style = MaterialTheme.typography.labelLarge,
                color = if (subscribed) colors.onSurface else colors.onPrimary
            )
        }
        if (state.notifyVisible) {
            SquareButton(
                icon = icons.bell,
                description = strings.notifications,
                tint = if (state.notifyOn) colors.tertiary else colors.onSurface,
                enabled = state.notifyEnabled,
                onClick = actions.onToggleNotify
            )
        }
        if (state.hasRss) {
            SquareButton(
                icon = icons.rss,
                description = strings.rss,
                tint = colors.onSurface,
                enabled = true,
                onClick = actions.onRss
            )
        }
    }
}

@Composable
private fun SquareButton(
    icon: Painter,
    description: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = icon,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f)
        )
    }
}

private fun LazyListScope.tabItems(
    tab: ChannelTab,
    content: ChannelTabContent,
    strings: ChannelStrings,
    icons: ChannelIcons,
    actions: ChannelActions
) {
    val error = content.error
    when {
        error != null -> item(key = "tab-error") {
            YpErrorState(
                modifier = Modifier.height(320.dp),
                error = error,
                labels = strings.error,
                onRetry = actions.onRetryTab,
                onAction = actions.onErrorAction,
                onOpenInBrowser = actions.onErrorOpenInBrowser
            )
        }

        content.isLoading -> items(6) { YpSkeletonRow() }

        content.isEmpty -> item(key = "tab-empty") {
            YpMessageState(
                modifier = Modifier.height(240.dp),
                title = strings.noVideos,
                detail = strings.emptyDetail
            )
        }

        else -> {
            if (tab.isStreams) {
                item(key = "controls") {
                    YpPlaylistControls(
                        icons = icons.controls,
                        labels = strings.controls,
                        onPlay = actions.onPlay,
                        onShuffle = actions.onShuffle,
                        onListen = actions.onListen,
                        onPlayLongClick = actions.onPlayLongClick,
                        onListenLongClick = actions.onListenLongClick
                    )
                }
            }
            itemsIndexed(content.items, key = { index, item -> "$index:${item.url}" }) { _, item ->
                when (item) {
                    is YpStreamItem -> YpVideoRow(
                        item = item,
                        onClick = { actions.onItemClick(item) },
                        onLongClick = { actions.onItemMenu(item) },
                        moreIcon = icons.more
                    )

                    is YpChannelItem -> YpChannelRow(
                        item = item,
                        subscribed = null,
                        labels = strings.subscribe,
                        onClick = { actions.onItemClick(item) },
                        onToggleSubscription = { }
                    )

                    is YpPlaylistItem -> YpPlaylistRow(
                        item = item,
                        onClick = { actions.onItemClick(item) }
                    )
                }
            }
            if (content.isLoadingMore) {
                item(key = "more") { YpLoadingMore() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.about(
    about: ChannelAbout,
    strings: ChannelStrings,
    actions: ChannelActions
) {
    about.description?.let { description ->
        item(key = "about-description") {
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 12.dp),
                text = linkify(description, actions.onLinkClick),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    about.subscribers?.let { (label, value) ->
        item(key = "about-subscribers") { AboutRow(label, value) }
    }
    about.images.forEach { (label, url) ->
        item(key = "about-image-$label") {
            Box(modifier = Modifier.clickable { actions.onLinkClick(url) }) {
                AboutRow(label, url.substringAfter("://").substringBefore('/'))
            }
        }
    }
    if (about.tags.isNotEmpty()) {
        item(key = "about-tags") {
            Column(modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 12.dp)) {
                Text(text = strings.tags, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    about.tags.forEach { tag ->
                        Text(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { actions.onTagClick(tag) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            text = tag,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YpScreenMargin, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val UrlPattern = Regex("""https?://[^\s<>"]+""")

/** The text with its web addresses as links. */
@Composable
private fun linkify(text: String, onLinkClick: (String) -> Unit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.tertiary
    return remember(text, linkColor) {
        buildAnnotatedString {
            var last = 0
            UrlPattern.findAll(text).forEach { match ->
                append(text.substring(last, match.range.first))
                val url = match.value.trimEnd('.', ',', ')', ';')
                withLink(
                    LinkAnnotation.Clickable(
                        tag = url,
                        styles = TextLinkStyles(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        ),
                        linkInteractionListener = { onLinkClick(url) }
                    )
                ) { append(url) }
                last = match.range.first + url.length
            }
            append(text.substring(last))
        }
    }
}
