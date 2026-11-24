package org.schabi.newpipe.extractor;

import org.apache.commons.lang3.StringUtils;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentsFilterConfig;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.localization.TimeAgoPatternsManager;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockApiSettings;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nullable;
import java.util.*;

/*
 * Copyright (C) Christian Schabesberger 2018 <chris.schabesberger@mailbox.org>
 * StreamingService.java is part of NewPipe.
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

public abstract class StreamingService {

    /**
     * This class holds meta information about the service implementation.
     */
    public static class ServiceInfo {
        private final String name;

        private final List<MediaCapability> mediaCapabilities;

        /**
         * Creates a new instance of a ServiceInfo
         *
         * @param name              the name of the service
         * @param mediaCapabilities the type of media this service can handle
         */
        public ServiceInfo(final String name, final List<MediaCapability> mediaCapabilities) {
            this.name = name;
            this.mediaCapabilities = Collections.unmodifiableList(mediaCapabilities);
        }

        public String getName() {
            return name;
        }

        public List<MediaCapability> getMediaCapabilities() {
            return mediaCapabilities;
        }

        public enum MediaCapability {
            AUDIO, VIDEO, LIVE, COMMENTS, BULLET_COMMENTS, SPONSORBLOCK
        }
    }

    /**
     * LinkType will be used to determine which type of URL you are handling, and therefore which
     * part of NewPipe should handle a certain URL.
     */
    public enum LinkType {
        NONE,
        STREAM,
        CHANNEL,
        PLAYLIST
    }

    public final int serviceId;
    private final ServiceInfo serviceInfo;
    private String tokens = null;
    private String additionalTokens = null;
    private String proxyToken = null;
    private Boolean proxyEnabled = false;
    private Set<String> cookieFunctions = null;
    private String audioLanguage = "original";
    private String autoTranslatedSubtitlesLanguage = "en";
    private boolean showAutoTranslatedSubtitles = false;
    private CommentsFilterConfig commentsFilterConfig = null;

    public Set<String> filterTypes = new HashSet<>();
    public InfoItemsCollector.FilterConfig filterConfig = null;

    public SponsorBlockApiSettings sponsorBlockApiSettings = null;
    
    private int loadingTimeout = 5;
    private boolean fetchFullPlaylist = false;
    private boolean fetchDislike = true;
    private long feedFetchInterval = 0;


    /**
     * Creates a new Streaming service.
     * If you Implement one do not set id within your implementation of this extractor, instead
     * set the id when you put the extractor into {@link ServiceList}
     * All other parameters can be set directly from the overriding constructor.
     *
     * @param id           the number of the service to identify him within the NewPipe frontend
     * @param name         the name of the service
     * @param capabilities the type of media this service can handle
     */
    public StreamingService(final int id,
                            final String name,
                            final List<ServiceInfo.MediaCapability> capabilities) {
        this.serviceId = id;
        this.serviceInfo = new ServiceInfo(name, capabilities);
    }

    public final int getServiceId() {
        return serviceId;
    }

    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    @Override
    public String toString() {
        return serviceId + ":" + serviceInfo.getName();
    }

    public abstract String getBaseUrl();

    /** Cache lifetime requested by a provider, in milliseconds. */
    public long getInfoCacheExpirationMillis() {
        return 60 * 60 * 1000L;
    }

    /**
     * Headers required by this provider for an image URL, or null for unrelated images.
     * Implementations must be cheap and must not perform network requests.
     */
    @Nullable
    public Map<String, String> getImageRequestHeaders(final String imageUrl) {
        return null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Url Id handler
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Must return a new instance of an implementation of LinkHandlerFactory for streams.
     *
     * @return an instance of a LinkHandlerFactory for streams
     */
    public abstract LinkHandlerFactory getStreamLHFactory();

    /**
     * Must return a new instance of an implementation of ListLinkHandlerFactory for channels.
     * If support for channels is not given null must be returned.
     *
     * @return an instance of a ListLinkHandlerFactory for channels or null
     */
    public abstract ListLinkHandlerFactory getChannelLHFactory();

    /**
     * Must return a new instance of an implementation of ListLinkHandlerFactory for channel tabs.
     * If support for channel tabs is not given null must be returned.
     *
     * @return an instance of a ListLinkHandlerFactory for channels or null
     */
    public abstract ListLinkHandlerFactory getChannelTabLHFactory();

    /**
     * Must return a new instance of an implementation of ListLinkHandlerFactory for playlists.
     * If support for playlists is not given null must be returned.
     *
     * @return an instance of a ListLinkHandlerFactory for playlists or null
     */
    public abstract ListLinkHandlerFactory getPlaylistLHFactory();

    /**
     * Must return an instance of an implementation of SearchQueryHandlerFactory.
     *
     * @return an instance of a SearchQueryHandlerFactory
     */
    public abstract SearchQueryHandlerFactory getSearchQHFactory();

    public abstract ListLinkHandlerFactory getCommentsLHFactory();
    public ListLinkHandlerFactory getBulletCommentsLHFactory() {
        return null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Extractors
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Must create a new instance of a SearchExtractor implementation.
     *
     * @param queryHandler specifies the keyword lock for, and the filters which should be applied.
     * @return a new SearchExtractor instance
     */
    public abstract SearchExtractor getSearchExtractor(SearchQueryHandler queryHandler);

    /**
     * Must create a new instance of a SuggestionExtractor implementation.
     *
     * @return a new SuggestionExtractor instance
     */
    public abstract SuggestionExtractor getSuggestionExtractor();

    /**
     * Outdated or obsolete. null can be returned.
     *
     * @return just null
     */
    public abstract SubscriptionExtractor getSubscriptionExtractor();

    /**
     * This method decides which strategy will be chosen to fetch the feed. In YouTube, for example,
     * a separate feed exists which is lightweight and made specifically to be used like this.
     * <p>
     * In services which there's no other way to retrieve them, null should be returned.
     *
     * @return a {@link FeedExtractor} instance or null.
     */
    @Nullable
    public FeedExtractor getFeedExtractor(final String url) throws ExtractionException {
        return null;
    }

    /**
     * Must create a new instance of a KioskList implementation.
     *
     * @return a new KioskList instance
     */
    public abstract KioskList getKioskList() throws ExtractionException;

    /**
     * Must create a new instance of a ChannelExtractor implementation.
     *
     * @param linkHandler is pointing to the channel which should be handled by this new instance.
     * @return a new ChannelExtractor
     */
    public abstract ChannelExtractor getChannelExtractor(ListLinkHandler linkHandler)
            throws ExtractionException;

    /**
     * Must create a new instance of a ChannelTabExtractor implementation.
     *
     * @param linkHandler is pointing to the channel which should be handled by this new instance.
     * @return a new ChannelTabExtractor
     */
    public abstract ChannelTabExtractor getChannelTabExtractor(ListLinkHandler linkHandler)
            throws ExtractionException;

    /**
     * Must crete a new instance of a PlaylistExtractor implementation.
     *
     * @param linkHandler is pointing to the playlist which should be handled by this new instance.
     * @return a new PlaylistExtractor
     */
    public abstract PlaylistExtractor getPlaylistExtractor(ListLinkHandler linkHandler)
            throws ExtractionException;

    /**
     * Must create a new instance of a StreamExtractor implementation.
     *    public void setYtdlpConfig(String ytdlpConfig) {
        this.ytdlpConfig = ytdlpConfig;
    }

    public String getYtdlpConfig() {
        return ytdlpConfig;
    }

    public void setYtdlpEnabled(boolean ytdlpEnabled) {
        this.ytdlpEnabled = ytdlpEnabled;
    }

    public boolean isYtdlpEnabled() {
        return ytdlpEnabled;
    }
     * @param linkHandler is pointing to the stream which should be handled by this new instance.
     * @return a new StreamExtractor
     */
    public abstract StreamExtractor getStreamExtractor(LinkHandler linkHandler)
            throws ExtractionException;

    public abstract CommentsExtractor getCommentsExtractor(ListLinkHandler linkHandler)
            throws ExtractionException;

    public BulletCommentsExtractor getBulletCommentsExtractor(ListLinkHandler linkHandler) throws ExtractionException{
        return null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Extractors without link handler
    //////////////////////////////////////////////////////////////////////////*/

    public SearchExtractor getSearchExtractor(final String query,
                                              final List<FilterItem> contentFilter,
                                              final List<FilterItem> sortFilter)
            throws ExtractionException {
        return getSearchExtractor(getSearchQHFactory()
                .fromQuery(query, contentFilter, sortFilter));
    }

    public ChannelExtractor getChannelExtractor(final String id,
                                                final List<FilterItem> contentFilter,
                                                final List<FilterItem> sortFilter)
            throws ExtractionException {
        return getChannelExtractor(getChannelLHFactory()
                .fromQuery(id, contentFilter, sortFilter));
    }

    public ChannelTabExtractor getChannelTabExtractorFromId(final String id, final String tab)
            throws ExtractionException {
        return getChannelTabExtractor(getChannelTabLHFactory().fromQuery(
                id, Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab)), null));
    }

    public ChannelTabExtractor getChannelTabExtractorFromId(final String id, final String tab,
                                                            final String baseUrl)
            throws ExtractionException {
        return getChannelTabExtractor(getChannelTabLHFactory().fromQuery(
                id, Collections.singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab)), null, baseUrl));
    }

    public PlaylistExtractor getPlaylistExtractor(final String id,
                                                  final List<FilterItem> contentFilter,
                                                  final List<FilterItem> sortFilter)
            throws ExtractionException {
        return getPlaylistExtractor(getPlaylistLHFactory()
                .fromQuery(id, contentFilter, sortFilter));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Short extractors overloads
    //////////////////////////////////////////////////////////////////////////*/

    public SearchExtractor getSearchExtractor(final String query) throws ExtractionException {
        return getSearchExtractor(getSearchQHFactory().fromQuery(query));
    }

    public ChannelExtractor getChannelExtractor(final String url) throws ExtractionException {
        return getChannelExtractor(getChannelLHFactory().fromUrl(url));
    }

    public PlaylistExtractor getPlaylistExtractor(final String url) throws ExtractionException {
        return getPlaylistExtractor(getPlaylistLHFactory().fromUrl(url));
    }

    public StreamExtractor getStreamExtractor(final String url) throws ExtractionException {
        return getStreamExtractor(getStreamLHFactory().fromUrl(url));
    }

    public CommentsExtractor getCommentsExtractor(final String url) throws ExtractionException {
        final ListLinkHandlerFactory listLinkHandlerFactory = getCommentsLHFactory();
        if (listLinkHandlerFactory == null) {
            return null;
        }
        return getCommentsExtractor(listLinkHandlerFactory.fromUrl(url));
    }

    public BulletCommentsExtractor getBulletCommentsExtractor(final String url) throws ExtractionException {
        final ListLinkHandlerFactory listLinkHandlerFactory = getBulletCommentsLHFactory();
        if (listLinkHandlerFactory == null) {
            return null;
        }
        return getBulletCommentsExtractor(listLinkHandlerFactory.fromUrl(url));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Figures out where the link is pointing to (a channel, a video, a playlist, etc.)
     *
     * @param url the url on which it should be decided of which link type it is
     * @return the link type of url
     */
    public final LinkType getLinkTypeByUrl(final String url) throws ParsingException {
        final String polishedUrl = Utils.followGoogleRedirectIfNeeded(url);

        final LinkHandlerFactory sH = getStreamLHFactory();
        final LinkHandlerFactory cH = getChannelLHFactory();
        final LinkHandlerFactory pH = getPlaylistLHFactory();

        if (sH != null && sH.acceptUrl(polishedUrl)) {
            return LinkType.STREAM;
        } else if (cH != null && cH.acceptUrl(polishedUrl)) {
            return LinkType.CHANNEL;
        } else if (pH != null && pH.acceptUrl(polishedUrl)) {
            return LinkType.PLAYLIST;
        } else {
            return LinkType.NONE;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Localization
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Returns a list of localizations that this service supports.
     */
    public List<Localization> getSupportedLocalizations() {
        return Collections.singletonList(Localization.DEFAULT);
    }

    /**
     * Returns a list of countries that this service supports.<br>
     */
    public List<ContentCountry> getSupportedCountries() {
        return Collections.singletonList(ContentCountry.DEFAULT);
    }

    /**
     * Returns the localization that should be used in this service. It will get which localization
     * the user prefer (using {@link NewPipe#getPreferredLocalization()}), then it will:
     * <ul>
     * <li>Check if the exactly localization is supported by this service.</li>
     * <li>If not, check if a less specific localization is available, using only the language
     * code.</li>
     * <li>Fallback to the {@link Localization#DEFAULT default} localization.</li>
     * </ul>
     */
    public Localization getLocalization() {
        final Localization preferredLocalization = NewPipe.getPreferredLocalization();

        // Check the localization's language and country
        if (getSupportedLocalizations().contains(preferredLocalization)) {
            return preferredLocalization;
        }

        // Fallback to the first supported language that matches the preferred language
        for (final Localization supportedLanguage : getSupportedLocalizations()) {
            if (supportedLanguage.getLanguageCode()
                    .equals(preferredLocalization.getLanguageCode())) {
                return supportedLanguage;
            }
        }

        return Localization.DEFAULT;
    }

    /**
     * Returns the country that should be used to fetch content in this service. It will get which
     * country the user prefer (using {@link NewPipe#getPreferredContentCountry()}), then it will:
     * <ul>
     * <li>Check if the country is supported by this service.</li>
     * <li>If not, fallback to the {@link ContentCountry#DEFAULT default} country.</li>
     * </ul>
     */
    public ContentCountry getContentCountry() {
        final ContentCountry preferredContentCountry = NewPipe.getPreferredContentCountry();

        if (getSupportedCountries().contains(preferredContentCountry)) {
            return preferredContentCountry;
        }

        return ContentCountry.DEFAULT;
    }

    /**
     * Get an instance of the time ago parser using the patterns related to the passed localization.
     * <br><br>
     * Just like {@link #getLocalization()}, it will also try to fallback to a less specific
     * localization if the exact one is not available/supported.
     *
     * @throws IllegalArgumentException if the localization is not supported (parsing patterns are
     *                                  not present).
     */
    public TimeAgoParser getTimeAgoParser(final Localization localization) {
        final TimeAgoParser targetParser = TimeAgoPatternsManager.getTimeAgoParserFor(localization);

        if (targetParser != null) {
            return targetParser;
        }

        if (!localization.getCountryCode().isEmpty()) {
            final Localization lessSpecificLocalization
                    = new Localization(localization.getLanguageCode());
            final TimeAgoParser lessSpecificParser
                    = TimeAgoPatternsManager.getTimeAgoParserFor(lessSpecificLocalization);

            if (lessSpecificParser != null) {
                return lessSpecificParser;
            }
        }

        throw new IllegalArgumentException(
                "Localization is not supported (\"" + localization + "\")");
    }

    public void setTokens(String tokens) {
        this.tokens = tokens;
    }

    public String getTokens() {
        return this.tokens;
    }

    public boolean hasTokens() {
        return StringUtils.isNotBlank(this.tokens);
    }

    public String getAdditionalTokens() {
        return additionalTokens;
    }

    public void setAdditionalTokens(String additionalTokens) {
        this.additionalTokens = additionalTokens;
    }

    public Set<String> getCookieFunctions() {
        if(cookieFunctions == null) {
            cookieFunctions = new HashSet<>();
        }
        return cookieFunctions;
    }

    public void setCookieFunctions(Set<String> cookieFunctions) {
        this.cookieFunctions = cookieFunctions;
    }

    public String getAudioLanguage() {
        return audioLanguage;
    }

    public void setAudioLanguage(String audioLanguage) {
        this.audioLanguage = audioLanguage;
    }

    public String getAutoTranslatedSubtitlesLanguage() {
        return autoTranslatedSubtitlesLanguage;
    }

    public void setAutoTranslatedSubtitlesLanguage(final String language) {
        this.autoTranslatedSubtitlesLanguage = language;
    }

    public boolean getShowAutoTranslatedSubtitles() {
        return showAutoTranslatedSubtitles;
    }

    public void setShowAutoTranslatedSubtitles(final boolean show) {
        this.showAutoTranslatedSubtitles = show;
    }

    @Nullable
    public CommentsFilterConfig getCommentsFilterConfig() {
        return commentsFilterConfig;
    }

    public void setCommentsFilterConfig(@Nullable final CommentsFilterConfig config) {
        this.commentsFilterConfig = config;
    }

    public Set<String> getFilterTypes() {
        return filterTypes;
    }

    public void setFilterTypes(Set<String> filterTypes) {
        this.filterTypes = filterTypes;
    }

    public InfoItemsCollector.FilterConfig getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(InfoItemsCollector.FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    public String getProxyToken() {
        return proxyToken;
    }

    public void setProxyToken(String proxyToken) {
        this.proxyToken = proxyToken;
    }

    public void setProxyEnabled(Boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public Boolean getProxyEnabled() {
        return proxyEnabled;
    }


    public void setSponsorBlockApiSettings(SponsorBlockApiSettings sponsorBlockApiSettings) {
        this.sponsorBlockApiSettings = sponsorBlockApiSettings;
    }

    public SponsorBlockApiSettings getSponsorBlockApiSettings() {
        return sponsorBlockApiSettings;
    }

    public int getLoadingTimeout() {
        return loadingTimeout;
    }

    public void setLoadingTimeout(int loadingTimeout) {
        this.loadingTimeout = loadingTimeout;
    }

    public boolean isFetchFullPlaylist() {
        return fetchFullPlaylist;
    }

    public void setFetchFullPlaylist(boolean fetchFullPlaylist) {
        this.fetchFullPlaylist = fetchFullPlaylist;
    }

    public boolean isFetchDislike() {
        return fetchDislike;
    }

    public void setFetchDislike(boolean fetchDislike) {
        this.fetchDislike = fetchDislike;
    }

    public long getFeedFetchInterval() {
        return feedFetchInterval;
    }

    public void setFeedFetchInterval(long feedFetchInterval) {
        this.feedFetchInterval = feedFetchInterval;
    }
}
