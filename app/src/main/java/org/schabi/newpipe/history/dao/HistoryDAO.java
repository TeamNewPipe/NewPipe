package org.schabi.newpipe.history.dao;


import android.support.annotation.NonNull;

public interface HistoryDAO<T> {
    void clearHistory();

    void addHistoryEntry(T historyEntry);

    @NonNull
    T[] loadAllHistoryEntries();

    void removeHistoryEntry(T historyEntry);
}
