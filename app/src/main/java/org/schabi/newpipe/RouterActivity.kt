package org.schabi.newpipe

import android.annotation.SuppressLint
import android.app.IntentService
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.math.MathUtils
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.isAtLeast
import androidx.lifecycle.Lifecycle.addObserver
import androidx.lifecycle.Lifecycle.currentState
import androidx.lifecycle.Lifecycle.removeObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import icepick.Icepick
import icepick.State
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.core.SingleOnSubscribe
import io.reactivex.rxjava3.core.SingleTransformer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Cancellable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.RouterActivity.FetcherService
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.ListRadioIconItemBinding
import org.schabi.newpipe.databinding.SingleChoiceDialogViewBinding
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.download.LoadingDialog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.createNotification
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.ktx.isNetworkRelated
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.ChannelTabHelper
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.urlfinder.UrlFinder.Companion.firstUrlFromInput
import org.schabi.newpipe.views.FocusOverlayView
import java.io.Serializable
import java.lang.ref.WeakReference
import java.util.Arrays
import java.util.Optional
import java.util.concurrent.Callable
import java.util.function.Function
import java.util.function.IntPredicate
import java.util.function.Predicate

/**
 * Get the url from the intent and open it in the chosen preferred player.
 */
class RouterActivity() : AppCompatActivity() {
    protected val disposables: CompositeDisposable = CompositeDisposable()

    @State
    protected var currentServiceId: Int = -1

    @State
    protected var currentLinkType: LinkType? = null

    @State
    protected var selectedRadioPosition: Int = -1
    protected var selectedPreviously: Int = -1
    protected var currentUrl: String? = null
    private var currentService: StreamingService? = null
    private var selectionIsDownload: Boolean = false
    private var selectionIsAddToPlaylist: Boolean = false
    private var alertDialogChoice: AlertDialog? = null
    private var dismissListener: FragmentManager.FragmentLifecycleCallbacks? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setDayNightMode(this)
        setTheme(if (ThemeHelper.isLightThemeSelected(this)) R.style.RouterActivityThemeLight else R.style.RouterActivityThemeDark)
        Localization.assureCorrectAppLanguage(this)

        // Pass-through touch events to background activities
        // so that our transparent window won't lock UI in the mean time
        // network request is underway before showing PlaylistDialog or DownloadDialog
        // (ref: https://stackoverflow.com/a/10606141)
        getWindow().addFlags((WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))

        // Android never fails to impress us with a list of new restrictions per API.
        // Starting with S (Android 12) one of the prerequisite conditions has to be met
        // before the FLAG_NOT_TOUCHABLE flag is allowed to kick in:
        // @see WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE
        // For our present purpose it seems we can just set LayoutParams.alpha to 0
        // on the strength of "4. Fully transparent windows" without affecting the scrim of dialogs
        val params: WindowManager.LayoutParams = getWindow().getAttributes()
        params.alpha = 0f
        getWindow().setAttributes(params)
        super.onCreate(savedInstanceState)
        Icepick.restoreInstanceState(this, savedInstanceState)

