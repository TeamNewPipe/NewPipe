package org.schabi.newpipe;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamInfo;
import org.schabi.newpipe.extractor.VideoStream;

import java.util.List;

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


class ActionBarHandler {
    private static final String TAG = ActionBarHandler.class.toString();

    private AppCompatActivity activity;
    private int selectedVideoStream = -1;

    private SharedPreferences defaultPreferences = null;

    private Menu menu;

    // Only callbacks are listed here, there are more actions which don't need a callback.
    // those are edited directly. Typically VideoItemDetailFragment will implement those callbacks.
    private OnActionListener onShareListener = null;
    private OnActionListener onOpenInBrowserListener = null;
    private OnActionListener onDownloadListener = null;
    private OnActionListener onPlayWithKodiListener = null;
    private OnActionListener onPlayAudioListener = null;


    // Triggered when a stream related action is triggered.
    public interface OnActionListener {
        void onActionSelected(int selectedStreamId);
    }

    public ActionBarHandler(AppCompatActivity activity) {
        this.activity = activity;
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    public void setupNavMenu(AppCompatActivity activity) {
        this.activity = activity;
        try {
            activity.getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void setupStreamList(final List<VideoStream> videoStreams) {
        if (activity != null) {
            selectedVideoStream = 0;


            // this array will be shown in the dropdown menu for selecting the stream/resolution.
            String[] itemArray = new String[videoStreams.size()];
            for (int i = 0; i < videoStreams.size(); i++) {
                VideoStream item = videoStreams.get(i);
                itemArray[i] = MediaFormat.getNameById(item.format) + " " + item.resolution;
            }
            int defaultResolution = getDefaultResolution(videoStreams);

            ArrayAdapter<String> itemAdapter = new ArrayAdapter<>(activity.getBaseContext(),
                    android.R.layout.simple_spinner_dropdown_item, itemArray);

            ActionBar ab = activity.getSupportActionBar();
            //todo: make this throwsable
            assert ab != null : "Could not get actionbar";
            ab.setListNavigationCallbacks(itemAdapter
                    , new ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                    selectedVideoStream = (int) itemId;
                    return true;
                }
            });

            ab.setSelectedNavigationItem(defaultResolution);
        }
    }


    private int getDefaultResolution(final List<VideoStream> videoStreams) {
        String defaultResolution = defaultPreferences
                .getString(activity.getString(R.string.default_resolution_key),
                        activity.getString(R.string.default_resolution_value));

        for (int i = 0; i < videoStreams.size(); i++) {
            VideoStream item = videoStreams.get(i);
            if (defaultResolution.equals(item.resolution)) {
                return i;
            }
        }
        // this is actually an error,
        // but maybe there is really no stream fitting to the default value.
        return 0;
    }

    public void setupMenu(Menu menu, MenuInflater inflater) {
        this.menu = menu;

        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsinflater.inflate(R.menu.videoitem_detail, menu);

        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        inflater.inflate(R.menu.videoitem_detail, menu);

        showPlayWithKodiAction(defaultPreferences
                .getBoolean(activity.getString(R.string.show_play_with_kodi_key), false));
    }

    public boolean onItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_item_share: {
                    /*
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_TEXT, websiteUrl);
                    intent.setType("text/plain");
                    activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_dialog_title)));
                    */
                if(onShareListener != null) {
                    onShareListener.onActionSelected(selectedVideoStream);
                }
                return true;
            }
            case R.id.menu_item_openInBrowser: {
                if(onOpenInBrowserListener != null) {
                    onOpenInBrowserListener.onActionSelected(selectedVideoStream);
                }
            }
            return true;
            case R.id.menu_item_download:
                if(onDownloadListener != null) {
                    onDownloadListener.onActionSelected(selectedVideoStream);
                }
                return true;
            case R.id.action_settings: {
                Intent intent = new Intent(activity, SettingsActivity.class);
                activity.startActivity(intent);
                return true;
            }
            case R.id.action_play_with_kodi:
                if(onPlayWithKodiListener != null) {
                    onPlayWithKodiListener.onActionSelected(selectedVideoStream);
                }
                return true;
            case R.id.menu_item_play_audio:
                if(onPlayAudioListener != null) {
                    onPlayAudioListener.onActionSelected(selectedVideoStream);
                }
                return true;
            default:
                Log.e(TAG, "Menu Item not known");
        }
        return false;
    }

    public int getSelectedVideoStream() {
        return selectedVideoStream;
    }

    public void setOnShareListener(OnActionListener listener) {
        onShareListener = listener;
    }

    public void setOnOpenInBrowserListener(OnActionListener listener) {
        onOpenInBrowserListener = listener;
    }

    public void setOnDownloadListener(OnActionListener listener) {
        onDownloadListener = listener;
    }

    public void setOnPlayWithKodiListener(OnActionListener listener) {
        onPlayWithKodiListener = listener;
    }

    public void setOnPlayAudioListener(OnActionListener listener) {
        onPlayAudioListener = listener;
    }

    public void showAudioAction(boolean visible) {
        menu.findItem(R.id.menu_item_play_audio).setVisible(visible);
    }

    public void showDownloadAction(boolean visible) {
        menu.findItem(R.id.menu_item_download).setVisible(visible);
    }

    public void showPlayWithKodiAction(boolean visible) {
        menu.findItem(R.id.action_play_with_kodi).setVisible(visible);
    }
}
