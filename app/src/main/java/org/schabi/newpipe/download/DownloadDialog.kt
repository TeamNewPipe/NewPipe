package org.schabi.newpipe.download

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.appcompat.widget.Toolbar
import androidx.collection.SparseArrayCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.nononsenseapps.filepicker.Utils
import icepick.Icepick
import icepick.State
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DownloadDialogBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.createNotification
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.AudioTrackAdapter
import org.schabi.newpipe.util.AudioTrackAdapter.AudioTracksWrapper
import org.schabi.newpipe.util.FilePickerActivityHelper
import org.schabi.newpipe.util.FilenameUtils
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.SecondaryStreamHelper
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener
import org.schabi.newpipe.util.StreamItemAdapter
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper
import org.schabi.newpipe.util.ThemeHelper
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder
import us.shandian.giga.service.MissionState
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.Objects
import java.util.Optional
import java.util.function.Function

class DownloadDialog : DialogFragment, RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    @State
    var currentInfo: StreamInfo? = null

    @State
    var wrappedVideoStreams: StreamInfoWrapper<VideoStream?>? = null

    @State
    var wrappedSubtitleStreams: StreamInfoWrapper<SubtitlesStream?>? = null

    @State
    var wrappedAudioTracks: AudioTracksWrapper? = null

    @State
    var selectedAudioTrackIndex: Int = 0

    @State
    var selectedVideoIndex: Int = 0 // set in the constructor

    @State
    var selectedAudioIndex: Int = 0 // default to the first item

    @State
    var selectedSubtitleIndex: Int = 0 // default to the first item
    private var mainStorageAudio: StoredDirectoryHelper? = null
    private var mainStorageVideo: StoredDirectoryHelper? = null
    private var downloadManager: DownloadManager? = null
    private var okButton: ActionMenuItemView? = null
    private var context: Context? = null
    private var askForSavePath: Boolean = false
    private var audioTrackAdapter: AudioTrackAdapter? = null
    private var audioStreamsAdapter: StreamItemAdapter<AudioStream?, Stream>? = null
    private var videoStreamsAdapter: StreamItemAdapter<VideoStream?, AudioStream?>? = null
    private var subtitleStreamsAdapter: StreamItemAdapter<SubtitlesStream?, Stream>? = null
    private val disposables: CompositeDisposable = CompositeDisposable()
    private var dialogBinding: DownloadDialogBinding? = null
    private var prefs: SharedPreferences? = null

    // Variables for file name and MIME type when picking new folder because it's not set yet
    private var filenameTmp: String? = null
    private var mimeTmp: String? = null
    private val requestDownloadSaveAsLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(
            StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestDownloadSaveAsResult(result) }))
    private val requestDownloadPickAudioFolderLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(
            StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestDownloadPickAudioFolderResult(result) }))
    private val requestDownloadPickVideoFolderLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(
            StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestDownloadPickVideoFolderResult(result) }))

    /*//////////////////////////////////////////////////////////////////////////
    // Instance creation
    ////////////////////////////////////////////////////////////////////////// */
    constructor()

    /**
     * Create a new download dialog with the video, audio and subtitle streams from the provided
     * stream info. Video streams and video-only streams will be put into a single list menu,
     * sorted according to their resolution and the default video resolution will be selected.
     *
     * @param context the context to use just to obtain preferences and strings (will not be stored)
     * @param info    the info from which to obtain downloadable streams and other info (e.g. title)
     */
    constructor(context: Context, info: StreamInfo) {
        currentInfo = info
        val audioStreams: List<AudioStream?> = ListHelper.getStreamsOfSpecifiedDelivery(info.getAudioStreams(), DeliveryMethod.PROGRESSIVE_HTTP)
        val groupedAudioStreams: List<List<AudioStream?>?>? = ListHelper.getGroupedAudioStreams(context, audioStreams)
        wrappedAudioTracks = AudioTracksWrapper((groupedAudioStreams)!!, context)
        selectedAudioTrackIndex = ListHelper.getDefaultAudioTrackGroup(context, groupedAudioStreams)

        // TODO: Adapt this code when the downloader support other types of stream deliveries
        val videoStreams: List<VideoStream?> = ListHelper.getSortedStreamVideosList(
                context,
                ListHelper.getStreamsOfSpecifiedDelivery(info.getVideoStreams(), DeliveryMethod.PROGRESSIVE_HTTP),
                ListHelper.getStreamsOfSpecifiedDelivery(info.getVideoOnlyStreams(), DeliveryMethod.PROGRESSIVE_HTTP),
                false,  // If there are multiple languages available, prefer streams without audio
                // to allow language selection
                wrappedAudioTracks!!.size() > 1
        )
        wrappedVideoStreams = StreamInfoWrapper(videoStreams, context)
        wrappedSubtitleStreams = StreamInfoWrapper(
                ListHelper.getStreamsOfSpecifiedDelivery(info.getSubtitles(), DeliveryMethod.PROGRESSIVE_HTTP), context)
        selectedVideoIndex = ListHelper.getDefaultResolutionIndex(context, videoStreams)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Android lifecycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) {
            Log.d(TAG, ("onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]"))
        }
        if (!PermissionHelper.checkStoragePermissions(getActivity(),
                        PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            dismiss()
            return
        }

        // context will remain null if dismiss() was called above, allowing to check whether the
        // dialog is being dismissed in onViewCreated()
        context = getContext()
        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(context))
        Icepick.restoreInstanceState(this, savedInstanceState)
        audioTrackAdapter = AudioTrackAdapter(wrappedAudioTracks)
        subtitleStreamsAdapter = StreamItemAdapter(wrappedSubtitleStreams)
        updateSecondaryStreams()
        val intent: Intent = Intent(context, DownloadManagerService::class.java)
        context!!.startService(intent)
        context!!.bindService(intent, object : ServiceConnection {
            public override fun onServiceConnected(cname: ComponentName, service: IBinder) {
                val mgr: DownloadManagerBinder = service as DownloadManagerBinder
                mainStorageAudio = mgr.getMainStorageAudio()
                mainStorageVideo = mgr.getMainStorageVideo()
                downloadManager = mgr.getDownloadManager()
                askForSavePath = mgr.askForSavePath()
                okButton!!.setEnabled(true)
                context!!.unbindService(this)
            }

            public override fun onServiceDisconnected(name: ComponentName) {
                // nothing to do
            }
        }, Context.BIND_AUTO_CREATE)
    }

    /**
     * Update the displayed video streams based on the selected audio track.
     */
    private fun updateSecondaryStreams() {
        val audioStreams: StreamInfoWrapper<AudioStream?>? = getWrappedAudioStreams()
        val secondaryStreams: SparseArrayCompat<SecondaryStreamHelper<AudioStream?>?> = SparseArrayCompat(4)
        val videoStreams: List<VideoStream?>? = wrappedVideoStreams.getStreamsList()
        wrappedVideoStreams!!.resetInfo()
        for (i in videoStreams!!.indices) {
            if (!videoStreams.get(i)!!.isVideoOnly()) {
                continue
            }
            val audioStream: AudioStream? = SecondaryStreamHelper.Companion.getAudioStreamFor(
                    (context)!!, audioStreams.getStreamsList(), (videoStreams.get(i))!!)
            if (audioStream != null) {
                secondaryStreams.append(i, SecondaryStreamHelper(audioStreams, audioStream))
            } else if (DEBUG) {
                val mediaFormat: MediaFormat? = videoStreams.get(i)!!.getFormat()
                if (mediaFormat != null) {
                    Log.w(TAG, ("No audio stream candidates for video format "
                            + mediaFormat.name))
                } else {
                    Log.w(TAG, "No audio stream candidates for unknown video format")
                }
            }
        }
        videoStreamsAdapter = StreamItemAdapter(wrappedVideoStreams, secondaryStreams)
        audioStreamsAdapter = StreamItemAdapter(audioStreams)
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        if (DEBUG) {
            Log.d(TAG, ("onCreateView() called with: "
                    + "inflater = [" + inflater + "], container = [" + container + "], "
                    + "savedInstanceState = [" + savedInstanceState + "]"))
        }
        return inflater.inflate(R.layout.download_dialog, container)
    }

    public override fun onViewCreated(view: View,
                                      savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialogBinding = DownloadDialogBinding.bind(view)
        if (context == null) {
            return  // the dialog is being dismissed, see the call to dismiss() in onCreate()
        }
        dialogBinding!!.fileName.setText(FilenameUtils.createFilename(getContext(),
                currentInfo!!.getName()))
        selectedAudioIndex = ListHelper.getDefaultAudioFormat(getContext(),
                getWrappedAudioStreams().getStreamsList())
        selectedSubtitleIndex = getSubtitleIndexBy(subtitleStreamsAdapter.getAll())
        dialogBinding!!.qualitySpinner.setOnItemSelectedListener(this)
        dialogBinding!!.audioStreamSpinner.setOnItemSelectedListener(this)
        dialogBinding!!.audioTrackSpinner.setOnItemSelectedListener(this)
        dialogBinding!!.videoAudioGroup.setOnCheckedChangeListener(this)
        initToolbar(dialogBinding!!.toolbarLayout.toolbar)
        setupDownloadOptions()
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val threads: Int = prefs.getInt(getString(R.string.default_download_threads), 3)
        dialogBinding!!.threadsCount.setText(threads.toString())
        dialogBinding!!.threads.setProgress(threads - 1)
        dialogBinding!!.threads.setOnSeekBarChangeListener(object : SimpleOnSeekBarChangeListener() {
            public override fun onProgressChanged(seekbar: SeekBar,
                                                  progress: Int,
                                                  fromUser: Boolean) {
                val newProgress: Int = progress + 1
                prefs.edit().putInt(getString(R.string.default_download_threads), newProgress)
                        .apply()
                dialogBinding!!.threadsCount.setText(newProgress.toString())
            }
        })
        fetchStreamsSize()
    }

    private fun initToolbar(toolbar: Toolbar) {
        if (DEBUG) {
            Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]")
        }
        toolbar.setTitle(R.string.download_dialog_title)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.inflateMenu(R.menu.dialog_url)
        toolbar.setNavigationOnClickListener(View.OnClickListener({ v: View? -> dismiss() }))
        toolbar.setNavigationContentDescription(R.string.cancel)
        okButton = toolbar.findViewById(R.id.okay)
        okButton.setEnabled(false) // disable until the download service connection is done
        toolbar.setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener({ item: MenuItem ->
            if (item.getItemId() == R.id.okay) {
                prepareSelectedDownload()
                return@setOnMenuItemClickListener true
            }
            false
        }))
    }

    public override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    public override fun onDestroyView() {
        dialogBinding = null
        super.onDestroyView()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Video, audio and subtitle spinners
    ////////////////////////////////////////////////////////////////////////// */
    private fun fetchStreamsSize() {
        disposables.clear()
        disposables.add(StreamInfoWrapper.Companion.fetchMoreInfoForWrapper<VideoStream?>(wrappedVideoStreams)
                .subscribe(Consumer<Boolean>({ result: Boolean? ->
                    if ((dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId()
                                    == R.id.video_button)) {
                        setupVideoSpinner()
                    }
                }), Consumer<Throwable>({ throwable: Throwable? ->
                    showSnackbar((context)!!,
                            ErrorInfo((throwable)!!, UserAction.DOWNLOAD_OPEN_DIALOG,
                                    "Downloading video stream size",
                                    currentInfo!!.getServiceId()))
                })))
        disposables.add(StreamInfoWrapper.Companion.fetchMoreInfoForWrapper<AudioStream?>(getWrappedAudioStreams())
                .subscribe(Consumer<Boolean>({ result: Boolean? ->
                    if ((dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId()
                                    == R.id.audio_button)) {
                        setupAudioSpinner()
                    }
                }), Consumer<Throwable>({ throwable: Throwable? ->
                    showSnackbar((context)!!,
                            ErrorInfo((throwable)!!, UserAction.DOWNLOAD_OPEN_DIALOG,
                                    "Downloading audio stream size",
                                    currentInfo!!.getServiceId()))
                })))
        disposables.add(StreamInfoWrapper.Companion.fetchMoreInfoForWrapper<SubtitlesStream?>(wrappedSubtitleStreams)
                .subscribe(Consumer<Boolean>({ result: Boolean? ->
                    if ((dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId()
                                    == R.id.subtitle_button)) {
                        setupSubtitleSpinner()
                    }
                }), Consumer<Throwable>({ throwable: Throwable? ->
                    showSnackbar((context)!!,
                            ErrorInfo((throwable)!!, UserAction.DOWNLOAD_OPEN_DIALOG,
                                    "Downloading subtitle stream size",
                                    currentInfo!!.getServiceId()))
                })))
    }

    private fun setupAudioTrackSpinner() {
        if (getContext() == null) {
            return
        }
        dialogBinding!!.audioTrackSpinner.setAdapter(audioTrackAdapter)
        dialogBinding!!.audioTrackSpinner.setSelection(selectedAudioTrackIndex)
    }

    private fun setupAudioSpinner() {
        if (getContext() == null) {
            return
        }
        dialogBinding!!.qualitySpinner.setVisibility(View.GONE)
        setRadioButtonsState(true)
        dialogBinding!!.audioStreamSpinner.setAdapter(audioStreamsAdapter)
        dialogBinding!!.audioStreamSpinner.setSelection(selectedAudioIndex)
        dialogBinding!!.audioStreamSpinner.setVisibility(View.VISIBLE)
        dialogBinding!!.audioTrackSpinner.setVisibility(
                if (wrappedAudioTracks!!.size() > 1) View.VISIBLE else View.GONE)
        dialogBinding!!.audioTrackPresentInVideoText.setVisibility(View.GONE)
    }

    private fun setupVideoSpinner() {
        if (getContext() == null) {
            return
        }
        dialogBinding!!.qualitySpinner.setAdapter(videoStreamsAdapter)
        dialogBinding!!.qualitySpinner.setSelection(selectedVideoIndex)
        dialogBinding!!.qualitySpinner.setVisibility(View.VISIBLE)
        setRadioButtonsState(true)
        dialogBinding!!.audioStreamSpinner.setVisibility(View.GONE)
        onVideoStreamSelected()
    }

    private fun onVideoStreamSelected() {
        val isVideoOnly: Boolean = videoStreamsAdapter!!.getItem(selectedVideoIndex)!!.isVideoOnly()
        dialogBinding!!.audioTrackSpinner.setVisibility(
                if (isVideoOnly && wrappedAudioTracks!!.size() > 1) View.VISIBLE else View.GONE)
        dialogBinding!!.audioTrackPresentInVideoText.setVisibility(
                if (!isVideoOnly && wrappedAudioTracks!!.size() > 1) View.VISIBLE else View.GONE)
    }

    private fun setupSubtitleSpinner() {
        if (getContext() == null) {
            return
        }
        dialogBinding!!.qualitySpinner.setAdapter(subtitleStreamsAdapter)
        dialogBinding!!.qualitySpinner.setSelection(selectedSubtitleIndex)
        dialogBinding!!.qualitySpinner.setVisibility(View.VISIBLE)
        setRadioButtonsState(true)
        dialogBinding!!.audioStreamSpinner.setVisibility(View.GONE)
        dialogBinding!!.audioTrackSpinner.setVisibility(View.GONE)
        dialogBinding!!.audioTrackPresentInVideoText.setVisibility(View.GONE)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Activity results
    ////////////////////////////////////////////////////////////////////////// */
    private fun requestDownloadPickAudioFolderResult(result: ActivityResult) {
        requestDownloadPickFolderResult(
                result, getString(R.string.download_path_audio_key), DownloadManager.Companion.TAG_AUDIO)
    }

    private fun requestDownloadPickVideoFolderResult(result: ActivityResult) {
        requestDownloadPickFolderResult(
                result, getString(R.string.download_path_video_key), DownloadManager.Companion.TAG_VIDEO)
    }

    private fun requestDownloadSaveAsResult(result: ActivityResult) {
        if (result.getResultCode() != Activity.RESULT_OK) {
            return
        }
        if (result.getData() == null || result.getData()!!.getData() == null) {
            showFailedDialog(R.string.general_error)
            return
        }
        if (FilePickerActivityHelper.Companion.isOwnFileUri((context)!!, result.getData()!!.getData()!!)) {
            val file: File = Utils.getFileForUri(result.getData()!!.getData()!!)
            checkSelectedDownload(null, Uri.fromFile(file), file.getName(),
                    StoredFileHelper.Companion.DEFAULT_MIME)
            return
        }
        val docFile: DocumentFile? = DocumentFile.fromSingleUri((context)!!,
                result.getData()!!.getData()!!)
        if (docFile == null) {
            showFailedDialog(R.string.general_error)
            return
        }

        // check if the selected file was previously used
        checkSelectedDownload(null, result.getData()!!.getData(), docFile.getName(),
                docFile.getType())
    }

    private fun requestDownloadPickFolderResult(result: ActivityResult,
                                                key: String,
                                                tag: String) {
        if (result.getResultCode() != Activity.RESULT_OK) {
            return
        }
        if (result.getData() == null || result.getData()!!.getData() == null) {
            showFailedDialog(R.string.general_error)
            return
        }
        var uri: Uri? = result.getData()!!.getData()
        if (FilePickerActivityHelper.Companion.isOwnFileUri((context)!!, (uri)!!)) {
            uri = Uri.fromFile(Utils.getFileForUri((uri)))
        } else {
            context!!.grantUriPermission(context!!.getPackageName(), uri,
                    StoredDirectoryHelper.Companion.PERMISSION_FLAGS)
        }
        PreferenceManager.getDefaultSharedPreferences((context)!!).edit().putString(key,
                uri.toString()).apply()
        try {
            val mainStorage: StoredDirectoryHelper = StoredDirectoryHelper((context)!!, (uri)!!, tag)
            checkSelectedDownload(mainStorage, mainStorage.findFile(filenameTmp),
                    filenameTmp, mimeTmp)
        } catch (e: IOException) {
            showFailedDialog(R.string.general_error)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Listeners
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCheckedChanged(group: RadioGroup, @IdRes checkedId: Int) {
        if (DEBUG) {
            Log.d(TAG, ("onCheckedChanged() called with: "
                    + "group = [" + group + "], checkedId = [" + checkedId + "]"))
        }
        var flag: Boolean = true
        when (checkedId) {
            R.id.audio_button -> setupAudioSpinner()
            R.id.video_button -> setupVideoSpinner()
            R.id.subtitle_button -> {
                setupSubtitleSpinner()
                flag = false
            }
        }
        dialogBinding!!.threads.setEnabled(flag)
    }

    public override fun onItemSelected(parent: AdapterView<*>,
                                       view: View,
                                       position: Int,
                                       id: Long) {
        if (DEBUG) {
            Log.d(TAG, ("onItemSelected() called with: "
                    + "parent = [" + parent + "], view = [" + view + "], "
                    + "position = [" + position + "], id = [" + id + "]"))
        }
        when (parent.getId()) {
            R.id.quality_spinner -> {
                when (dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId()) {
                    R.id.video_button -> {
                        selectedVideoIndex = position
                        onVideoStreamSelected()
                    }

                    R.id.subtitle_button -> selectedSubtitleIndex = position
                }
                onItemSelectedSetFileName()
            }

            R.id.audio_track_spinner -> {
                val trackChanged: Boolean = selectedAudioTrackIndex != position
                selectedAudioTrackIndex = position
                if (trackChanged) {
                    updateSecondaryStreams()
                    fetchStreamsSize()
                }
            }

            R.id.audio_stream_spinner -> selectedAudioIndex = position
        }
    }

    private fun onItemSelectedSetFileName() {
        val fileName: String? = FilenameUtils.createFilename(getContext(), currentInfo!!.getName())
        val prevFileName: String = Optional.ofNullable(dialogBinding!!.fileName.getText())
                .map(Function({ obj: Editable -> obj.toString() }))
                .orElse("")
        if ((prevFileName.isEmpty()
                        || (prevFileName == fileName) || prevFileName.startsWith(getString(R.string.caption_file_name, fileName, "")))) {
            // only update the file name field if it was not edited by the user
            when (dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId()) {
                R.id.audio_button, R.id.video_button -> if (!(prevFileName == fileName)) {
                    // since the user might have switched between audio and video, the correct
                    // text might already be in place, so avoid resetting the cursor position
                    dialogBinding!!.fileName.setText(fileName)
                }

                R.id.subtitle_button -> {
                    val setSubtitleLanguageCode: String = subtitleStreamsAdapter
                            .getItem(selectedSubtitleIndex)!!.getLanguageTag()
                    // this will reset the cursor position, which is bad UX, but it can't be avoided
                    dialogBinding!!.fileName.setText(getString(
                            R.string.caption_file_name, fileName, setSubtitleLanguageCode))
                }
            }
        }
    }

    public override fun onNothingSelected(parent: AdapterView<*>?) {}

    /*//////////////////////////////////////////////////////////////////////////
    // Download
    ////////////////////////////////////////////////////////////////////////// */
    protected fun setupDownloadOptions() {
        setRadioButtonsState(false)
        setupAudioTrackSpinner()
        val isVideoStreamsAvailable: Boolean = videoStreamsAdapter!!.getCount() > 0
        val isAudioStreamsAvailable: Boolean = audioStreamsAdapter!!.getCount() > 0
        val isSubtitleStreamsAvailable: Boolean = subtitleStreamsAdapter!!.getCount() > 0
        dialogBinding!!.audioButton.setVisibility(if (isAudioStreamsAvailable) View.VISIBLE else View.GONE)
        dialogBinding!!.videoButton.setVisibility(if (isVideoStreamsAvailable) View.VISIBLE else View.GONE)
        dialogBinding!!.subtitleButton.setVisibility(if (isSubtitleStreamsAvailable) View.VISIBLE else View.GONE)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val defaultMedia: String? = prefs.getString(getString(R.string.last_used_download_type),
                getString(R.string.last_download_type_video_key))
        if ((isVideoStreamsAvailable
                        && ((defaultMedia == getString(R.string.last_download_type_video_key))))) {
            dialogBinding!!.videoButton.setChecked(true)
            setupVideoSpinner()
        } else if ((isAudioStreamsAvailable
                        && ((defaultMedia == getString(R.string.last_download_type_audio_key))))) {
            dialogBinding!!.audioButton.setChecked(true)
            setupAudioSpinner()
        } else if ((isSubtitleStreamsAvailable
                        && ((defaultMedia == getString(R.string.last_download_type_subtitle_key))))) {
            dialogBinding!!.subtitleButton.setChecked(true)
            setupSubtitleSpinner()
        } else if (isVideoStreamsAvailable) {
            dialogBinding!!.videoButton.setChecked(true)
            setupVideoSpinner()
        } else if (isAudioStreamsAvailable) {
            dialogBinding!!.audioButton.setChecked(true)
            setupAudioSpinner()
        } else if (isSubtitleStreamsAvailable) {
            dialogBinding!!.subtitleButton.setChecked(true)
            setupSubtitleSpinner()
        } else {
            Toast.makeText(getContext(), R.string.no_streams_available_download,
                    Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun setRadioButtonsState(enabled: Boolean) {
        dialogBinding!!.audioButton.setEnabled(enabled)
        dialogBinding!!.videoButton.setEnabled(enabled)
        dialogBinding!!.subtitleButton.setEnabled(enabled)
    }

    private fun getWrappedAudioStreams(): StreamInfoWrapper<AudioStream?>? {
        if (selectedAudioTrackIndex < 0 || selectedAudioTrackIndex > wrappedAudioTracks!!.size()) {
            return StreamInfoWrapper.Companion.empty<AudioStream?>()
        }
        return wrappedAudioTracks.getTracksList().get(selectedAudioTrackIndex)
    }

    private fun getSubtitleIndexBy(streams: List<SubtitlesStream?>): Int {
        val preferredLocalization: Localization = NewPipe.getPreferredLocalization()
        var candidate: Int = 0
        for (i in streams.indices) {
            val streamLocale: Locale = streams.get(i)!!.getLocale()
            val languageEquals: Boolean = (streamLocale.getLanguage() != null
                    ) && (preferredLocalization.getLanguageCode() != null
                    ) && (streamLocale.getLanguage()
                    == Locale(preferredLocalization.getLanguageCode()).getLanguage())
            val countryEquals: Boolean = (streamLocale.getCountry() != null
                    && (streamLocale.getCountry() == preferredLocalization.getCountryCode()))
            if (languageEquals) {
                if (countryEquals) {
                    return i
                }
                candidate = i
            }
        }
        return candidate
    }

    private fun getNameEditText(): String {
        val str: String = Objects.requireNonNull(dialogBinding!!.fileName.getText()).toString()
                .trim({ it <= ' ' })
        return FilenameUtils.createFilename(context, if (str.isEmpty()) currentInfo!!.getName() else str)
    }

    private fun showFailedDialog(@StringRes msg: Int) {
        org.schabi.newpipe.util.Localization.assureCorrectAppLanguage(requireContext())
        AlertDialog.Builder((context)!!)
                .setTitle(R.string.general_error)
                .setMessage(msg)
                .setNegativeButton(getString(R.string.ok), null)
                .show()
    }

    private fun launchDirectoryPicker(launcher: ActivityResultLauncher<Intent>) {
        NoFileManagerSafeGuard.launchSafe<Intent>(launcher, StoredDirectoryHelper.Companion.getPicker(context), TAG,
                context)
    }

    private fun prepareSelectedDownload() {
        val mainStorage: StoredDirectoryHelper?
        val format: MediaFormat?
        val selectedMediaType: String
        val size: Long

        // first, build the filename and get the output folder (if possible)
        // later, run a very very very large file checking logic
        filenameTmp = getNameEditText() + "."
        when (dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId()) {
            R.id.audio_button -> {
                selectedMediaType = getString(R.string.last_download_type_audio_key)
                mainStorage = mainStorageAudio
                format = audioStreamsAdapter!!.getItem(selectedAudioIndex)!!.getFormat()
                size = getWrappedAudioStreams()!!.getSizeInBytes(selectedAudioIndex)
                if (format == MediaFormat.WEBMA_OPUS) {
                    mimeTmp = "audio/ogg"
                    filenameTmp += "opus"
                } else if (format != null) {
                    mimeTmp = format.mimeType
                    filenameTmp += format.getSuffix()
                }
            }

            R.id.video_button -> {
                selectedMediaType = getString(R.string.last_download_type_video_key)
                mainStorage = mainStorageVideo
                format = videoStreamsAdapter!!.getItem(selectedVideoIndex)!!.getFormat()
                size = wrappedVideoStreams!!.getSizeInBytes(selectedVideoIndex)
                if (format != null) {
                    mimeTmp = format.mimeType
                    filenameTmp += format.getSuffix()
                }
            }

            R.id.subtitle_button -> {
                selectedMediaType = getString(R.string.last_download_type_subtitle_key)
                mainStorage = mainStorageVideo // subtitle & video files go together
                format = subtitleStreamsAdapter!!.getItem(selectedSubtitleIndex)!!.getFormat()
                size = wrappedSubtitleStreams!!.getSizeInBytes(selectedSubtitleIndex)
                if (format != null) {
                    mimeTmp = format.mimeType
                }
                if (format == MediaFormat.TTML) {
                    filenameTmp += MediaFormat.SRT.getSuffix()
                } else if (format != null) {
                    filenameTmp += format.getSuffix()
                }
            }

            else -> throw RuntimeException("No stream selected")
        }
        if (!askForSavePath && ((mainStorage == null
                        ) || (mainStorage.isDirect() == NewPipeSettings.useStorageAccessFramework(context)
                        ) || mainStorage.isInvalidSafStorage())) {
            // Pick new download folder if one of:
            // - Download folder is not set
            // - Download folder uses SAF while SAF is disabled
            // - Download folder doesn't use SAF while SAF is enabled
            // - Download folder uses SAF but the user manually revoked access to it
            Toast.makeText(context, getString(R.string.no_dir_yet),
                    Toast.LENGTH_LONG).show()
            if (dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                launchDirectoryPicker(requestDownloadPickAudioFolderLauncher)
            } else {
                launchDirectoryPicker(requestDownloadPickVideoFolderLauncher)
            }
            return
        }
        if (askForSavePath) {
            val initialPath: Uri?
            if (NewPipeSettings.useStorageAccessFramework(context)) {
                initialPath = null
            } else {
                val initialSavePath: File
                if (dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                    initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC)
                } else {
                    initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES)
                }
                initialPath = Uri.parse(initialSavePath.getAbsolutePath())
            }
            NoFileManagerSafeGuard.launchSafe<Intent>(requestDownloadSaveAsLauncher,
                    StoredFileHelper.Companion.getNewPicker((context)!!, filenameTmp, (mimeTmp)!!, initialPath), TAG,
                    context)
            return
        }

        // Check for free memory space (for api 24 and up)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val freeSpace: Long = mainStorage!!.getFreeMemory()
            if (freeSpace <= size) {
                Toast.makeText(context, getString(R.string.error_insufficient_storage), Toast.LENGTH_LONG).show()
                // move the user to storage setting tab
                val storageSettingsIntent: Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                if (storageSettingsIntent.resolveActivity(context!!.getPackageManager()) != null) {
                    startActivity(storageSettingsIntent)
                }
                return
            }
        }

        // check for existing file with the same name
        checkSelectedDownload(mainStorage, mainStorage!!.findFile(filenameTmp), filenameTmp,
                mimeTmp)

        // remember the last media type downloaded by the user
        prefs!!.edit().putString(getString(R.string.last_used_download_type), selectedMediaType)
                .apply()
    }

    private fun checkSelectedDownload(mainStorage: StoredDirectoryHelper?,
                                      targetFile: Uri?,
                                      filename: String?,
                                      mime: String?) {
        var storage: StoredFileHelper?
        try {
            if (mainStorage == null) {
                // using SAF on older android version
                storage = StoredFileHelper(context, null, (targetFile)!!, "")
            } else if (targetFile == null) {
                // the file does not exist, but it is probably used in a pending download
                storage = StoredFileHelper(mainStorage.getUri(), filename, mime,
                        mainStorage.getTag())
            } else {
                // the target filename is already use, attempt to use it
                storage = StoredFileHelper(context, mainStorage.getUri(), targetFile,
                        mainStorage.getTag())
            }
        } catch (e: Exception) {
            createNotification(requireContext(),
                    ErrorInfo(e, UserAction.DOWNLOAD_FAILED, "Getting storage"))
            return
        }

        // get state of potential mission referring to the same file
        val state: MissionState? = downloadManager!!.checkForExistingMission(storage)
        @StringRes val msgBtn: Int
        @StringRes val msgBody: Int
        when (state) {
            MissionState.Finished -> {
                msgBtn = R.string.overwrite
                msgBody = R.string.overwrite_finished_warning
            }

            MissionState.Pending -> {
                msgBtn = R.string.overwrite
                msgBody = R.string.download_already_pending
            }

            MissionState.PendingRunning -> {
                msgBtn = R.string.generate_unique_name
                msgBody = R.string.download_already_running
            }

            MissionState.None -> {
                if (mainStorage == null) {
                    // This part is called if:
                    // * using SAF on older android version
                    // * save path not defined
                    // * if the file exists overwrite it, is not necessary ask
                    if (!storage.existsAsFile() && !storage.create()) {
                        showFailedDialog(R.string.error_file_creation)
                        return
                    }
                    continueSelectedDownload(storage)
                    return
                } else if (targetFile == null) {
                    // This part is called if:
                    // * the filename is not used in a pending/finished download
                    // * the file does not exists, create
                    if (!mainStorage.mkdirs()) {
                        showFailedDialog(R.string.error_path_creation)
                        return
                    }
                    storage = mainStorage.createFile(filename, mime)
                    if (storage == null || !storage.canWrite()) {
                        showFailedDialog(R.string.error_file_creation)
                        return
                    }
                    continueSelectedDownload(storage)
                    return
                }
                msgBtn = R.string.overwrite
                msgBody = R.string.overwrite_unrelated_warning
            }

            else -> return  // unreachable
        }
        val askDialog: AlertDialog.Builder = AlertDialog.Builder((context)!!)
                .setTitle(R.string.download_dialog_title)
                .setMessage(msgBody)
                .setNegativeButton(R.string.cancel, null)
        val finalStorage: StoredFileHelper = storage
        if (mainStorage == null) {
            // This part is called if:
            // * using SAF on older android version
            // * save path not defined
            when (state) {
                MissionState.Pending, MissionState.Finished -> askDialog.setPositiveButton(msgBtn, DialogInterface.OnClickListener({ dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    downloadManager!!.forgetMission(finalStorage)
                    continueSelectedDownload(finalStorage)
                }))
            }
            askDialog.show()
            return
        }
        askDialog.setPositiveButton(msgBtn, DialogInterface.OnClickListener({ dialog: DialogInterface, which: Int ->
            dialog.dismiss()
            var storageNew: StoredFileHelper?
            when (state) {
                MissionState.Finished, MissionState.Pending -> {
                    downloadManager!!.forgetMission(finalStorage)
                    if (targetFile == null) {
                        storageNew = mainStorage.createFile(filename, mime)
                    } else {
                        try {
                            // try take (or steal) the file
                            storageNew = StoredFileHelper(context, mainStorage.getUri(),
                                    targetFile, mainStorage.getTag())
                        } catch (e: IOException) {
                            Log.e(TAG, ("Failed to take (or steal) the file in "
                                    + targetFile.toString()))
                            storageNew = null
                        }
                    }
                    if (storageNew != null && storageNew.canWrite()) {
                        continueSelectedDownload(storageNew)
                    } else {
                        showFailedDialog(R.string.error_file_creation)
                    }
                }

                MissionState.None -> {
                    if (targetFile == null) {
                        storageNew = mainStorage.createFile(filename, mime)
                    } else {
                        try {
                            storageNew = StoredFileHelper(context, mainStorage.getUri(),
                                    targetFile, mainStorage.getTag())
                        } catch (e: IOException) {
                            Log.e(TAG, ("Failed to take (or steal) the file in "
                                    + targetFile.toString()))
                            storageNew = null
                        }
                    }
                    if (storageNew != null && storageNew.canWrite()) {
                        continueSelectedDownload(storageNew)
                    } else {
                        showFailedDialog(R.string.error_file_creation)
                    }
                }

                MissionState.PendingRunning -> {
                    storageNew = mainStorage.createUniqueFile((filename)!!, mime)
                    if (storageNew == null) {
                        showFailedDialog(R.string.error_file_creation)
                    } else {
                        continueSelectedDownload(storageNew)
                    }
                }
            }
        }))
        askDialog.show()
    }

    private fun continueSelectedDownload(storage: StoredFileHelper) {
        if (!storage.canWrite()) {
            showFailedDialog(R.string.permission_denied)
            return
        }

        // check if the selected file has to be overwritten, by simply checking its length
        try {
            if (storage.length() > 0) {
                storage.truncate()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to truncate the file: " + storage.getUri().toString(), e)
            showFailedDialog(R.string.overwrite_failed)
            return
        }
        val selectedStream: Stream?
        var secondaryStream: Stream? = null
        val kind: Char
        var threads: Int = dialogBinding!!.threads.getProgress() + 1
        val urls: Array<String>
        val recoveryInfo: List<MissionRecoveryInfo>
        var psName: String? = null
        var psArgs: Array<String>? = null
        var nearLength: Long = 0
        when (dialogBinding!!.videoAudioGroup.getCheckedRadioButtonId()) {
            R.id.audio_button -> {
                kind = 'a'
                selectedStream = audioStreamsAdapter!!.getItem(selectedAudioIndex)
                if (selectedStream!!.getFormat() == MediaFormat.M4A) {
                    psName = Postprocessing.Companion.ALGORITHM_M4A_NO_DASH
                } else if (selectedStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                    psName = Postprocessing.Companion.ALGORITHM_OGG_FROM_WEBM_DEMUXER
                }
            }

            R.id.video_button -> {
                kind = 'v'
                selectedStream = videoStreamsAdapter!!.getItem(selectedVideoIndex)
                val secondary: SecondaryStreamHelper<AudioStream?>? = videoStreamsAdapter
                        .getAllSecondary()
                        .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream))
                if (secondary != null) {
                    secondaryStream = secondary.getStream()
                    if (selectedStream!!.getFormat() == MediaFormat.MPEG_4) {
                        psName = Postprocessing.Companion.ALGORITHM_MP4_FROM_DASH_MUXER
                    } else {
                        psName = Postprocessing.Companion.ALGORITHM_WEBM_MUXER
                    }
                    val videoSize: Long = wrappedVideoStreams!!.getSizeInBytes(
                            selectedStream as VideoStream?)

                    // set nearLength, only, if both sizes are fetched or known. This probably
                    // does not work on slow networks but is later updated in the downloader
                    if (secondary.getSizeInBytes() > 0 && videoSize > 0) {
                        nearLength = secondary.getSizeInBytes() + videoSize
                    }
                }
            }

            R.id.subtitle_button -> {
                threads = 1 // use unique thread for subtitles due small file size
                kind = 's'
                selectedStream = subtitleStreamsAdapter!!.getItem(selectedSubtitleIndex)
                if (selectedStream!!.getFormat() == MediaFormat.TTML) {
                    psName = Postprocessing.Companion.ALGORITHM_TTML_CONVERTER
                    psArgs = arrayOf(
                            selectedStream.getFormat()!!.getSuffix(),
                            "false" // ignore empty frames
                    )
                }
            }

            else -> return
        }
        if (secondaryStream == null) {
            urls = arrayOf(
                    selectedStream!!.getContent()
            )
            recoveryInfo = java.util.List.of(MissionRecoveryInfo((selectedStream)))
        } else {
            if (secondaryStream.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) {
                throw IllegalArgumentException(("Unsupported stream delivery format"
                        + secondaryStream.getDeliveryMethod()))
            }
            urls = arrayOf(
                    selectedStream!!.getContent(), secondaryStream.getContent()
            )
            recoveryInfo = java.util.List.of(
                    MissionRecoveryInfo((selectedStream)),
                    MissionRecoveryInfo(secondaryStream)
            )
        }
        DownloadManagerService.Companion.startMission(context, urls, storage, kind, threads,
                currentInfo!!.getUrl(), psName, psArgs, nearLength, ArrayList<MissionRecoveryInfo>(recoveryInfo))
        Toast.makeText(context, getString(R.string.download_has_started),
                Toast.LENGTH_SHORT).show()
        dismiss()
    }

    companion object {
        private val TAG: String = "DialogFragment"
        private val DEBUG: Boolean = MainActivity.Companion.DEBUG
    }
}
