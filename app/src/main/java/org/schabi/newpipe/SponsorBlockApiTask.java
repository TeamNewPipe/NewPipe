package org.schabi.newpipe;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.util.SponsorTimeInfo;
import org.schabi.newpipe.util.TimeFrame;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SponsorBlockApiTask extends AsyncTask<String, Void, JSONObject> {
    private static final Application APP = App.getApp();
    private static final String SPONSOR_BLOCK_API_URL = "https://api.sponsor.ajay.app/api/";
    private static final int TIMEOUT_PERIOD = 30;
    private static final String TAG = SponsorBlockApiTask.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;
    private OkHttpClient client;

    // api methods
    public SponsorTimeInfo getVideoSponsorTimes(final String url) throws ExecutionException,
            InterruptedException, JSONException {
        String videoId = parseIdFromUrl(url);
        String apiSuffix = "getVideoSponsorTimes?videoID=" + videoId;

        JSONObject obj = execute(apiSuffix).get();
        JSONArray arrayObj = obj.getJSONArray("sponsorTimes");

        SponsorTimeInfo result = new SponsorTimeInfo();

        for (int i = 0; i < arrayObj.length(); i++) {
            JSONArray subArrayObj = arrayObj.getJSONArray(i);

            double startTime = subArrayObj.getDouble(0) * 1000;
            double endTime = subArrayObj.getDouble(1) * 1000;

            TimeFrame timeFrame = new TimeFrame(startTime, endTime);

            result.timeFrames.add(timeFrame);
        }

        return result;
    }

    public void postVideoSponsorTimes(final String url, final double startTime,
                                      final double endTime, final String userId) {
        double dStartTime = startTime / 1000.0;
        double dEndTime = endTime / 1000.0;

        String videoId = parseIdFromUrl(url);
        String apiSuffix = "postVideoSponsorTimes?videoID="
                + videoId
                + "&startTime="
                + dStartTime
                + "&endTime="
                + dEndTime
                + "&userID=" + (userId == null
                ? getRandomUserId()
                : userId);

        execute(apiSuffix);
    }

    // task methods
    @Override
    protected JSONObject doInBackground(final String... strings) {
        if (isCancelled() || !isConnected()) {
            return null;
        }

        try {
            if (client == null) {
                client = getUnsafeOkHttpClient()
                        .newBuilder()
                        .readTimeout(TIMEOUT_PERIOD, TimeUnit.SECONDS)
                        .build();
            }

            Request request = new Request.Builder()
                    .url(SPONSOR_BLOCK_API_URL + strings[0])
                    .build();

            Response response = client.newCall(request).execute();
            ResponseBody responseBody = response.body();

            return responseBody == null
                    ? null
                    : new JSONObject(responseBody.string());

        } catch (Exception ex) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }
        }

        return null;
    }

    // helper methods
    private boolean isConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) APP.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null
                && cm.getActiveNetworkInfo().isConnected();
    }

    private OkHttpClient getUnsafeOkHttpClient()
            throws NoSuchAlgorithmException, KeyManagementException {
        final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @SuppressLint("TrustAllX509TrustManager")
                    @Override
                    public void checkClientTrusted(final java.security.cert.X509Certificate[] chain,
                                                   final String authType) {
                    }

                    @SuppressLint("TrustAllX509TrustManager")
                    @Override
                    public void checkServerTrusted(final java.security.cert.X509Certificate[] chain,
                                                   final String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[] {};
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        return new OkHttpClient
                .Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }

    private String parseIdFromUrl(final String youTubeUrl) {
        String pattern = "(?<=youtu.be/|watch\\?v=|/videos/|embed/)[^#&?]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(youTubeUrl);
        if (matcher.find()) {
            return matcher.group();
        } else {
            return null;
        }
    }

    private String getRandomUserId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder salt = new StringBuilder();
        Random random = new Random();

        while (salt.length() < 36) {
            int index = (int) (random.nextFloat() * chars.length());
            salt.append(chars.charAt(index));
        }

        return salt.toString();
    }
}
