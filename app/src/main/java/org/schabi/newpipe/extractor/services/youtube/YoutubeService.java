package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeBulletCommentsExtractor;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeBulletCommentsLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousExtractors;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousStreamExtractor;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousCommentsExtractor;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousPostCommentsExtractor;

import static java.util.Arrays.asList;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.*;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeChannelExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeChannelTabExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeCommentsExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePostCommentsExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeDiagnosticStreamExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeFeedExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMixPlaylistExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMusicSearchExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePlaylistExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeSearchExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeSubscriptionExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeSuggestionExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeTrendingExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeLiveExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeShortsExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeTrendingGamingVideosExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeTrendingMoviesAndShowsTrailersExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeTrendingMusicExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeTrendingPodcastsEpisodesExtractor;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeCommentsLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePostCommentsTarget;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeLiveLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePlaylistLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeShortsLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingGamingVideosLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingMoviesAndShowsTrailersLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingMusicLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingPodcastsEpisodesLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import javax.annotation.Nullable;
import java.util.List;

/*
 * Created by Christian Schabesberger on 23.08.15.
 *
 * Copyright (C) Christian Schabesberger 2018 <chris.schabesberger@mailbox.org>
 * YoutubeService.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class YoutubeService extends StreamingService {
    public WatchDataCache watchDataCache = new WatchDataCache();

    public YoutubeService(final int id) {
        super(id, "YouTube", asList(AUDIO, VIDEO, LIVE, COMMENTS, BULLET_COMMENTS, SPONSORBLOCK));
    }

    @Override
    public String getBaseUrl() {
        return "https://youtube.com";
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return YoutubeStreamLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return YoutubeChannelLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return YoutubeChannelTabLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return YoutubePlaylistLinkHandlerFactory.getInstance();
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return YoutubeSearchQueryHandlerFactory.getInstance();
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler) {
        if (InvidiousBackend.isEnabled()) {
            return new InvidiousStreamExtractor(this, linkHandler);
        }
        return new YoutubeDiagnosticStreamExtractor(this, linkHandler, watchDataCache);
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler linkHandler) {
        if (InvidiousBackend.isEnabled()) {
            return new InvidiousExtractors.Channel(this, linkHandler);
        }
        return new YoutubeChannelExtractor(this, linkHandler);
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler linkHandler) {
        if (InvidiousBackend.isEnabled()) {
            return new InvidiousExtractors.ChannelTab(this, linkHandler);
        }
        return new YoutubeChannelTabExtractor(this, linkHandler);
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler linkHandler) {
        if (InvidiousBackend.isEnabled()) {
            return new InvidiousExtractors.Playlist(this, linkHandler);
        }
        if (YoutubeParsingHelper.isYoutubeMixId(linkHandler.getId())) {
            return new YoutubeMixPlaylistExtractor(this, linkHandler);
        } else {
            return new YoutubePlaylistExtractor(this, linkHandler);
        }
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler query) {
        if (InvidiousBackend.isEnabled()) {
            return new InvidiousExtractors.Search(this, query);
        }
        final List<FilterItem> contentFilters = query.getContentFilters();

        if (contentFilters.isEmpty()) {
            // something is odd
            throw new RuntimeException("contentFilters is empty. WHY?");
        }

        final FilterItem filterItem = contentFilters.get(0);
        if (filterItem instanceof YoutubeFilters.MusicYoutubeContentFilterItem) {
            return new YoutubeMusicSearchExtractor(this, query);
        } else {
            return new YoutubeSearchExtractor(this, query);
        }
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        if (InvidiousBackend.isEnabled()) {
            return new InvidiousExtractors.Suggestions(this);
        }
        return new YoutubeSuggestionExtractor(this);
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {
        final KioskList list = new KioskList(this);

        final ListLinkHandlerFactory liveLHF = YoutubeLiveLinkHandlerFactory.INSTANCE;
        final ListLinkHandlerFactory shortsLHF = YoutubeShortsLinkHandlerFactory.INSTANCE;
        final ListLinkHandlerFactory podcastsLHF =
                YoutubeTrendingPodcastsEpisodesLinkHandlerFactory.INSTANCE;
        final ListLinkHandlerFactory gamingLHF =
                YoutubeTrendingGamingVideosLinkHandlerFactory.INSTANCE;
        final ListLinkHandlerFactory moviesAndShowsLHF =
                YoutubeTrendingMoviesAndShowsTrailersLinkHandlerFactory.INSTANCE;
        final ListLinkHandlerFactory musicLHF = YoutubeTrendingMusicLinkHandlerFactory.INSTANCE;
        final ListLinkHandlerFactory trendingLHF = new YoutubeTrendingLinkHandlerFactory();

        try {
            list.addKioskEntry(
                    (streamingService, url, id) -> InvidiousBackend.isEnabled()
                            ? new InvidiousExtractors.Kiosk(this, liveLHF.fromUrl(url), id)
                            : new YoutubeLiveExtractor(
                            YoutubeService.this,
                            liveLHF.fromUrl(url),
                            id),
                    liveLHF,
                    YoutubeLiveLinkHandlerFactory.KIOSK_ID
            );
            list.addKioskEntry(
                    (streamingService, url, id) -> InvidiousBackend.isEnabled()
                            ? new InvidiousExtractors.Kiosk(this, shortsLHF.fromUrl(url), id)
                            : new YoutubeShortsExtractor(
                            YoutubeService.this,
                            shortsLHF.fromUrl(url),
                            id),
                    shortsLHF,
                    YoutubeShortsLinkHandlerFactory.KIOSK_ID
            );
            list.addKioskEntry(
                    (streamingService, url, id) -> InvidiousBackend.isEnabled()
                            ? new InvidiousExtractors.Kiosk(this, podcastsLHF.fromUrl(url), id)
                            : new YoutubeTrendingPodcastsEpisodesExtractor(
                            YoutubeService.this,
                            podcastsLHF.fromUrl(url),
                            id),
                    podcastsLHF,
                    YoutubeTrendingPodcastsEpisodesLinkHandlerFactory.KIOSK_ID
            );
            list.addKioskEntry(
                    (streamingService, url, id) -> InvidiousBackend.isEnabled()
                            ? new InvidiousExtractors.Kiosk(this, gamingLHF.fromUrl(url), id)
                            : new YoutubeTrendingGamingVideosExtractor(
                            YoutubeService.this,
                            gamingLHF.fromUrl(url),
                            id),
                    gamingLHF,
                    YoutubeTrendingGamingVideosLinkHandlerFactory.KIOSK_ID
            );
            list.addKioskEntry(
                    (streamingService, url, id) -> InvidiousBackend.isEnabled()
                            ? new InvidiousExtractors.Kiosk(this, moviesAndShowsLHF.fromUrl(url), id)
                            : new YoutubeTrendingMoviesAndShowsTrailersExtractor(
                                    YoutubeService.this,
                                    moviesAndShowsLHF.fromUrl(url),
                                    id),
                    moviesAndShowsLHF,
                    YoutubeTrendingMoviesAndShowsTrailersLinkHandlerFactory.KIOSK_ID
            );
            list.addKioskEntry(
                    (streamingService, url, id) -> InvidiousBackend.isEnabled()
                            ? new InvidiousExtractors.Kiosk(this, musicLHF.fromUrl(url), id)
                            : new YoutubeTrendingMusicExtractor(
                            YoutubeService.this,
                            musicLHF.fromUrl(url),
                            id),
                    musicLHF,
                    YoutubeTrendingMusicLinkHandlerFactory.KIOSK_ID
            );
            list.addKioskEntry(
                    (streamingService, url, id) -> InvidiousBackend.isEnabled()
                            ? new InvidiousExtractors.Kiosk(this, trendingLHF.fromUrl(url), id)
                            : new YoutubeTrendingExtractor(
                            YoutubeService.this,
                            trendingLHF.fromUrl(url),
                            id
                    ),
                    trendingLHF,
                    "Trending"
            );
            list.setDefaultKiosk(YoutubeLiveLinkHandlerFactory.KIOSK_ID);
        } catch (final Exception e) {
            throw new ExtractionException(e);
        }

        return list;
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return new YoutubeSubscriptionExtractor(this);
    }

    @Nullable
    @Override
    public FeedExtractor getFeedExtractor(final String channelUrl) throws ExtractionException {
        if (InvidiousBackend.isEnabled()) {
            // The lightweight videos endpoint omits continuations, Shorts and live streams.
            // Returning no dedicated feed selects the paginated channel-tab feed loader.
            return null;
        }
        return new YoutubeFeedExtractor(this, getChannelLHFactory().fromUrl(channelUrl));
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return YoutubeCommentsLinkHandlerFactory.getInstance();
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler urlIdHandler)
            throws ExtractionException {
        if (YoutubePostCommentsTarget.fromId(urlIdHandler.getId()) != null) {
            return InvidiousBackend.isEnabled()
                    ? new InvidiousPostCommentsExtractor(this, urlIdHandler)
                    : new YoutubePostCommentsExtractor(this, urlIdHandler);
        }
        if (InvidiousBackend.isEnabled()) {
            return new InvidiousCommentsExtractor(this, urlIdHandler);
        }
        return new YoutubeCommentsExtractor(this, urlIdHandler);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Localization
    //////////////////////////////////////////////////////////////////////////*/

    // https://www.youtube.com/picker_ajax?action_language_json=1
    // Using "zu" (Zulu) to prevent YouTube from auto-translating video titles
    private static final List<Localization> SUPPORTED_LANGUAGES = Localization.listFrom(
            "zu"
    );

    // https://www.youtube.com/picker_ajax?action_country_json=1
    private static final List<ContentCountry> SUPPORTED_COUNTRIES = ContentCountry.listFrom(
            "DZ", "AR", "AU", "AT", "AZ", "BH", "BD", "BY", "BE", "BO", "BA", "BR", "BG", "CA",
            "CL", "CO", "CR", "HR", "CY", "CZ", "DK", "DO", "EC", "EG", "SV", "EE", "FI", "FR",
            "GE", "DE", "GH", "GR", "GT", "HN", "HK", "HU", "IS", "IN", "ID", "IQ", "IE", "IL",
            "IT", "JM", "JP", "JO", "KZ", "KE", "KW", "LV", "LB", "LY", "LI", "LT", "LU", "MY",
            "MT", "MX", "ME", "MA", "NP", "NL", "NZ", "NI", "NG", "MK", "NO", "OM", "PK", "PA",
            "PG", "PY", "PE", "PH", "PL", "PT", "PR", "QA", "RO", "RU", "SA", "SN", "RS", "SG",
            "SK", "SI", "ZA", "KR", "ES", "LK", "SE", "CH", "TW", "TZ", "TH", "TN", "TR", "UG",
            "UA", "AE", "GB", "US", "UY", "VE", "VN", "YE", "ZW"
    );

    @Override
    public List<Localization> getSupportedLocalizations() {
        return SUPPORTED_LANGUAGES;
    }

    @Override
    public Localization getLocalization() {
        return new Localization("zu");
    }

    @Override
    public List<ContentCountry> getSupportedCountries() {
        return SUPPORTED_COUNTRIES;
    }

    @Override
    public BulletCommentsExtractor getBulletCommentsExtractor(ListLinkHandler linkHandler) throws ExtractionException {
        if (InvidiousBackend.isEnabled()) {
            throw new ExtractionException("YouTube bullet comments are unavailable through Invidious");
        }
        return new YoutubeBulletCommentsExtractor(this, linkHandler, watchDataCache);
    }

    @Override
    public ListLinkHandlerFactory getBulletCommentsLHFactory() {
        return new YoutubeBulletCommentsLinkHandlerFactory();
    }
}
