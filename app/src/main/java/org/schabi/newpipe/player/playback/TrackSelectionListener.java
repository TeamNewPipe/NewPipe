package org.schabi.newpipe.player.playback;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.view.MenuItem;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;


public class TrackSelectionListener implements PopupMenu.OnMenuItemClickListener {
    private final String autoString;
    private final CustomTrackSelector trackSelector;
    private final int rendererIndex;
    private final TrackGroupArray rendererTrackGroups;

    public TrackSelectionListener(@NonNull final CustomTrackSelector trackSelector,
                                  @NonNull final String autoString,
                                  final int rendererIndex) {
        this.trackSelector = trackSelector;
        this.autoString = autoString;
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = checkNotNull(trackSelector
                .getCurrentMappedTrackInfo());
        this.rendererIndex = rendererIndex;

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

        if (!item.getTitle().equals(autoString)) {
            final DefaultTrackSelector.SelectionOverride selectionOverride =
                    new DefaultTrackSelector.SelectionOverride(
                            rendererIndex, item.getItemId() - 1);
            builder.setSelectionOverride(rendererIndex, rendererTrackGroups,
                    selectionOverride);
        }

        trackSelector.setParameters(builder);
        return true;
    }
}
