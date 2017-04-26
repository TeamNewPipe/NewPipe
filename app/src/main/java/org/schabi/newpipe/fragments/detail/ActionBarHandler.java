package org.schabi.newpipe.fragments.detail;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream_info.VideoStream;
import org.schabi.newpipe.util.Utils;

import java.util.List;

/**
 * Created by Christian Schabesberger on 18.08.15.
 * <p>
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * DetailsMenuHandler.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */


class ActionBarHandler {
    private static final String TAG = "ActionBarHandler";

    private AppCompatActivity activity;
    private int selectedVideoStream = -1;

    private SharedPreferences defaultPreferences;

    private Menu menu;

    // Only callbacks are listed here, there are more actions which don't need a callback.
    // those are edited directly. Typically VideoDetailFragment will implement those callbacks.
    private OnActionListener onShareListener;
    private OnActionListener onOpenInBrowserListener;
    private OnActionListener onDownloadListener;
    private OnActionListener onPlayWithKodiListener;

    // Triggered when a stream related action is triggered.
    public interface OnActionListener {
        void onActionSelected(int selectedStreamId);
    }

    public ActionBarHandler(AppCompatActivity activity) {
        this.activity = activity;
    }

    public void setupStreamList(final List<VideoStream> videoStreams, Spinner toolbarSpinner) {
        if (activity == null) return;
        selectedVideoStream = 0;

        // this array will be shown in the dropdown menu for selecting the stream/resolution.
        String[] itemArray = new String[videoStreams.size()];
        for (int i = 0; i < videoStreams.size(); i++) {
            VideoStream item = videoStreams.get(i);
            itemArray[i] = MediaFormat.getNameById(item.format) + " " + item.resolution;
        }

        int defaultResolutionIndex = Utils.getDefaultResolution(activity, videoStreams);
        ArrayAdapter<String> itemAdapter = new ArrayAdapter<>(activity.getBaseContext(), android.R.layout.simple_spinner_dropdown_item, itemArray);
        toolbarSpinner.setAdapter(itemAdapter);
        toolbarSpinner.setSelection(defaultResolutionIndex);
        toolbarSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVideoStream = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }

    public void setupMenu(Menu menu, MenuInflater inflater) {
        this.menu = menu;

        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsinflater.inflate(R.menu.videoitem_detail, menu);

        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        inflater.inflate(R.menu.video_detail_menu, menu);

        showPlayWithKodiAction(defaultPreferences.getBoolean(activity.getString(R.string.show_play_with_kodi_key), false));
    }

    public boolean onItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_item_share: {
                if (onShareListener != null) {
                    onShareListener.onActionSelected(selectedVideoStream);
                }
                return true;
            }
            case R.id.menu_item_openInBrowser: {
                if (onOpenInBrowserListener != null) {
                    onOpenInBrowserListener.onActionSelected(selectedVideoStream);
                }
                return true;
            }
            case R.id.menu_item_download:
                if (onDownloadListener != null) {
                    onDownloadListener.onActionSelected(selectedVideoStream);
                }
                return true;
            case R.id.action_play_with_kodi:
                if (onPlayWithKodiListener != null) {
                    onPlayWithKodiListener.onActionSelected(selectedVideoStream);
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

    public void showDownloadAction(boolean visible) {
        menu.findItem(R.id.menu_item_download).setVisible(visible);
    }

    public void showPlayWithKodiAction(boolean visible) {
        menu.findItem(R.id.action_play_with_kodi).setVisible(visible);
    }
}
