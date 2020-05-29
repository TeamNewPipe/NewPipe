package org.schabi.newpipe.util;

import android.content.Context;

import org.schabi.newpipe.R;

/**
 * Created by Chrsitian Schabesberger on 28.09.17.
 * KioskTranslator.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public final class KioskTranslator {
    private KioskTranslator() { }

    public static String getTranslatedKioskName(final String kioskId, final Context c) {
        switch (kioskId) {
            case "Trending":
                return c.getString(R.string.trending);
            case "Top 50":
                return c.getString(R.string.top_50);
            case "New & hot":
                return c.getString(R.string.new_and_hot);
            case "Local":
                return c.getString(R.string.local);
            case "Recently added":
                return c.getString(R.string.recently_added);
            case "Most liked":
                return c.getString(R.string.most_liked);
            case "conferences":
                return c.getString(R.string.conferences);
            default:
                return kioskId;
        }
    }

    public static int getKioskIcon(final String kioskId, final Context c) {
        switch (kioskId) {
            case "Trending":
            case "Top 50":
            case "New & hot":
            case "conferences":
                return ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_kiosk_hot);
            case "Local":
                return ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_kiosk_local);
            case "Recently added":
                return ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_kiosk_recent);
            case "Most liked":
                return ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_thumb_up);
            default:
                return 0;
        }
    }
}
