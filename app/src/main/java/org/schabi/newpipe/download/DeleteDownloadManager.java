package org.schabi.newpipe.download;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.view.View;

import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.DownloadMission;

public class DeleteDownloadManager {

    private static final String KEY_STATE = "delete_manager_state";

    private final View mView;
    private final HashSet<String> mPendingMap;
    private final List<Disposable> mDisposableList;
    private DownloadManager mDownloadManager;
    private final PublishSubject<DownloadMission> publishSubject = PublishSubject.create();

    DeleteDownloadManager(Activity activity) {
        mPendingMap = new HashSet<>();
        mDisposableList = new ArrayList<>();
        mView = activity.findViewById(android.R.id.content);
    }

    public Observable<DownloadMission> getUndoObservable() {
        return publishSubject;
    }

    public boolean contains(@NonNull DownloadMission mission) {
        return mPendingMap.contains(mission.url);
    }

    public void add(@NonNull DownloadMission mission) {
        mPendingMap.add(mission.url);

        if (mPendingMap.size() == 1) {
            showUndoDeleteSnackbar(mission);
        }
    }

    public void setDownloadManager(@NonNull DownloadManager downloadManager) {
        mDownloadManager = downloadManager;

        if (mPendingMap.size() < 1) return;

        showUndoDeleteSnackbar();
    }

    public void restoreState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        List<String> list = savedInstanceState.getStringArrayList(KEY_STATE);
        if (list != null) {
            mPendingMap.addAll(list);
        }
    }

    public void saveState(@Nullable Bundle outState) {
        if (outState == null) return;

        for (Disposable disposable : mDisposableList) {
            disposable.dispose();
        }

        outState.putStringArrayList(KEY_STATE, new ArrayList<>(mPendingMap));
    }

    private void showUndoDeleteSnackbar() {
        if (mPendingMap.size() < 1) return;

        String url = mPendingMap.iterator().next();

        for (int i = 0; i < mDownloadManager.getCount(); i++) {
            DownloadMission mission = mDownloadManager.getMission(i);
            if (url.equals(mission.url)) {
                showUndoDeleteSnackbar(mission);
                break;
            }
        }
    }

    private void showUndoDeleteSnackbar(@NonNull DownloadMission mission) {
        final Snackbar snackbar = Snackbar.make(mView, mission.name, Snackbar.LENGTH_INDEFINITE);
        final Disposable disposable = Observable.timer(3, TimeUnit.SECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(l -> snackbar.dismiss());

        mDisposableList.add(disposable);

        snackbar.setAction(R.string.undo, v -> {
            mPendingMap.remove(mission.url);
            publishSubject.onNext(mission);
            disposable.dispose();
            snackbar.dismiss();
        });

        snackbar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (!disposable.isDisposed()) {
                    Completable.fromAction(() -> deletePending(mission))
                            .subscribeOn(Schedulers.io())
                            .subscribe();
                }
                mPendingMap.remove(mission.url);
                snackbar.removeCallback(this);
                mDisposableList.remove(disposable);
                showUndoDeleteSnackbar();
            }
        });

        snackbar.show();
    }

    public void deletePending() {
        if (mPendingMap.size() < 1) return;

        HashSet<Integer> idSet = new HashSet<>();
        for (int i = 0; i < mDownloadManager.getCount(); i++) {
            if (contains(mDownloadManager.getMission(i))) {
                idSet.add(i);
            }
        }

        for (Integer id : idSet) {
            mDownloadManager.deleteMission(id);
        }

        mPendingMap.clear();
    }

    private void deletePending(@NonNull DownloadMission mission) {
        for (int i = 0; i < mDownloadManager.getCount(); i++) {
            if (mission.url.equals(mDownloadManager.getMission(i).url)) {
                mDownloadManager.deleteMission(i);
                break;
            }
        }
    }
}
