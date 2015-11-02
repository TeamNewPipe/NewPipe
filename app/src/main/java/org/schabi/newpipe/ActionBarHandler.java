package org.schabi.newpipe;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;

/**
 * Created by Christian Schabesberger on 18.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * DetailsMenuHandler.java is part of NewPipe.
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

public class ActionBarHandler {
    private static final String TAG = ActionBarHandler.class.toString();
    private static final String KORE_PACKET = "org.xbmc.kore";

    private String websiteUrl = "";
    private AppCompatActivity activity;
    private VideoInfo.VideoStream[] videoStreams = null;
    private VideoInfo.AudioStream audioStream = null;
    private int selectedStream = -1;
    private String videoTitle = "";

    SharedPreferences defaultPreferences = null;

    class FormatItemSelectListener implements ActionBar.OnNavigationListener {
        @Override
        public boolean onNavigationItemSelected(int itemPosition, long itemId) {
            selectFormatItem((int)itemId);
            return true;
        }
    }

    public ActionBarHandler(AppCompatActivity activity) {
        this.activity = activity;
    }

    public void setupNavMenu(AppCompatActivity activity) {
        this.activity = activity;
        activity.getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
    }

    public void setStreams(VideoInfo.VideoStream[] videoStreams, VideoInfo.AudioStream[] audioStreams) {
        this.videoStreams = videoStreams;
        selectedStream = 0;
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        String[] itemArray = new String[videoStreams.length];
        String defaultResolution = defaultPreferences
                .getString(activity.getString(R.string.defaultResolutionPreference),
                        activity.getString(R.string.defaultResolutionListItem));
        int defaultResolutionPos = 0;

        for(int i = 0; i < videoStreams.length; i++) {
            itemArray[i] = VideoInfo.getNameById(videoStreams[i].format) + " " + videoStreams[i].resolution;
            if(defaultResolution.equals(videoStreams[i].resolution)) {
                defaultResolutionPos = i;
            }
        }

        ArrayAdapter<String> itemAdapter = new ArrayAdapter<String>(activity.getBaseContext(),
                android.R.layout.simple_spinner_dropdown_item, itemArray);
        if(activity != null) {
            ActionBar ab = activity.getSupportActionBar();
            ab.setListNavigationCallbacks(itemAdapter
                    ,new FormatItemSelectListener());
            ab.setSelectedNavigationItem(defaultResolutionPos);
        }

        // set audioStream
        audioStream = null;
        String preferedFormat = defaultPreferences
                .getString(activity.getString(R.string.defaultAudioFormatPreference), "webm");
        if(preferedFormat.equals("webm")) {
            for(VideoInfo.AudioStream s : audioStreams) {
                if(s.format == VideoInfo.I_WEBMA) {
                    audioStream = s;
                }
            }
        } else if(preferedFormat.equals("m4a")){
            for(VideoInfo.AudioStream s : audioStreams) {
                Log.d(TAG, VideoInfo.getMimeById(s.format) + " : " + Integer.toString(s.bandwidth));
                if(s.format == VideoInfo.I_M4A &&
                        (audioStream == null || audioStream.bandwidth > s.bandwidth)) {
                    audioStream = s;
                    Log.d(TAG, "last choosen");
                }
            }
        }
    }

    private void selectFormatItem(int i) {
        selectedStream = i;
    }

    public boolean setupMenu(Menu menu, MenuInflater inflater) {
        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsinflater.inflate(R.menu.videoitem_detail, menu);

        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(activity);

        inflater.inflate(R.menu.videoitem_detail, menu);
        MenuItem playItem = menu.findItem(R.id.menu_item_play);
        MenuItem shareItem = menu.findItem(R.id.menu_item_share);
        MenuItem castItem = menu.findItem(R.id.action_play_with_kodi);

        MenuItemCompat.setShowAsAction(playItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS
                | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        MenuItemCompat.setShowAsAction(shareItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
                | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);

        castItem.setVisible(defaultPreferences
                .getBoolean(activity.getString(R.string.showPlayWidthKodiPreference), false));

        return true;
    }

    public boolean onItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch(id) {
            case R.id.menu_item_play:
                playVideo();
                return true;
            case R.id.menu_item_share:
                if(!videoTitle.isEmpty()) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_TEXT, websiteUrl);
                    intent.setType("text/plain");
                    activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.shareDialogTitle)));
                }
                return true;
            case R.id.menu_item_openInBrowser: {
                openInBrowser();
            }
                return true;
            case R.id.menu_item_download:
                downloadVideo();
                return true;
            case R.id.action_settings: {
                Intent intent = new Intent(activity, SettingsActivity.class);
                activity.startActivity(intent);
            }
            break;
            case R.id.action_play_with_kodi:
                playWithKodi();
                return true;
            case R.id.menu_item_play_audio:
                playAudio();
                return true;
            default:
                Log.e(TAG, "Menu Item not known");
        }
        return false;
    }

    public void setVideoInfo(String websiteUrl, String videoTitle) {
        this.websiteUrl = websiteUrl;
        this.videoTitle = videoTitle;
    }

    public void playVideo() {
        // ----------- THE MAGIC MOMENT ---------------
        if(!videoTitle.isEmpty()) {
            if (PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(activity.getString(R.string.useExternalPlayer), false)) {

                // External Player
                Intent intent = new Intent();
                try {
                    intent.setAction(Intent.ACTION_VIEW);

                    intent.setDataAndType(Uri.parse(videoStreams[selectedStream].url),
                            VideoInfo.getMimeById(videoStreams[selectedStream].format));
                    intent.putExtra(Intent.EXTRA_TITLE, videoTitle);
                    intent.putExtra("title", videoTitle);

                    activity.startActivity(intent);      // HERE !!!
                } catch (Exception e) {
                    e.printStackTrace();
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setMessage(R.string.noPlayerFound)
                            .setPositiveButton(R.string.installStreamPlayer, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setData(Uri.parse(activity.getString(R.string.fdroidVLCurl)));
                                    activity.startActivity(intent);
                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            });
                    builder.create().show();
                }
            } else {
                // Internal Player
                Intent intent = new Intent(activity, PlayVideoActivity.class);
                intent.putExtra(PlayVideoActivity.VIDEO_TITLE, videoTitle);
                intent.putExtra(PlayVideoActivity.STREAM_URL, videoStreams[selectedStream].url);
                intent.putExtra(PlayVideoActivity.VIDEO_URL, websiteUrl);
                activity.startActivity(intent);
            }
        }
        // --------------------------------------------
    }

    public void downloadVideo() {
        Log.d(TAG, "bla");
        if(!videoTitle.isEmpty()) {
            String videoSuffix = "." + VideoInfo.getSuffixById(videoStreams[selectedStream].format);
            String audioSuffix = "." + VideoInfo.getSuffixById(audioStream.format);
            Bundle args = new Bundle();
            args.putString(DownloadDialog.FILE_SUFFIX_VIDEO, videoSuffix);
            args.putString(DownloadDialog.FILE_SUFFIX_AUDIO, audioSuffix);
            args.putString(DownloadDialog.TITLE, videoTitle);
            args.putString(DownloadDialog.VIDEO_URL, videoStreams[selectedStream].url);
            args.putString(DownloadDialog.AUDIO_URL, audioStream.url);
            DownloadDialog downloadDialog = new DownloadDialog();
            downloadDialog.setArguments(args);
            downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
        }
    }

    public void openInBrowser() {
        if(!videoTitle.isEmpty()) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(websiteUrl));

            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.chooseBrowser)));
        }
    }

    public void playWithKodi() {
        if(!videoTitle.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setPackage(KORE_PACKET);
                intent.setData(Uri.parse(websiteUrl.replace("https", "http")));
                activity.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage(R.string.koreNotFound)
                        .setPositiveButton(R.string.installeKore, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent();
                                intent.setAction(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse(activity.getString(R.string.fdroidKoreUrl)));
                                activity.startActivity(intent);
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                builder.create().show();
            }
        }
    }

    public void playAudio() {
        Intent intent = new Intent();
        try {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(audioStream.url),
                    VideoInfo.getMimeById(audioStream.format));
            intent.putExtra(Intent.EXTRA_TITLE, videoTitle);
            intent.putExtra("title", videoTitle);
            activity.startActivity(intent);      // HERE !!!
        } catch (Exception e) {
            e.printStackTrace();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(R.string.noPlayerFound)
                    .setPositiveButton(R.string.installStreamPlayer, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(activity.getString(R.string.fdroidVLCurl)));
                            activity.startActivity(intent);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(TAG, "You unlocked a secret unicorn.");
                        }
                    });
            builder.create().show();
            Log.d(TAG, "Either no Streaming player for audio was installed, or something importand crashed:");
            e.printStackTrace();
        }
    }
}
