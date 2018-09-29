package org.schabi.newpipe;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.schabi.newpipe.extractor.DownloadRequest;
import org.schabi.newpipe.extractor.DownloadResponse;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;


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

    private static Downloader instance;
    private String mCookies;
    private final OkHttpClient client;

    private Downloader(OkHttpClient.Builder builder) {
        this.client = builder
                .readTimeout(30, TimeUnit.SECONDS)
                //.cache(new Cache(new File(context.getExternalCacheDir(), "okhttp"), 16 * 1024 * 1024))
                .build();
    }

    /**
     * It's recommended to call exactly once in the entire lifetime of the application.
     *
     * @param builder if null, default builder will be used
     */
    public static Downloader init(@Nullable OkHttpClient.Builder builder) {
        return instance = new Downloader(builder != null ? builder : new OkHttpClient.Builder());
    }

    public static Downloader getInstance() {
        return instance;
    }

    public String getCookies() {
        return mCookies;
    }

    public void setCookies(String cookies) {
        mCookies = cookies;
    }

    /**
     * Get the size of the content that the url is pointing by firing a HEAD request.
     *
     * @param url an url pointing to the content
     * @return the size of the content, in bytes
     */
    public long getContentLength(String url) throws IOException {
        Response response = null;
        try {
            final Request request = new Request.Builder()
                    .head().url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .build();
            response = client.newCall(request).execute();

            return Long.parseLong(response.header("Content-Length"));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid content length", e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
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
        return getBody(siteUrl, customProperties).string();
    }

    public InputStream stream(String siteUrl) throws IOException {
        try {
            return getBody(siteUrl, Collections.emptyMap()).byteStream();
        } catch (ReCaptchaException e) {
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    private ResponseBody getBody(String siteUrl, Map<String, String> customProperties) throws IOException, ReCaptchaException {
        final Request.Builder requestBuilder = new Request.Builder()
                .method("GET", null).url(siteUrl);

        for (Map.Entry<String, String> header : customProperties.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        if (!customProperties.containsKey("User-Agent")) {
            requestBuilder.header("User-Agent", USER_AGENT);
        }

        if (!TextUtils.isEmpty(mCookies)) {
            requestBuilder.addHeader("Cookie", mCookies);
        }

        final Request request = requestBuilder.build();
        final Response response = client.newCall(request).execute();
        final ResponseBody body = response.body();

        if (response.code() == 429) {
            throw new ReCaptchaException("reCaptcha Challenge requested");
        }

        if (body == null) {
            response.close();
            return null;
        }

        return body;
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
        return download(siteUrl, Collections.emptyMap());
    }


    @Override
    public DownloadResponse get(String siteUrl, DownloadRequest request) throws IOException, ReCaptchaException {
        final Request.Builder requestBuilder = new Request.Builder()
                .method("GET", null).url(siteUrl);

        Map<String, List<String>> requestHeaders = request.getRequestHeaders();
        // set custom headers in request
        for (Map.Entry<String, List<String>> pair : requestHeaders.entrySet()) {
            for(String value : pair.getValue()){
                requestBuilder.addHeader(pair.getKey(), value);
            }
        }

        if (!requestHeaders.containsKey("User-Agent")) {
            requestBuilder.header("User-Agent", USER_AGENT);
        }

        if (!TextUtils.isEmpty(mCookies)) {
            requestBuilder.addHeader("Cookie", mCookies);
        }

        final Request okRequest = requestBuilder.build();
        final Response response = client.newCall(okRequest).execute();
        final ResponseBody body = response.body();

        if (response.code() == 429) {
            throw new ReCaptchaException("reCaptcha Challenge requested");
        }

        if (body == null) {
            response.close();
            return null;
        }

        return new DownloadResponse(body.string(), response.headers().toMultimap());
    }

    @Override
    public DownloadResponse get(String siteUrl) throws IOException, ReCaptchaException {
        return get(siteUrl, DownloadRequest.emptyRequest);
    }

    @Override
    public DownloadResponse post(String siteUrl, DownloadRequest request) throws IOException, ReCaptchaException {

        Map<String, List<String>> requestHeaders = request.getRequestHeaders();
        if(null == requestHeaders.get("Content-Type") || requestHeaders.get("Content-Type").isEmpty()){
            // content type header is required. maybe throw an exception here
            return null;
        }

        String contentType = requestHeaders.get("Content-Type").get(0);

        RequestBody okRequestBody = null;
        if(null != request.getRequestBody()){
            okRequestBody = RequestBody.create(MediaType.parse(contentType), request.getRequestBody());
        }
        final Request.Builder requestBuilder = new Request.Builder()
                .method("POST",  okRequestBody).url(siteUrl);

        // set custom headers in request
        for (Map.Entry<String, List<String>> pair : requestHeaders.entrySet()) {
            for(String value : pair.getValue()){
                requestBuilder.addHeader(pair.getKey(), value);
            }
        }

        if (!requestHeaders.containsKey("User-Agent")) {
            requestBuilder.header("User-Agent", USER_AGENT);
        }

        if (!TextUtils.isEmpty(mCookies)) {
            requestBuilder.addHeader("Cookie", mCookies);
        }

        final Request okRequest = requestBuilder.build();
        final Response response = client.newCall(okRequest).execute();
        final ResponseBody body = response.body();

        if (response.code() == 429) {
            throw new ReCaptchaException("reCaptcha Challenge requested");
        }

        if (body == null) {
            response.close();
            return null;
        }

        return new DownloadResponse(body.string(), response.headers().toMultimap());
    }
}