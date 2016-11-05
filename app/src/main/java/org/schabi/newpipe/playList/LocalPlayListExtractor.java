package org.schabi.newpipe.playList;

import android.content.Context;

import org.schabi.newpipe.extractor.AbstractStreamInfo;
import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfoCollector;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfoExtractor;

import java.io.IOException;

public class LocalPlayListExtractor extends ChannelExtractor {

    private final PlayListDataSource dataSource;
    private final StreamPreviewInfo firstItem;
    private final int page;

    public LocalPlayListExtractor(final Context context, UrlIdHandler urlIdHandlerInstance, final int playlistId, int page) throws IOException, ExtractionException {
        super(urlIdHandlerInstance, "", page, playlistId);
        dataSource = new PlayListDataSource(context);
        firstItem = dataSource.getNextEntriesForItems(getPlayListId(), 0);
        this.page = page;
    }

    private int getPlayListId() {
        return getServiceId();
    }

    @Override
    public String getChannelName() throws ParsingException {
        return dataSource.getPlaylistName(getPlayListId());
    }

    @Override
    public String getAvatarUrl() throws ParsingException {
        try {
            return "https://i.ytimg.com/vi/" + firstItem.id + "/hqdefault.jpg";
        } catch (final Exception e) {
            throw new ParsingException(e.getMessage(), e);
        }
    }

    @Override
    public String getBannerUrl() throws ParsingException {
        try {
            return "https://i.ytimg.com/vi/" + firstItem.id + "/maxresdefault.jpg";
        } catch (final Exception e) {
            throw new ParsingException(e.getMessage(), e);
        }
    }

    @Override
    public String getFeedUrl() throws ParsingException {
        return "";
    }

    @Override
    public StreamPreviewInfoCollector getStreams() throws ParsingException {
        final StreamPreviewInfoCollector collector = getStreamPreviewInfoCollector();
        final PlayList playList = dataSource.getPlayListWithEntries(getPlayListId(), page);
        for(final StreamPreviewInfo streamPreviewInfo : playList.getEntries()) {
            collector.commit(new StreamPreviewInfoExtractor() {
                @Override
                public AbstractStreamInfo.StreamType getStreamType() throws ParsingException {
                    return AbstractStreamInfo.StreamType.VIDEO_STREAM;
                }

                @Override
                public String getWebPageUrl() throws ParsingException {
                    return streamPreviewInfo.webpage_url;
                }

                @Override
                public String getTitle() throws ParsingException {
                    return streamPreviewInfo.title;
                }

                @Override
                public int getDuration() throws ParsingException {
                    return streamPreviewInfo.duration;
                }

                @Override
                public String getUploader() throws ParsingException {
                    return streamPreviewInfo.uploader;
                }

                @Override
                public String getUploadDate() throws ParsingException {
                    return streamPreviewInfo.upload_date;
                }

                @Override
                public long getViewCount() throws ParsingException {
                    return streamPreviewInfo.view_count;
                }

                @Override
                public String getThumbnailUrl() throws ParsingException {
                    return streamPreviewInfo.thumbnail_url;
                }
            });
        }
        return collector;
    }

    @Override
    public boolean hasNextPage() throws ParsingException {
        return dataSource.hasNextPage(getPlayListId(), page);
    }
}
