package org.schabi.newpipe.fragments.local;

import android.text.format.DateFormat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.info_list.stored.StreamStatisticsInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WatchHistoryFragment extends StatisticsPlaylistFragment {
    @Override
    protected String getName() {
        return getString(R.string.title_watch_history);
    }

    @Override
    protected List<InfoItem> processResult(List<StreamStatisticsEntry> results)  {
        Collections.sort(results, (left, right) ->
                right.latestAccessDate.compareTo(left.latestAccessDate));

        List<InfoItem> items = new ArrayList<>(results.size());
        for (final StreamStatisticsEntry stream : results) {
            items.add(stream.toStreamStatisticsInfoItem());
        }
        return items;
    }

    @Override
    protected String getAdditionalDetail(StreamStatisticsInfoItem infoItem) {
        return DateFormat.getLongDateFormat(getContext()).format(infoItem.getLatestAccessDate());
    }
}
