package org.schabi.newpipe.download;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.detail.SpinnerToolbarAdapter;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import us.shandian.giga.service.DownloadManagerService;

public class DownloadDialog extends DialogFragment implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "DialogFragment";
    private static final boolean DEBUG = MainActivity.DEBUG;

    private static final String INFO_KEY = "info_key";
    private static final String SORTED_VIDEOS_LIST_KEY = "sorted_videos_list_key";
    private static final String SELECTED_VIDEO_KEY = "selected_video_key";
    private static final String SELECTED_AUDIO_KEY = "selected_audio_key";

    private StreamInfo currentInfo;
    private ArrayList<VideoStream> sortedStreamVideosList;
    private int selectedVideoIndex;
    private int selectedAudioIndex;

    private EditText nameEditText;
    private Spinner streamsSpinner;
    private RadioGroup radioVideoAudioGroup;
    private TextView threadsCountTextView;
    private SeekBar threadsSeekBar;

    public static DownloadDialog newInstance(StreamInfo info, ArrayList<VideoStream> sortedStreamVideosList, int selectedVideoIndex) {
        DownloadDialog dialog = new DownloadDialog();
        dialog.setInfo(info, sortedStreamVideosList, selectedVideoIndex);
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        return dialog;
    }

    private void setInfo(StreamInfo info, ArrayList<VideoStream> sortedStreamVideosList, int selectedVideoIndex) {
        this.currentInfo = info;
        this.selectedVideoIndex = selectedVideoIndex;
        this.sortedStreamVideosList = sortedStreamVideosList;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        if (!PermissionHelper.checkStoragePermissions(getActivity())) {
            getDialog().dismiss();
            return;
        }

        if (savedInstanceState != null) {
            Serializable serial = savedInstanceState.getSerializable(INFO_KEY);
            if (serial instanceof StreamInfo) currentInfo = (StreamInfo) serial;

            serial = savedInstanceState.getSerializable(SORTED_VIDEOS_LIST_KEY);
            if (serial instanceof ArrayList) { //noinspection unchecked
                sortedStreamVideosList = (ArrayList<VideoStream>) serial;
            }

            selectedVideoIndex = savedInstanceState.getInt(SELECTED_VIDEO_KEY, 0);
            selectedAudioIndex = savedInstanceState.getInt(SELECTED_AUDIO_KEY, 0);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreateView() called with: inflater = [" + inflater + "], container = [" + container + "], savedInstanceState = [" + savedInstanceState + "]");
        return inflater.inflate(R.layout.dialog_url, container);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        nameEditText = view.findViewById(R.id.file_name);
        nameEditText.setText(FilenameUtils.createFilename(getContext(), currentInfo.getName()));
        selectedAudioIndex = ListHelper.getDefaultAudioFormat(getContext(), currentInfo.getAudioStreams());

        streamsSpinner = view.findViewById(R.id.quality_spinner);
        streamsSpinner.setOnItemSelectedListener(this);

        threadsCountTextView = view.findViewById(R.id.threads_count);
        threadsSeekBar = view.findViewById(R.id.threads);
        radioVideoAudioGroup = view.findViewById(R.id.video_audio_group);
        radioVideoAudioGroup.setOnCheckedChangeListener(this);

        initToolbar(view.<Toolbar>findViewById(R.id.toolbar));
        checkDownloadOptions(view);
        setupVideoSpinner(sortedStreamVideosList, streamsSpinner);

        int def = 3;
        threadsCountTextView.setText(String.valueOf(def));
        threadsSeekBar.setProgress(def - 1);
        threadsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                threadsCountTextView.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar p1) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar p1) {
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(INFO_KEY, currentInfo);
        outState.putSerializable(SORTED_VIDEOS_LIST_KEY, sortedStreamVideosList);
        outState.putInt(SELECTED_VIDEO_KEY, selectedVideoIndex);
        outState.putInt(SELECTED_AUDIO_KEY, selectedAudioIndex);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Inits
    //////////////////////////////////////////////////////////////////////////*/

    private void initToolbar(Toolbar toolbar) {
        if (DEBUG) Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]");
        toolbar.setTitle(R.string.download_dialog_title);
        toolbar.setNavigationIcon(ThemeHelper.isLightThemeSelected(getActivity()) ? R.drawable.ic_arrow_back_black_24dp : R.drawable.ic_arrow_back_white_24dp);
        toolbar.inflateMenu(R.menu.dialog_url);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getDialog().dismiss();
            }
        });

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.okay) {
                    downloadSelected();
                    return true;
                } else return false;
            }
        });
    }

    public void setupAudioSpinner(final List<AudioStream> audioStreams, Spinner spinner) {
        String[] items = new String[audioStreams.size()];
        for (int i = 0; i < audioStreams.size(); i++) {
            AudioStream audioStream = audioStreams.get(i);
            items[i] = audioStream.getFormat().getName() + " " + audioStream.getAverageBitrate() + "kbps";
        }

        ArrayAdapter<String> itemAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, items);
        spinner.setAdapter(itemAdapter);
        spinner.setSelection(selectedAudioIndex);
    }

    public void setupVideoSpinner(final List<VideoStream> videoStreams, Spinner spinner) {
        spinner.setAdapter(new SpinnerToolbarAdapter(getContext(), videoStreams, true));
        spinner.setSelection(selectedVideoIndex);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Radio group Video&Audio options - Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        if (DEBUG) Log.d(TAG, "onCheckedChanged() called with: group = [" + group + "], checkedId = [" + checkedId + "]");
        switch (checkedId) {
            case R.id.audio_button:
                setupAudioSpinner(currentInfo.audio_streams, streamsSpinner);
                break;
            case R.id.video_button:
                setupVideoSpinner(sortedStreamVideosList, streamsSpinner);
                break;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Streams Spinner Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (DEBUG) Log.d(TAG, "onItemSelected() called with: parent = [" + parent + "], view = [" + view + "], position = [" + position + "], id = [" + id + "]");
        switch (radioVideoAudioGroup.getCheckedRadioButtonId()) {
            case R.id.audio_button:
                selectedAudioIndex = position;
                break;
            case R.id.video_button:
                selectedVideoIndex = position;
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void checkDownloadOptions(View view) {
        RadioButton audioButton = view.findViewById(R.id.audio_button);
        RadioButton videoButton = view.findViewById(R.id.video_button);

        if (currentInfo.getAudioStreams() == null || currentInfo.getAudioStreams().size() == 0) {
            audioButton.setVisibility(View.GONE);
            videoButton.setChecked(true);
        } else if (sortedStreamVideosList == null || sortedStreamVideosList.size() == 0) {
            videoButton.setVisibility(View.GONE);
            audioButton.setChecked(true);
        }
    }


    private void downloadSelected() {
        String url, location;

        String fileName = nameEditText.getText().toString().trim();
        if (fileName.isEmpty()) fileName = FilenameUtils.createFilename(getContext(), currentInfo.getName());

        boolean isAudio = radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button;
        if (isAudio) {
            url = currentInfo.getAudioStreams().get(selectedAudioIndex).getUrl();
            location = NewPipeSettings.getAudioDownloadPath(getContext());
            fileName += "." + currentInfo.getAudioStreams().get(selectedAudioIndex).getFormat().getSuffix();
        } else {
            url = sortedStreamVideosList.get(selectedVideoIndex).getUrl();
            location = NewPipeSettings.getVideoDownloadPath(getContext());
            fileName += "." + sortedStreamVideosList.get(selectedVideoIndex).getFormat().getSuffix();
        }

        DownloadManagerService.startMission(getContext(), url, location, fileName, isAudio, threadsSeekBar.getProgress() + 1);
        getDialog().dismiss();
    }
}
