package org.schabi.newpipe.util

import android.content.Context
import org.schabi.newpipe.R

/**
 * Created by Christian Schabesberger on 28.09.17.
 * KioskTranslator.java is part of NewPipe.
 *
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 *
 */
object KioskTranslator {
    fun getTranslatedKioskName(kioskId: String?, c: Context?): String? {
        when (kioskId) {
            "Trending" -> return c!!.getString(R.string.trending)
            "Top 50" -> return c!!.getString(R.string.top_50)
            "New & hot" -> return c!!.getString(R.string.new_and_hot)
            "Local" -> return c!!.getString(R.string.local)
            "Recently added" -> return c!!.getString(R.string.recently_added)
            "Most liked" -> return c!!.getString(R.string.most_liked)
            "conferences" -> return c!!.getString(R.string.conferences)
            "recent" -> return c!!.getString(R.string.recent)
            "live" -> return c!!.getString(R.string.duration_live)
            "Featured" -> return c!!.getString(R.string.featured)
            "Radio" -> return c!!.getString(R.string.radio)
            else -> return kioskId
        }
    }

    fun getKioskIcon(kioskId: String?): Int {
        when (kioskId) {
            "Trending", "Top 50", "New & hot", "conferences" -> return R.drawable.ic_whatshot
            "Local" -> return R.drawable.ic_home
            "Recently added", "recent" -> return R.drawable.ic_add_circle_outline
            "Most liked" -> return R.drawable.ic_thumb_up
            "live" -> return R.drawable.ic_live_tv
            "Featured" -> return R.drawable.ic_stars
            "Radio" -> return R.drawable.ic_radio
            else -> return 0
        }
    }
}
