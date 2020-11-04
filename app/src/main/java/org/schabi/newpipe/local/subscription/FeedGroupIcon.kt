package org.schabi.newpipe.local.subscription

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

enum class FeedGroupIcon(
    /**
     * The id that will be used to store and retrieve icons from some persistent storage (e.g. DB).
     */
    val id: Int,

    /**
     * The attribute that points to a drawable resource. "R.attr" is used here to support multiple themes.
     */
    @AttrRes val drawableResourceAttr: Int
) {
    ALL(0, R.attr.ic_asterisk),
    MUSIC(1, R.attr.ic_music_note),
    EDUCATION(2, R.attr.ic_school),
    FITNESS(3, R.attr.ic_fitness_center),
    SPACE(4, R.attr.ic_telescope),
    COMPUTER(5, R.attr.ic_computer),
    GAMING(6, R.attr.ic_videogame_asset),
    SPORTS(7, R.attr.ic_sports),
    NEWS(8, R.attr.ic_megaphone),
    FAVORITES(9, R.attr.ic_heart),
    CAR(10, R.attr.ic_car),
    MOTORCYCLE(11, R.attr.ic_motorcycle),
    TREND(12, R.attr.ic_trending_up),
    MOVIE(13, R.attr.ic_movie),
    BACKUP(14, R.attr.ic_backup),
    ART(15, R.attr.ic_palette),
    PERSON(16, R.attr.ic_person),
    PEOPLE(17, R.attr.ic_people),
    MONEY(18, R.attr.ic_money),
    KIDS(19, R.attr.ic_child_care),
    FOOD(20, R.attr.ic_fastfood),
    SMILE(21, R.attr.ic_smile),
    EXPLORE(22, R.attr.ic_explore),
    RESTAURANT(23, R.attr.ic_restaurant),
    MIC(24, R.attr.ic_mic),
    HEADSET(25, R.attr.ic_headset),
    RADIO(26, R.attr.ic_radio),
    SHOPPING_CART(27, R.attr.ic_shopping_cart),
    WATCH_LATER(28, R.attr.ic_watch_later),
    WORK(29, R.attr.ic_work),
    HOT(30, R.attr.ic_kiosk_hot),
    CHANNEL(31, R.attr.ic_channel),
    BOOKMARK(32, R.attr.ic_bookmark),
    PETS(33, R.attr.ic_pets),
    WORLD(34, R.attr.ic_world),
    STAR(35, R.attr.ic_stars),
    SUN(36, R.attr.ic_sunny),
    RSS(37, R.attr.ic_rss);

    @DrawableRes
    fun getDrawableRes(context: Context): Int {
        return ThemeHelper.resolveResourceIdFromAttr(context, drawableResourceAttr)
    }
}
