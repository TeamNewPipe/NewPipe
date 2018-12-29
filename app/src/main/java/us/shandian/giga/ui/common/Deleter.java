package us.shandian.giga.ui.common;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.view.View;

import org.schabi.newpipe.R;

import java.util.ArrayList;

import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManager.MissionIterator;
import us.shandian.giga.ui.adapter.MissionAdapter;

public class Deleter {
    private static final int TIMEOUT = 5000;// ms
    private static final int DELAY = 350;// ms
    private static final int DELAY_RESUME = 400;// ms
    private static final String BUNDLE_NAMES = "us.shandian.giga.ui.common.deleter.names";
    private static final String BUNDLE_LOCATIONS = "us.shandian.giga.ui.common.deleter.locations";

    private Snackbar snackbar;
    private ArrayList<Mission> items;
    private boolean running = true;

    private Context mContext;
    private MissionAdapter mAdapter;
    private DownloadManager mDownloadManager;
    private MissionIterator mIterator;
    private Handler mHandler;
    private View mView;

    private final Runnable rShow;
    private final Runnable rNext;
    private final Runnable rCommit;

    public Deleter(Bundle b, View v, Context c, MissionAdapter a, DownloadManager d, MissionIterator i, Handler h) {
        mView = v;
        mContext = c;
        mAdapter = a;
        mDownloadManager = d;
        mIterator = i;
        mHandler = h;

        // use variables to know the reference of the lambdas
        rShow = this::show;
        rNext = this::next;
        rCommit = this::commit;

        items = new ArrayList<>(2);

        if (b != null) {
            String[] names = b.getStringArray(BUNDLE_NAMES);
            String[] locations = b.getStringArray(BUNDLE_LOCATIONS);

            if (names == null || locations == null) return;
            if (names.length < 1 || locations.length < 1) return;
            if (names.length != locations.length) return;

            items.ensureCapacity(names.length);

            for (int j = 0; j < locations.length; j++) {
                Mission mission = mDownloadManager.getAnyMission(locations[j], names[j]);
                if (mission == null) continue;

                items.add(mission);
                mIterator.hide(mission);
            }

            if (items.size() > 0) resume();
        }
    }

    public void append(Mission item) {
        mIterator.hide(item);
        items.add(0, item);

        show();
    }

    private void forget() {
        mIterator.unHide(items.remove(0));
        mAdapter.applyChanges();

        show();
    }

    private void show() {
        if (items.size() < 1) return;

        pause();
        running = true;

        mHandler.postDelayed(rNext, DELAY);
    }

    private void next() {
        if (items.size() < 1) return;

        String msg = mContext.getString(R.string.file_deleted).concat(":\n").concat(items.get(0).name);

        snackbar = Snackbar.make(mView, msg, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.undo, s -> forget());
        snackbar.setActionTextColor(Color.YELLOW);
        snackbar.show();

        mHandler.postDelayed(rCommit, TIMEOUT);
    }

    private void commit() {
        if (items.size() < 1) return;

        while (items.size() > 0) {
            Mission mission = items.remove(0);
            if (mission.deleted) continue;

            mIterator.unHide(mission);
            mDownloadManager.deleteMission(mission);

            if (mission instanceof FinishedMission) {
                mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(mission.getDownloadedFile())));
            }
            break;
        }

        if (items.size() < 1) {
            pause();
            return;
        }

        show();
    }

    private void pause() {
        running = false;
        mHandler.removeCallbacks(rNext);
        mHandler.removeCallbacks(rShow);
        mHandler.removeCallbacks(rCommit);
        if (snackbar != null) snackbar.dismiss();
    }

    public void resume() {
        if (running) return;
        mHandler.postDelayed(rShow, DELAY_RESUME);
    }

    public void dispose(Bundle bundle) {
        if (items.size() < 1) return;

        pause();

        if (bundle == null) {
            for (Mission mission : items) mDownloadManager.deleteMission(mission);
            items = null;
            return;
        }

        String[] names = new String[items.size()];
        String[] locations = new String[items.size()];

        for (int i = 0; i < items.size(); i++) {
            Mission mission = items.get(i);
            names[i] = mission.name;
            locations[i] = mission.location;
        }

        bundle.putStringArray(BUNDLE_NAMES, names);
        bundle.putStringArray(BUNDLE_LOCATIONS, locations);
    }
}
