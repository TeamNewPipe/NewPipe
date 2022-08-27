package org.schabi.newpipe.fragments.detail;

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
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentSponsorBlockBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerListener;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.util.SponsorBlockMode;
import org.schabi.newpipe.util.VideoSegment;

import java.util.HashSet;
import java.util.Set;

public class SponsorBlockFragment extends Fragment implements PlayerListener {
    FragmentSponsorBlockBinding binding;
    private Player currentPlayer;
    private SponsorBlockMode currentSponsorBlockMode;

    // TODO: use when ready
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private VideoSegment[] currentVideoSegments;

    public SponsorBlockFragment() {
        // required
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentSponsorBlockBinding.inflate(inflater, container, false);

        binding.skippingIsEnabledSwitch
                .setChecked(currentSponsorBlockMode == SponsorBlockMode.ENABLED);
        binding.skippingIsEnabledSwitch
                .setOnCheckedChangeListener((compoundButton, b) ->
                        updatePlayerSetSponsorBlockEnabled(b));

        binding.channelIsWhitelistedSwitch
                .setChecked(currentSponsorBlockMode == SponsorBlockMode.IGNORE);
        binding.channelIsWhitelistedSwitch
                .setOnCheckedChangeListener(((compoundButton, b) ->
                        updatePlayerSetWhitelistForChannelEnabled(b)));

        return binding.getRoot();
    }

    public void setPlayer(final Player player) {
        this.currentPlayer = player;
        this.currentPlayer.setPlayerListener(this);
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

    @Override
    public void onPlayerMetadataChanged(final Player player) {
        currentSponsorBlockMode = player.getSponsorBlockMode();

        if (binding == null) {
            return;
        }

        binding.skippingIsEnabledSwitch
                .setChecked(currentSponsorBlockMode == SponsorBlockMode.ENABLED);
        binding.channelIsWhitelistedSwitch
                .setChecked(currentSponsorBlockMode == SponsorBlockMode.IGNORE);
    }

    @Override
    public void onPlayQueueItemChanged(final PlayQueueItem item) {
        currentVideoSegments = item == null
                ? null
                : item.getVideoSegments();
    }
}
