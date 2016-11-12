package org.schabi.newpipe.playList.extractor;

import android.content.Context;

import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.playList.PlayListDataSource;

import java.io.IOException;

public class HistoricExtractor extends LocalPlayListExtractor {
    public HistoricExtractor(Context context, UrlIdHandler urlIdHandlerInstance, int page) throws IOException, ExtractionException {
        super(context, urlIdHandlerInstance, PlayListDataSource.PLAYLIST_SYSTEM.HISTORIC_ID, page);
    }
}
