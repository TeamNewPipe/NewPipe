/*
 * SPDX-FileCopyrightText: 2017-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import org.schabi.newpipe.R

object KioskTranslator {
    @JvmStatic
    fun getTranslatedKioskName(kioskId: String?, context: Context): String {
        return when (kioskId) {
            null, "" -> context.getString(R.string.search)
            "Trending" -> context.getString(R.string.trending)
            "Top 50" -> context.getString(R.string.top_50)
            "New & hot" -> context.getString(R.string.new_and_hot)
            "Local" -> context.getString(R.string.local)
            "Recently added" -> context.getString(R.string.recently_added)
            "Most liked" -> context.getString(R.string.most_liked)
            "conferences" -> context.getString(R.string.conferences)
            "recent" -> context.getString(R.string.recent)
            "live", "Recommended Lives" -> context.getString(R.string.recommended_lives)
            "Featured" -> context.getString(R.string.featured)
            "Staff Picks" -> context.getString(R.string.vimeo_staff_picks)
            "Radio" -> context.getString(R.string.radio)
            "trending_gaming" -> context.getString(R.string.trending_gaming)
            "trending_music" -> context.getString(R.string.trending_music)
            "trending_movies_and_shows" -> context.getString(R.string.trending_movies)
            "trending_podcasts_episodes" -> context.getString(R.string.trending_podcasts)
            "youtube_shorts" -> context.getString(R.string.youtube_shorts)
            else -> kioskId
        }
    }

    @JvmStatic
    fun getKioskIcon(kioskId: String?): Int {
        return when (kioskId) {
            null, "" -> R.drawable.ic_search
            "Trending", "Top 50", "New & hot", "conferences" -> R.drawable.ic_whatshot
            "Trending This Month" -> R.drawable.ic_whatshot
            "Local" -> R.drawable.ic_home
            "Recently added", "recent" -> R.drawable.ic_add_circle_outline
            "Most liked" -> R.drawable.ic_thumb_up
            "live", "Recommended Lives" -> R.drawable.ic_live_tv
            "Featured" -> R.drawable.ic_stars
            "Staff Picks" -> R.drawable.ic_stars
            "Radio" -> R.drawable.ic_radio
            "trending_gaming" -> R.drawable.ic_videogame_asset
            "trending_music" -> R.drawable.ic_music_note
            "trending_movies_and_shows" -> R.drawable.ic_movie
            "trending_podcasts_episodes" -> R.drawable.ic_podcasts
            "youtube_shorts" -> R.drawable.ic_smart_display
            else -> 0
        }
    }
}
