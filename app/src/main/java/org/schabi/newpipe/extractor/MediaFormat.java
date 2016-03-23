package org.schabi.newpipe.extractor;

/**
 * Created by Adam Howard on 08/11/15.
 *
 * Copyright (c) Christian Schabesberger <chris.schabesberger@mailbox.org>
 *     and Adam Howard <achdisposable1@gmail.com> 2015
 *
 * MediaFormat.java is part of NewPipe.
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

/**Static data about various media formats support by Newpipe, eg mime type, extension*/

public enum MediaFormat {
    //video and audio combined formats
    //           id      name    suffix  mime type
    MPEG_4      (0x0,   "MPEG-4", "mp4", "video/mp4"),
    v3GPP       (0x1,   "3GPP",   "3gp", "video/3gpp"),
    WEBM        (0x2,   "WebM",  "webm", "video/webm"),
    // audio formats
    M4A         (0x3,   "m4a",   "m4a",  "audio/mp4"),
    WEBMA       (0x4,   "WebM",  "webm", "audio/webm");

    public final int id;
    @SuppressWarnings("WeakerAccess")
    public final String name;
    @SuppressWarnings("WeakerAccess")
    public final String suffix;
    public final String mimeType;

    MediaFormat(int id, String name, String suffix, String mimeType) {
        this.id = id;
        this.name = name;
        this.suffix = suffix;
        this.mimeType = mimeType;
    }

    /**Return the friendly name of the media format with the supplied id
     * @param ident the id of the media format. Currently an arbitrary, NewPipe-specific number.
     * @return the friendly name of the MediaFormat associated with this ids,
     * or an empty String if none match it.*/
    public static String getNameById(int ident) {
        for (MediaFormat vf : MediaFormat.values()) {
            if(vf.id == ident) return vf.name;
        }
        return "";
    }

    /**Return the file extension of the media format with the supplied id
     * @param ident the id of the media format. Currently an arbitrary, NewPipe-specific number.
     * @return the file extension of the MediaFormat associated with this ids,
     * or an empty String if none match it.*/
    public static String getSuffixById(int ident) {
        for (MediaFormat vf : MediaFormat.values()) {
            if(vf.id == ident) return vf.suffix;
        }
        return "";
    }

    /**Return the MIME type of the media format with the supplied id
     * @param ident the id of the media format. Currently an arbitrary, NewPipe-specific number.
     * @return the MIME type of the MediaFormat associated with this ids,
     * or an empty String if none match it.*/
    public static String getMimeById(int ident) {
        for (MediaFormat vf : MediaFormat.values()) {
            if(vf.id == ident) return vf.mimeType;
        }
        return "";
    }
}
