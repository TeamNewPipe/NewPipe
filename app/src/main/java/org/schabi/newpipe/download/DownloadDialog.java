package org.schabi.newpipe.download;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonParserException;
import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DownloadDialogBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.streams.SrtFromTtmlWriter;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.SecondaryStreamHelper;
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.StreamItemAdapter;
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import us.shandian.giga.get.HlsDownloadStreamHelper;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.get.SabrDownloadStreamHelper;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder;
import us.shandian.giga.service.MissionState;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class DownloadDialog extends DialogFragment
        implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener,
        StateSaver.WriteRead {
    private static final String TAG = "DialogFragment";
    private static final boolean DEBUG = MainActivity.DEBUG;

    StreamInfo currentInfo;
    StreamSizeWrapper<AudioStream> wrappedAudioStreams = StreamSizeWrapper.empty();
    StreamSizeWrapper<VideoStream> wrappedVideoStreams = StreamSizeWrapper.empty();
    StreamSizeWrapper<SubtitlesStream> wrappedSubtitleStreams = StreamSizeWrapper.empty();
    int selectedVideoIndex = 0;
    int selectedAudioIndex = 0;
    int selectedSubtitleIndex = 0;

    @Nullable
    private OnDismissListener onDismissListener = null;

    private StoredDirectoryHelper mainStorageAudio = null;
    private StoredDirectoryHelper mainStorageVideo = null;
    private DownloadManager downloadManager = null;
    private ActionMenuItemView okButton = null;
    private Context context;
    private boolean askForSavePath;

    private StreamItemAdapter<AudioStream, Stream> audioStreamsAdapter;
    private StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter;
    private StreamItemAdapter<SubtitlesStream, Stream> subtitleStreamsAdapter;

    private final CompositeDisposable disposables = new CompositeDisposable();

    private DownloadDialogBinding dialogBinding;

    private SharedPreferences prefs;
    private org.schabi.newpipe.util.SavedState savedState;

    // Variables for file name and MIME type when picking new folder because it's not set yet
    private String filenameTmp;
    private String mimeTmp;

    private final ActivityResultLauncher<Intent> requestDownloadSaveAsLauncher =
            registerForActivityResult(
                    new StartActivityForResult(), this::requestDownloadSaveAsResult);
    private final ActivityResultLauncher<Intent> requestDownloadPickAudioFolderLauncher =
            registerForActivityResult(
                    new StartActivityForResult(), this::requestDownloadPickAudioFolderResult);
    private final ActivityResultLauncher<Intent> requestDownloadPickVideoFolderLauncher =
            registerForActivityResult(
                    new StartActivityForResult(), this::requestDownloadPickVideoFolderResult);


    /*//////////////////////////////////////////////////////////////////////////
    // Instance creation
    //////////////////////////////////////////////////////////////////////////*/

    public static DownloadDialog newInstance(final StreamInfo info) {
        final DownloadDialog dialog = new DownloadDialog();
        dialog.setInfo(info);
        return dialog;
    }

    public static DownloadDialog newInstance(final Context context, final StreamInfo info) {
        final ArrayList<VideoStream> streamsList = new ArrayList<>(ListHelper
                .getSortedStreamVideosList(context, info.getVideoStreams(),
                        info.getVideoOnlyStreams(), false, false));

        final List<VideoStream> filteredVideoStreams = ListHelper
                .filterVideoStreamsByPreferredLanguage(context, streamsList,
                        info.getAudioStreams());

        final int selectedStreamIndex = ListHelper.getDefaultResolutionIndex(
                context, filteredVideoStreams);
        HlsDownloadStreamHelper.addManifestFallbackIfNeeded(filteredVideoStreams, info);

        final List<AudioStream> downloadableAudio = ListHelper
                .filterDownloadableAudioStreams(info.getAudioStreams());
        HlsDownloadStreamHelper.addAudioFallbackIfNeeded(downloadableAudio, info);

        final DownloadDialog instance = newInstance(info);
        instance.setVideoStreams(filteredVideoStreams);
        instance.setSelectedVideoStream(selectedStreamIndex >= 0 ? selectedStreamIndex : 0);
        instance.setAudioStreams(downloadableAudio);
        instance.setSubtitleStreams(info.getSubtitles());

        return instance;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Setters
    //////////////////////////////////////////////////////////////////////////*/

    private void setInfo(final StreamInfo info) {
        this.currentInfo = info;
    }

    public void setAudioStreams(final List<AudioStream> audioStreams) {
        setAudioStreams(new StreamSizeWrapper<>(audioStreams, getContext()));
    }

    public void setAudioStreams(final StreamSizeWrapper<AudioStream> was) {
        this.wrappedAudioStreams = was;
    }

    public void setVideoStreams(final List<VideoStream> videoStreams) {
        setVideoStreams(new StreamSizeWrapper<>(videoStreams, getContext()));
    }

    public void setVideoStreams(final StreamSizeWrapper<VideoStream> wvs) {
        this.wrappedVideoStreams = wvs;
    }

    public void setSubtitleStreams(final List<SubtitlesStream> subtitleStreams) {
        setSubtitleStreams(new StreamSizeWrapper<>(subtitleStreams, getContext()));
    }

    public void setSubtitleStreams(
            final StreamSizeWrapper<SubtitlesStream> wss) {
        this.wrappedSubtitleStreams = wss;
    }

    public void setSelectedVideoStream(final int svi) {
        this.selectedVideoIndex = svi;
    }

    public void setSelectedAudioStream(final int sai) {
        this.selectedAudioIndex = sai;
    }

    public void setSelectedSubtitleStream(final int ssi) {
        this.selectedSubtitleIndex = ssi;
    }

    public void setOnDismissListener(@Nullable final OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Android lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }

        if (!PermissionHelper.checkStoragePermissions(getActivity(),
                PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            dismiss();
            return;
        }

        context = getContext();

        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(context));
        if (savedInstanceState != null) {
            savedState = StateSaver.tryToRestore(savedInstanceState, this);
            selectedVideoIndex = savedInstanceState.getInt("selectedVideoIndex", 0);
            selectedAudioIndex = savedInstanceState.getInt("selectedAudioIndex", 0);
            selectedSubtitleIndex = savedInstanceState.getInt("selectedSubtitleIndex", 0);
        }

        final SparseArray<SecondaryStreamHelper<AudioStream>> secondaryStreams
                = new SparseArray<>(4);
        final List<VideoStream> videoStreams = wrappedVideoStreams.getStreamsList();

        for (int i = 0; i < videoStreams.size(); i++) {
            if (!videoStreams.get(i).isVideoOnly()) {
                continue;
            }
            final AudioStream audioStream = SecondaryStreamHelper
                    .getAudioStreamFor(getContext(), SabrDownloadStreamHelper.audioStreamsForVideo(
                            wrappedAudioStreams.getStreamsList(), videoStreams.get(i)), videoStreams.get(i));

            if (audioStream != null && SabrDownloadStreamHelper
                    .isCompatibleSecondaryStream(videoStreams.get(i), audioStream)) {
                secondaryStreams
                        .append(i, new SecondaryStreamHelper<>(wrappedAudioStreams, audioStream));
            } else if (DEBUG) {
                Log.w(TAG, "No audio stream candidates for video format "
                        + videoStreams.get(i).getFormat().name());
            }
        }

        this.videoStreamsAdapter = new StreamItemAdapter<>(context, wrappedVideoStreams,
                secondaryStreams);
        this.audioStreamsAdapter = new StreamItemAdapter<>(context, wrappedAudioStreams);
        this.subtitleStreamsAdapter = new StreamItemAdapter<>(context, wrappedSubtitleStreams);

        final Intent intent = new Intent(context, DownloadManagerService.class);
        context.startService(intent);

        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName cname, final IBinder service) {
                final DownloadManagerBinder mgr = (DownloadManagerBinder) service;

                mainStorageAudio = mgr.getMainStorageAudio();
                mainStorageVideo = mgr.getMainStorageVideo();
                downloadManager = mgr.getDownloadManager();
                askForSavePath = mgr.askForSavePath();

                okButton.setEnabled(true);

                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                // nothing to do
            }
        }, Context.BIND_AUTO_CREATE);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreateView() called with: "
                    + "inflater = [" + inflater + "], container = [" + container + "], "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }
        return inflater.inflate(R.layout.download_dialog, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dialogBinding = DownloadDialogBinding.bind(view);

        dialogBinding.fileName.setText(FilenameUtils.createFilename(getContext(),
                currentInfo.getName()));
        selectedAudioIndex = ListHelper
                .getDefaultAudioFormat(getContext(), wrappedAudioStreams.getStreamsList());

        selectedSubtitleIndex = getSubtitleIndexBy(subtitleStreamsAdapter.getAll());

        dialogBinding.qualitySpinner.setOnItemSelectedListener(this);

        dialogBinding.videoAudioGroup.setOnCheckedChangeListener(this);

        initToolbar(dialogBinding.toolbarLayout.toolbar);
        setupDownloadOptions();

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        final int threads = prefs.getInt(getString(R.string.default_download_threads), 3);
        dialogBinding.threadsCount.setText(String.valueOf(threads));
        dialogBinding.threads.setProgress(threads - 1);
        dialogBinding.threads.setOnSeekBarChangeListener(new SimpleOnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull final SeekBar seekbar, final int progress,
                                          final boolean fromUser) {
                final int newProgress = progress + 1;
                prefs.edit().putInt(getString(R.string.default_download_threads), newProgress)
                        .apply();
                dialogBinding.threadsCount.setText(String.valueOf(newProgress));
            }
        });

        fetchStreamsSize();
    }

    private void initToolbar(final Toolbar toolbar) {
        if (DEBUG) {
            Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]");
        }

        toolbar.setTitle(R.string.download_dialog_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.inflateMenu(R.menu.dialog_url);
        toolbar.setNavigationOnClickListener(v -> dismiss());
        toolbar.setNavigationContentDescription(R.string.cancel);

        okButton = toolbar.findViewById(R.id.okay);
        okButton.setEnabled(false); // disable until the download service connection is done

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.okay) {
                prepareSelectedDownload();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.onDismiss(dialog);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
        StateSaver.onDestroy(savedState);
    }

    @Override
    public void onDestroyView() {
        dialogBinding = null;
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        savedState = StateSaver.tryToSave(
                requireActivity().isChangingConfigurations(), savedState, outState, this);
        outState.putInt("selectedVideoIndex", selectedVideoIndex);
        outState.putInt("selectedAudioIndex", selectedAudioIndex);
        outState.putInt("selectedSubtitleIndex", selectedSubtitleIndex);
    }

    @Override
    public String generateSuffix() {
        return "." + System.nanoTime() + ".download";
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        objectsToSave.add(currentInfo);
        objectsToSave.add(wrappedAudioStreams);
        objectsToSave.add(wrappedVideoStreams);
        objectsToSave.add(wrappedSubtitleStreams);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) {
        currentInfo = (StreamInfo) savedObjects.poll();
        wrappedAudioStreams = (StreamSizeWrapper<AudioStream>) savedObjects.poll();
        wrappedVideoStreams = (StreamSizeWrapper<VideoStream>) savedObjects.poll();
        wrappedSubtitleStreams = (StreamSizeWrapper<SubtitlesStream>) savedObjects.poll();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Video, audio and subtitle spinners
    //////////////////////////////////////////////////////////////////////////*/

    private void fetchStreamsSize() {
        disposables.clear();
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedVideoStreams)
                .subscribe(result -> {
                    if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()
                            == R.id.video_button) {
                        setupVideoSpinner();
                    }
                }, throwable -> ErrorUtil.showSnackbar(context,
                        new ErrorInfo(throwable, UserAction.DOWNLOAD_OPEN_DIALOG,
                                "Downloading video stream size",
                                currentInfo.getServiceId()))));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedAudioStreams)
                .subscribe(result -> {
                    if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()
                            == R.id.audio_button) {
                        setupAudioSpinner();
                    }
                }, throwable -> ErrorUtil.showSnackbar(context,
                        new ErrorInfo(throwable, UserAction.DOWNLOAD_OPEN_DIALOG,
                                "Downloading audio stream size",
                                currentInfo.getServiceId()))));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedSubtitleStreams)
                .subscribe(result -> {
                    if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()
                            == R.id.subtitle_button) {
                        setupSubtitleSpinner();
                    }
                }, throwable -> ErrorUtil.showSnackbar(context,
                        new ErrorInfo(throwable, UserAction.DOWNLOAD_OPEN_DIALOG,
                                "Downloading subtitle stream size",
                                currentInfo.getServiceId()))));
    }

    private void setupAudioSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setAdapter(audioStreamsAdapter);
        dialogBinding.qualitySpinner.setSelection(selectedAudioIndex);
        setRadioButtonsState(true);
    }

    private void setupVideoSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setAdapter(videoStreamsAdapter);
        dialogBinding.qualitySpinner.setSelection(selectedVideoIndex);
        setRadioButtonsState(true);
    }

    private void setupSubtitleSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setAdapter(subtitleStreamsAdapter);
        dialogBinding.qualitySpinner.setSelection(selectedSubtitleIndex);
        setRadioButtonsState(true);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Activity results
    //////////////////////////////////////////////////////////////////////////*/

    private void requestDownloadPickAudioFolderResult(final ActivityResult result) {
        requestDownloadPickFolderResult(
                result, getString(R.string.download_path_audio_key), DownloadManager.TAG_AUDIO);
    }

    private void requestDownloadPickVideoFolderResult(final ActivityResult result) {
        requestDownloadPickFolderResult(
                result, getString(R.string.download_path_video_key), DownloadManager.TAG_VIDEO);
    }

    private void requestDownloadSaveAsResult(final ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK) {
            return;
        }

        if (result.getData() == null || result.getData().getData() == null) {
            showFailedDialog(R.string.general_error);
            return;
        }

        if (FilePickerActivityHelper.isOwnFileUri(context, result.getData().getData())) {
            final File file = Utils.getFileForUri(result.getData().getData());
            checkSelectedDownload(null, Uri.fromFile(file), file.getName(),
                    StoredFileHelper.DEFAULT_MIME);
            return;
        }

        final DocumentFile docFile
                = DocumentFile.fromSingleUri(context, result.getData().getData());
        if (docFile == null) {
            showFailedDialog(R.string.general_error);
            return;
        }

        // check if the selected file was previously used
        checkSelectedDownload(null, result.getData().getData(), docFile.getName(),
                docFile.getType());
    }

    private void requestDownloadPickFolderResult(final ActivityResult result,
                                                 final String key,
                                                 final String tag) {
        if (result.getResultCode() != Activity.RESULT_OK) {
            return;
        }

        if (result.getData() == null || result.getData().getData() == null) {
            showFailedDialog(R.string.general_error);
            return;
        }

        Uri uri = result.getData().getData();
        if (FilePickerActivityHelper.isOwnFileUri(context, uri)) {
            uri = Uri.fromFile(Utils.getFileForUri(uri));
        } else {
            context.grantUriPermission(context.getPackageName(), uri,
                    StoredDirectoryHelper.PERMISSION_FLAGS);
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(key, uri.toString()).apply();

        try {
            final StoredDirectoryHelper mainStorage
                    = new StoredDirectoryHelper(context, uri, tag);
            checkSelectedDownload(mainStorage, mainStorage.findFile(filenameTmp),
                    filenameTmp, mimeTmp);
        } catch (final IOException e) {
            showFailedDialog(R.string.general_error);
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Listeners
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCheckedChanged(final RadioGroup group, @IdRes final int checkedId) {
        if (DEBUG) {
            Log.d(TAG, "onCheckedChanged() called with: "
                    + "group = [" + group + "], checkedId = [" + checkedId + "]");
        }
        boolean flag = true;

        if (checkedId == R.id.audio_button) {
            setupAudioSpinner();
        } else if (checkedId == R.id.video_button) {
            setupVideoSpinner();
        } else if (checkedId == R.id.subtitle_button) {
            setupSubtitleSpinner();
            flag = false;
        }

        dialogBinding.threads.setEnabled(flag);
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view,
                               final int position, final long id) {
        if (DEBUG) {
            Log.d(TAG, "onItemSelected() called with: "
                    + "parent = [" + parent + "], view = [" + view + "], "
                    + "position = [" + position + "], id = [" + id + "]");
        }
        final int checkedId1 = dialogBinding.videoAudioGroup.getCheckedRadioButtonId();
        if (checkedId1 == R.id.audio_button) {
            selectedAudioIndex = position;
        } else if (checkedId1 == R.id.video_button) {
            selectedVideoIndex = position;
        } else if (checkedId1 == R.id.subtitle_button) {
            selectedSubtitleIndex = position;
        }
        onItemSelectedSetFileName();
    }

    private void onItemSelectedSetFileName() {
        final String fileName = FilenameUtils.createFilename(getContext(), currentInfo.getName());
        final String prevFileName = Optional.ofNullable(dialogBinding.fileName.getText())
                .map(Object::toString)
                .orElse("");

        if (prevFileName.isEmpty()
                || prevFileName.equals(fileName)
                || prevFileName.startsWith(getString(R.string.caption_file_name, fileName, ""))) {
            // only update the file name field if it was not edited by the user

            final int checkedId2 = dialogBinding.videoAudioGroup.getCheckedRadioButtonId();
            if (checkedId2 == R.id.audio_button || checkedId2 == R.id.video_button) {
                if (!prevFileName.equals(fileName)) {
                    dialogBinding.fileName.setText(fileName);
                }
            } else if (checkedId2 == R.id.subtitle_button) {
                final String setSubtitleLanguageCode = subtitleStreamsAdapter
                        .getItem(selectedSubtitleIndex).getLanguageTag();
                dialogBinding.fileName.setText(getString(
                        R.string.caption_file_name, fileName, setSubtitleLanguageCode));
            }
        }
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Download
    //////////////////////////////////////////////////////////////////////////*/

    protected void setupDownloadOptions() {
        setRadioButtonsState(false);

        final boolean isVideoStreamsAvailable = videoStreamsAdapter.getCount() > 0;
        final boolean isAudioStreamsAvailable = audioStreamsAdapter.getCount() > 0;
        final boolean isSubtitleStreamsAvailable = subtitleStreamsAdapter.getCount() > 0;

        dialogBinding.audioButton.setVisibility(isAudioStreamsAvailable ? View.VISIBLE : View.GONE);
        dialogBinding.videoButton.setVisibility(isVideoStreamsAvailable ? View.VISIBLE : View.GONE);
        dialogBinding.subtitleButton.setVisibility(isSubtitleStreamsAvailable
                ? View.VISIBLE : View.GONE);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final String defaultMedia = prefs.getString(getString(R.string.last_used_download_type),
                    getString(R.string.last_download_type_video_key));

        if (isVideoStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_video_key)))) {
            dialogBinding.videoButton.setChecked(true);
            setupVideoSpinner();
        } else if (isAudioStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_audio_key)))) {
            dialogBinding.audioButton.setChecked(true);
            setupAudioSpinner();
        } else if (isSubtitleStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_subtitle_key)))) {
            dialogBinding.subtitleButton.setChecked(true);
            setupSubtitleSpinner();
        } else if (isVideoStreamsAvailable) {
            dialogBinding.videoButton.setChecked(true);
            setupVideoSpinner();
        } else if (isAudioStreamsAvailable) {
            dialogBinding.audioButton.setChecked(true);
            setupAudioSpinner();
        } else if (isSubtitleStreamsAvailable) {
            dialogBinding.subtitleButton.setChecked(true);
            setupSubtitleSpinner();
        } else {
            Toast.makeText(getContext(), R.string.no_streams_available_download,
                    Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }

    private void setRadioButtonsState(final boolean enabled) {
        dialogBinding.audioButton.setEnabled(enabled);
        dialogBinding.videoButton.setEnabled(enabled);
        dialogBinding.subtitleButton.setEnabled(enabled);
    }

    private int getSubtitleIndexBy(final List<SubtitlesStream> streams) {
        final Localization preferredLocalization = NewPipe.getPreferredLocalization();

        int candidate = 0;
        for (int i = 0; i < streams.size(); i++) {
            final Locale streamLocale = streams.get(i).getLocale();

            final boolean languageEquals = streamLocale.getLanguage() != null
                    && preferredLocalization.getLanguageCode() != null
                    && streamLocale.getLanguage()
                    .equals(new Locale(preferredLocalization.getLanguageCode()).getLanguage());
            final boolean countryEquals = streamLocale.getCountry() != null
                    && streamLocale.getCountry().equals(preferredLocalization.getCountryCode());

            if (languageEquals) {
                if (countryEquals) {
                    return i;
                }

                candidate = i;
            }
        }

        return candidate;
    }

    private String getNameEditText() {
        final String str = dialogBinding.fileName.getText().toString();

        return FilenameUtils.createFilename(context, str.isEmpty() ? currentInfo.getName() : str);
    }

    private void showFailedDialog(@StringRes final int msg) {
        assureCorrectAppLanguage(getContext());
        new AlertDialog.Builder(context)
                .setTitle(R.string.general_error)
                .setMessage(msg)
                .setNegativeButton(getString(R.string.ok), null)
                .create()
                .show();
    }

    private void launchDirectoryPicker(final ActivityResultLauncher<Intent> launcher) {
        NoFileManagerSafeGuard.launchSafe(
                launcher,
                StoredDirectoryHelper.getPicker(context),
                TAG,
                context
        );
    }

    private void prepareSelectedDownload() {
        final StoredDirectoryHelper mainStorage;
        final MediaFormat format;
        final String selectedMediaType;

        // first, build the filename and get the output folder (if possible)
        // later, run a very very very large file checking logic

        filenameTmp = getNameEditText().concat(".");

        final int checkedId = dialogBinding.videoAudioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.audio_button) {
            selectedMediaType = getString(R.string.last_download_type_audio_key);
            mainStorage = mainStorageAudio;
            format = audioStreamsAdapter.getItem(selectedAudioIndex).getFormat();
            if (format == MediaFormat.WEBMA_OPUS) {
                mimeTmp = "audio/ogg";
                filenameTmp += "opus";
            } else {
                mimeTmp = format.mimeType;
                filenameTmp += format.suffix;
            }
        } else if (checkedId == R.id.video_button) {
            selectedMediaType = getString(R.string.last_download_type_video_key);
            mainStorage = mainStorageVideo;
            format = videoStreamsAdapter.getItem(selectedVideoIndex).getFormat();
            mimeTmp = format.mimeType;
            filenameTmp += format.suffix;
        } else if (checkedId == R.id.subtitle_button) {
            selectedMediaType = getString(R.string.last_download_type_subtitle_key);
            mainStorage = mainStorageVideo;
            format = subtitleStreamsAdapter.getItem(selectedSubtitleIndex).getFormat();
            mimeTmp = format.mimeType;
            filenameTmp += (format == MediaFormat.TTML ? MediaFormat.SRT : format).suffix;
        } else {
            throw new RuntimeException("No stream selected");
        }

        if (!askForSavePath
                && (mainStorage == null
                || mainStorage.isDirect() == NewPipeSettings.useStorageAccessFramework(context)
                || mainStorage.isInvalidSafStorage())) {
            // Pick new download folder if one of:
            // - Download folder is not set
            // - Download folder uses SAF while SAF is disabled
            // - Download folder doesn't use SAF while SAF is enabled
            // - Download folder uses SAF but the user manually revoked access to it
            Toast.makeText(context, getString(R.string.no_dir_yet),
                    Toast.LENGTH_LONG).show();

            if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                launchDirectoryPicker(requestDownloadPickAudioFolderLauncher);
            } else {
                launchDirectoryPicker(requestDownloadPickVideoFolderLauncher);
            }

            return;
        }

        if (askForSavePath) {
            final Uri initialPath;
            if (NewPipeSettings.useStorageAccessFramework(context)) {
                initialPath = null;
            } else {
                final File initialSavePath;
                if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                    initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC);
                } else {
                    initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES);
                }
                initialPath = Uri.parse(initialSavePath.getAbsolutePath());
            }

            NoFileManagerSafeGuard.launchSafe(
                    requestDownloadSaveAsLauncher,
                    StoredFileHelper.getNewPicker(context, filenameTmp, mimeTmp, initialPath),
                    TAG,
                    context
            );

            return;
        }

        // check for existing file with the same name
        checkSelectedDownload(mainStorage, mainStorage.findFile(filenameTmp), filenameTmp, mimeTmp);

        // remember the last media type downloaded by the user
        prefs.edit().putString(getString(R.string.last_used_download_type), selectedMediaType)
                .apply();
    }

    private void checkSelectedDownload(final StoredDirectoryHelper mainStorage,
                                       final Uri targetFile, final String filename,
                                       final String mime) {
        StoredFileHelper storage;

        try {
            if (mainStorage == null) {
                // using SAF on older android version
                storage = new StoredFileHelper(context, null, targetFile, "");
            } else if (targetFile == null) {
                // the file does not exist, but it is probably used in a pending download
                storage = new StoredFileHelper(mainStorage.getUri(), filename, mime,
                        mainStorage.getTag());
            } else {
                // the target filename is already use, attempt to use it
                storage = new StoredFileHelper(context, mainStorage.getUri(), targetFile,
                        mainStorage.getTag());
            }
        } catch (final Exception e) {
            ErrorUtil.createNotification(requireContext(),
                    new ErrorInfo(e, UserAction.DOWNLOAD_FAILED, "Getting storage"));
            return;
        }

        // get state of potential mission referring to the same file
        if (downloadManager == null) {
            ErrorUtil.createNotification(requireContext(),
                    new ErrorInfo(new IllegalStateException("DownloadManager not initialized"), 
                            UserAction.DOWNLOAD_FAILED, "DownloadManager is null"));
            return;
        }
        final MissionState state = downloadManager.checkForExistingMission(storage);
        @StringRes final int msgBtn;
        @StringRes final int msgBody;

        // this switch checks if there is already a mission referring to the same file
        switch (state) {
            case Finished: // there is already a finished mission
                msgBtn = R.string.overwrite;
                msgBody = R.string.overwrite_finished_warning;
                break;
            case Pending:
                msgBtn = R.string.overwrite;
                msgBody = R.string.download_already_pending;
                break;
            case PendingRunning:
                msgBtn = R.string.generate_unique_name;
                msgBody = R.string.download_already_running;
                break;
            case None: // there is no mission referring to the same file
                if (mainStorage == null) {
                    // This part is called if:
                    // * using SAF on older android version
                    // * save path not defined
                    // * if the file exists overwrite it, is not necessary ask
                    if (!storage.existsAsFile() && !storage.create()) {
                        showFailedDialog(R.string.error_file_creation);
                        return;
                    }
                    continueSelectedDownload(storage);
                    return;
                } else if (targetFile == null) {
                    // This part is called if:
                    // * the filename is not used in a pending/finished download
                    // * the file does not exists, create

                    if (!mainStorage.mkdirs()) {
                        showFailedDialog(R.string.error_path_creation);
                        return;
                    }

                    storage = mainStorage.createFile(filename, mime);
                    if (storage == null || !storage.canWrite()) {
                        showFailedDialog(R.string.error_file_creation);
                        return;
                    }

                    continueSelectedDownload(storage);
                    if(currentInfo.getService() == ServiceList.BiliBili && dialogBinding.videoAudioGroup.getCheckedRadioButtonId() == R.id.video_button){
                        mainStorage.createFile(filename.replace(".mp4", ".tmp.mp4"), "video/mp4");
                        mainStorage.createFile(filename.replace(".mp4", ".tmp"), String.valueOf(MediaFormat.M4A));
                    }
                    return;
                }
                msgBtn = R.string.overwrite;
                msgBody = R.string.overwrite_unrelated_warning;
                break;
            default:
                return; // unreachable
        }

        final AlertDialog.Builder askDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.download_dialog_title)
                .setMessage(msgBody)
                .setNegativeButton(R.string.cancel, null);
        final StoredFileHelper finalStorage = storage;


        if (mainStorage == null) {
            // This part is called if:
            // * using SAF on older android version
            // * save path not defined
            switch (state) {
                case Pending:
                case Finished:
                    askDialog.setPositiveButton(msgBtn, (dialog, which) -> {
                        dialog.dismiss();
                        downloadManager.forgetMission(finalStorage);
                        continueSelectedDownload(finalStorage);
                    });
                    break;
            }

            askDialog.create().show();
            return;
        }
        askDialog.setPositiveButton(msgBtn, (dialog, which) -> {
            dialog.dismiss();

            StoredFileHelper storageNew;
            switch (state) {
                case Finished:
                case Pending:
                    downloadManager.forgetMission(finalStorage);
                case None:
                    if (targetFile == null) {
                        storageNew = mainStorage.createFile(filename, mime);
                    } else {
                        try {
                            // try take (or steal) the file
                            storageNew = new StoredFileHelper(context, mainStorage.getUri(),
                                    targetFile, mainStorage.getTag());
                        } catch (final IOException e) {
                            Log.e(TAG, "Failed to take (or steal) the file in "
                                    + targetFile.toString());
                            storageNew = null;
                        }
                    }

                    if (storageNew != null && storageNew.canWrite()) {
//                        mainStorage.remove(filename);
                        if(currentInfo.getService() == ServiceList.BiliBili && dialogBinding.videoAudioGroup.getCheckedRadioButtonId() == R.id.video_button){
                            mainStorage.createFile(filename.replace(".mp4", ".tmp.mp4"), "video/mp4");
                            mainStorage.createFile(filename.replace(".mp4", ".tmp"), String.valueOf(MediaFormat.M4A));
                        }
                        continueSelectedDownload(storageNew);
                    } else {
                        showFailedDialog(R.string.error_file_creation);
                    }
                    break;
                case PendingRunning:
                    storageNew = mainStorage.createUniqueFile(filename, mime);
                    if (storageNew == null) {
                        showFailedDialog(R.string.error_file_creation);
                    } else {
                        continueSelectedDownload(storageNew);
                    }
                    break;
            }
        });
        askDialog.create().show();
    }

    private void continueSelectedDownload(@NonNull final StoredFileHelper storage) {
        if (!storage.canWrite()) {
            showFailedDialog(R.string.permission_denied);
            return;
        }

        // check if the selected file has to be overwritten, by simply checking its length
        try {
            if (storage.length() > 0) {
                storage.truncate();
            }
        } catch (final IOException e) {
            Log.e(TAG, "failed to truncate the file: " + storage.getUri().toString(), e);
            showFailedDialog(R.string.overwrite_failed);
            return;
        }

        final Stream selectedStream;
        Stream secondaryStream = null;
        final char kind;
        int threads = dialogBinding.threads.getProgress() + 1;
        final String[] urls;
        final MissionRecoveryInfo[] recoveryInfo;
        final String[] resourceDeliveryMethods;
        final String[] resourceManifestUrls;
        final boolean[] resourceIsUrls;
        String psName = null;
        String[] psArgs = null;
        long nearLength = 0;

        // more download logic: select muxer, subtitle converter, etc.
        final int checkedId3 = dialogBinding.videoAudioGroup.getCheckedRadioButtonId();
        if (checkedId3 == R.id.audio_button) {
            kind = 'a';
            selectedStream = audioStreamsAdapter.getItem(selectedAudioIndex);
            if (currentInfo.getService() == ServiceList.NicoNico) {
                psName = Postprocessing.NICONICO_MUXER;
            } else if (selectedStream.getFormat() == MediaFormat.M4A && currentInfo.getService() != ServiceList.BiliBili) {
                psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
            } else if (selectedStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
            }
        } else if (checkedId3 == R.id.video_button) {
            kind = 'v';
            selectedStream = videoStreamsAdapter.getItem(selectedVideoIndex);

            final SecondaryStreamHelper<AudioStream> secondary = videoStreamsAdapter
                    .getAllSecondary()
                    .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream));

            if (secondary != null) {
                secondaryStream = secondary.getStream();

                if(currentInfo.getService() == ServiceList.BiliBili) {
                    psName = Postprocessing.BILIBILI_MUXER;
                } else if (currentInfo.getService() == ServiceList.NicoNico) {
                    psName = Postprocessing.NICONICO_MUXER;
                } else {
                    if (selectedStream.getFormat() == MediaFormat.MPEG_4) {
                        psName = Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER;
                    } else {
                        psName = Postprocessing.ALGORITHM_WEBM_MUXER;
                    }
                }

                psArgs = null;
                final long videoSize = wrappedVideoStreams
                        .getSizeInBytes((VideoStream) selectedStream);

                // set nearLength, only, if both sizes are fetched or known. This probably
                // does not work on slow networks but is later updated in the downloader
                if (secondary.getSizeInBytes() > 0 && videoSize > 0) {
                    nearLength = secondary.getSizeInBytes() + videoSize;
                }
            }
        } else if (checkedId3 == R.id.subtitle_button) {
            threads = 1;
            kind = 's';
            selectedStream = subtitleStreamsAdapter.getItem(selectedSubtitleIndex);
            if(!selectedStream.isUrl()){
                try {
                    String content = selectedStream.getContent();
                    if (selectedStream.getFormat() == MediaFormat.TTML) {
                        content = SrtFromTtmlWriter.convertTtmlToSrt(content);
                    }
                    OutputStream outputStream = storage.context.getContentResolver().openOutputStream(storage.getUri());
                    outputStream.write(content.getBytes());
                    outputStream.close();
                    Toast.makeText(context, getString(R.string.recaptcha_done_button),
                            Toast.LENGTH_SHORT).show();

                    dismiss();
                    return;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (selectedStream.getFormat() == MediaFormat.TTML) {
                psName = Postprocessing.ALGORITHM_TTML_CONVERTER;
                psArgs = new String[]{
                        selectedStream.getFormat().getSuffix(),
                        "false"
                };
            }
        } else {
            return;
        }

        if (secondaryStream == null) {
            urls = new String[]{
                    selectedStream.getContent()
            };
            recoveryInfo = new MissionRecoveryInfo[]{
                    new MissionRecoveryInfo(selectedStream)
            };
        } else {
            urls = new String[]{
                    selectedStream.getContent(),
                    secondaryStream.getContent()
            };
            recoveryInfo = new MissionRecoveryInfo[]{new MissionRecoveryInfo(selectedStream),
                    new MissionRecoveryInfo(secondaryStream)};
        }

        resourceDeliveryMethods = HlsDownloadStreamHelper
                .buildResourceDeliveryMethods(selectedStream, secondaryStream);
        resourceManifestUrls = HlsDownloadStreamHelper
                .buildResourceManifestUrls(selectedStream, secondaryStream);
        resourceIsUrls = HlsDownloadStreamHelper
                .buildResourceIsUrls(selectedStream, secondaryStream);
        if (HlsDownloadStreamHelper.containsHlsResource(resourceDeliveryMethods,
                resourceManifestUrls, urls)
                || SabrDownloadStreamHelper.containsSabrStream(selectedStream, secondaryStream)) {
            psName = null;
            psArgs = null;
        }

        DownloadManagerService.startMission(context, urls, storage, kind, threads,
                currentInfo.getUrl(), psName, psArgs, nearLength, recoveryInfo,
                resourceDeliveryMethods, resourceManifestUrls, resourceIsUrls);

        Toast.makeText(context, getString(R.string.download_has_started),
                Toast.LENGTH_SHORT).show();

        dismiss();
    }
}
