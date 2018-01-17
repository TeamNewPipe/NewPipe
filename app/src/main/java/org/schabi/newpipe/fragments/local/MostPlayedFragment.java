package org.schabi.newpipe.fragments.local;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.info_list.stored.StreamStatisticsInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MostPlayedFragment extends HistoryPlaylistFragment {
    @Override
    protected String getName() {
        return getString(R.string.title_most_played);
    }

    @Override
    protected List<InfoItem> processResult(List<StreamStatisticsEntry> results)  {
        Collections.sort(results, (left, right) ->
                ((Long) right.watchCount).compareTo(left.watchCount));

        List<InfoItem> items = new ArrayList<>(results.size());
        for (final StreamStatisticsEntry stream : results) {
            items.add(stream.toStreamStatisticsInfoItem());
        }
        return items;
    }

    @Override
    protected String getAdditionalDetail(StreamStatisticsInfoItem infoItem) {
        final int watchCount = (int) infoItem.getWatchCount();
        return getResources().getQuantityString(R.plurals.views, watchCount, watchCount);
    }
}
