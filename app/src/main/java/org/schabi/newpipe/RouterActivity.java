package org.schabi.newpipe;

import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.AUDIO;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.math.MathUtils;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.ListRadioIconItemBinding;
import org.schabi.newpipe.databinding.SingleChoiceDialogViewBinding;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.StreamingService.LinkType;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException;
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.ktx.ExceptionUtils;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.urlfinder.UrlFinder;
import org.schabi.newpipe.views.FocusOverlayView;

import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import icepick.Icepick;
import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Get the url from the intent and open it in the chosen preferred player.
 */
public class RouterActivity extends AppCompatActivity {
    protected final CompositeDisposable disposables = new CompositeDisposable();
    @State
    protected int currentServiceId = -1;
    @State
    protected LinkType currentLinkType;
    @State
    protected int selectedRadioPosition = -1;
    protected int selectedPreviously = -1;
    protected String currentUrl;
    private StreamingService currentService;
    private boolean selectionIsDownload = false;
    private boolean selectionIsAddToPlaylist = false;
    private AlertDialog alertDialogChoice = null;
    private FragmentManager.FragmentLifecycleCallbacks dismissListener = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        ThemeHelper.setDayNightMode(this);
        setTheme(ThemeHelper.isLightThemeSelected(this)
                ? R.style.RouterActivityThemeLight : R.style.RouterActivityThemeDark);
        Localization.assureCorrectAppLanguage(this);

        // Pass-through touch events to background activities
        // so that our transparent window won't lock UI in the mean time
        // network request is underway before showing PlaylistDialog or DownloadDialog
        // (ref: https://stackoverflow.com/a/10606141)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        // Android never fails to impress us with a list of new restrictions per API.
        // Starting with S (Android 12) one of the prerequisite conditions has to be met
        // before the FLAG_NOT_TOUCHABLE flag is allowed to kick in:
        // @see WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE
        // For our present purpose it seems we can just set LayoutParams.alpha to 0
        // on the strength of "4. Fully transparent windows" without affecting the scrim of dialogs
        final WindowManager.LayoutParams params = getWindow().getAttributes();
        params.alpha = 0f;
        getWindow().setAttributes(params);

        super.onCreate(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);

