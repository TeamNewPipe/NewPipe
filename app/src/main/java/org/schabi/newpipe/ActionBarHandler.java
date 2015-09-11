package org.schabi.newpipe;

import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
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

import java.io.File;

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
    private static ActionBarHandler handler = null;

    private Context context = null;
    private String webisteUrl = "";
    private AppCompatActivity activity;
    private VideoInfo.Stream[] streams = null;
    private int selectedStream = -1;
    private String videoTitle = "";

    public static ActionBarHandler getHandler() {
        if(handler == null) {
            handler = new ActionBarHandler();
        }
        return handler;
    }

    class ForamatItemSelectListener implements ActionBar.OnNavigationListener {
        @Override
        public boolean onNavigationItemSelected(int itemPosition, long itemId) {
            selectFormatItem((int)itemId);
            return true;
        }
    }

    public void setupNavMenu(AppCompatActivity activity) {
        this.activity = activity;
        activity.getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
    }

    public void setStreams(VideoInfo.Stream[] streams) {
        // // TODO: 11.09.15 add auto stream option 
        this.streams = streams;
        selectedStream = 0;
        String[] itemArray = new String[streams.length];
        for(int i = 0; i < streams.length; i++) {
            itemArray[i] = streams[i].format + " " + streams[i].resolution;
        }

        ArrayAdapter<String> itemAdapter = new ArrayAdapter<String>(activity.getBaseContext(),
                android.R.layout.simple_spinner_dropdown_item, itemArray);
        if(activity != null) {
            activity.getSupportActionBar().setListNavigationCallbacks(itemAdapter
                    ,new ForamatItemSelectListener());
        }
    }

    private void selectFormatItem(int i) {
        selectedStream = i;
    }

    public boolean setupMenu(Menu menu, MenuInflater inflater, Context context) {
        this.context = context;
        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsinflater.inflate(R.menu.videoitem_detail, menu);

        inflater.inflate(R.menu.videoitem_detail, menu);
        MenuItem playItem = menu.findItem(R.id.menu_item_play);
        MenuItem shareItem = menu.findItem(R.id.menu_item_share);

        MenuItemCompat.setShowAsAction(playItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS
                | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        MenuItemCompat.setShowAsAction(shareItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
                | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);

        return true;
    }

    public boolean onItemSelected(MenuItem item, Context context) {
        this.context = context;
        int id = item.getItemId();
        switch(id) {
            case R.id.menu_item_play:
                playVideo();
                break;
            case R.id.menu_item_share:
                if(!videoTitle.isEmpty()) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_TEXT, webisteUrl);
                    intent.setType("text/plain");
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.shareDialogTitle)));
                }
                break;
            case R.id.menu_item_openInBrowser: {
                openInBrowser();
            }
                break;
            case R.id.menu_item_download:
                downloadVideo();
                break;
            case R.id.action_settings: {
                Intent intent = new Intent(context, SettingsActivity.class);
                context.startActivity(intent);
            }
            default:
                Log.e(TAG, "Menu Item not known");
        }
        return true;
    }

    public void setVideoInfo(String websiteUrl, String videoTitle) {
        this.webisteUrl = websiteUrl;
        this.videoTitle = videoTitle;
    }

    public void playVideo() {
        // ----------- THE MAGIC MOMENT ---------------
        if(!videoTitle.isEmpty()) {
            if (PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("use_external_player", false)) {
                Intent intent = new Intent();
                try {
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(streams[selectedStream].url),
                            "video/" + streams[selectedStream].format);
                    context.startActivity(intent);      // HERE !!!
                } catch (Exception e) {
                    e.printStackTrace();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setMessage(R.string.noPlayerFound)
                            .setPositiveButton(R.string.installStreamPlayer, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setData(Uri.parse(context.getString(R.string.fdroidVLCurl)));
                                    context.startActivity(intent);
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
                Intent intent = new Intent(context, PlayVideoActivity.class);
                intent.putExtra(PlayVideoActivity.VIDEO_TITLE, videoTitle);
                intent.putExtra(PlayVideoActivity.STREAM_URL, streams[selectedStream].url);
                intent.putExtra(PlayVideoActivity.VIDEO_URL, webisteUrl);
                context.startActivity(intent);
            }
        }
        // --------------------------------------------
    }

    public void downloadVideo() {
        Log.d(TAG, "bla");
        if(!videoTitle.isEmpty()) {
            String suffix = "";
            switch (streams[selectedStream].format) {
                case VideoInfo.F_WEBM:
                    suffix = ".webm";
                    break;
                case VideoInfo.F_MPEG_4:
                    suffix = ".mp4";
                    break;
                case VideoInfo.F_3GPP:
                    suffix = ".3gp";
                    break;
            }
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(
                    Uri.parse(streams[selectedStream].url));
            request.setDestinationUri(Uri.fromFile(new File(
                            PreferenceManager.getDefaultSharedPreferences(context)
                            .getString("download_path_preference", "/storage/emulated/0/NewPipe")
                            + "/" + videoTitle + suffix)));
            try {
                dm.enqueue(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void openInBrowser() {
        if(!videoTitle.isEmpty()) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(webisteUrl));

            context.startActivity(Intent.createChooser(intent, context.getString(R.string.chooseBrowser)));
        }
    }
}
