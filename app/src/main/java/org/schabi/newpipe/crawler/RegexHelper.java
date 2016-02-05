package org.schabi.newpipe.crawler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Christian Schabesberger on 02.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * RegexHelper.java is part of NewPipe.
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

/** avoid using regex !!! */
public class RegexHelper {

    public static class RegexException extends ParsingException {
        public RegexException(String message) {
            super(message);
        }
    }

    public static String matchGroup1(String pattern, String input) throws RegexException {
        Pattern pat = Pattern.compile(pattern);
        Matcher mat = pat.matcher(input);
        boolean foundMatch = mat.find();
        if (foundMatch) {
            return mat.group(1);
        }
        else {
            //Log.e(TAG, "failed to find pattern \""+pattern+"\" inside of \""+input+"\"");
            throw new RegexException("failed to find pattern \""+pattern+" inside of "+input+"\"");
        }
    }
}
