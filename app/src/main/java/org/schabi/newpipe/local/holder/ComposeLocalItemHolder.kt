package org.schabi.newpipe.local.holder

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.info_list.CommonItem
import org.schabi.newpipe.info_list.PipePlayComposeTheme
import org.schabi.newpipe.info_list.buildLocalItemState
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.ThemeHelper
import java.time.format.DateTimeFormatter

class ComposeLocalItemHolder(
    private val localItemBuilder: LocalItemBuilder,
    parent: ViewGroup,
    private val itemViewMode: ItemViewMode,
    private val showDragHandle: Boolean
) : LocalItemHolder(
    localItemBuilder,
    ComposeView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
) {

    private val composeView = itemView as ComposeView

    override fun updateFromItem(
        item: LocalItem,
        historyRecordManager: HistoryRecordManager,
        dateTimeFormatter: DateTimeFormatter
    ) {
        val state = buildLocalItemState(composeView.context, item, dateTimeFormatter) ?: return
        composeView.setContent {
            PipePlayComposeTheme(composeView.context) {
                CommonItem(
                    state = state,
                    isGridLayout = ThemeHelper.isGrid(itemViewMode),
                    isCardLayout = itemViewMode == ItemViewMode.CARD,
                    showDragHandle = showDragHandle,
                    onClick = { localItemBuilder.getOnItemSelectedListener()?.selected(item) },
                    onLongClick = { localItemBuilder.getOnItemSelectedListener()?.held(item) },
                    onDragStart = {
                        if (showDragHandle) {
                            localItemBuilder.getOnItemSelectedListener()?.drag(item, this@ComposeLocalItemHolder)
                        }
                    }
                )
            }
        }
    }
}
