package org.schabi.newpipe.database;

import org.schabi.newpipe.database.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistRemoteDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamStateDAO;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;

public interface Database {
    SubscriptionDAO subscriptionDAO();

    SearchHistoryDAO searchHistoryDAO();

    StreamDAO streamDAO();

    StreamHistoryDAO streamHistoryDAO();

    StreamStateDAO streamStateDAO();

    PlaylistDAO playlistDAO();

    PlaylistStreamDAO playlistStreamDAO();

    PlaylistRemoteDAO playlistRemoteDAO();
}
