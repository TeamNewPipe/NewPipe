package org.schabi.newpipe.fragments.local;

import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamHistoryEntity;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.Date;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class StreamRecordManager {

    private final AppDatabase database;
    private final StreamDAO streamTable;
    private final StreamHistoryDAO historyTable;

    public StreamRecordManager(final AppDatabase db) {
        database = db;
        streamTable = db.streamDAO();
        historyTable = db.streamHistoryDAO();
    }

    public Single<Long> onViewed(final StreamInfo info) {
        return Single.fromCallable(() -> database.runInTransaction(() -> {
            final long streamId = streamTable.upsert(new StreamEntity(info));
            return historyTable.insert(new StreamHistoryEntity(streamId, new Date()));
        })).subscribeOn(Schedulers.io());
    }

    public int removeHistory(final long streamId) {
        return historyTable.deleteStreamHistory(streamId);
    }

    public Flowable<List<StreamStatisticsEntry>> getStatistics() {
        return historyTable.getStatistics();
    }
}
