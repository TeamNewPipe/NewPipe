package org.schabi.newpipe.download;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import org.schabi.newpipe.App;
import org.schabi.newpipe.NewPipeSettings;
import org.schabi.newpipe.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;


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

    private DownloadManager mManager;
    private DownloadManagerService.DMBinder mBinder;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName p1, IBinder binder) {
            mBinder = (DownloadManagerService.DMBinder) binder;
            mManager = mBinder.getDownloadManager();
        }

        @Override
        public void onServiceDisconnected(ComponentName p1) {

        }
    };


    public DownloadDialog() {

    }

    public static DownloadDialog newInstance(Bundle args)
    {
        DownloadDialog dialog = new DownloadDialog();
        dialog.setArguments(args);
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if(ContextCompat.checkSelfPermission(this.getContext(),Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(getActivity(),new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);

        Intent i = new Intent();
        i.setClass(getContext(), DownloadManagerService.class);
        getContext().startService(i);
        getContext().bindService(i, mConnection, Context.BIND_AUTO_CREATE);


        return inflater.inflate(R.layout.dialog_url, container);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle arguments = getArguments();
        final Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        final EditText name = (EditText) view.findViewById(R.id.file_name);
        final TextView tCount = (TextView) view.findViewById(R.id.threads_count);
        final SeekBar threads = (SeekBar) view.findViewById(R.id.threads);

        toolbar.setTitle(R.string.download_dialog_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
        toolbar.inflateMenu(R.menu.dialog_url);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getDialog().dismiss();
            }
        });

        threads.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                tCount.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar p1) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar p1) {

            }
        });

        checkDownloadOptions();

        //int def = mPrefs.getInt("threads", 4);
        int def = 3;
        threads.setProgress(def - 1);
        tCount.setText(String.valueOf(def));

        name.setText(createFileName(arguments.getString(TITLE)));


        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.okay) {
                    download();
                    return true;
                } else {
                    return false;
                }
            }
        });

    }

    protected void checkDownloadOptions(){
        View view = getView();
        Bundle arguments = getArguments();
        CheckBox audio = (CheckBox) view.findViewById(R.id.audio);
        CheckBox video = (CheckBox) view.findViewById(R.id.video);

        if(arguments.getString(AUDIO_URL) == null) {
            audio.setVisibility(View.GONE);
        } else if(arguments.getString(VIDEO_URL) == null) {
            video.setVisibility(View.GONE);
        }
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


    //download audio, video or both?
    private void download()
    {
        View view = getView();
        Bundle arguments = getArguments();
        final EditText name = (EditText) view.findViewById(R.id.file_name);
        final SeekBar threads = (SeekBar) view.findViewById(R.id.threads);
        CheckBox audio = (CheckBox) view.findViewById(R.id.audio);
        CheckBox video = (CheckBox) view.findViewById(R.id.video);

        String fName = name.getText().toString().trim();

        // todo: add timeout? would be bad if the thread gets locked dueto this.
        while (mBinder == null);

        if(audio.isChecked()){
            int res = mManager.startMission(
                    arguments.getString(AUDIO_URL),
                    fName + arguments.getString(FILE_SUFFIX_AUDIO),
                    threads.getProgress() + 1);
            mBinder.onMissionAdded(mManager.getMission(res));
        }

        if(video.isChecked()){
            int res = mManager.startMission(
                    arguments.getString(VIDEO_URL),
                    fName + arguments.getString(FILE_SUFFIX_VIDEO),
                    threads.getProgress() + 1);
            mBinder.onMissionAdded(mManager.getMission(res));
        }
        getDialog().dismiss();

    }

    private void download(String url, String title,
                          String fileSuffix, File downloadDir, Context context) {

        File saveFilePath = new File(downloadDir,createFileName(title) + fileSuffix);

        long id = 0;

        Log.i(TAG,"Started downloading '" + url +
                "' => '" + saveFilePath + "' #" + id);

        if (App.isUsingTor()) {
            //if using Tor, do not use DownloadManager because the proxy cannot be set
            //we'll see later
            FileDownloader.downloadFile(getContext(), url, saveFilePath, title);
        } else {
            Intent intent = new Intent(getContext(), MainActivity.class);
            intent.setAction(MainActivity.INTENT_DOWNLOAD);
            intent.setData(Uri.parse(url));
            intent.putExtra("fileName", createFileName(title) + fileSuffix);
            startActivity(intent);
        }
    }
}
