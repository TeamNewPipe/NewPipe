package org.schabi.newpipe.database.history.dao;

import org.schabi.newpipe.database.BasicDAO;

public interface HistoryDAO<T> extends BasicDAO<T> {
    T getLatestEntry();
}
