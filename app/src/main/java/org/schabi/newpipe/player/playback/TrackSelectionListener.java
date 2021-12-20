package org.schabi.newpipe.player.playback;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import static org.schabi.newpipe.player.Player.getResolutionStringFromFormat;

import android.content.Context;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;

import org.schabi.newpipe.R;

/**
 * A listener class to allow setting a specified quality for livestreams when clicking an item on
 * the quality selector popup menu in video players.
 */
public class TrackSelectionListener implements PopupMenu.OnMenuItemClickListener {
    private final Context context;
    private final CustomTrackSelector trackSelector;
    private final int rendererIndex;
    private final TextView qualityTextView;
    private final TrackGroupArray rendererTrackGroups;

    /**
     * Create a {@link TrackSelectionListener}.
     *
     * @param context         the player context
     * @param trackSelector   the {@link CustomTrackSelector} used by the player, which couldn't be
     *                        null
     * @param rendererIndex   the index of the video renderer
     * @param qualityTextView the {@link TextView quality text view} on which the playing quality
     *                        will be updated after a quality item is clicked
     * @throws NullPointerException if trackSelector is null
     */
    public TrackSelectionListener(@NonNull final Context context,
                                  @NonNull final CustomTrackSelector trackSelector,
                                  final int rendererIndex,
                                  @NonNull final TextView qualityTextView) {
        this.context = context;
        this.trackSelector = trackSelector;
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = checkNotNull(
                trackSelector.getCurrentMappedTrackInfo());
        this.rendererIndex = rendererIndex;
        this.qualityTextView = qualityTextView;

        rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
    }

    @Override
    public boolean onMenuItemClick(@NonNull final MenuItem item) {
        final DefaultTrackSelector.Parameters selectionParameters = trackSelector.getParameters();
        final boolean isDisabled = selectionParameters.getRendererDisabled(rendererIndex);
        final DefaultTrackSelector.ParametersBuilder builder = selectionParameters.buildUpon()
                .clearSelectionOverrides(rendererIndex)
                .setRendererDisabled(rendererIndex, isDisabled);
        // The auto string
        final String qualityTextViewText = qualityTextView.getText().toString();

        final int itemId = item.getItemId();
        if (itemId == 0) {
            // 0 is the index of the Auto item of the quality selector
            trackSelector.setParameters(builder);

            // If the quality text view text contains already the auto string, it means the user
            // already selected the auto option and pressed again the auto option from the quality
            // selector, so no need to update the text of the quality text view (if we update it,
            // we will get something like Auto (Auto (1080p))).
            if (!qualityTextViewText.contains(context.getString(R.string.auto_quality))) {
                qualityTextView.setText(context.getString(R.string.auto_quality_selected,
                        context.getString(R.string.auto_quality),
                        qualityTextViewText));
            }
        } else {
            // A resolution string
            TrackGroup videoTrackGroup = null;
            int lengthOfVideoTrackGroupWhichMatchMenuItem = 0;
            for (int i = 0; i < rendererTrackGroups.length; i++) {
                final TrackGroup trackGroup = rendererTrackGroups.get(i);
                final int rendererTrackGroupLength = trackGroup.length;
                if (rendererTrackGroupLength >= itemId) {
                    lengthOfVideoTrackGroupWhichMatchMenuItem = rendererTrackGroupLength;
                    videoTrackGroup = trackGroup;
                }
            }

            final int qualityId = lengthOfVideoTrackGroupWhichMatchMenuItem > 0
                    ? lengthOfVideoTrackGroupWhichMatchMenuItem - itemId
                    : itemId - 1;
            final DefaultTrackSelector.SelectionOverride selectionOverride =
                    new DefaultTrackSelector.SelectionOverride(
                            rendererIndex, qualityId);
            builder.setSelectionOverride(rendererIndex, rendererTrackGroups,
                    selectionOverride);

            // The quality needs only to be set here when a user keeps the quality selected
            // automatically by ExoPlayer but selects it manually with the quality selector (the
            // quality string will be changed in other cases with the analytics listener set in the
            // player)
            if (qualityTextViewText.contains(context.getString(R.string.auto_quality))) {
                final Format currentPlayingFormat = videoTrackGroup != null
                        ? videoTrackGroup.getFormat(qualityId)
                        // Fallback to the first track group
                        : rendererTrackGroups.get(0).getFormat(qualityId);
                qualityTextView.setText(getResolutionStringFromFormat(context,
                        currentPlayingFormat));
            }

            trackSelector.setParameters(builder);
        }

        return true;
    }
}
