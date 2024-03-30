package org.schabi.newpipe.info_list.holder

import android.view.ViewGroup
import org.schabi.newpipe.R
import org.schabi.newpipe.info_list.InfoItemBuilder

class ChannelCardInfoItemHolder(infoItemBuilder: InfoItemBuilder,
                                parent: ViewGroup?) : ChannelMiniInfoItemHolder(infoItemBuilder, R.layout.list_channel_card_item, parent) {
    override fun getDescriptionMaxLineCount(content: String?): Int {
        // Based on `list_channel_card_item` left side content (thumbnail 100dp
        // + additional details), Right side description can grow up to 8 lines.
        return 8
    }
}
