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
    fun getTranslatedKioskName(kioskId: String, context: Context): String {
        return when (kioskId) {
            "Trending" -> context.getString(R.string.trending)
            "Top 50" -> context.getString(R.string.top_50)
            "New & hot" -> context.getString(R.string.new_and_hot)
            "Local" -> context.getString(R.string.local)
            "Recently added" -> context.getString(R.string.recently_added)
            "Most liked" -> context.getString(R.string.most_liked)
            "conferences" -> context.getString(R.string.conferences)
            "recent" -> context.getString(R.string.recent)
            "live" -> context.getString(R.string.duration_live)
            "Featured" -> context.getString(R.string.featured)
            "Radio" -> context.getString(R.string.radio)
            "trending_gaming" -> context.getString(R.string.trending_gaming)
            "trending_music" -> context.getString(R.string.trending_music)
            "trending_movies_and_shows" -> context.getString(R.string.trending_movies)
            "trending_podcasts_episodes" -> context.getString(R.string.trending_podcasts)
            else -> kioskId
        }
    }

    @JvmStatic
    fun getKioskIcon(kioskId: String): Int {
        return when (kioskId) {
            "Trending", "Top 50", "New & hot", "conferences" -> R.drawable.ic_whatshot
            "Local" -> R.drawable.ic_home
            "Recently added", "recent" -> R.drawable.ic_add_circle_outline
            "Most liked" -> R.drawable.ic_thumb_up
            "live" -> R.drawable.ic_live_tv
            "Featured" -> R.drawable.ic_stars
            "Radio" -> R.drawable.ic_radio
            "trending_gaming" -> R.drawable.ic_videogame_asset
            "trending_music" -> R.drawable.ic_music_note
            "trending_movies_and_shows" -> R.drawable.ic_movie
            "trending_podcasts_episodes" -> R.drawable.ic_podcasts
            else -> 0
        }
    }
}
