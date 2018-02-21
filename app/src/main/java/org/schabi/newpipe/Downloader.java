package org.schabi.newpipe;

import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/*
 * Created by Christian Schabesberger on 28.01.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
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

public class Downloader implements org.schabi.newpipe.extractor.Downloader {

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:43.0) Gecko/20100101 Firefox/43.0";
    private static String mCookies = "";

    private static Downloader instance = null;

    protected static OkHttpClient client = new OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build();

    private Downloader() {
    }

    public static Downloader getInstance() {
        if (instance == null) {
            synchronized (Downloader.class) {
                if (instance == null) {
                    instance = new Downloader();
                }
            }
        }
        return instance;
    }

    public static synchronized void setCookies(String cookies) {
        Downloader.mCookies = cookies;
    }

    public static synchronized String getCookies() {
        return Downloader.mCookies;
    }

    /**
     * Download the text file at the supplied URL as in download(String),
     * but set the HTTP header field "Accept-Language" to the supplied string.
     *
     * @param siteUrl  the URL of the text file to return the contents of
     * @param language the language (usually a 2-character code) to set as the preferred language
     * @return the contents of the specified text file
     */
    @Override
    public String download(String siteUrl, String language) throws IOException, ReCaptchaException {
        Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("Accept-Language", language);
        return download(siteUrl, requestProperties);
    }

    /**
     * Download the text file at the supplied URL as in download(String),
     * but set the HTTP headers included in the customProperties map.
     *
     * @param siteUrl          the URL of the text file to return the contents of
     * @param customProperties set request header properties
     * @return the contents of the specified text file
     * @throws IOException
     */
    @Override
    public String download(String siteUrl, Map<String, String> customProperties) throws IOException, ReCaptchaException {
        Request.Builder requestBuilder = new Request.Builder().url(siteUrl).addHeader("User-Agent", USER_AGENT).method("GET", null);
        for (Map.Entry<String, String> header : customProperties.entrySet()) {
            requestBuilder = requestBuilder.addHeader(header.getKey(), header.getValue());
        }
        if (getCookies().length() > 0) {
            requestBuilder = requestBuilder.addHeader("Cookie", getCookies());
        }
        Request request = requestBuilder.build();

        Response response = client.newCall(request).execute();

        if (response.code() == 429) {
            throw new ReCaptchaException("reCaptcha Challenge requested");
        }

        return response.body().string();
    }

    /**
     * Download (via HTTP) the text file located at the supplied URL, and return its contents.
     * Primarily intended for downloading web pages.
     *
     * @param siteUrl the URL of the text file to download
     * @return the contents of the specified text file
     */
    @Override
    public String download(String siteUrl) throws IOException, ReCaptchaException {
        return download(siteUrl, new HashMap<>());
    }
}
