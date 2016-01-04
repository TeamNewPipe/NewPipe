package org.schabi.newpipe;


import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.HttpsURLConnection;

import info.guardianproject.netcipher.NetCipher;

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
    public static final String TAG = "Downloader";
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
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestProperty("Accept-Language", language);
            ret = dl(con);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    /**Common functionality between download(String url) and download(String url, String language)*/
    private static String dl(HttpsURLConnection con) throws IOException {
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
            HttpsURLConnection con = NetCipher.getHttpsURLConnection(url);
            ret = dl(con);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        return ret;
    }

    /**
     * Downloads a file from a URL in the background using an {@link AsyncTask}.
     *
     * @param fileURL      HTTP URL of the file to be downloaded
     * @param saveFilePath path of the directory to save the file
     * @throws IOException
     */
    public static void downloadFile(final Context context, final String fileURL, final String saveFilePath) {
        new AsyncTask<Void, Integer, Void>() {

            private NotificationManager nm;
            private NotificationCompat.Builder builder;
            private int notifyId = 0x1234;
            private int fileSize = 0xffffffff;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                Drawable icon = context.getResources().getDrawable(R.mipmap.ic_launcher);
                builder = new NotificationCompat.Builder(context)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setLargeIcon(((BitmapDrawable) icon).getBitmap())
                        .setContentTitle(saveFilePath.substring(saveFilePath.lastIndexOf('/') + 1))
                        .setContentText(saveFilePath)
                        .setProgress(fileSize, 0, false);
                nm.notify(notifyId, builder.build());
            }

            @Override
            protected Void doInBackground(Void... voids) {
                HttpsURLConnection con = null;
                try {
                    con = NetCipher.getHttpsURLConnection(fileURL);
                    int responseCode = con.getResponseCode();

                    // always check HTTP response code first
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        fileSize = con.getContentLength();
                        InputStream inputStream = new BufferedInputStream(con.getInputStream());
                        FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                        int bufferSize = 8192;
                        int downloaded = 0;

                        int bytesRead = -1;
                        byte[] buffer = new byte[bufferSize];
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            downloaded += bytesRead;
                            if (downloaded % 50000 < bufferSize) {
                                publishProgress(downloaded);
                            }
                        }

                        outputStream.close();
                        inputStream.close();
                        publishProgress(bufferSize);

                    } else {
                        Log.i(TAG, "No file to download. Server replied HTTP code: " + responseCode);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (con != null) {
                        con.disconnect();
                        con = null;
                    }
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Integer... progress) {
                builder.setProgress(fileSize, progress[0], false);
                nm.notify(notifyId, builder.build());
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                nm.cancel(notifyId);
            }
        }.execute();
    }

}
