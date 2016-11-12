package org.schabi.newpipe.playList.extractor;

import android.content.Context;

import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.playList.PlayListDataSource;

import java.io.IOException;

public class QueueExtractor extends LocalPlayListExtractor {

    public QueueExtractor(final Context context, final UrlIdHandler urlIdHandlerInstance, int page) throws IOException, ExtractionException {
        super(context,urlIdHandlerInstance, PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID, page);
    }

    @Override
    public String getChannelName() throws ParsingException {
        return "Queue";
    }

}