        // FragmentManager will take care to recreate (Playlist|Download)Dialog when screen rotates
        // We used to .setOnDismissListener(dialog -> finish()); when creating these DialogFragments
        // but those callbacks won't survive a config change
        // Try an alternate approach to hook into FragmentManager instead, to that effect
        // (ref: https://stackoverflow.com/a/44028453)
        final FragmentManager fm = getSupportFragmentManager();
        if (dismissListener == null) {
            dismissListener = new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentDestroyed(@NonNull final FragmentManager fm,
                                                @NonNull final Fragment f) {
                    super.onFragmentDestroyed(fm, f);
                    if (f instanceof DialogFragment && fm.getFragments().isEmpty()) {
                        // No more DialogFragments, we're done
                        finish();
                    }
                }
            };
        }
        fm.registerFragmentLifecycleCallbacks(dismissListener, false);

        if (TextUtils.isEmpty(currentUrl)) {
            currentUrl = getUrl(getIntent());

            if (TextUtils.isEmpty(currentUrl)) {
                handleText();
                finish();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // we need to dismiss the dialog before leaving the activity or we get leaks
        if (alertDialogChoice != null) {
            alertDialogChoice.dismiss();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Don't overlap the DialogFragment after rotating the screen
        // If there's no DialogFragment, we're either starting afresh
        // or we didn't make it to PlaylistDialog or DownloadDialog before the orientation change
        if (getSupportFragmentManager().getFragments().isEmpty()) {
            // Start over from scratch
            handleUrl(currentUrl);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (dismissListener != null) {
            getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(dismissListener);
        }

        disposables.clear();
    }

    @Override
    public void finish() {
        // allow the activity to recreate in case orientation changes
        if (!isChangingConfigurations()) {
            super.finish();
        }
    }

    private void handleUrl(final String url) {
        disposables.add(Observable
                .fromCallable(() -> {
                    try {
                        if (currentServiceId == -1) {
                            currentService = NewPipe.getServiceByUrl(url);
                            currentServiceId = currentService.getServiceId();
                            currentLinkType = currentService.getLinkTypeByUrl(url);
                            currentUrl = url;
                        } else {
                            currentService = NewPipe.getService(currentServiceId);
                        }

                        // return whether the url was found to be supported or not
                        return currentLinkType != LinkType.NONE;
                    } catch (final ExtractionException e) {
                        // this can be reached only when the url is completely unsupported
                        return false;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isUrlSupported -> {
                    if (isUrlSupported) {
                        onSuccess();
                    } else {
                        showUnsupportedUrlDialog(url);
                    }
                }, throwable -> handleError(this, new ErrorInfo(throwable,
                        UserAction.SHARE_TO_NEWPIPE, "Getting service from url: " + url))));
    }

    /**
     * @param context the context. It will be {@code finish()}ed at the end of the handling if it is
     *                an instance of {@link RouterActivity}.
     * @param errorInfo the error information
     */
    private static void handleError(final Context context, final ErrorInfo errorInfo) {
        if (errorInfo.getThrowable() != null) {
            errorInfo.getThrowable().printStackTrace();
        }

        if (errorInfo.getThrowable() instanceof ReCaptchaException) {
            Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
            // Starting ReCaptcha Challenge Activity
            final Intent intent = new Intent(context, ReCaptchaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else if (errorInfo.getThrowable() != null
                && ExceptionUtils.isNetworkRelated(errorInfo.getThrowable())) {
            Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof AgeRestrictedContentException) {
            Toast.makeText(context, R.string.restricted_video_no_stream,
                    Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof GeographicRestrictionException) {
            Toast.makeText(context, R.string.georestricted_content, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof PaidContentException) {
            Toast.makeText(context, R.string.paid_content, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof PrivateContentException) {
            Toast.makeText(context, R.string.private_content, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof SoundCloudGoPlusContentException) {
            Toast.makeText(context, R.string.soundcloud_go_plus_content,
                    Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof YoutubeMusicPremiumContentException) {
            Toast.makeText(context, R.string.youtube_music_premium_content,
                    Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof ContentNotAvailableException) {
            Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof ContentNotSupportedException) {
            Toast.makeText(context, R.string.content_not_supported, Toast.LENGTH_LONG).show();
        } else {
            ErrorUtil.createNotification(context, errorInfo);
        }

        if (context instanceof RouterActivity) {
            ((RouterActivity) context).finish();
        }
    }

    protected void showUnsupportedUrlDialog(final String url) {
        final Context context = getThemeWrapperContext();
        new AlertDialog.Builder(context)
                .setTitle(R.string.unsupported_url)
                .setMessage(R.string.unsupported_url_dialog_message)
                .setIcon(R.drawable.ic_share)
                .setPositiveButton(R.string.open_in_browser,
                        (dialog, which) -> ShareUtils.openUrlInBrowser(this, url))
                .setNegativeButton(R.string.share,
                        (dialog, which) -> ShareUtils.shareText(this, "", url)) // no subject
                .setNeutralButton(R.string.cancel, null)
                .setOnDismissListener(dialog -> finish())
                .show();
    }

    protected void onSuccess() {
        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(this);

        final ChoiceAvailabilityChecker choiceChecker = new ChoiceAvailabilityChecker(
                getChoicesForService(currentService, currentLinkType),
                preferences.getString(getString(R.string.preferred_open_action_key),
                        getString(R.string.preferred_open_action_default)));

        // Check for non-player related choices
        if (choiceChecker.isAvailableAndSelected(
                R.string.show_info_key,
                R.string.download_key,
                R.string.add_to_playlist_key)) {
            handleChoice(choiceChecker.getSelectedChoiceKey());
            return;
        }
        // Check if the choice is player related
        if (choiceChecker.isAvailableAndSelected(
                R.string.video_player_key,
                R.string.background_player_key,
                R.string.popup_player_key)) {

            final String selectedChoice = choiceChecker.getSelectedChoiceKey();

            final boolean isExtVideoEnabled = preferences.getBoolean(
                    getString(R.string.use_external_video_player_key), false);
            final boolean isExtAudioEnabled = preferences.getBoolean(
                    getString(R.string.use_external_audio_player_key), false);
            final boolean isVideoPlayerSelected =
                    selectedChoice.equals(getString(R.string.video_player_key))
                            || selectedChoice.equals(getString(R.string.popup_player_key));
            final boolean isAudioPlayerSelected =
                    selectedChoice.equals(getString(R.string.background_player_key));

            if (currentLinkType != LinkType.STREAM
                    && ((isExtAudioEnabled && isAudioPlayerSelected)
                    || (isExtVideoEnabled && isVideoPlayerSelected))
            ) {
                Toast.makeText(this, R.string.external_player_unsupported_link_type,
                        Toast.LENGTH_LONG).show();
                handleChoice(getString(R.string.show_info_key));
                return;
            }

            final List<StreamingService.ServiceInfo.MediaCapability> capabilities =
                    currentService.getServiceInfo().getMediaCapabilities();

            // Check if the service supports the choice
            if ((isVideoPlayerSelected && capabilities.contains(VIDEO))
                    || (isAudioPlayerSelected && capabilities.contains(AUDIO))) {
                handleChoice(selectedChoice);
            } else {
                handleChoice(getString(R.string.show_info_key));
            }
            return;
        }

        // Default / Ask always
        final List<AdapterChoiceItem> availableChoices = choiceChecker.getAvailableChoices();
        switch (availableChoices.size()) {
            case 1:
                handleChoice(availableChoices.get(0).key);
                break;
            case 0:
                handleChoice(getString(R.string.show_info_key));
                break;
            default:
                showDialog(availableChoices);
                break;
        }
    }

    /**
     * This is a helper class for checking if the choices are available and/or selected.
     */
    class ChoiceAvailabilityChecker {
        private final List<AdapterChoiceItem> availableChoices;
        private final String selectedChoiceKey;

        ChoiceAvailabilityChecker(
                @NonNull final List<AdapterChoiceItem> availableChoices,
                @NonNull final String selectedChoiceKey) {
            this.availableChoices = availableChoices;
            this.selectedChoiceKey = selectedChoiceKey;
        }

        public List<AdapterChoiceItem> getAvailableChoices() {
            return availableChoices;
        }

        public String getSelectedChoiceKey() {
            return selectedChoiceKey;
        }

        public boolean isAvailableAndSelected(@StringRes final int... wantedKeys) {
            return Arrays.stream(wantedKeys).anyMatch(this::isAvailableAndSelected);
        }

        public boolean isAvailableAndSelected(@StringRes final int wantedKey) {
            final String wanted = getString(wantedKey);
            // Check if the wanted option is selected
            if (!selectedChoiceKey.equals(wanted)) {
                return false;
            }
            // Check if it's available
            return availableChoices.stream().anyMatch(item -> wanted.equals(item.key));
        }
    }

    private void showDialog(final List<AdapterChoiceItem> choices) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        final Context themeWrapperContext = getThemeWrapperContext();
        final LayoutInflater layoutInflater = LayoutInflater.from(themeWrapperContext);

        final SingleChoiceDialogViewBinding binding =
                SingleChoiceDialogViewBinding.inflate(layoutInflater);
        final RadioGroup radioGroup = binding.list;

        final DialogInterface.OnClickListener dialogButtonsClickListener = (dialog, which) -> {
            final int indexOfChild = radioGroup.indexOfChild(
                    radioGroup.findViewById(radioGroup.getCheckedRadioButtonId()));
            final AdapterChoiceItem choice = choices.get(indexOfChild);

            handleChoice(choice.key);

            // open future streams always like this one, because "always" button was used by user
            if (which == DialogInterface.BUTTON_POSITIVE) {
                preferences.edit()
                        .putString(getString(R.string.preferred_open_action_key), choice.key)
                        .apply();
            }
        };

        alertDialogChoice = new AlertDialog.Builder(themeWrapperContext)
                .setTitle(R.string.preferred_open_action_share_menu_title)
                .setView(binding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.just_once, dialogButtonsClickListener)
                .setPositiveButton(R.string.always, dialogButtonsClickListener)
                .setOnDismissListener(dialog -> {
                    if (!selectionIsDownload && !selectionIsAddToPlaylist) {
                        finish();
                    }
                })
                .create();

        alertDialogChoice.setOnShowListener(dialog -> setDialogButtonsState(
                alertDialogChoice, radioGroup.getCheckedRadioButtonId() != -1));

        radioGroup.setOnCheckedChangeListener((group, checkedId) ->
                setDialogButtonsState(alertDialogChoice, true));
        final View.OnClickListener radioButtonsClickListener = v -> {
            final int indexOfChild = radioGroup.indexOfChild(v);
            if (indexOfChild == -1) {
                return;
            }

            selectedPreviously = selectedRadioPosition;
            selectedRadioPosition = indexOfChild;

            if (selectedPreviously == selectedRadioPosition) {
                handleChoice(choices.get(selectedRadioPosition).key);
            }
        };

        int id = 12345;
        for (final AdapterChoiceItem item : choices) {
            final RadioButton radioButton = ListRadioIconItemBinding.inflate(layoutInflater)
                    .getRoot();
            radioButton.setText(item.description);
            radioButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    AppCompatResources.getDrawable(themeWrapperContext, item.icon),
                    null, null, null);
            radioButton.setChecked(false);
            radioButton.setId(id++);
            radioButton.setLayoutParams(new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            radioButton.setOnClickListener(radioButtonsClickListener);
            radioGroup.addView(radioButton);
        }

        if (selectedRadioPosition == -1) {
            final String lastSelectedPlayer = preferences.getString(
                    getString(R.string.preferred_open_action_last_selected_key), null);
            if (!TextUtils.isEmpty(lastSelectedPlayer)) {
                for (int i = 0; i < choices.size(); i++) {
                    final AdapterChoiceItem c = choices.get(i);
                    if (lastSelectedPlayer.equals(c.key)) {
                        selectedRadioPosition = i;
                        break;
                    }
                }
            }
        }

        selectedRadioPosition = MathUtils.clamp(selectedRadioPosition, -1, choices.size() - 1);
        if (selectedRadioPosition != -1) {
            ((RadioButton) radioGroup.getChildAt(selectedRadioPosition)).setChecked(true);
        }
        selectedPreviously = selectedRadioPosition;

        alertDialogChoice.show();

        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(alertDialogChoice);
        }
    }

    private List<AdapterChoiceItem> getChoicesForService(final StreamingService service,
                                                         final LinkType linkType) {
        final AdapterChoiceItem showInfo = new AdapterChoiceItem(
                getString(R.string.show_info_key), getString(R.string.show_info),
                R.drawable.ic_info_outline);
        final AdapterChoiceItem videoPlayer = new AdapterChoiceItem(
                getString(R.string.video_player_key), getString(R.string.video_player),
                R.drawable.ic_play_arrow);
        final AdapterChoiceItem backgroundPlayer = new AdapterChoiceItem(
                getString(R.string.background_player_key), getString(R.string.background_player),
                R.drawable.ic_headset);
        final AdapterChoiceItem popupPlayer = new AdapterChoiceItem(
                getString(R.string.popup_player_key), getString(R.string.popup_player),
                R.drawable.ic_picture_in_picture);

        final List<AdapterChoiceItem> returnedItems = new ArrayList<>();
        returnedItems.add(showInfo); // Always present

        final List<StreamingService.ServiceInfo.MediaCapability> capabilities =
                service.getServiceInfo().getMediaCapabilities();

        if (linkType == LinkType.STREAM) {
            if (capabilities.contains(VIDEO)) {
                returnedItems.add(videoPlayer);
                returnedItems.add(popupPlayer);
            }
            if (capabilities.contains(AUDIO)) {
                returnedItems.add(backgroundPlayer);
            }
            // download is redundant for linkType CHANNEL AND PLAYLIST (till playlist downloading is
            // not supported )
            returnedItems.add(new AdapterChoiceItem(getString(R.string.download_key),
                    getString(R.string.download),
                    R.drawable.ic_file_download));

            // Add to playlist is not necessary for CHANNEL and PLAYLIST linkType since those can
            // not be added to a playlist
            returnedItems.add(new AdapterChoiceItem(getString(R.string.add_to_playlist_key),
                    getString(R.string.add_to_playlist),
                    R.drawable.ic_add));
        } else {
            // LinkType.NONE is never present because it's filtered out before
            // channels and playlist can be played as they contain a list of videos
            final SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(this);
            final boolean isExtVideoEnabled = preferences.getBoolean(
                    getString(R.string.use_external_video_player_key), false);
            final boolean isExtAudioEnabled = preferences.getBoolean(
                    getString(R.string.use_external_audio_player_key), false);

            if (capabilities.contains(VIDEO) && !isExtVideoEnabled) {
                returnedItems.add(videoPlayer);
                returnedItems.add(popupPlayer);
            }
            if (capabilities.contains(AUDIO) && !isExtAudioEnabled) {
                returnedItems.add(backgroundPlayer);
            }
        }

        return returnedItems;
    }

    protected Context getThemeWrapperContext() {
        return new ContextThemeWrapper(this, ThemeHelper.isLightThemeSelected(this)
                ? R.style.LightTheme : R.style.DarkTheme);
    }

    private void setDialogButtonsState(final AlertDialog dialog, final boolean state) {
        final Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        final Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (negativeButton == null || positiveButton == null) {
            return;
        }

        negativeButton.setEnabled(state);
        positiveButton.setEnabled(state);
    }

    private void handleText() {
        final String searchString = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        final int serviceId = getIntent().getIntExtra(Constants.KEY_SERVICE_ID, 0);
        final Intent intent = new Intent(getThemeWrapperContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        NavigationHelper.openSearch(getThemeWrapperContext(), serviceId, searchString);
    }

    private void handleChoice(final String selectedChoiceKey) {
        final List<String> validChoicesList = Arrays.asList(getResources()
                .getStringArray(R.array.preferred_open_action_values_list));
        if (validChoicesList.contains(selectedChoiceKey)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(getString(
                            R.string.preferred_open_action_last_selected_key), selectedChoiceKey)
                    .apply();
        }

        if (selectedChoiceKey.equals(getString(R.string.popup_player_key))
                && !PermissionHelper.isPopupEnabledElseAsk(this)) {
            finish();
            return;
        }

        if (selectedChoiceKey.equals(getString(R.string.download_key))) {
            if (PermissionHelper.checkStoragePermissions(this,
                    PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                selectionIsDownload = true;
                openDownloadDialog();
            }
            return;
        }

        if (selectedChoiceKey.equals(getString(R.string.add_to_playlist_key))) {
            selectionIsAddToPlaylist = true;
            openAddToPlaylistDialog();
            return;
        }

        // stop and bypass FetcherService if InfoScreen was selected since
        // StreamDetailFragment can fetch data itself
        if (selectedChoiceKey.equals(getString(R.string.show_info_key))
                || canHandleChoiceLikeShowInfo(selectedChoiceKey)) {
            disposables.add(Observable
                    .fromCallable(() -> NavigationHelper.getIntentByLink(this, currentUrl))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(intent -> {
                        startActivity(intent);
                        finish();
                    }, throwable -> handleError(this, new ErrorInfo(throwable,
                            UserAction.SHARE_TO_NEWPIPE, "Starting info activity: " + currentUrl)))
            );
            return;
        }

        final Intent intent = new Intent(this, FetcherService.class);
        final Choice choice = new Choice(currentService.getServiceId(), currentLinkType,
                currentUrl, selectedChoiceKey);
        intent.putExtra(FetcherService.KEY_CHOICE, choice);
        startService(intent);

        finish();
    }

    private boolean canHandleChoiceLikeShowInfo(final String selectedChoiceKey) {
        if (!selectedChoiceKey.equals(getString(R.string.video_player_key))) {
            return false;
        }
        // "video player" can be handled like "show info" (because VideoDetailFragment can load
        // the stream instead of FetcherService) when...

        // ...Autoplay is enabled
        if (!PlayerHelper.isAutoplayAllowedByUser(getThemeWrapperContext())) {
            return false;
        }

        final boolean isExtVideoEnabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.use_external_video_player_key), false);
        // ...it's not done via an external player
        if (isExtVideoEnabled) {
            return false;
        }

        // ...the player is not running or in normal Video-mode/type
        final PlayerType playerType = PlayerHolder.getInstance().getType();
        return playerType == null || playerType == PlayerType.MAIN;
    }

    public static class PersistentFragment extends Fragment {
        private WeakReference<AppCompatActivity> weakContext;
        private final CompositeDisposable disposables = new CompositeDisposable();
        private int running = 0;

        private synchronized void inFlight(final boolean started) {
            if (started) {
                running++;
            } else {
                running--;
                if (running <= 0) {
                    getActivityContext().ifPresent(context -> context.getSupportFragmentManager()
                            .beginTransaction().remove(this).commit());
                }
            }
        }

        @Override
        public void onAttach(@NonNull final Context activityContext) {
            super.onAttach(activityContext);
            weakContext = new WeakReference<>((AppCompatActivity) activityContext);
        }

        @Override
        public void onDetach() {
            super.onDetach();
            weakContext = null;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            disposables.clear();
        }

        /**
         * @return the activity context, if there is one and the activity is not finishing
         */
        private Optional<AppCompatActivity> getActivityContext() {
            return Optional.ofNullable(weakContext)
                    .map(Reference::get)
                    .filter(context -> !context.isFinishing());
        }

        // guard against IllegalStateException in calling DialogFragment.show() whilst in background
        // (which could happen, say, when the user pressed the home button while waiting for
        // the network request to return) when it internally calls FragmentTransaction.commit()
        // after the FragmentManager has saved its states (isStateSaved() == true)
        // (ref: https://stackoverflow.com/a/39813506)
        private void runOnVisible(final Consumer<AppCompatActivity> runnable) {
            getActivityContext().ifPresentOrElse(context -> {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    context.runOnUiThread(() -> {
                        runnable.accept(context);
                        inFlight(false);
                    });
                } else {
                    getLifecycle().addObserver(new DefaultLifecycleObserver() {
                        @Override
                        public void onResume(@NonNull final LifecycleOwner owner) {
                            getLifecycle().removeObserver(this);
                            getActivityContext().ifPresentOrElse(context ->
                                    context.runOnUiThread(() -> {
                                        runnable.accept(context);
                                        inFlight(false);
                                    }),
                                    () -> inFlight(false)
                            );
                        }
                    });
                    // this trick doesn't seem to work on Android 10+ (API 29)
                    // which places restrictions on starting activities from the background
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                            && !context.isChangingConfigurations()) {
                        // try to bring the activity back to front if minimised
                        final Intent i = new Intent(context, RouterActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    }
                }

            }, () -> {
                // this branch is executed if there is no activity context
                inFlight(false);
            });
        }

        <T> Single<T> pleaseWait(final Single<T> single) {
            // 'abuse' ambWith() here to cancel the toast for us when the wait is over
            return single.ambWith(Single.create(emitter -> getActivityContext().ifPresent(context ->
                    context.runOnUiThread(() -> {
                        // Getting the stream info usually takes a moment
                        // Notifying the user here to ensure that no confusion arises
                        final Toast toast = Toast.makeText(context,
                                getString(R.string.processing_may_take_a_moment),
                                Toast.LENGTH_LONG);
                        toast.show();
                        emitter.setCancellable(toast::cancel);
            }))));
        }

        @SuppressLint("CheckResult")
        private void openDownloadDialog(final int currentServiceId, final String currentUrl) {
            inFlight(true);
            disposables.add(ExtractorHelper.getStreamInfo(currentServiceId, currentUrl, true)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(this::pleaseWait)
                    .subscribe(result ->
                        runOnVisible(ctx -> {
                            final FragmentManager fm = ctx.getSupportFragmentManager();
                            final DownloadDialog downloadDialog = new DownloadDialog(ctx, result);
                            // dismiss listener to be handled by FragmentManager
                            downloadDialog.show(fm, "downloadDialog");
                        }
                    ), throwable -> runOnVisible(ctx ->
                            ((RouterActivity) ctx).showUnsupportedUrlDialog(currentUrl))));
        }

        private void openAddToPlaylistDialog(final int currentServiceId, final String currentUrl) {
            inFlight(true);
            disposables.add(ExtractorHelper.getStreamInfo(currentServiceId, currentUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(this::pleaseWait)
                    .subscribe(
                            info -> getActivityContext().ifPresent(context ->
                                    PlaylistDialog.createCorrespondingDialog(context,
                                            List.of(new StreamEntity(info)),
                                            playlistDialog -> runOnVisible(ctx -> {
                                                // dismiss listener to be handled by FragmentManager
                                                final FragmentManager fm =
                                                        ctx.getSupportFragmentManager();
                                                playlistDialog.show(fm, "addToPlaylistDialog");
                                            })
                                    )),
                            throwable -> runOnVisible(ctx -> handleError(ctx, new ErrorInfo(
                                    throwable,
                                    UserAction.REQUESTED_STREAM,
                                    "Tried to add " + currentUrl + " to a playlist",
                                    ((RouterActivity) ctx).currentService.getServiceId())
                            ))
                    )
            );
        }
    }

    private void openAddToPlaylistDialog() {
        getPersistFragment().openAddToPlaylistDialog(currentServiceId, currentUrl);
    }

    private void openDownloadDialog() {
        getPersistFragment().openDownloadDialog(currentServiceId, currentUrl);
    }

    private PersistentFragment getPersistFragment() {
        final FragmentManager fm = getSupportFragmentManager();
        PersistentFragment persistFragment =
                (PersistentFragment) fm.findFragmentByTag("PERSIST_FRAGMENT");
        if (persistFragment == null) {
            persistFragment = new PersistentFragment();
            fm.beginTransaction()
                    .add(persistFragment, "PERSIST_FRAGMENT")
                    .commitNow();
        }
        return persistFragment;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (final int i : grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                finish();
                return;
            }
        }
        if (requestCode == PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE) {
            openDownloadDialog();
        }
    }

    private static class AdapterChoiceItem {
        final String description;
        final String key;
        @DrawableRes
        final int icon;

        AdapterChoiceItem(final String key, final String description, final int icon) {
            this.key = key;
            this.description = description;
            this.icon = icon;
        }
    }

    private static class Choice implements Serializable {
        final int serviceId;
        final String url;
        final String playerChoice;
        final LinkType linkType;

        Choice(final int serviceId, final LinkType linkType,
               final String url, final String playerChoice) {
            this.serviceId = serviceId;
            this.linkType = linkType;
            this.url = url;
            this.playerChoice = playerChoice;
        }

        @NonNull
        @Override
        public String toString() {
            return serviceId + ":" + url + " > " + linkType + " ::: " + playerChoice;
        }
    }

    public static class FetcherService extends IntentService {

        public static final String KEY_CHOICE = "key_choice";
        private static final int ID = 456;
        private Disposable fetcher;

        public FetcherService() {
            super(FetcherService.class.getSimpleName());
        }

        @Override
        public void onCreate() {
            super.onCreate();
            startForeground(ID, createNotification().build());
        }

        @Override
        protected void onHandleIntent(@Nullable final Intent intent) {
            if (intent == null) {
                return;
            }

            final Serializable serializable = intent.getSerializableExtra(KEY_CHOICE);
            if (!(serializable instanceof Choice)) {
                return;
            }
            final Choice playerChoice = (Choice) serializable;
            handleChoice(playerChoice);
        }

        public void handleChoice(final Choice choice) {
            Single<? extends Info> single = null;
            UserAction userAction = UserAction.SOMETHING_ELSE;

            switch (choice.linkType) {
                case STREAM:
                    single = ExtractorHelper.getStreamInfo(choice.serviceId, choice.url, false);
                    userAction = UserAction.REQUESTED_STREAM;
                    break;
                case CHANNEL:
                    single = ExtractorHelper.getChannelInfo(choice.serviceId, choice.url, false);
                    userAction = UserAction.REQUESTED_CHANNEL;
                    break;
                case PLAYLIST:
                    single = ExtractorHelper.getPlaylistInfo(choice.serviceId, choice.url, false);
                    userAction = UserAction.REQUESTED_PLAYLIST;
                    break;
            }


            if (single != null) {
                final UserAction finalUserAction = userAction;
                final Consumer<Info> resultHandler = getResultHandler(choice);
                fetcher = single
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(info -> {
                            resultHandler.accept(info);
                            if (fetcher != null) {
                                fetcher.dispose();
                            }
                        }, throwable -> handleError(this, new ErrorInfo(throwable, finalUserAction,
                                choice.url + " opened with " + choice.playerChoice,
                                choice.serviceId)));
            }
        }

        public Consumer<Info> getResultHandler(final Choice choice) {
            return info -> {
                final String videoPlayerKey = getString(R.string.video_player_key);
                final String backgroundPlayerKey = getString(R.string.background_player_key);
                final String popupPlayerKey = getString(R.string.popup_player_key);

                final SharedPreferences preferences = PreferenceManager
                        .getDefaultSharedPreferences(this);
                final boolean isExtVideoEnabled = preferences.getBoolean(
                        getString(R.string.use_external_video_player_key), false);
                final boolean isExtAudioEnabled = preferences.getBoolean(
                        getString(R.string.use_external_audio_player_key), false);

                final PlayQueue playQueue;
                if (info instanceof StreamInfo) {
                    if (choice.playerChoice.equals(backgroundPlayerKey) && isExtAudioEnabled) {
                        NavigationHelper.playOnExternalAudioPlayer(this, (StreamInfo) info);
                        return;
                    } else if (choice.playerChoice.equals(videoPlayerKey) && isExtVideoEnabled) {
                        NavigationHelper.playOnExternalVideoPlayer(this, (StreamInfo) info);
                        return;
                    }
                    playQueue = new SinglePlayQueue((StreamInfo) info);
                } else if (info instanceof ChannelInfo) {
                    playQueue = new ChannelPlayQueue((ChannelInfo) info);
                } else if (info instanceof PlaylistInfo) {
                    playQueue = new PlaylistPlayQueue((PlaylistInfo) info);
                } else {
                    return;
                }

                if (choice.playerChoice.equals(videoPlayerKey)) {
                    NavigationHelper.playOnMainPlayer(this, playQueue, false);
                } else if (choice.playerChoice.equals(backgroundPlayerKey)) {
                    NavigationHelper.playOnBackgroundPlayer(this, playQueue, true);
                } else if (choice.playerChoice.equals(popupPlayerKey)) {
                    NavigationHelper.playOnPopupPlayer(this, playQueue, true);
                }
            };
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            if (fetcher != null) {
                fetcher.dispose();
            }
        }

        private NotificationCompat.Builder createNotification() {
            return new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(
                            getString(R.string.preferred_player_fetcher_notification_title))
                    .setContentText(
                            getString(R.string.preferred_player_fetcher_notification_message));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    private String getUrl(final Intent intent) {
        String foundUrl = null;
        if (intent.getData() != null) {
            // Called from another app
            foundUrl = intent.getData().toString();
        } else if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            // Called from the share menu
            final String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            foundUrl = UrlFinder.firstUrlFromInput(extraText);
        }

        return foundUrl;
    }
}
