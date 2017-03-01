package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream_info.AudioStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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

    public static List<AudioStream> getAudioStreams(String dashManifestUrl)
            throws DashMpdParsingException, ReCaptchaException {
        String dashDoc;
        Downloader downloader = NewPipe.getDownloader();
        try {
            dashDoc = downloader.download(dashManifestUrl);
        } catch(IOException ioe) {
            throw new DashMpdParsingException("Could not get dash mpd: " + dashManifestUrl, ioe);
        } catch (ReCaptchaException e) {
            throw new ReCaptchaException("reCaptcha Challenge needed");
        }
        Vector<AudioStream> audioStreams = new Vector<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream stream = new ByteArrayInputStream(dashDoc.getBytes());

            Document doc = builder.parse(stream);
            NodeList adaptationSetList = doc.getElementsByTagName("AdaptationSet");
            for(int i = 0; i < adaptationSetList.getLength(); i++) {
                Element adaptationSet = (Element) adaptationSetList.item(i);
                String memeType = adaptationSet.getAttribute("mimeType");
                if(memeType.contains("audio")) {
                    Element representation = (Element) adaptationSet.getElementsByTagName("Representation").item(0);
                    String url = representation.getElementsByTagName("BaseURL").item(0).getTextContent();
                    int bandwidth = Integer.parseInt(representation.getAttribute("bandwidth"));
                    int samplingRate = Integer.parseInt(representation.getAttribute("audioSamplingRate"));
                    int format = -1;
                    if(memeType.equals(MediaFormat.WEBMA.mimeType)) {
                        format = MediaFormat.WEBMA.id;
                    } else if(memeType.equals(MediaFormat.M4A.mimeType)) {
                        format = MediaFormat.M4A.id;
                    }
                    audioStreams.add(new AudioStream(url, format, bandwidth, samplingRate));
                }
            }
        }
        catch(Exception e) {
            throw new DashMpdParsingException("Could not parse Dash mpd", e);
        }
        return audioStreams;
    }
}
