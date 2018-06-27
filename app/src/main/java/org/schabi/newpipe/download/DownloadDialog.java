package org.schabi.newpipe.download;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.kapodamy.SubtitleConverter;
import org.kapodamy.SubtitleUtils;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.StreamItemAdapter;
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import icepick.Icepick;
import icepick.State;
import io.reactivex.disposables.CompositeDisposable;
import us.shandian.giga.service.DownloadManagerService;

public class DownloadDialog extends DialogFragment implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "DialogFragment";
    private static final boolean DEBUG = MainActivity.DEBUG;

    @State protected StreamInfo currentInfo;
    @State protected StreamSizeWrapper<AudioStream> wrappedAudioStreams = StreamSizeWrapper.empty();
    @State protected StreamSizeWrapper<VideoStream> wrappedVideoStreams = StreamSizeWrapper.empty();
    @State protected StreamSizeWrapper<SubtitlesStream> wrappedSubtitleStreams = StreamSizeWrapper.empty();
    @State protected int selectedVideoIndex = 0;
    @State protected int selectedAudioIndex = 0;
    @State protected int selectedSubtitleIndex = 0;

    private StreamItemAdapter<AudioStream> audioStreamsAdapter;
    private StreamItemAdapter<VideoStream> videoStreamsAdapter;
    private StreamItemAdapter<SubtitlesStream> subtitleStreamsAdapter;

    private CompositeDisposable disposables = new CompositeDisposable();

    private EditText nameEditText;
    private Spinner streamsSpinner;
    private RadioGroup radioVideoAudioGroup;
    private TextView threadsCountTextView;
    private SeekBar threadsSeekBar;

    public static DownloadDialog newInstance(StreamInfo info) {
        DownloadDialog dialog = new DownloadDialog();
        dialog.setInfo(info);
        return dialog;
    }

    public static DownloadDialog newInstance(Context context, StreamInfo info) {
        final ArrayList<VideoStream> streamsList = new ArrayList<>(ListHelper.getSortedStreamVideosList(context,
                info.getVideoStreams(), info.getVideoOnlyStreams(), false));
        final int selectedStreamIndex = ListHelper.getDefaultResolutionIndex(context, streamsList);

        final DownloadDialog instance = newInstance(info);
        instance.setVideoStreams(streamsList);
        instance.setSelectedVideoStream(selectedStreamIndex);
        instance.setAudioStreams(info.getAudioStreams());
        instance.setSubtitleStreams(info.getSubtitles());

        return instance;
    }

    private void setInfo(StreamInfo info) {
        this.currentInfo = info;
    }

    public void setAudioStreams(List<AudioStream> audioStreams) {
        setAudioStreams(new StreamSizeWrapper<>(audioStreams));
    }

    public void setAudioStreams(StreamSizeWrapper<AudioStream> wrappedAudioStreams) {
        this.wrappedAudioStreams = wrappedAudioStreams;
    }

    public void setVideoStreams(List<VideoStream> videoStreams) {
        setVideoStreams(new StreamSizeWrapper<>(videoStreams));
    }

    public void setVideoStreams(StreamSizeWrapper<VideoStream> wrappedVideoStreams) {
        this.wrappedVideoStreams = wrappedVideoStreams;
    }

    public void setSubtitleStreams(List<SubtitlesStream> subtitleStreams) {
        setSubtitleStreams(new StreamSizeWrapper<>(subtitleStreams));
    }

    public void setSubtitleStreams(StreamSizeWrapper<SubtitlesStream> wrappedSubtitleStreams) {
        this.wrappedSubtitleStreams = wrappedSubtitleStreams;
    }

    public void setSelectedVideoStream(int selectedVideoIndex) {
        this.selectedVideoIndex = selectedVideoIndex;
    }

    public void setSelectedAudioStream(int selectedAudioIndex) {
        this.selectedAudioIndex = selectedAudioIndex;
    }

    public void setSelectedSubtitleStream(int selectedSubtitleIndex) {
        this.selectedSubtitleIndex = selectedSubtitleIndex;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        if (!PermissionHelper.checkStoragePermissions(getActivity(), PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            getDialog().dismiss();
            return;
        }

        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(getContext()));
        Icepick.restoreInstanceState(this, savedInstanceState);

        this.videoStreamsAdapter = new StreamItemAdapter<>(getContext(), wrappedVideoStreams, true);
        this.audioStreamsAdapter = new StreamItemAdapter<>(getContext(), wrappedAudioStreams);
        this.subtitleStreamsAdapter = new StreamItemAdapter<>(getContext(), wrappedSubtitleStreams);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreateView() called with: inflater = [" + inflater + "], container = [" + container + "], savedInstanceState = [" + savedInstanceState + "]");
        return inflater.inflate(R.layout.download_dialog, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        nameEditText = view.findViewById(R.id.file_name);
        nameEditText.setText(FilenameUtils.createFilename(getContext(), currentInfo.getName()));
        selectedAudioIndex = ListHelper.getDefaultAudioFormat(getContext(), currentInfo.getAudioStreams());

        selectedSubtitleIndex = SubtitleUtils.getSubtitleIndexBy(subtitleStreamsAdapter.getAll(),
                NewPipe.getLanguage().concat("-").concat(NewPipeSettings.getCountry(getContext()))
        );

        streamsSpinner = view.findViewById(R.id.quality_spinner);
        streamsSpinner.setOnItemSelectedListener(this);

        threadsCountTextView = view.findViewById(R.id.threads_count);
        threadsSeekBar = view.findViewById(R.id.threads);

        radioVideoAudioGroup = view.findViewById(R.id.video_audio_group);
        radioVideoAudioGroup.setOnCheckedChangeListener(this);

        initToolbar(view.findViewById(R.id.toolbar));
        setupDownloadOptions();

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

        fetchStreamsSize();
    }

    private void fetchStreamsSize() {
        disposables.clear();

        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedVideoStreams).subscribe(result -> {
            if (radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.video_button) {
                setupVideoSpinner();
            }
        }));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedAudioStreams).subscribe(result -> {
            if (radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                setupAudioSpinner();
            }
        }));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedSubtitleStreams).subscribe(result -> {
            if (radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.subtitle_button) {
                setupSubtitleSpinner();
            }
        }));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Inits
    //////////////////////////////////////////////////////////////////////////*/

    private void initToolbar(Toolbar toolbar) {
        if (DEBUG) Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]");
        toolbar.setTitle(R.string.download_dialog_title);
        toolbar.setNavigationIcon(ThemeHelper.isLightThemeSelected(getActivity()) ? R.drawable.ic_arrow_back_black_24dp : R.drawable.ic_arrow_back_white_24dp);
        toolbar.inflateMenu(R.menu.dialog_url);
        toolbar.setNavigationOnClickListener(v -> getDialog().dismiss());

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.okay) {
                downloadSelected();
                return true;
            }
            return false;
        });
    }

    private void setupAudioSpinner() {
        if (getContext() == null) return;

        streamsSpinner.setAdapter(audioStreamsAdapter);
        streamsSpinner.setSelection(selectedAudioIndex);
        setRadioButtonsState(true);
    }

    private void setupVideoSpinner() {
        if (getContext() == null) return;

        streamsSpinner.setAdapter(videoStreamsAdapter);
        streamsSpinner.setSelection(selectedVideoIndex);
        setRadioButtonsState(true);
    }

    private void setupSubtitleSpinner() {
        if (getContext() == null) return;

        streamsSpinner.setAdapter(subtitleStreamsAdapter);
        streamsSpinner.setSelection(selectedSubtitleIndex);
        setRadioButtonsState(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Radio group Video&Audio options - Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        if (DEBUG) Log.d(TAG, "onCheckedChanged() called with: group = [" + group + "], checkedId = [" + checkedId + "]");
        boolean flag = true;

        switch (checkedId) {
            case R.id.audio_button:
                setupAudioSpinner();
                break;
            case R.id.video_button:
                setupVideoSpinner();
                break;
            case R.id.subtitle_button:
                setupSubtitleSpinner();
                flag = false;
                break;
        }

        threadsSeekBar.setEnabled(flag);
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
            case R.id.subtitle_button:
                selectedSubtitleIndex = position;
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void setupDownloadOptions() {
        setRadioButtonsState(false);

        final RadioButton audioButton = radioVideoAudioGroup.findViewById(R.id.audio_button);
        final RadioButton videoButton = radioVideoAudioGroup.findViewById(R.id.video_button);
        final RadioButton subtitleButton = radioVideoAudioGroup.findViewById(R.id.subtitle_button);
        final boolean isVideoStreamsAvailable = videoStreamsAdapter.getCount() > 0;
        final boolean isAudioStreamsAvailable = audioStreamsAdapter.getCount() > 0;
        final boolean isSubtitleStreamsAvailable = subtitleStreamsAdapter.getCount() > 0;

        audioButton.setVisibility(isAudioStreamsAvailable ? View.VISIBLE : View.GONE);
        videoButton.setVisibility(isVideoStreamsAvailable ? View.VISIBLE : View.GONE);
        subtitleButton.setVisibility(isSubtitleStreamsAvailable ? View.VISIBLE : View.GONE);

        if (isVideoStreamsAvailable) {
            videoButton.setChecked(true);
            setupVideoSpinner();
        } else if (isAudioStreamsAvailable) {
            audioButton.setChecked(true);
            setupAudioSpinner();
        } else if (isSubtitleStreamsAvailable) {
            subtitleButton.setChecked(true);
            setupSubtitleSpinner();
        } else {
            Toast.makeText(getContext(), R.string.no_streams_available_download, Toast.LENGTH_SHORT).show();
            getDialog().dismiss();
        }
    }

    private void setRadioButtonsState(boolean enabled) {
        radioVideoAudioGroup.findViewById(R.id.audio_button).setEnabled(enabled);
        radioVideoAudioGroup.findViewById(R.id.video_button).setEnabled(enabled);
        radioVideoAudioGroup.findViewById(R.id.subtitle_button).setEnabled(enabled);
    }

    private void downloadSelected() {
        Stream stream;
        String location;
        char kind;// Unused in any DownloadManager

        String fileName = nameEditText.getText().toString().trim();
        if (fileName.isEmpty()) fileName = FilenameUtils.createFilename(getContext(), currentInfo.getName());

        switch (radioVideoAudioGroup.getCheckedRadioButtonId()) {
            case R.id.audio_button:
                stream = audioStreamsAdapter.getItem(selectedAudioIndex);
                location = NewPipeSettings.getAudioDownloadPath(getContext());
                kind = 'a';
                break;
            case R.id.video_button:
                stream = videoStreamsAdapter.getItem(selectedVideoIndex);
                location = NewPipeSettings.getVideoDownloadPath(getContext());
                kind = 'v';
                break;
            case R.id.subtitle_button:
                stream = subtitleStreamsAdapter.getItem(selectedSubtitleIndex);
                location = NewPipeSettings.getVideoDownloadPath(getContext());// assume that subtitle & video go together
                kind = 's';
                break;
            default:
                return;
        }

        String url = stream.getUrl();
        int threads;

        if (radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.subtitle_button) {
            fileName += ".srt";// final subtitle format
            SubtitleConverter.startMission(getContext(), (SubtitlesStream) stream, location, fileName);
        } else {
            fileName += "." + stream.getFormat().getSuffix();
            int threads = threadsSeekBar.getProgress() + 1;
            DownloadManagerService.startMission(getContext(), url, location, fileName, kind, threads);
        }

        getDialog().dismiss();
    }
}
