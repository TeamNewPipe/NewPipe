package org.schabi.newpipe.fragments.playlist;

import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamHistoryEntity;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.Date;
import java.util.List;

import io.reactivex.MaybeObserver;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
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

    public int onChanged(final StreamInfoItem infoItem) {
        // Only existing streams are updated
        return streamTable.update(new StreamEntity(infoItem));
    }

    public Single<Long> onViewed(final StreamInfo info) {
        return Single.fromCallable(() -> database.runInTransaction(() -> {
            final long streamId = streamTable.upsert(new StreamEntity(info));
            return historyTable.insert(new StreamHistoryEntity(streamId, new Date()));
        })).subscribeOn(Schedulers.io());
    }

    public int removeHistory(final long streamId) {
        return historyTable.deleteHistory(streamId);
    }

    public void removeRecord() {
        historyTable.getStatistics().firstElement().subscribe(
                new MaybeObserver<List<StreamStatisticsEntry>>() {

                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onSuccess(List<StreamStatisticsEntry> streamStatisticsEntries) {
                        hashCode();
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                }
        );
    }
}
