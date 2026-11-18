package org.schabi.newpipe.download

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.evernote.android.state.State
import com.livefront.bridge.Bridge
import java.io.IOException
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DownloadDialogBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.AudioTrackAdapter
import org.schabi.newpipe.util.AudioTrackAdapter.AudioTracksWrapper
import org.schabi.newpipe.util.FilenameUtils
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener
import org.schabi.newpipe.util.StreamItemAdapter
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper
import org.schabi.newpipe.util.ThemeHelper
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService

class DownloadDialog() :
    DialogFragment(),
    RadioGroup.OnCheckedChangeListener,
    AdapterView.OnItemSelectedListener {

    @State
    @JvmField
    var currentInfo: StreamInfo? = null

    @State
    @JvmField
    var wrappedVideoStreams: StreamInfoWrapper<VideoStream>? = null

    @State
    @JvmField
    var wrappedSubtitleStreams: StreamInfoWrapper<SubtitlesStream>? = null

    @State
    @JvmField
    var wrappedAudioTracks: AudioTracksWrapper? = null

    @State
    @JvmField
    var selectedAudioTrackIndex = 0

    @State
    @JvmField
    var selectedVideoIndex = 0

    @State
    @JvmField
    var selectedAudioIndex = 0

    @State
    @JvmField
    var selectedSubtitleIndex = 0

    @State
    @JvmField
    var selectedAudioOutputIndex = 0

    @State
    @JvmField
    var selectedMp3BitrateIndex = 1

    @State
    @JvmField
    var muxedAudioFallbackVideoIndex = -1

    @State
    @JvmField
    var filenameTmp: String? = null

    @State
    @JvmField
    var mimeTmp: String? = null

    @State
    @JvmField
    var selectedSegments = ""

    @State
    @JvmField
    var companionCaptionIndex = -1

    private var mainStorageAudio: StoredDirectoryHelper? = null
    private var mainStorageVideo: StoredDirectoryHelper? = null
    private var downloadManager: DownloadManager? = null
    private var okButton: MenuItem? = null
    private var downloadContext: Context? = null
    private var askForSavePath = false

    private lateinit var audioTrackAdapter: AudioTrackAdapter
    private lateinit var audioStreamsAdapter: StreamItemAdapter<AudioStream, Stream>
    private lateinit var videoStreamsAdapter: StreamItemAdapter<VideoStream, AudioStream>
    private lateinit var subtitleStreamsAdapter: StreamItemAdapter<SubtitlesStream, Stream>

    private var streamSizeLoader: DownloadStreamSizeLoader? = null
    private var serviceConnector: DownloadServiceConnector? = null

    private var _dialogBinding: DownloadDialogBinding? = null
    private val dialogBinding: DownloadDialogBinding
        get() = checkNotNull(_dialogBinding)

    private lateinit var prefs: SharedPreferences

    private val requestDownloadSaveAsLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult(), ::requestDownloadSaveAsResult)
    private val requestDownloadPickAudioFolderLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult(), ::requestDownloadPickAudioFolderResult)
    private val requestDownloadPickVideoFolderLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult(), ::requestDownloadPickVideoFolderResult)

    constructor(context: Context, info: StreamInfo) : this() {
        currentInfo = info
        val catalog = DownloadStreamCatalogFactory.create(context, info)
        wrappedAudioTracks = catalog.audioTracks
        wrappedVideoStreams = catalog.videoStreams
        wrappedSubtitleStreams = catalog.subtitleStreams
        selectedAudioTrackIndex = catalog.selectedAudioTrackIndex
        selectedVideoIndex = catalog.selectedVideoIndex
        muxedAudioFallbackVideoIndex = catalog.muxedAudioFallbackVideoIndex
    }

    private val streamInfo: StreamInfo
        get() = checkNotNull(currentInfo)
    private val videoStreams: StreamInfoWrapper<VideoStream>
        get() = checkNotNull(wrappedVideoStreams)
    private val subtitleStreams: StreamInfoWrapper<SubtitlesStream>
        get() = checkNotNull(wrappedSubtitleStreams)
    private val audioTracks: AudioTracksWrapper
        get() = checkNotNull(wrappedAudioTracks)
    private val dialogContext: Context
        get() = checkNotNull(downloadContext)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")
        }

        if (!PermissionHelper.checkStoragePermissions(
                activity,
                PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE
            )
        ) {
            dismiss()
            return
        }

        downloadContext = context
        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(dialogContext))
        Bridge.restoreInstanceState(this, savedInstanceState)

        audioTrackAdapter = AudioTrackAdapter(audioTracks)
        subtitleStreamsAdapter = StreamItemAdapter(subtitleStreams)
        updateSecondaryStreams()
        streamSizeLoader = DownloadStreamSizeLoader(
            videoStreams,
            DownloadAudioStreamsProvider(::getWrappedAudioStreams),
            subtitleStreams,
            DownloadStreamSizeLoadedListener(::onStreamSizeLoaded),
            DownloadStreamSizeErrorListener(::onStreamSizeLoadError)
        )

        serviceConnector = DownloadServiceConnector(
            dialogContext,
            DownloadServiceConnectedListener(::onDownloadServiceConnected)
        ).also { it.connect() }
    }

    private fun onDownloadServiceConnected(state: DownloadServiceState) {
        mainStorageAudio = state.mainStorageAudio
        mainStorageVideo = state.mainStorageVideo
        downloadManager = state.downloadManager
        askForSavePath = state.askForSavePath
        okButton?.isEnabled = true
    }

    private fun updateSecondaryStreams() {
        val adapters = DownloadStreamAdapterFactory.create(
            dialogContext,
            audioTracks,
            selectedAudioTrackIndex,
            videoStreams,
            muxedAudioFallbackVideoIndex,
            DEBUG
        )
        videoStreamsAdapter = adapters.videoStreams
        audioStreamsAdapter = adapters.audioStreams
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (DEBUG) {
            Log.d(
                TAG,
                "onCreateView() called with: inflater = [$inflater], container = [$container], " +
                    "savedInstanceState = [$savedInstanceState]"
            )
        }
        return inflater.inflate(R.layout.download_dialog, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _dialogBinding = DownloadDialogBinding.bind(view)
        if (downloadContext == null) {
            return
        }

        dialogBinding.fileName.setText(
            FilenameUtils.createFilename(dialogContext, streamInfo.name)
        )
        selectedAudioIndex = ListHelper.getDefaultAudioFormat(
            dialogContext,
            getWrappedAudioStreams().streamsList
        )
        selectedSubtitleIndex = DownloadSubtitleSelectionPolicy.preferredIndex(
            subtitleStreamsAdapter.getAll()
        )

        dialogBinding.qualitySpinner.onItemSelectedListener = this
        dialogBinding.audioStreamSpinner.onItemSelectedListener = this
        dialogBinding.audioTrackSpinner.onItemSelectedListener = this
        dialogBinding.audioOutputFormatSpinner.onItemSelectedListener = this
        dialogBinding.mp3BitrateSpinner.onItemSelectedListener = this
        dialogBinding.videoAudioGroup.setOnCheckedChangeListener(this)

        newOptionsController().setupOutputSpinners(
            selectedAudioOutputIndex,
            selectedMp3BitrateIndex
        )

        initToolbar(dialogBinding.toolbarLayout.toolbar)
        setupDownloadOptions()
        dialogBinding.companionCaption.setOnClickListener {
            val options = arrayOf(getString(R.string.companion_caption_off), *subtitleStreamsAdapter.getAll().map { it.displayLanguageName + " (" + it.languageTag + ")" }.toTypedArray())
            com.google.android.material.dialog.MaterialAlertDialogBuilder(dialogContext)
                .setTitle(R.string.companion_caption_title)
                .setSingleChoiceItems(options, companionCaptionIndex + 1) { choice, index ->
                    companionCaptionIndex = index - 1
                    updateCompanionCaption()
                    choice.dismiss()
                }.setNegativeButton(R.string.cancel, null).show()
        }
        updateCompanionCaption()
        dialogBinding.downloadSegments.setOnClickListener {
            DownloadSegmentDialog.show(dialogContext, streamInfo.duration, streamInfo.streamSegments, selectedSegments) {
                selectedSegments = it
                if (it.isNotEmpty()) {
                    selectedAudioOutputIndex = 0
                    dialogBinding.audioOutputFormatSpinner.setSelection(0)
                }
                updateSegmentSelection()
            }
        }
        updateSegmentSelection()

        prefs = PreferenceManager.getDefaultSharedPreferences(dialogContext)
        val threads = prefs.getInt(getString(R.string.default_download_threads), 3)
        dialogBinding.threadsCount.text = threads.toString()
        dialogBinding.threads.progress = threads - 1
        dialogBinding.threads.setOnSeekBarChangeListener(
            object : SimpleOnSeekBarChangeListener() {
                override fun onProgressChanged(
                    seekbar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val newProgress = progress + 1
                    prefs.edit()
                        .putInt(getString(R.string.default_download_threads), newProgress)
                        .apply()
                    dialogBinding.threadsCount.text = newProgress.toString()
                }
            }
        )

        fetchStreamsSize()
    }

    private fun initToolbar(toolbar: Toolbar) {
        if (DEBUG) {
            Log.d(TAG, "initToolbar() called with: toolbar = [$toolbar]")
        }

        toolbar.setTitle(R.string.download_dialog_title)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.inflateMenu(R.menu.dialog_url)
        toolbar.setNavigationOnClickListener { dismiss() }
        toolbar.setNavigationContentDescription(R.string.cancel)

        okButton = toolbar.menu.findItem(R.id.okay).also {
            it.isEnabled = downloadManager != null
        }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.okay) {
                prepareSelectedDownload()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroyView() {
        streamSizeLoader?.clear()
        _dialogBinding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        serviceConnector?.disconnect()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Bridge.saveInstanceState(this, outState)
    }

    private fun fetchStreamsSize() {
        streamSizeLoader?.refresh()
    }

    private fun onStreamSizeLoaded(option: DownloadMediaOption) {
        if (_dialogBinding == null) {
            return
        }
        newOptionsController().refreshLoadedOption(
            option,
            selectedVideoIndex,
            selectedAudioIndex,
            selectedSubtitleIndex,
            selectedAudioOutputIndex
        )
    }

    private fun onStreamSizeLoadError(option: DownloadMediaOption, throwable: Throwable) {
        ErrorUtil.showSnackbar(
            dialogContext,
            ErrorInfo(
                throwable,
                UserAction.DOWNLOAD_OPEN_DIALOG,
                option.sizeRequestDescription,
                streamInfo
            )
        )
    }

    private fun setupAudioSpinner() {
        newOptionsController().showAudio(selectedAudioIndex, selectedAudioOutputIndex)
    }

    private fun setupVideoSpinner() {
        newOptionsController().showVideo(selectedVideoIndex)
    }

    private fun onVideoStreamSelected() {
        newOptionsController().updateVideoAudioTrackVisibility(selectedVideoIndex)
    }

    private fun setupSubtitleSpinner() {
        newOptionsController().showSubtitle(selectedSubtitleIndex)
    }

    private fun requestDownloadPickAudioFolderResult(result: ActivityResult) {
        newPickerResultHandler().handleFolder(
            result,
            getString(R.string.download_path_audio_key),
            DownloadManager.TAG_AUDIO,
            filenameTmp,
            mimeTmp
        )
    }

    private fun requestDownloadPickVideoFolderResult(result: ActivityResult) {
        newPickerResultHandler().handleFolder(
            result,
            getString(R.string.download_path_video_key),
            DownloadManager.TAG_VIDEO,
            filenameTmp,
            mimeTmp
        )
    }

    private fun requestDownloadSaveAsResult(result: ActivityResult) {
        newPickerResultHandler().handleSaveAs(result)
    }

    private fun newPickerResultHandler(): DownloadPickerResultHandler {
        return DownloadPickerResultHandler(
            dialogContext,
            downloadManager,
            DownloadReadyListener(::continueSelectedDownload),
            DownloadFailureListener(::showFailedDialog)
        )
    }

    override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
        if (DEBUG) {
            Log.d(TAG, "onCheckedChanged() called with: group = [$group], checkedId = [$checkedId]")
        }

        when (checkedId) {
            R.id.audio_button -> setupAudioSpinner()
            R.id.video_button -> setupVideoSpinner()
            R.id.subtitle_button -> setupSubtitleSpinner()
        }
        updateCompanionCaption()
        dialogBinding.threads.isEnabled = checkedId != R.id.subtitle_button
        updateSegmentSelection()
    }

    private fun updateCompanionCaption() {
        if (_dialogBinding == null) return
        val supported = dialogBinding.videoAudioGroup.checkedRadioButtonId == R.id.video_button &&
            subtitleStreamsAdapter.getAll().isNotEmpty() && selectedSegments.isEmpty()
        dialogBinding.companionCaption.visibility = if (supported) View.VISIBLE else View.GONE
        val selected = subtitleStreamsAdapter.getAll().getOrNull(companionCaptionIndex)
        dialogBinding.companionCaption.text = getString(R.string.companion_caption_label, selected?.displayLanguageName ?: getString(R.string.companion_caption_off))
    }

    private fun updateSegmentSelection() {
        if (_dialogBinding == null) return
        val supported = streamInfo.duration > 0 && !org.schabi.newpipe.util.StreamTypeUtil.isLiveStream(streamInfo.streamType) && dialogBinding.videoAudioGroup.checkedRadioButtonId != R.id.subtitle_button
        updateCompanionCaption()
        dialogBinding.downloadSegments.isEnabled = supported
        dialogBinding.downloadSegments.text = if (selectedSegments.isEmpty()) getString(R.string.download_segments) else getString(R.string.download_segments_selected, DownloadSegments.decode(selectedSegments).size)
        dialogBinding.audioOutputFormatSpinner.isEnabled = selectedSegments.isEmpty() || !supported
    }

    override fun onItemSelected(
        parent: AdapterView<*>,
        view: View?,
        position: Int,
        id: Long
    ) {
        if (DEBUG) {
            Log.d(
                TAG,
                "onItemSelected() called with: parent = [$parent], view = [$view], " +
                    "position = [$position], id = [$id]"
            )
        }

        when (parent.id) {
            R.id.quality_spinner -> {
                when (dialogBinding.videoAudioGroup.checkedRadioButtonId) {
                    R.id.video_button -> {
                        selectedVideoIndex = position
                        onVideoStreamSelected()
                    }

                    R.id.subtitle_button -> selectedSubtitleIndex = position
                }
                onItemSelectedSetFileName()
            }

            R.id.audio_track_spinner -> {
                val trackChanged = selectedAudioTrackIndex != position
                selectedAudioTrackIndex = position
                if (trackChanged) {
                    updateSecondaryStreams()
                    fetchStreamsSize()
                }
            }

            R.id.audio_stream_spinner -> {
                selectedAudioIndex = position
                updateMp3BitrateVisibility()
            }

            R.id.audio_output_format_spinner -> {
                selectedAudioOutputIndex = position
                updateMp3BitrateVisibility()
            }

            R.id.mp3_bitrate_spinner -> selectedMp3BitrateIndex = position
        }
    }

    private fun onItemSelectedSetFileName() {
        val fileName = FilenameUtils.createFilename(dialogContext, streamInfo.name)
        val previousFileName = dialogBinding.fileName.text?.toString().orEmpty()

        if (
            previousFileName.isEmpty() ||
            previousFileName == fileName ||
            previousFileName.startsWith(
                getString(R.string.caption_file_name, fileName, "")
            )
        ) {
            when (dialogBinding.videoAudioGroup.checkedRadioButtonId) {
                R.id.audio_button,
                R.id.video_button -> if (previousFileName != fileName) {
                    dialogBinding.fileName.setText(fileName)
                }

                R.id.subtitle_button -> {
                    val languageCode = subtitleStreamsAdapter
                        .getItem(selectedSubtitleIndex)
                        .languageTag
                    dialogBinding.fileName.setText(
                        getString(R.string.caption_file_name, fileName, languageCode)
                    )
                }
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

    protected fun setupDownloadOptions() {
        if (
            !newOptionsController().setupInitial(
                selectedAudioTrackIndex,
                selectedAudioIndex,
                selectedVideoIndex,
                selectedSubtitleIndex,
                selectedAudioOutputIndex
            )
        ) {
            dismiss()
        }
    }

    private fun updateMp3BitrateVisibility() {
        if (_dialogBinding == null) {
            return
        }
        newOptionsController().updateMp3BitrateVisibility(
            selectedAudioOutputIndex,
            selectedAudioIndex
        )
    }

    private fun isMp3OutputSelected(): Boolean {
        return selectedSegments.isEmpty() && DownloadAudioOutputPolicy.isMp3Output(selectedAudioOutputIndex)
    }

    private fun getSelectedMp3Bitrate(): Int {
        return DownloadAudioOutputPolicy.bitrateForIndex(selectedMp3BitrateIndex)
    }

    private fun getWrappedAudioStreams(): StreamInfoWrapper<AudioStream> {
        if (selectedAudioTrackIndex !in 0 until audioTracks.size()) {
            return StreamInfoWrapper.empty()
        }
        return audioTracks.tracksList[selectedAudioTrackIndex]
    }

    private fun hasMuxedAudioFallback(): Boolean {
        return muxedAudioFallbackVideoIndex in videoStreams.streamsList.indices
    }

    private fun getMuxedAudioFallbackSource(): VideoStream? {
        return if (hasMuxedAudioFallback()) {
            videoStreams.streamsList[muxedAudioFallbackVideoIndex]
        } else {
            null
        }
    }

    private fun newOptionsController(): DownloadOptionsController {
        return DownloadOptionsController(
            dialogContext,
            dialogBinding,
            audioTrackAdapter,
            audioStreamsAdapter,
            videoStreamsAdapter,
            subtitleStreamsAdapter,
            audioTracks,
            hasMuxedAudioFallback()
        )
    }

    private fun getNameEditText(): String {
        val enteredName = dialogBinding.fileName.text?.toString().orEmpty().trim()
        return FilenameUtils.createFilename(
            dialogContext,
            enteredName.ifEmpty { streamInfo.name }
        )
    }

    private fun showFailedDialog(@StringRes message: Int) {
        AlertDialog.Builder(dialogContext)
            .setTitle(R.string.general_error)
            .setMessage(message)
            .setNegativeButton(getString(R.string.ok), null)
            .show()
    }

    private fun launchSaveAsPicker(filename: String, mimeType: String?, initialPath: Uri?) {
        NoFileManagerSafeGuard.launchSafe(
            requestDownloadSaveAsLauncher,
            StoredFileHelper.getNewPicker(
                dialogContext,
                filename,
                mimeType ?: StoredFileHelper.DEFAULT_MIME,
                initialPath
            ),
            TAG,
            dialogContext
        )
    }

    private fun prepareSelectedDownload() {
        val mainStorage: StoredDirectoryHelper?
        var outputPlan: DownloadOutputPlan
        val selectedMediaType: String
        val checkedButton = dialogBinding.videoAudioGroup.checkedRadioButtonId
        val baseFilename = getNameEditText()

        when (checkedButton) {
            R.id.audio_button -> {
                selectedMediaType = getString(R.string.last_download_type_audio_key)
                mainStorage = mainStorageAudio
                outputPlan = DownloadOutputPlanFactory.forAudio(
                    baseFilename,
                    audioStreamsAdapter.getItem(selectedAudioIndex).format,
                    getWrappedAudioStreams().getSizeInBytes(selectedAudioIndex),
                    streamInfo.duration,
                    isMp3OutputSelected(),
                    getSelectedMp3Bitrate()
                )
            }

            R.id.video_button -> {
                selectedMediaType = getString(R.string.last_download_type_video_key)
                mainStorage = mainStorageVideo
                outputPlan = DownloadOutputPlanFactory.forVideo(
                    baseFilename,
                    videoStreamsAdapter.getItem(selectedVideoIndex).format,
                    videoStreams.getSizeInBytes(selectedVideoIndex)
                )
            }

            R.id.subtitle_button -> {
                selectedMediaType = getString(R.string.last_download_type_subtitle_key)
                mainStorage = mainStorageVideo
                outputPlan = DownloadOutputPlanFactory.forSubtitle(
                    baseFilename,
                    subtitleStreamsAdapter.getItem(selectedSubtitleIndex).format,
                    subtitleStreams.getSizeInBytes(selectedSubtitleIndex)
                )
            }

            else -> error("No stream selected")
        }

        if (selectedSegments.isNotEmpty() && checkedButton != R.id.subtitle_button) {
            val audio = checkedButton == R.id.audio_button
            outputPlan = DownloadOutputPlan(
                "${baseFilename}_segments.${if (audio) "m4a" else "mp4"}",
                if (audio) "audio/mp4" else "video/mp4",
                outputPlan.estimatedSize
            )
        }
        filenameTmp = outputPlan.filename
        mimeTmp = outputPlan.mimeType

        val usedConfiguredStorage = DownloadDestinationCoordinator(
            dialogContext,
            DownloadSaveAsListener(::launchSaveAsPicker),
            requestDownloadPickAudioFolderLauncher,
            requestDownloadPickVideoFolderLauncher,
            ConfiguredDownloadTargetListener(::checkSelectedDownload)
        ).prepare(
            checkedButton == R.id.audio_button,
            mainStorage,
            outputPlan,
            askForSavePath && !(checkedButton == R.id.video_button && companionCaptionIndex >= 0 && selectedSegments.isEmpty())
        )
        if (!usedConfiguredStorage) {
            return
        }

        prefs.edit()
            .putString(getString(R.string.last_used_download_type), selectedMediaType)
            .apply()
    }

    private fun checkSelectedDownload(
        mainStorage: StoredDirectoryHelper,
        targetFile: Uri?,
        filename: String,
        mime: String?
    ) {
        val manager = downloadManager
        if (manager == null) {
            showFailedDialog(R.string.general_error)
            return
        }
        DownloadStorageCoordinator(
            dialogContext,
            manager,
            DownloadReadyListener(::continueSelectedDownload),
            DownloadFailureListener(::showFailedDialog)
        ).check(mainStorage, targetFile, filename, mime)
    }

    private fun continueSelectedDownload(storage: StoredFileHelper, directory: StoredDirectoryHelper?) {
        if (!storage.canWrite()) {
            showFailedDialog(R.string.permission_denied)
            return
        }

        val threads = dialogBinding.threads.progress + 1
        val request = when (dialogBinding.videoAudioGroup.checkedRadioButtonId) {
            R.id.audio_button -> DownloadMissionRequestFactory.forAudio(
                audioStreamsAdapter.getItem(selectedAudioIndex),
                getMuxedAudioFallbackSource(),
                isMp3OutputSelected(),
                getSelectedMp3Bitrate(),
                threads
            )

            R.id.video_button -> {
                val selectedStream = videoStreamsAdapter.getItem(selectedVideoIndex)
                val secondary = videoStreamsAdapter.getAllSecondary()[
                    videoStreams.streamsList.indexOf(selectedStream)
                ]
                DownloadMissionRequestFactory.forVideo(
                    selectedStream,
                    secondary?.stream,
                    videoStreams.getSizeInBytes(selectedStream),
                    secondary?.sizeInBytes ?: 0,
                    threads
                )
            }

            R.id.subtitle_button -> DownloadMissionRequestFactory.forSubtitle(
                subtitleStreamsAdapter.getItem(selectedSubtitleIndex)
            )

            else -> return
        }

        val companion = if (request.kind == 'v' && selectedSegments.isEmpty() && companionCaptionIndex >= 0) {
            try {
                CompanionCaption.prepare(
                    dialogContext,
                    requireNotNull(directory),
                    storage,
                    subtitleStreamsAdapter.getItem(companionCaptionIndex)
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to prepare companion caption", exception)
                showFailedDialog(R.string.companion_caption_failed)
                return
            }
        } else {
            null
        }

        try {
            if (storage.length() > 0) {
                storage.truncate()
            }
        } catch (exception: IOException) {
            Log.e(TAG, "Failed to truncate the file: ${storage.uri}", exception)
            companion?.first?.delete()
            showFailedDialog(R.string.overwrite_failed)
            return
        }

        DownloadManagerService.startMission(
            dialogContext,
            request.urls,
            storage,
            request.kind,
            request.threads,
            streamInfo,
            if (selectedSegments.isNotEmpty() && request.kind != 's') Postprocessing.ALGORITHM_SEGMENTS else request.postprocessingName,
            if (selectedSegments.isNotEmpty() && request.kind != 's') arrayOf(selectedSegments, request.postprocessingName.orEmpty()) + request.postprocessingArguments.orEmpty() else request.postprocessingArguments,
            request.nearLength,
            request.recoveryInfo
        )

        companion?.let { (file, caption) ->
            DownloadManagerService.startMission(
                dialogContext, caption.urls, file, caption.kind, caption.threads, streamInfo,
                caption.postprocessingName, caption.postprocessingArguments, caption.nearLength, caption.recoveryInfo
            )
        }

        Toast.makeText(dialogContext, getString(R.string.download_has_started), Toast.LENGTH_SHORT)
            .show()
        dismiss()
    }

    private companion object {
        const val TAG = "DialogFragment"
        val DEBUG = MainActivity.DEBUG
    }
}
