package org.schabi.newpipe.player;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.local.history.HistoryRecordManager;

import java.util.List;

import io.reactivex.schedulers.Schedulers;

public class HistoryHelper extends Service {
    private Context context;
    private HistoryRecordManager History;

    private Subscription databaseSubscription;
    private String[] returnarr;

    public HistoryHelper(Context inputcontext){context= inputcontext;}

    public String[] onClose(){
        History = new HistoryRecordManager(context);
        History.getStreamStatistics().observeOn(Schedulers.trampoline()).subscribe(getHistoryObserver());

        try{ Thread.sleep(200); } catch( InterruptedException e){ }

        Log.i("Iminpain", Boolean.toString(returnarr == null));

        return returnarr;
    }
    private Subscriber<List<StreamStatisticsEntry>> getHistoryObserver() {
        return new Subscriber<List<StreamStatisticsEntry>>() {
            @Override
            public void onSubscribe(Subscription s) {

                if (databaseSubscription != null) databaseSubscription.cancel();
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(List<StreamStatisticsEntry> streams) {
                handleResult(streams);
                if (databaseSubscription != null) databaseSubscription.request(1);
            }
            @Override
            public void onError(Throwable exception) {
            }
            @Override
            public void onComplete() {
            }
        };
    }

    public void handleResult(@NonNull List<StreamStatisticsEntry> result) {
        returnarr = new String[result.size()];

        for(int i = 0; i < result.size(); i ++){
            Log.i("Iminpain", result.get(i).url);
            returnarr[i] = result.get(i).url;
        }


    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
