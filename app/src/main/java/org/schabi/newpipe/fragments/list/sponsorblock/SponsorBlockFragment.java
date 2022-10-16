package org.schabi.newpipe.fragments.list.sponsorblock;

import static org.schabi.newpipe.util.TimeUtils.millisecondsToString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentSponsorBlockBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.util.SponsorBlockCategory;
import org.schabi.newpipe.util.SponsorBlockMode;
import org.schabi.newpipe.util.SponsorBlockSegment;
import org.schabi.newpipe.util.SponsorBlockUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SponsorBlockFragment
        extends Fragment
        implements CompoundButton.OnCheckedChangeListener {
    FragmentSponsorBlockBinding binding;
    private Player currentPlayer;
    private Integer markedStartTime = null;
    private Integer markedEndTime = null;
    private SponsorBlockSegmentListAdapter segmentListAdapter;
    private ArrayList<SponsorBlockSegment> sponsorBlockSegments;
    private Disposable submitSegmentSubscriber;

    public SponsorBlockFragment() {
    }

    public SponsorBlockFragment(final Player player) {
        currentPlayer = player;
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        segmentListAdapter = new SponsorBlockSegmentListAdapter(context);
        segmentListAdapter.setItems(sponsorBlockSegments);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (submitSegmentSubscriber != null) {
            submitSegmentSubscriber.dispose();
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentSponsorBlockBinding.inflate(inflater, container, false);
        binding.sponsorBlockControlsMarkSegmentStart.setOnClickListener(v -> doMark(true));
        binding.sponsorBlockControlsMarkSegmentEnd.setOnClickListener(v -> doMark(false));
        binding.sponsorBlockControlsClearSegment.setOnClickListener(v -> doClear());
        binding.sponsorBlockControlsSubmitSegment.setOnClickListener(v -> doSubmit());
        binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(this);
        binding.channelIsWhitelistedSwitch.setOnCheckedChangeListener(this);
        binding.segmentList.setAdapter(segmentListAdapter);

        if (currentPlayer != null && currentPlayer.getCurrentItem() != null) {
            updateWithPlayer(currentPlayer);
        }

        return binding.getRoot();
    }

    @Override
    public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
        if (buttonView.getId() == R.id.skipping_is_enabled_switch) {
            updatePlayerSetSponsorBlockEnabled(isChecked);
        } else if (buttonView.getId() == R.id.channel_is_whitelisted_switch) {
            updatePlayerSetWhitelistForChannelEnabled(isChecked);
        }
    }

    public void updateWithPlayer(final Player player) {
        currentPlayer = player;

        if (currentPlayer == null) {
            return;
        }

        if (binding == null) {
            return;
        }

        binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(null);
        binding.channelIsWhitelistedSwitch.setOnCheckedChangeListener(null);
        binding.skippingIsEnabledSwitch
                .setChecked(currentPlayer.getSponsorBlockMode() == SponsorBlockMode.ENABLED);
        binding.channelIsWhitelistedSwitch
                .setChecked(currentPlayer.getSponsorBlockMode() == SponsorBlockMode.IGNORE);
        binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(this);
        binding.channelIsWhitelistedSwitch.setOnCheckedChangeListener(this);

        if (binding.channelIsWhitelistedSwitch.isChecked()) {
            binding.skippingIsEnabledSwitch.setEnabled(false);
        }

        if (currentPlayer.getCurrentItem() != null) {
            sponsorBlockSegments = currentPlayer.getCurrentItem().getSponsorBlockSegments();
            if (segmentListAdapter != null) {
                segmentListAdapter.setItems(sponsorBlockSegments);
            }
        }
    }

    private void updatePlayerSetSponsorBlockEnabled(final boolean value) {
        if (currentPlayer == null) {
            return;
        }

        currentPlayer.setSponsorBlockMode(value
                ? SponsorBlockMode.ENABLED
                : SponsorBlockMode.DISABLED);
    }

    private void updatePlayerSetWhitelistForChannelEnabled(final boolean value) {
        if (currentPlayer == null) {
            return;
        }

        final Context context = requireContext();

        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);

        final Set<String> uploaderWhitelist = new HashSet<>(prefs.getStringSet(
                context.getString(R.string.sponsor_block_whitelist_key),
                new HashSet<>()));

        binding.skippingIsEnabledSwitch.setEnabled(!value);

        final String toastText;

        if (value) {
            uploaderWhitelist.add(currentPlayer.getUploaderName());
            currentPlayer.setSponsorBlockMode(SponsorBlockMode.IGNORE);
            toastText = context
                    .getString(R.string
                            .sponsor_block_uploader_added_to_whitelist_toast);
        } else {
            uploaderWhitelist.remove(currentPlayer.getUploaderName());
            currentPlayer.setSponsorBlockMode(binding.skippingIsEnabledSwitch.isChecked()
                    ? SponsorBlockMode.ENABLED
                    : SponsorBlockMode.DISABLED);
            toastText = context
                    .getString(R.string
                            .sponsor_block_uploader_removed_from_whitelist_toast);
        }

        prefs.edit()
                .putStringSet(
                        context.getString(R.string.sponsor_block_whitelist_key),
                        new HashSet<>(uploaderWhitelist))
                .apply();

        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show();
    }

    private void doMark(final boolean isStart) {
        if (currentPlayer == null) {
            return;
        }

        final PlayQueueItem currentItem = currentPlayer.getCurrentItem();
        if (currentItem == null) {
            return;
        }

        final int currentProgress =
                Math.max((int) currentPlayer.getExoPlayer().getCurrentPosition(), 0);
        if (isStart) {
            if (markedEndTime != null && currentProgress > markedEndTime) {
                Toast.makeText(getContext(),
                        getString(R.string.sponsor_block_invalid_start_toast),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            markedStartTime = currentProgress;
        } else {
            if (markedStartTime != null && currentProgress < markedStartTime) {
                Toast.makeText(getContext(),
                        getString(R.string.sponsor_block_invalid_end_toast),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            markedEndTime = currentProgress;
        }

        if (markedStartTime != null) {
            binding.sponsorBlockControlsSegmentStart.setText(
                    millisecondsToString(markedStartTime));
        }

        if (markedEndTime != null) {
            binding.sponsorBlockControlsSegmentEnd.setText(
                    millisecondsToString(markedEndTime));
        }

        if (markedStartTime != null && markedEndTime != null) {
            currentItem.removeSponsorBlockSegment("TEMP");

            final SponsorBlockSegment segment = new SponsorBlockSegment(
                    "TEMP",
                    markedStartTime,
                    markedEndTime,
                    SponsorBlockCategory.PENDING);

            currentItem.addSponsorBlockSegment(segment);

            currentPlayer.requestMarkSeekbar();
            segmentListAdapter.setItems(currentItem.getSponsorBlockSegments());
        }

        final String message = isStart
                ? getString(R.string.sponsor_block_marked_start_toast)
                : getString(R.string.sponsor_block_marked_end_toast);
        Toast.makeText(getContext(),
                message,
                Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("SetTextI18n")
    private void doClear() {
        if (currentPlayer == null) {
            return;
        }

        final PlayQueueItem currentItem = currentPlayer.getCurrentItem();
        if (currentItem == null) {
            return;
        }

        if (markedStartTime == null && markedEndTime == null) {
            return;
        }

        new AlertDialog
                .Builder(requireContext())
                .setMessage(R.string.sponsor_block_clear_marked_segment_prompt)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    currentItem.removeSponsorBlockSegment("TEMP");

                    currentPlayer.requestMarkSeekbar();
                    segmentListAdapter.setItems(currentItem.getSponsorBlockSegments());

                    markedStartTime = null;
                    markedEndTime = null;

                    binding.sponsorBlockControlsSegmentStart.setText("00:00:00");
                    binding.sponsorBlockControlsSegmentEnd.setText("00:00:00");

                    dialog.dismiss();
                })
                .show();
    }

    @SuppressLint("SetTextI18n")
    private void doSubmit() {
        final Optional<StreamInfo> currentStreamInfo = currentPlayer.getCurrentStreamInfo();
        if (!currentStreamInfo.isPresent()) {
            return;
        }

        final Context context = requireContext();

        final PlayQueueItem currentPlayQueueItem = currentPlayer.getCurrentItem();
        if (currentPlayQueueItem == null) {
            return;
        }

        if (markedStartTime == null || markedEndTime == null) {
            Toast.makeText(context,
                    getString(R.string.sponsor_block_missing_times_toast),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.sponsor_block_select_a_category);
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
        builder.setItems(new String[]{
                SponsorBlockCategory.SPONSOR.getFriendlyName(context),
                SponsorBlockCategory.INTRO.getFriendlyName(context),
                SponsorBlockCategory.OUTRO.getFriendlyName(context),
                SponsorBlockCategory.INTERACTION.getFriendlyName(context),
                SponsorBlockCategory.SELF_PROMO.getFriendlyName(context),
                SponsorBlockCategory.NON_MUSIC.getFriendlyName(context),
                SponsorBlockCategory.PREVIEW.getFriendlyName(context),
                SponsorBlockCategory.FILLER.getFriendlyName(context)
        }, (dialog, which) -> {
            final SponsorBlockCategory category = SponsorBlockCategory.values()[which];
            final SponsorBlockSegment newSegment =
                    new SponsorBlockSegment("", markedStartTime, markedEndTime, category);
            submitSegmentSubscriber =
                    Single.fromCallable(() ->
                                    SponsorBlockUtils.submitSponsorBlockSegment(
                                            context,
                                            currentStreamInfo.get(),
                                            newSegment))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(response -> {
                                if (response.responseCode() != 200) {
                                    String message = response.responseMessage();
                                    if (message.equals("")) {
                                        message = "Error " + response.responseCode();
                                    }
                                    Toast.makeText(context,
                                            message,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                currentPlayQueueItem.removeSponsorBlockSegment("TEMP");
                                currentPlayQueueItem.addSponsorBlockSegment(newSegment);

                                currentPlayer.requestMarkSeekbar();
                                segmentListAdapter.setItems(
                                        currentPlayQueueItem.getSponsorBlockSegments());

                                markedStartTime = null;
                                markedEndTime = null;

                                binding.sponsorBlockControlsSegmentStart.setText("00:00:00");
                                binding.sponsorBlockControlsSegmentEnd.setText("00:00:00");

                                new AlertDialog
                                        .Builder(context)
                                        .setMessage(R.string.sponsor_block_upload_success_message)
                                        .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                                        .show();
                            }, throwable -> {
                                if (throwable instanceof NullPointerException) {
                                    return;
                                }
                                ErrorUtil.showSnackbar(context,
                                        new ErrorInfo(throwable, UserAction.USER_REPORT,
                                                "Submit SponsorBlock segment"));
                            });

            dialog.dismiss();
        });
        builder.show();
    }
}
