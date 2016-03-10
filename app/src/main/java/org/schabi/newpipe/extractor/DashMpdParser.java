package org.schabi.newpipe.extractor;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 02.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DashMpdParser.java is part of NewPipe.
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

public class DashMpdParser {

    private DashMpdParser() {
    }

    static class DashMpdParsingException extends ParsingException {
        DashMpdParsingException(String message, Exception e) {
            super(message, e);
        }
    }

    public static List<AudioStream> getAudioStreams(String dashManifestUrl,
                                                             Downloader downloader)
            throws DashMpdParsingException {
        String dashDoc;
        try {
            dashDoc = downloader.download(dashManifestUrl);
        } catch(IOException ioe) {
            throw new DashMpdParsingException("Could not get dash mpd: " + dashManifestUrl, ioe);
        }
        Vector<AudioStream> audioStreams = new Vector<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(dashDoc));
            String tagName = "";
            String currentMimeType = "";
            int currentBandwidth = -1;
            int currentSamplingRate = -1;
            boolean currentTagIsBaseUrl = false;
            for(int eventType = parser.getEventType();
                eventType != XmlPullParser.END_DOCUMENT;
                eventType = parser.next() ) {
                switch(eventType) {
                    case XmlPullParser.START_TAG:
                        tagName = parser.getName();
                        if(tagName.equals("AdaptationSet")) {
                            currentMimeType = parser.getAttributeValue(XmlPullParser.NO_NAMESPACE, "mimeType");
                        } else if(tagName.equals("Representation") && currentMimeType.contains("audio")) {
                            currentBandwidth = Integer.parseInt(
                                    parser.getAttributeValue(XmlPullParser.NO_NAMESPACE, "bandwidth"));
                            currentSamplingRate = Integer.parseInt(
                                    parser.getAttributeValue(XmlPullParser.NO_NAMESPACE, "audioSamplingRate"));
                        } else if(tagName.equals("BaseURL")) {
                            currentTagIsBaseUrl = true;
                        }
                        break;

                    case XmlPullParser.TEXT:
                        // actual stream tag
                        if(currentTagIsBaseUrl &&
                                (currentMimeType.contains("audio"))) {
                            int format = -1;
                            if(currentMimeType.equals(MediaFormat.WEBMA.mimeType)) {
                                format = MediaFormat.WEBMA.id;
                            } else if(currentMimeType.equals(MediaFormat.M4A.mimeType)) {
                                format = MediaFormat.M4A.id;
                            }
                            audioStreams.add(new AudioStream(parser.getText(),
                                    format, currentBandwidth, currentSamplingRate));
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if(tagName.equals("AdaptationSet")) {
                            currentMimeType = "";
                        } else if(tagName.equals("BaseURL")) {
                            currentTagIsBaseUrl = false;
                        }
                        break;
                }
            }
        } catch(Exception e) {
            throw new DashMpdParsingException("Could not parse Dash mpd", e);
        }
        return audioStreams;
    }
}
