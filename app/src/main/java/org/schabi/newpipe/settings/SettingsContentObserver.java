package org.schabi.newpipe.settings;

import android.database.ContentObserver;
import android.os.Handler;

public class SettingsContentObserver extends ContentObserver {
    private OnChangeListener listener;

    public interface OnChangeListener {
        void onSettingsChanged();
    }

    public SettingsContentObserver(Handler handler, OnChangeListener listener) {
        super(handler);
        this.listener = listener;
    }

    @Override
    public boolean deliverSelfNotifications() {
        return super.deliverSelfNotifications();
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        if (listener != null)
            listener.onSettingsChanged();
    }
}