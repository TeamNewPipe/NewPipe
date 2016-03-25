package org.schabi.newpipe.download;

import android.Manifest;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import org.schabi.newpipe.App;
import org.schabi.newpipe.NewPipeSettings;
import org.schabi.newpipe.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Christian Schabesberger on 21.09.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * DownloadDialog.java is part of NewPipe.
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

public class DownloadDialog extends DialogFragment {
    private static final String TAG = DialogFragment.class.getName();

    public static final String TITLE = "name";
    public static final String FILE_SUFFIX_AUDIO = "file_suffix_audio";
    public static final String FILE_SUFFIX_VIDEO = "file_suffix_video";
    public static final String AUDIO_URL = "audio_url";
    public static final String VIDEO_URL = "video_url";
    private Bundle arguments;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        arguments = getArguments();
        super.onCreateDialog(savedInstanceState);
        if(ContextCompat.checkSelfPermission(this.getContext(),Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(getActivity(),new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.download_dialog_title);

        // If no audio stream available
        if(arguments.getString(AUDIO_URL) == null) {
            builder.setItems(R.array.download_options_no_audio, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Context context = getActivity();
                    String title = arguments.getString(TITLE);
                    switch (which) {
                        case 0:     // Video
                            download(arguments.getString(VIDEO_URL),
                                    title,
                                    arguments.getString(FILE_SUFFIX_VIDEO),
                                    NewPipeSettings.getVideoDownloadFolder(context),context);
                            break;
                        default:
                            Log.d(TAG, "lolz");
                    }
                }
            });
            // If no video stream available
        } else if(arguments.getString(VIDEO_URL) == null) {
            builder.setItems(R.array.download_options_no_video, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Context context = getActivity();
                    String title = arguments.getString(TITLE);
                    switch (which) {
                        case 0:     // Audio
                            download(arguments.getString(AUDIO_URL),
                                    title,
                                    arguments.getString(FILE_SUFFIX_AUDIO),
                                    NewPipeSettings.getAudioDownloadFolder(context),context);
                            break;
                        default:
                            Log.d(TAG, "lolz");
                    }
                }
            });
            //if both streams ar available
        } else {
            builder.setItems(R.array.download_options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Context context = getActivity();
                    String title = arguments.getString(TITLE);
                    switch (which) {
                        case 0:     // Video
                            download(arguments.getString(VIDEO_URL),
                                    title,
                                    arguments.getString(FILE_SUFFIX_VIDEO),
                                    NewPipeSettings.getVideoDownloadFolder(context), context);
                            break;
                        case 1:
                            download(arguments.getString(AUDIO_URL),
                                    title,
                                    arguments.getString(FILE_SUFFIX_AUDIO),
                                    NewPipeSettings.getAudioDownloadFolder(context), context);
                            break;
                        default:
                            Log.d(TAG, "lolz");
                    }
                }
            });
        }
        return builder.create();
    }

    /**
     * #143 #44 #42 #22: make shure that the filename does not contain illegal chars.
     * This should fix some of the "cannot download" problems.
     * */
    private String createFileName(String fName) {
        // from http://eng-przemelek.blogspot.de/2009/07/how-to-create-valid-file-name.html

        List<String> forbiddenCharsPatterns = new ArrayList<> ();
        forbiddenCharsPatterns.add("[:]+"); // Mac OS, but it looks that also Windows XP
        forbiddenCharsPatterns.add("[\\*\"/\\\\\\[\\]\\:\\;\\|\\=\\,]+");  // Windows
        forbiddenCharsPatterns.add("[^\\w\\d\\.]+");  // last chance... only latin letters and digits
        String nameToTest = fName;
        for (String pattern : forbiddenCharsPatterns) {
            nameToTest = nameToTest.replaceAll(pattern, "_");
        }
        return nameToTest;
    }

    private void download(String url, String title,
                          String fileSuffix, File downloadDir, Context context) {

        if(!downloadDir.exists()) {
            //attempt to create directory
            boolean mkdir = downloadDir.mkdirs();
            if(!mkdir && !downloadDir.isDirectory()) {
                String message = context.getString(R.string.err_dir_create,downloadDir.toString());
                Log.e(TAG, message);
                Toast.makeText(context,message , Toast.LENGTH_LONG).show();

                return;
            }
            String message = context.getString(R.string.info_dir_created,downloadDir.toString());
            Log.e(TAG, message);
            Toast.makeText(context,message , Toast.LENGTH_LONG).show();
        }

        File saveFilePath = new File(downloadDir,createFileName(title) + fileSuffix);

        long id = 0;


        if (App.isUsingTor()) {
            // if using Tor, do not use DownloadManager because the proxy cannot be set
            FileDownloader.downloadFile(getContext(), url, saveFilePath, title);
        } else {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(
                    Uri.parse(url));
            request.setDestinationUri(Uri.fromFile(saveFilePath));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            request.setTitle(title);
            request.setDescription("'" + url +
                    "' => '" + saveFilePath + "'");
            request.allowScanningByMediaScanner();

            try {
                id = dm.enqueue(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Log.i(TAG,"Started downloading '" + url +
                "' => '" + saveFilePath + "' #" + id);
    }
}
