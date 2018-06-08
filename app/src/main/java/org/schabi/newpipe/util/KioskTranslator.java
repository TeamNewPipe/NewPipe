package org.schabi.newpipe.util;

import android.content.Context;

import org.schabi.newpipe.R;

/**
 * Created by Chrsitian Schabesberger on 28.09.17.
 * KioskTranslator.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class KioskTranslator {
    public static String getTranslatedKioskName(String kioskId, Context c) {
        switch (kioskId) {
            case "Trending":
                return c.getString(R.string.trending);
            case "Top 50":
                return c.getString(R.string.top_50);
            case "New & hot":
                return c.getString(R.string.new_and_hot);
            default:
                return kioskId;
        }
    }

    public static int getKioskIcons(String kioskId, Context c) {
        switch(kioskId) {
            case "Trending":
                return ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_hot);
            case "Top 50":
                return ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_hot);
            case "New & hot":
                return ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_hot);
            default:
                return 0;
        }
    }
}
