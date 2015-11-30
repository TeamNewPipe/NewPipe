package org.schabi.newpipe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Created by Christian Schabesberger on 14.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * Downloader.java is part of NewPipe.
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

public class Downloader {

    private static final String USER_AGENT = "Mozilla/5.0";

    /**Download the text file at the supplied URL as in download(String),
     * but set the HTTP header field "Accept-Language" to the supplied string.
     * @param siteUrl the URL of the text file to return the contents of
     * @param language the language (usually a 2-character code) to set as the preferred language
     * @return the contents of the specified text file*/
    public static String download(String siteUrl, String language) {
        String ret = "";
        try {
            URL url = new URL(siteUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("Accept-Language", language);
            ret = dl(con);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return ret;
    }
    /**Common functionality between download(String url) and download(String url, String language)*/
    private static String dl(HttpURLConnection con) throws IOException {
        StringBuilder response = new StringBuilder();

        try {
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", USER_AGENT);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;

            while((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

        }
        catch(UnknownHostException uhe) {//thrown when there's no internet connection
            uhe.printStackTrace();
            //Toast.makeText(getActivity(), uhe.getMessage(), Toast.LENGTH_LONG).show();
        }

        return response.toString();
    }

/**Download (via HTTP) the text file located at the supplied URL, and return its contents.
 * Primarily intended for downloading web pages.
 * @param siteUrl the URL of the text file to download
 * @return the contents of the specified text file*/
    public static String download(String siteUrl) {
        String ret = "";

        try {
            URL url = new URL(siteUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            ret = dl(con);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        return ret;
    }
}
