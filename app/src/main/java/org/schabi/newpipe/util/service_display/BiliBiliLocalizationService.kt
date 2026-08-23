package org.schabi.newpipe.util.service_display

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliStreamLinkHandlerFactory.baseUrl

object BiliBiliLocalizationService : LocalizationService() {


    override fun localizeStatKey(key: String): Int {
        return when (key) {
            "aid" -> R.string.av_num
            "view" -> R.string.stat_view
            "danmaku" -> R.string.stat_danmaku
            "reply" -> R.string.stat_reply
            "favorite" -> R.string.stat_favorite
            "coin" -> R.string.stat_coin
            "share" -> R.string.stat_share
            "now_rank" -> R.string.stat_now_rank
            "his_rank" -> R.string.stat_his_rank
            "like" -> R.string.stat_like
            "dislike" -> R.string.stat_dislike
            "evaluation" -> R.string.stat_evaluation
            else -> 0
        }
    }

    override fun handleStatContent(key: String, content: String): String {
        return when (key) {
            /*
            "aid" -> {
                val av = "av$content"
                val url = "$baseUrl$av/"
                return "<a href=$url>$av</a>"
            }
             */

            else -> content
        }
    }
}