        // FragmentManager will take care to recreate (Playlist|Download)Dialog when screen rotates
        // We used to .setOnDismissListener(dialog -> finish()); when creating these DialogFragments
        // but those callbacks won't survive a config change
        // Try an alternate approach to hook into FragmentManager instead, to that effect
        // (ref: https://stackoverflow.com/a/44028453)
        val fm: FragmentManager = getSupportFragmentManager()
        if (dismissListener == null) {
            dismissListener = object : FragmentManager.FragmentLifecycleCallbacks() {
                public override fun onFragmentDestroyed(fm: FragmentManager,
                                                        f: Fragment) {
                    super.onFragmentDestroyed(fm, f)
                    if (f is DialogFragment && fm.getFragments().isEmpty()) {
                        // No more DialogFragments, we're done
                        finish()
                    }
                }
            }
        }
        fm.registerFragmentLifecycleCallbacks(dismissListener!!, false)
        if (TextUtils.isEmpty(currentUrl)) {
            currentUrl = getUrl(getIntent())
            if (TextUtils.isEmpty(currentUrl)) {
                handleText()
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // we need to dismiss the dialog before leaving the activity or we get leaks
        if (alertDialogChoice != null) {
            alertDialogChoice!!.dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    override fun onStart() {
        super.onStart()

        // Don't overlap the DialogFragment after rotating the screen
        // If there's no DialogFragment, we're either starting afresh
        // or we didn't make it to PlaylistDialog or DownloadDialog before the orientation change
        if (getSupportFragmentManager().getFragments().isEmpty()) {
            // Start over from scratch
            handleUrl(currentUrl)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dismissListener != null) {
            getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(dismissListener!!)
        }
        disposables.clear()
    }

    public override fun finish() {
        // allow the activity to recreate in case orientation changes
        if (!isChangingConfigurations()) {
            super.finish()
        }
    }

    private fun handleUrl(url: String?) {
        disposables.add(Observable
                .fromCallable<Boolean>(Callable<Boolean>({
                    try {
                        if (currentServiceId == -1) {
                            currentService = NewPipe.getServiceByUrl(url)
                            currentServiceId = currentService.getServiceId()
                            currentLinkType = currentService.getLinkTypeByUrl(url)
                            currentUrl = url
                        } else {
                            currentService = NewPipe.getService(currentServiceId)
                        }

                        // return whether the url was found to be supported or not
                        return@fromCallable currentLinkType != LinkType.NONE
                    } catch (e: ExtractionException) {
                        // this can be reached only when the url is completely unsupported
                        return@fromCallable false
                    }
                }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(io.reactivex.rxjava3.functions.Consumer<Boolean>({ isUrlSupported: Boolean ->
                    if (isUrlSupported) {
                        onSuccess()
                    } else {
                        showUnsupportedUrlDialog(url)
                    }
                }), io.reactivex.rxjava3.functions.Consumer<Throwable>({ throwable: Throwable? ->
                    handleError(this, ErrorInfo((throwable)!!,
                            UserAction.SHARE_TO_NEWPIPE, "Getting service from url: " + url))
                })))
    }

    protected fun showUnsupportedUrlDialog(url: String?) {
        val context: Context = getThemeWrapperContext()
        AlertDialog.Builder(context)
                .setTitle(R.string.unsupported_url)
                .setMessage(R.string.unsupported_url_dialog_message)
                .setIcon(R.drawable.ic_share)
                .setPositiveButton(R.string.open_in_browser,
                        DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> ShareUtils.openUrlInBrowser(this, url) }))
                .setNegativeButton(R.string.share,
                        DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> shareText(this, "", url) })) // no subject
                .setNeutralButton(R.string.cancel, null)
                .setOnDismissListener(DialogInterface.OnDismissListener({ dialog: DialogInterface? -> finish() }))
                .show()
    }

    protected fun onSuccess() {
        val preferences: SharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(this)
        val choiceChecker: ChoiceAvailabilityChecker = ChoiceAvailabilityChecker(
                getChoicesForService(currentService, currentLinkType),
                (preferences.getString(getString(R.string.preferred_open_action_key),
                        getString(R.string.preferred_open_action_default)))!!)

        // Check for non-player related choices
        if (choiceChecker.isAvailableAndSelected(
                        R.string.show_info_key,
                        R.string.download_key,
                        R.string.add_to_playlist_key)) {
            handleChoice(choiceChecker.getSelectedChoiceKey())
            return
        }
        // Check if the choice is player related
        if (choiceChecker.isAvailableAndSelected(
                        R.string.video_player_key,
                        R.string.background_player_key,
                        R.string.popup_player_key)) {
            val selectedChoice: String = choiceChecker.getSelectedChoiceKey()
            val isExtVideoEnabled: Boolean = preferences.getBoolean(
                    getString(R.string.use_external_video_player_key), false)
            val isExtAudioEnabled: Boolean = preferences.getBoolean(
                    getString(R.string.use_external_audio_player_key), false)
            val isVideoPlayerSelected: Boolean = ((selectedChoice == getString(R.string.video_player_key)) || (selectedChoice == getString(R.string.popup_player_key)))
            val isAudioPlayerSelected: Boolean = (selectedChoice == getString(R.string.background_player_key))
            if ((currentLinkType != LinkType.STREAM
                            && ((isExtAudioEnabled && isAudioPlayerSelected)
                            || (isExtVideoEnabled && isVideoPlayerSelected)))) {
                Toast.makeText(this, R.string.external_player_unsupported_link_type,
                        Toast.LENGTH_LONG).show()
                handleChoice(getString(R.string.show_info_key))
                return
            }
            val capabilities: List<MediaCapability> = currentService!!.getServiceInfo().getMediaCapabilities()

            // Check if the service supports the choice
            if (((isVideoPlayerSelected && capabilities.contains(MediaCapability.VIDEO))
                            || (isAudioPlayerSelected && capabilities.contains(MediaCapability.AUDIO)))) {
                handleChoice(selectedChoice)
            } else {
                handleChoice(getString(R.string.show_info_key))
            }
            return
        }

        // Default / Ask always
        val availableChoices: List<AdapterChoiceItem> = choiceChecker.getAvailableChoices()
        when (availableChoices.size) {
            1 -> handleChoice(availableChoices.get(0).key)
            0 -> handleChoice(getString(R.string.show_info_key))
            else -> showDialog(availableChoices)
        }
    }

    /**
     * This is a helper class for checking if the choices are available and/or selected.
     */
    internal inner class ChoiceAvailabilityChecker(
            private val availableChoices: List<AdapterChoiceItem>,
            private val selectedChoiceKey: String) {
        fun getAvailableChoices(): List<AdapterChoiceItem> {
            return availableChoices
        }

        fun getSelectedChoiceKey(): String {
            return selectedChoiceKey
        }

        fun isAvailableAndSelected(@StringRes vararg wantedKeys: Int): Boolean {
            return Arrays.stream(wantedKeys).anyMatch(IntPredicate({ wantedKey: Int -> this.isAvailableAndSelected(wantedKey) }))
        }

        fun isAvailableAndSelected(@StringRes wantedKey: Int): Boolean {
            val wanted: String = getString(wantedKey)
            // Check if the wanted option is selected
            if (!(selectedChoiceKey == wanted)) {
                return false
            }
            // Check if it's available
            return availableChoices.stream().anyMatch(Predicate({ item: AdapterChoiceItem -> (wanted == item.key) }))
        }
    }

    private fun showDialog(choices: List<AdapterChoiceItem>) {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val themeWrapperContext: Context = getThemeWrapperContext()
        val layoutInflater: LayoutInflater = LayoutInflater.from(themeWrapperContext)
        val binding: SingleChoiceDialogViewBinding = SingleChoiceDialogViewBinding.inflate(layoutInflater)
        val radioGroup: RadioGroup = binding.list
        val dialogButtonsClickListener: DialogInterface.OnClickListener = DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int ->
            val indexOfChild: Int = radioGroup.indexOfChild(
                    radioGroup.findViewById(radioGroup.getCheckedRadioButtonId()))
            val choice: AdapterChoiceItem = choices.get(indexOfChild)
            handleChoice(choice.key)

            // open future streams always like this one, because "always" button was used by user
            if (which == DialogInterface.BUTTON_POSITIVE) {
                preferences.edit()
                        .putString(getString(R.string.preferred_open_action_key), choice.key)
                        .apply()
            }
        })
        alertDialogChoice = AlertDialog.Builder(themeWrapperContext)
                .setTitle(R.string.preferred_open_action_share_menu_title)
                .setView(binding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.just_once, dialogButtonsClickListener)
                .setPositiveButton(R.string.always, dialogButtonsClickListener)
                .setOnDismissListener(DialogInterface.OnDismissListener({ dialog: DialogInterface? ->
                    if (!selectionIsDownload && !selectionIsAddToPlaylist) {
                        finish()
                    }
                }))
                .create()
        alertDialogChoice!!.setOnShowListener(OnShowListener({ dialog: DialogInterface? ->
            setDialogButtonsState(
                    alertDialogChoice!!, radioGroup.getCheckedRadioButtonId() != -1)
        }))
        radioGroup.setOnCheckedChangeListener(RadioGroup.OnCheckedChangeListener({ group: RadioGroup?, checkedId: Int -> setDialogButtonsState(alertDialogChoice!!, true) }))
        val radioButtonsClickListener: View.OnClickListener = View.OnClickListener({ v: View? ->
            val indexOfChild: Int = radioGroup.indexOfChild(v)
            if (indexOfChild == -1) {
                return@OnClickListener
            }
            selectedPreviously = selectedRadioPosition
            selectedRadioPosition = indexOfChild
            if (selectedPreviously == selectedRadioPosition) {
                handleChoice(choices.get(selectedRadioPosition).key)
            }
        })
        var id: Int = 12345
        for (item: AdapterChoiceItem in choices) {
            val radioButton: RadioButton = ListRadioIconItemBinding.inflate(layoutInflater)
                    .getRoot()
            radioButton.setText(item.description)
            radioButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    AppCompatResources.getDrawable(themeWrapperContext, item.icon),
                    null, null, null)
            radioButton.setChecked(false)
            radioButton.setId(id++)
            radioButton.setLayoutParams(RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            radioButton.setOnClickListener(radioButtonsClickListener)
            radioGroup.addView(radioButton)
        }
        if (selectedRadioPosition == -1) {
            val lastSelectedPlayer: String? = preferences.getString(
                    getString(R.string.preferred_open_action_last_selected_key), null)
            if (!TextUtils.isEmpty(lastSelectedPlayer)) {
                for (i in choices.indices) {
                    val c: AdapterChoiceItem = choices.get(i)
                    if ((lastSelectedPlayer == c.key)) {
                        selectedRadioPosition = i
                        break
                    }
                }
            }
        }
        selectedRadioPosition = MathUtils.clamp(selectedRadioPosition, -1, choices.size - 1)
        if (selectedRadioPosition != -1) {
            (radioGroup.getChildAt(selectedRadioPosition) as RadioButton).setChecked(true)
        }
        selectedPreviously = selectedRadioPosition
        alertDialogChoice!!.show()
        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.Companion.setupFocusObserver(alertDialogChoice!!)
        }
    }

    private fun getChoicesForService(service: StreamingService?,
                                     linkType: LinkType?): List<AdapterChoiceItem> {
        val showInfo: AdapterChoiceItem = AdapterChoiceItem(
                getString(R.string.show_info_key), getString(R.string.show_info),
                R.drawable.ic_info_outline)
        val videoPlayer: AdapterChoiceItem = AdapterChoiceItem(
                getString(R.string.video_player_key), getString(R.string.video_player),
                R.drawable.ic_play_arrow)
        val backgroundPlayer: AdapterChoiceItem = AdapterChoiceItem(
                getString(R.string.background_player_key), getString(R.string.background_player),
                R.drawable.ic_headset)
        val popupPlayer: AdapterChoiceItem = AdapterChoiceItem(
                getString(R.string.popup_player_key), getString(R.string.popup_player),
                R.drawable.ic_picture_in_picture)
        val returnedItems: MutableList<AdapterChoiceItem> = ArrayList()
        returnedItems.add(showInfo) // Always present
        val capabilities: List<MediaCapability> = service!!.getServiceInfo().getMediaCapabilities()
        if (linkType == LinkType.STREAM) {
            if (capabilities.contains(MediaCapability.VIDEO)) {
                returnedItems.add(videoPlayer)
                returnedItems.add(popupPlayer)
            }
            if (capabilities.contains(MediaCapability.AUDIO)) {
                returnedItems.add(backgroundPlayer)
            }
            // download is redundant for linkType CHANNEL AND PLAYLIST (till playlist downloading is
            // not supported )
            returnedItems.add(AdapterChoiceItem(getString(R.string.download_key),
                    getString(R.string.download),
                    R.drawable.ic_file_download))

            // Add to playlist is not necessary for CHANNEL and PLAYLIST linkType since those can
            // not be added to a playlist
            returnedItems.add(AdapterChoiceItem(getString(R.string.add_to_playlist_key),
                    getString(R.string.add_to_playlist),
                    R.drawable.ic_add))
        } else {
            // LinkType.NONE is never present because it's filtered out before
            // channels and playlist can be played as they contain a list of videos
            val preferences: SharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(this)
            val isExtVideoEnabled: Boolean = preferences.getBoolean(
                    getString(R.string.use_external_video_player_key), false)
            val isExtAudioEnabled: Boolean = preferences.getBoolean(
                    getString(R.string.use_external_audio_player_key), false)
            if (capabilities.contains(MediaCapability.VIDEO) && !isExtVideoEnabled) {
                returnedItems.add(videoPlayer)
                returnedItems.add(popupPlayer)
            }
            if (capabilities.contains(MediaCapability.AUDIO) && !isExtAudioEnabled) {
                returnedItems.add(backgroundPlayer)
            }
        }
        return returnedItems
    }

    protected fun getThemeWrapperContext(): Context {
        return ContextThemeWrapper(this, if (ThemeHelper.isLightThemeSelected(this)) R.style.LightTheme else R.style.DarkTheme)
    }

    private fun setDialogButtonsState(dialog: AlertDialog, state: Boolean) {
        val negativeButton: Button? = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        val positiveButton: Button? = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        if (negativeButton == null || positiveButton == null) {
            return
        }
        negativeButton.setEnabled(state)
        positiveButton.setEnabled(state)
    }

    private fun handleText() {
        val searchString: String? = getIntent().getStringExtra(Intent.EXTRA_TEXT)
        val serviceId: Int = getIntent().getIntExtra(KEY_SERVICE_ID, 0)
        val intent: Intent = Intent(getThemeWrapperContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        NavigationHelper.openSearch(getThemeWrapperContext(), serviceId, searchString)
    }

    private fun handleChoice(selectedChoiceKey: String) {
        val validChoicesList: List<String> = Arrays.asList(*getResources()
                .getStringArray(R.array.preferred_open_action_values_list))
        if (validChoicesList.contains(selectedChoiceKey)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(getString(
                            R.string.preferred_open_action_last_selected_key), selectedChoiceKey)
                    .apply()
        }
        if (((selectedChoiceKey == getString(R.string.popup_player_key)) && !PermissionHelper.isPopupEnabledElseAsk(this))) {
            finish()
            return
        }
        if ((selectedChoiceKey == getString(R.string.download_key))) {
            if (PermissionHelper.checkStoragePermissions(this,
                            PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                selectionIsDownload = true
                openDownloadDialog()
            }
            return
        }
        if ((selectedChoiceKey == getString(R.string.add_to_playlist_key))) {
            selectionIsAddToPlaylist = true
            openAddToPlaylistDialog()
            return
        }

        // stop and bypass FetcherService if InfoScreen was selected since
        // StreamDetailFragment can fetch data itself
        if (((selectedChoiceKey == getString(R.string.show_info_key)) || canHandleChoiceLikeShowInfo(selectedChoiceKey))) {
            disposables.add(Observable
                    .fromCallable(Callable({ NavigationHelper.getIntentByLink(this, currentUrl) }))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(io.reactivex.rxjava3.functions.Consumer({ intent: Intent? ->
                        startActivity(intent)
                        finish()
                    }), io.reactivex.rxjava3.functions.Consumer({ throwable: Throwable? ->
                        handleError(this, ErrorInfo((throwable)!!,
                                UserAction.SHARE_TO_NEWPIPE, "Starting info activity: " + currentUrl))
                    }))
            )
            return
        }
        val intent: Intent = Intent(this, FetcherService::class.java)
        val choice: Choice = Choice(currentService!!.getServiceId(), currentLinkType,
                currentUrl, selectedChoiceKey)
        intent.putExtra(FetcherService.KEY_CHOICE, choice)
        startService(intent)
        finish()
    }

    private fun canHandleChoiceLikeShowInfo(selectedChoiceKey: String): Boolean {
        if (!(selectedChoiceKey == getString(R.string.video_player_key))) {
            return false
        }
        // "video player" can be handled like "show info" (because VideoDetailFragment can load
        // the stream instead of FetcherService) when...

        // ...Autoplay is enabled
        if (!PlayerHelper.isAutoplayAllowedByUser(getThemeWrapperContext())) {
            return false
        }
        val isExtVideoEnabled: Boolean = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.use_external_video_player_key), false)
        // ...it's not done via an external player
        if (isExtVideoEnabled) {
            return false
        }

        // ...the player is not running or in normal Video-mode/type
        val playerType: PlayerType? = PlayerHolder.Companion.getInstance().getType()
        return playerType == null || playerType == PlayerType.MAIN
    }

    class PersistentFragment() : Fragment() {
        private var weakContext: WeakReference<AppCompatActivity>? = null
        private val disposables: CompositeDisposable = CompositeDisposable()
        private var running: Int = 0
        @Synchronized
        private fun inFlight(started: Boolean) {
            if (started) {
                running++
            } else {
                running--
                if (running <= 0) {
                    getActivityContext().ifPresent(java.util.function.Consumer({ context: AppCompatActivity? ->
                        context!!.getSupportFragmentManager()
                                .beginTransaction().remove(this).commit()
                    }))
                }
            }
        }

        public override fun onAttach(activityContext: Context) {
            super.onAttach(activityContext)
            weakContext = WeakReference(activityContext as AppCompatActivity)
        }

        public override fun onDetach() {
            super.onDetach()
            weakContext = null
        }

        @Suppress("deprecation")
        public override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setRetainInstance(true)
        }

        public override fun onDestroy() {
            super.onDestroy()
            disposables.clear()
        }

        /**
         * @return the activity context, if there is one and the activity is not finishing
         */
        private fun getActivityContext(): Optional<AppCompatActivity?> {
            return Optional.ofNullable(weakContext)
                    .map(Function({ obj: WeakReference<AppCompatActivity>? -> obj!!.get() }))
                    .filter(Predicate({ context: AppCompatActivity? -> !context!!.isFinishing() }))
        }

        // guard against IllegalStateException in calling DialogFragment.show() whilst in background
        // (which could happen, say, when the user pressed the home button while waiting for
        // the network request to return) when it internally calls FragmentTransaction.commit()
        // after the FragmentManager has saved its states (isStateSaved() == true)
        // (ref: https://stackoverflow.com/a/39813506)
        private fun runOnVisible(runnable: java.util.function.Consumer<AppCompatActivity>) {
            getActivityContext().ifPresentOrElse(java.util.function.Consumer<AppCompatActivity?>({ context: AppCompatActivity? ->
                if (getLifecycle().currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    context!!.runOnUiThread(Runnable({
                        runnable.accept((context))
                        inFlight(false)
                    }))
                } else {
                    getLifecycle().addObserver(object : DefaultLifecycleObserver {
                        public override fun onResume(owner: LifecycleOwner) {
                            getLifecycle().removeObserver(this)
                            getActivityContext().ifPresentOrElse(java.util.function.Consumer({ context: AppCompatActivity? ->
                                context!!.runOnUiThread(Runnable({
                                    runnable.accept((context))
                                    inFlight(false)
                                }))
                            }),
                                    Runnable({ inFlight(false) })
                            )
                        }
                    })
                    // this trick doesn't seem to work on Android 10+ (API 29)
                    // which places restrictions on starting activities from the background
                    if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                                    && !context!!.isChangingConfigurations())) {
                        // try to bring the activity back to front if minimised
                        val i: Intent = Intent(context, RouterActivity::class.java)
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        startActivity(i)
                    }
                }
            }), Runnable({ // this branch is executed if there is no activity context
                inFlight(false)
            })
            )
        }

        fun <T> pleaseWait(single: Single<T>): Single<T> {
            // 'abuse' ambWith() here to cancel the toast for us when the wait is over
            return single.ambWith(Single.create(SingleOnSubscribe({ emitter: SingleEmitter<T> ->
                getActivityContext().ifPresent(java.util.function.Consumer({ context: AppCompatActivity? ->
                    context!!.runOnUiThread(Runnable({

                        // Getting the stream info usually takes a moment
                        // Notifying the user here to ensure that no confusion arises
                        val toast: Toast = Toast.makeText(context,
                                getString(R.string.processing_may_take_a_moment),
                                Toast.LENGTH_LONG)
                        toast.show()
                        emitter.setCancellable(Cancellable({ toast.cancel() }))
                    }))
                }))
            })))
        }

        @SuppressLint("CheckResult")
        fun openDownloadDialog(currentServiceId: Int, currentUrl: String?) {
            inFlight(true)
            val loadingDialog: LoadingDialog = LoadingDialog(R.string.loading_metadata_title)
            loadingDialog.show(getParentFragmentManager(), "loadingDialog")
            disposables.add(ExtractorHelper.getStreamInfo(currentServiceId, currentUrl, true)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose<StreamInfo>(SingleTransformer<StreamInfo, StreamInfo>({ single: Single<StreamInfo> -> pleaseWait(single) }))
                    .subscribe(io.reactivex.rxjava3.functions.Consumer<StreamInfo>({ result: StreamInfo ->
                        runOnVisible(java.util.function.Consumer<AppCompatActivity>({ ctx: AppCompatActivity ->
                            loadingDialog.dismiss()
                            val fm: FragmentManager = ctx.getSupportFragmentManager()
                            val downloadDialog: DownloadDialog = DownloadDialog(ctx, result)
                            // dismiss listener to be handled by FragmentManager
                            downloadDialog.show(fm, "downloadDialog")
                        })
                        )
                    }), io.reactivex.rxjava3.functions.Consumer<Throwable>({ throwable: Throwable? ->
                        runOnVisible(java.util.function.Consumer<AppCompatActivity>({ ctx: AppCompatActivity ->
                            loadingDialog.dismiss()
                            (ctx as RouterActivity).showUnsupportedUrlDialog(currentUrl)
                        }))
                    })))
        }

        fun openAddToPlaylistDialog(currentServiceId: Int, currentUrl: String?) {
            inFlight(true)
            disposables.add(ExtractorHelper.getStreamInfo(currentServiceId, currentUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose<StreamInfo>(SingleTransformer<StreamInfo, StreamInfo>({ single: Single<StreamInfo> -> pleaseWait(single) }))
                    .subscribe(
                            io.reactivex.rxjava3.functions.Consumer<StreamInfo>({ info: StreamInfo? ->
                                getActivityContext().ifPresent(java.util.function.Consumer<AppCompatActivity?>({ context: AppCompatActivity? ->
                                    PlaylistDialog.Companion.createCorrespondingDialog(context,
                                            java.util.List.of<StreamEntity?>(StreamEntity((info)!!)),
                                            java.util.function.Consumer<PlaylistDialog>({ playlistDialog: PlaylistDialog ->
                                                runOnVisible(java.util.function.Consumer({ ctx: AppCompatActivity ->
                                                    // dismiss listener to be handled by FragmentManager
                                                    val fm: FragmentManager = ctx.getSupportFragmentManager()
                                                    playlistDialog.show(fm, "addToPlaylistDialog")
                                                }))
                                            })
                                    )
                                }))
                            }),
                            io.reactivex.rxjava3.functions.Consumer<Throwable>({ throwable: Throwable? ->
                                runOnVisible(java.util.function.Consumer({ ctx: AppCompatActivity ->
                                    handleError(ctx, ErrorInfo(
                                            (throwable)!!,
                                            UserAction.REQUESTED_STREAM,
                                            "Tried to add " + currentUrl + " to a playlist",
                                            (ctx as RouterActivity).currentService!!.getServiceId())
                                    )
                                }))
                            })
                    )
            )
        }
    }

    private fun openAddToPlaylistDialog() {
        getPersistFragment().openAddToPlaylistDialog(currentServiceId, currentUrl)
    }

    private fun openDownloadDialog() {
        getPersistFragment().openDownloadDialog(currentServiceId, currentUrl)
    }

    private fun getPersistFragment(): PersistentFragment {
        val fm: FragmentManager = getSupportFragmentManager()
        var persistFragment: PersistentFragment? = fm.findFragmentByTag("PERSIST_FRAGMENT") as PersistentFragment?
        if (persistFragment == null) {
            persistFragment = PersistentFragment()
            fm.beginTransaction()
                    .add(persistFragment, "PERSIST_FRAGMENT")
                    .commitNow()
        }
        return persistFragment
    }

    public override fun onRequestPermissionsResult(requestCode: Int,
                                                   permissions: Array<String>,
                                                   grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i: Int in grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                finish()
                return
            }
        }
        if (requestCode == PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE) {
            openDownloadDialog()
        }
    }

    private class AdapterChoiceItem internal constructor(val key: String, val description: String, @field:DrawableRes val icon: Int)
    class Choice internal constructor(val serviceId: Int, val linkType: LinkType?,
                                      val url: String?, val playerChoice: String) : Serializable {
        public override fun toString(): String {
            return serviceId.toString() + ":" + url + " > " + linkType + " ::: " + playerChoice
        }
    }

    class FetcherService() : IntentService(FetcherService::class.java.getSimpleName()) {
        private var fetcher: Disposable? = null
        public override fun onCreate() {
            super.onCreate()
            startForeground(ID, createNotification().build())
        }

        override fun onHandleIntent(intent: Intent?) {
            if (intent == null) {
                return
            }
            val serializable: Serializable? = intent.getSerializableExtra(KEY_CHOICE)
            if (!(serializable is Choice)) {
                return
            }
            handleChoice(serializable)
        }

        fun handleChoice(choice: Choice) {
            var single: Single<out Info?>? = null
            var userAction: UserAction = UserAction.SOMETHING_ELSE
            when (choice.linkType) {
                LinkType.STREAM -> {
                    single = ExtractorHelper.getStreamInfo(choice.serviceId, choice.url, false)
                    userAction = UserAction.REQUESTED_STREAM
                }

                LinkType.CHANNEL -> {
                    single = ExtractorHelper.getChannelInfo(choice.serviceId, choice.url, false)
                    userAction = UserAction.REQUESTED_CHANNEL
                }

                LinkType.PLAYLIST -> {
                    single = ExtractorHelper.getPlaylistInfo(choice.serviceId, choice.url, false)
                    userAction = UserAction.REQUESTED_PLAYLIST
                }
            }
            if (single != null) {
                val finalUserAction: UserAction = userAction
                val resultHandler: java.util.function.Consumer<Info?> = getResultHandler(choice)
                fetcher = single
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ info: Info? ->
                            resultHandler.accept(info)
                            if (fetcher != null) {
                                fetcher!!.dispose()
                            }
                        }, io.reactivex.rxjava3.functions.Consumer<Throwable>({ throwable: Throwable? ->
                            handleError(this, ErrorInfo((throwable)!!, finalUserAction,
                                    choice.url + " opened with " + choice.playerChoice,
                                    choice.serviceId))
                        }))
            }
        }

        fun getResultHandler(choice: Choice): java.util.function.Consumer<Info?> {
            return java.util.function.Consumer({ info: Info? ->
                val videoPlayerKey: String = getString(R.string.video_player_key)
                val backgroundPlayerKey: String = getString(R.string.background_player_key)
                val popupPlayerKey: String = getString(R.string.popup_player_key)
                val preferences: SharedPreferences = PreferenceManager
                        .getDefaultSharedPreferences(this)
                val isExtVideoEnabled: Boolean = preferences.getBoolean(
                        getString(R.string.use_external_video_player_key), false)
                val isExtAudioEnabled: Boolean = preferences.getBoolean(
                        getString(R.string.use_external_audio_player_key), false)
                val playQueue: PlayQueue
                if (info is StreamInfo) {
                    if ((choice.playerChoice == backgroundPlayerKey) && isExtAudioEnabled) {
                        NavigationHelper.playOnExternalAudioPlayer(this, info)
                        return@Consumer
                    } else if ((choice.playerChoice == videoPlayerKey) && isExtVideoEnabled) {
                        NavigationHelper.playOnExternalVideoPlayer(this, info)
                        return@Consumer
                    }
                    playQueue = SinglePlayQueue(info as StreamInfo?)
                } else if (info is ChannelInfo) {
                    val playableTab: Optional<ListLinkHandler> = info.getTabs()
                            .stream()
                            .filter(Predicate({ obj: ListLinkHandler? -> ChannelTabHelper.isStreamsTab() }))
                            .findFirst()
                    if (playableTab.isPresent()) {
                        playQueue = ChannelTabPlayQueue(info.getServiceId(), playableTab.get())
                    } else {
                        return@Consumer  // there is no playable tab
                    }
                } else if (info is PlaylistInfo) {
                    playQueue = PlaylistPlayQueue(info)
                } else {
                    return@Consumer
                }
                if ((choice.playerChoice == videoPlayerKey)) {
                    NavigationHelper.playOnMainPlayer(this, playQueue, false)
                } else if ((choice.playerChoice == backgroundPlayerKey)) {
                    NavigationHelper.playOnBackgroundPlayer(this, playQueue, true)
                } else if ((choice.playerChoice == popupPlayerKey)) {
                    NavigationHelper.playOnPopupPlayer(this, playQueue, true)
                }
            })
        }

        public override fun onDestroy() {
            super.onDestroy()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            if (fetcher != null) {
                fetcher!!.dispose()
            }
        }

        private fun createNotification(): NotificationCompat.Builder {
            return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(
                            getString(R.string.preferred_player_fetcher_notification_title))
                    .setContentText(
                            getString(R.string.preferred_player_fetcher_notification_message))
        }

        companion object {
            val KEY_CHOICE: String = "key_choice"
            private val ID: Int = 456
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun getUrl(intent: Intent): String? {
        var foundUrl: String? = null
        if (intent.getData() != null) {
            // Called from another app
            foundUrl = intent.getData().toString()
        } else if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            // Called from the share menu
            val extraText: String? = intent.getStringExtra(Intent.EXTRA_TEXT)
            foundUrl = firstUrlFromInput(extraText)
        }
        return foundUrl
    }

    companion object {
        /**
         * @param context the context. It will be `finish()`ed at the end of the handling if it is
         * an instance of [RouterActivity].
         * @param errorInfo the error information
         */
        private fun handleError(context: Context, errorInfo: ErrorInfo) {
            if (errorInfo.throwable != null) {
                errorInfo.throwable!!.printStackTrace()
            }
            if (errorInfo.throwable is ReCaptchaException) {
                Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show()
                // Starting ReCaptcha Challenge Activity
                val intent: Intent = Intent(context, ReCaptchaActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else if ((errorInfo.throwable != null
                            && errorInfo.throwable!!.isNetworkRelated)) {
                Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
            } else if (errorInfo.throwable is AgeRestrictedContentException) {
                Toast.makeText(context, R.string.restricted_video_no_stream,
                        Toast.LENGTH_LONG).show()
            } else if (errorInfo.throwable is GeographicRestrictionException) {
                Toast.makeText(context, R.string.georestricted_content, Toast.LENGTH_LONG).show()
            } else if (errorInfo.throwable is PaidContentException) {
                Toast.makeText(context, R.string.paid_content, Toast.LENGTH_LONG).show()
            } else if (errorInfo.throwable is PrivateContentException) {
                Toast.makeText(context, R.string.private_content, Toast.LENGTH_LONG).show()
            } else if (errorInfo.throwable is SoundCloudGoPlusContentException) {
                Toast.makeText(context, R.string.soundcloud_go_plus_content,
                        Toast.LENGTH_LONG).show()
            } else if (errorInfo.throwable is YoutubeMusicPremiumContentException) {
                Toast.makeText(context, R.string.youtube_music_premium_content,
                        Toast.LENGTH_LONG).show()
            } else if (errorInfo.throwable is ContentNotAvailableException) {
                Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show()
            } else if (errorInfo.throwable is ContentNotSupportedException) {
                Toast.makeText(context, R.string.content_not_supported, Toast.LENGTH_LONG).show()
            } else {
                createNotification(context, errorInfo)
            }
            if (context is RouterActivity) {
                context.finish()
            }
        }
    }
}
