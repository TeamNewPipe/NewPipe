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

public class TrackSelectionListener implements PopupMenu.OnMenuItemClickListener {
    private final Context context;
    private final CustomTrackSelector trackSelector;
    private final int rendererIndex;
    private final TextView qualityTextView;
    private final TrackGroupArray rendererTrackGroups;

    public TrackSelectionListener(@NonNull final Context context,
                                  @NonNull final CustomTrackSelector trackSelector,
                                  final int rendererIndex,
                                  @NonNull final TextView qualityTextView) {
        this.context = context;
        this.trackSelector = trackSelector;
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = checkNotNull(trackSelector
                .getCurrentMappedTrackInfo());
        this.rendererIndex = rendererIndex;
        this.qualityTextView = qualityTextView;

        rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
    }

    @Override
    public boolean onMenuItemClick(@NonNull final MenuItem item) {
        final DefaultTrackSelector.Parameters selectionParameters =
                trackSelector.getParameters();
        final boolean isDisabled = selectionParameters.getRendererDisabled(rendererIndex);
        final DefaultTrackSelector.ParametersBuilder builder =
                selectionParameters.buildUpon()
                        .clearSelectionOverrides(rendererIndex)
                        .setRendererDisabled(rendererIndex, isDisabled);

        if (item.getItemId() != 0) {
            final TrackGroup videoTrackGroup = rendererTrackGroups.get(0);

            final int qualityId = videoTrackGroup.length - item.getItemId();
            final DefaultTrackSelector.SelectionOverride selectionOverride =
                    new DefaultTrackSelector.SelectionOverride(
                            rendererIndex, qualityId);
            builder.setSelectionOverride(rendererIndex, rendererTrackGroups,
                    selectionOverride);

            final Format currentPlayingFormat = videoTrackGroup.getFormat(qualityId);
            qualityTextView.setText(getResolutionStringFromFormat(currentPlayingFormat));
        } else {
            final String qualityTextViewText = qualityTextView.getText().toString();
            if (!qualityTextViewText.contains(context.getString(R.string.auto_quality))) {
                qualityTextView.setText(context.getString(R.string.auto_quality_selected,
                        context.getString(R.string.auto_quality),
                        qualityTextViewText));
            }
        }

        trackSelector.setParameters(builder);
        return true;
    }
}
