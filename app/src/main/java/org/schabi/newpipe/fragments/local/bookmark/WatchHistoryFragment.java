package org.schabi.newpipe.fragments.local.bookmark;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;

import java.util.Collections;
import java.util.List;

public final class WatchHistoryFragment extends StatisticsPlaylistFragment {
    @Override
    protected String getName() {
        return getString(R.string.title_watch_history);
    }

    @Override
    protected List<StreamStatisticsEntry> processResult(List<StreamStatisticsEntry> results)  {
        Collections.sort(results, (left, right) ->
                right.latestAccessDate.compareTo(left.latestAccessDate));
        return results;
    }
}
