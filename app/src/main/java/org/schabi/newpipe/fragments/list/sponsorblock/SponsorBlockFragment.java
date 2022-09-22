package org.schabi.newpipe.fragments.list.sponsorblock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentSponsorBlockBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.util.SponsorBlockMode;
import org.schabi.newpipe.util.SponsorBlockSegment;

import java.util.HashSet;
import java.util.Set;

public class SponsorBlockFragment
        extends Fragment
        implements CompoundButton.OnCheckedChangeListener {
    FragmentSponsorBlockBinding binding;
    private Player currentPlayer;
    private PlayQueue currentPlayQueue;
    private SponsorBlockSegmentListAdapter segmentListAdapter;
    private SponsorBlockSegment[] sponsorBlockSegments;

    public SponsorBlockFragment() {
    }

    public SponsorBlockFragment(final Player player, final PlayQueue playQueue) {
        currentPlayer = player;
        currentPlayQueue = playQueue;
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        segmentListAdapter = new SponsorBlockSegmentListAdapter(context);
        segmentListAdapter.setItems(sponsorBlockSegments);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentSponsorBlockBinding.inflate(inflater, container, false);
        binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(this);
        binding.channelIsWhitelistedSwitch.setOnCheckedChangeListener(this);
        binding.segmentList.setAdapter(segmentListAdapter);

        if (currentPlayer != null && currentPlayQueue != null) {
            update(currentPlayer, currentPlayQueue);
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

    public void update(final Player player, final PlayQueue playQueue) {
        currentPlayer = player;
        currentPlayQueue = playQueue;

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

        if (currentPlayQueue != null) {
            final PlayQueueItem item = currentPlayQueue.getItem();
            sponsorBlockSegments = item == null
                    ? null
                    : item.getSponsorBlockSegments();

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
        final Context context = getContext();

        if (context == null) {
            return;
        }

        if (currentPlayer == null) {
            return;
        }

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
}
