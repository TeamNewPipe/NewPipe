package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentSortOrder;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.*;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class YoutubeCommentsExtractor extends CommentsExtractor {

    private static final String COMMENT_VIEW_MODEL_KEY = "commentViewModel";
    private static final String COMMENT_RENDERER_KEY = "commentRenderer";

    /**
     * Whether comments are disabled on video.
     */
    private boolean commentsDisabled;
    private CommentSortOrder sortOrder = CommentSortOrder.TOP;

    @Override
    public void setSortOrder(final CommentSortOrder order) {
        sortOrder = java.util.Objects.requireNonNull(order);
    }

    @Override
    public CommentSortOrder getSortOrder() {
        return sortOrder;
    }

    /**
     * The second ajax <b>/next</b> response.
     */
    private JsonObject ajaxJson;
    private JSONObject ajaxJsonSafe;

    public YoutubeCommentsExtractor(
            final StreamingService service,
            final ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Nonnull
    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage()
            throws IOException, ExtractionException {

        if (commentsDisabled) {
            return getInfoItemsPageForDisabledComments();
        }

        if (sortOrder == CommentSortOrder.NEWEST) {
            return getPage(new Page(getUrl(),
                    YoutubeCommentSort.continuation(ajaxJson, sortOrder)));
        }
        return extractComments(ajaxJson, ajaxJsonSafe);
    }

    /**
     * Finds the initial comments token and initializes commentsDisabled.
     * <br/>
     * Also sets {@link #commentsDisabled}.
     *
     * @return the continuation token or null if none was found
     */
    @Nullable
    private String findInitialCommentsToken(final JsonObject nextResponse) {
        final JsonArray contents = getJsonContents(nextResponse);

        // For videos where comments are unavailable, this would be null
        if (contents == null) {
            return null;
        }

        final String token = contents.stream()
                // Only use JsonObjects
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                // Check if the comment-section is present
                .filter(jObj -> {
                    try {
                        return "comments-section".equals(
                                JsonUtils.getString(jObj, "itemSectionRenderer.targetId"));
                    } catch (final ParsingException ignored) {
                        return false;
                    }
                })
                .findFirst()
                // Extract the token (or null in case of error)
                .map(itemSectionRenderer -> {
                    try {
                        return JsonUtils.getString(
                                itemSectionRenderer
                                        .getObject("itemSectionRenderer")
                                        .getArray("contents").getObject(0),
                                "continuationItemRenderer.continuationEndpoint"
                                        + ".continuationCommand.token");
                    } catch (final ParsingException ignored) {
                        return null;
                    }
                })
                .orElse(null);

        // The comments are disabled if we couldn't get a token
        commentsDisabled = token == null;

        return token;
    }

    @Nullable
    private JsonArray getJsonContents(final JsonObject nextResponse) {
        try {
            return JsonUtils.getArray(nextResponse,
                    "contents.twoColumnWatchNextResults.results.results.contents");
        } catch (final ParsingException e) {
            return null;
        }
    }

    @Nonnull
    private JsonObject getMutationPayloadFromEntityKey(@Nonnull final JsonArray mutations,
                                                       @Nonnull final String commentKey)
            throws ParsingException {
        return mutations.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(mutation -> commentKey.equals(
                        mutation.getString("entityKey")))
                .findFirst()
                .orElseThrow(() -> new ParsingException(
                        "Could not get comment entity payload mutation"))
                .getObject("payload");
    }

    /** Comment images are stored separately from the main comment entity in newer responses. */
    @Nonnull
    private JsonObject getOptionalCommentSurface(@Nonnull final JsonArray mutations,
                                                 @Nonnull final JsonObject viewModel) {
        final String key = viewModel.getString("commentSurfaceKey", "");
        if (key.isEmpty()) {
            return new JsonObject();
        }
        return mutations.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(mutation -> key.equals(mutation.getString("entityKey"))
                        || key.equals(mutation.getObject("payload")
                        .getObject("commentSurfaceEntityPayload").getString("key")))
                .findFirst()
                .map(mutation -> mutation.getObject("payload")
                        .getObject("commentSurfaceEntityPayload"))
                .orElseGet(JsonObject::new);
    }

    @Nonnull
    private InfoItemsPage<CommentsInfoItem> getInfoItemsPageForDisabledComments() {
        return new InfoItemsPage<>(Collections.emptyList(), null, Collections.emptyList());
    }

    @Nullable
    private Page getNextPage(@Nonnull final JsonObject jsonObject, JSONObject jsonObjectSafe) throws ExtractionException {
        final JsonArray onResponseReceivedEndpoints =
                jsonObject.getArray("onResponseReceivedEndpoints");

        // Prevent ArrayIndexOutOfBoundsException
        if (onResponseReceivedEndpoints.isEmpty()) {
            return null;
        }

        if(jsonObjectSafe == null) {
            return getNextPage(onResponseReceivedEndpoints
                    .getObject(0)
                    .getObject("reloadContinuationItemsCommand")
                    .getArray("continuationItems")
                    .getObject(0)
                    .getObject("commentsHeaderRenderer")
                    .getObject("sortMenu")
                    .getObject("sortFilterSubMenuRenderer")
                    .getArray("subMenuItems")
                    .getObject(0)
                    .getObject("serviceEndpoint")
                    .getObject("continuationCommand")
                    .getString("token"));
        }

        final JsonArray continuationItemsArray;
        try {
            final JsonObject endpoint = onResponseReceivedEndpoints
                    .getObject(onResponseReceivedEndpoints.size() - 1);
            continuationItemsArray = endpoint
                    .getObject("reloadContinuationItemsCommand",
                            endpoint.getObject("appendContinuationItemsAction"))
                    .getArray("continuationItems");
        } catch (final Exception e) {
            return null;
        }
        // Prevent ArrayIndexOutOfBoundsException
        if (continuationItemsArray.isEmpty()) {
            return null;
        }

        JSONArray continuationItems = null;
        try {
            try {
                continuationItems = jsonObjectSafe
                        .getJSONArray("onResponseReceivedEndpoints")
                        .getJSONObject(onResponseReceivedEndpoints.size() - 1)
                        .getJSONObject("reloadContinuationItemsCommand")
                        .getJSONArray("continuationItems");
            } catch (final Exception ignored) {

            }
            // fall back to "appendContinuationItemsAction"
            if (continuationItems == null) {
                continuationItems = jsonObjectSafe
                        .getJSONArray("onResponseReceivedEndpoints")
                        .getJSONObject(onResponseReceivedEndpoints.size() - 1)
                        .getJSONObject("appendContinuationItemsAction")
                        .getJSONArray("continuationItems");
            }

            final JSONObject continuationItemRenderer = continuationItems
                    .getJSONObject(continuationItems.length() - 1)
                    .getJSONObject("continuationItemRenderer");

            if(continuationItemRenderer.has("button")) { // replies
                //TODO: seems reply send 2 useless requests which should be avoided
                //button.buttonRenderer.command.continuationCommand.token
                return getNextPage(continuationItemRenderer
                        .getJSONObject("button")
                        .getJSONObject("buttonRenderer")
                        .getJSONObject("command")
                        .getJSONObject("continuationCommand")
                        .getString("token")
                );
            }


            return getNextPage(continuationItemRenderer
                    .getJSONObject("continuationEndpoint")
                    .getJSONObject("continuationCommand")
                    .getString("token")
            );
        } catch (JSONException e) {
            return null;
        }
    }

    @Nonnull
    private Page getNextPage(final String continuation) throws ParsingException {
        return new Page(getUrl(), continuation); // URL is ignored tho
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {

        if (commentsDisabled) {
            return getInfoItemsPageForDisabledComments();
        }

        if (page == null || isNullOrEmpty(page.getId())) {
            throw new IllegalArgumentException("Page doesn't have the continuation.");
        }

        final Localization localization = getExtractorLocalization();
        // @formatter:off
        final byte[] body = JsonWriter.string(
                        prepareDesktopJsonBuilder(localization, getExtractorContentCountry())
                                .value("continuation", page.getId())
                                .done())
                .getBytes(StandardCharsets.UTF_8);
        // @formatter:on

        String resp = getJsonPostResponseRaw("next", body, localization);
        final JsonObject jsonObject = JsonUtils.toJsonObject(resp);
        final JSONObject jsonObjectSafe;
        try {
            jsonObjectSafe = new JSONObject(resp);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return extractComments(jsonObject, jsonObjectSafe);
    }

    private InfoItemsPage<CommentsInfoItem> extractComments(final JsonObject jsonObject, final JSONObject jsonObjectSafe)
            throws ExtractionException {
        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(
                getServiceId());
        if(jsonObjectSafe != null) {
            collectCommentsFrom(collector, jsonObject);
        }
        return new InfoItemsPage<>(collector, getNextPage(jsonObject, jsonObjectSafe));
    }

    private void collectCommentsFrom(@Nonnull final CommentsInfoItemsCollector collector,
                                     @Nonnull final JsonObject jsonObject)
            throws ParsingException {

        final JsonArray onResponseReceivedEndpoints =
                jsonObject.getArray("onResponseReceivedEndpoints");
        // Prevent ArrayIndexOutOfBoundsException
        if (onResponseReceivedEndpoints.isEmpty()) {
            return;
        }
        final JsonObject commentsEndpoint =
                onResponseReceivedEndpoints.getObject(onResponseReceivedEndpoints.size() - 1);

        final String path;

        if (commentsEndpoint.has("reloadContinuationItemsCommand")) {
            path = "reloadContinuationItemsCommand.continuationItems";
        } else if (commentsEndpoint.has("appendContinuationItemsAction")) {
            path = "appendContinuationItemsAction.continuationItems";
        } else {
            // No comments
            return;
        }

        final JsonArray contents;
        try {
            contents = JsonUtils.getArray(commentsEndpoint, path);
        } catch (final Exception e) {
            // No comments
            return;
        }

        final int index = contents.size() - 1;
        if (!contents.isEmpty() && contents.getObject(index).has("continuationItemRenderer")) {
            contents.remove(index);
        }

        // The mutations object, which is returned in the comments' continuation
        // It contains parts of comment data when comments are returned with a view model
        final JsonArray mutations = jsonObject.getObject("frameworkUpdates")
                .getObject("entityBatchUpdate")
                .getArray("mutations");
        final String videoUrl = getUrl();
        final TimeAgoParser timeAgoParser = getTimeAgoParser();

        for (final Object o : contents) {
            if (!(o instanceof JsonObject)) {
                continue;
            }

            collectCommentItem(mutations, (JsonObject) o, collector, videoUrl, timeAgoParser);
        }
    }

    /** Shared renderer parsing for video and public post comment continuations. */
    protected final void collectCommentItem(@Nonnull final JsonArray mutations,
                                    @Nonnull final JsonObject content,
                                    @Nonnull final CommentsInfoItemsCollector collector,
                                    @Nonnull final String videoUrl,
                                    @Nonnull final TimeAgoParser timeAgoParser)
            throws ParsingException {
        if (content.has("commentThreadRenderer")) {
            final JsonObject commentThreadRenderer =
                    content.getObject("commentThreadRenderer");
            if (commentThreadRenderer.has(COMMENT_VIEW_MODEL_KEY)) {
                final JsonObject commentViewModel =
                        commentThreadRenderer.getObject(COMMENT_VIEW_MODEL_KEY)
                                .getObject(COMMENT_VIEW_MODEL_KEY);
                collector.commit(new YoutubeCommentsEUVMInfoItemExtractor(
                        commentViewModel,
                        commentThreadRenderer.getObject("replies")
                                .getObject("commentRepliesRenderer"),
                        getMutationPayloadFromEntityKey(mutations,
                                commentViewModel.getString("commentKey", ""))
                                .getObject("commentEntityPayload"),
                        getMutationPayloadFromEntityKey(mutations,
                                commentViewModel.getString("toolbarStateKey", ""))
                                .getObject("engagementToolbarStateEntityPayload"),
                        getOptionalCommentSurface(mutations, commentViewModel),
                        videoUrl,
                        timeAgoParser));
            } else if (commentThreadRenderer.has("comment")) {
                collector.commit(new YoutubeCommentsInfoItemExtractor(
                        commentThreadRenderer.getObject("comment")
                                .getObject(COMMENT_RENDERER_KEY),
                        commentThreadRenderer.getObject("replies")
                                .getObject("commentRepliesRenderer"),
                        videoUrl,
                        timeAgoParser));
            }
        } else if (content.has(COMMENT_VIEW_MODEL_KEY)) {
            final JsonObject commentViewModel = content.getObject(COMMENT_VIEW_MODEL_KEY);
            collector.commit(new YoutubeCommentsEUVMInfoItemExtractor(
                    commentViewModel,
                    null,
                    getMutationPayloadFromEntityKey(mutations,
                            commentViewModel.getString("commentKey", ""))
                            .getObject("commentEntityPayload"),
                    getMutationPayloadFromEntityKey(mutations,
                            commentViewModel.getString("toolbarStateKey", ""))
                            .getObject("engagementToolbarStateEntityPayload"),
                    getOptionalCommentSurface(mutations, commentViewModel),
                    videoUrl,
                    timeAgoParser));
        } else if (content.has(COMMENT_RENDERER_KEY)) {
            // commentRenderers are directly returned for comment replies, so there is no
            // commentRepliesRenderer to provide
            // Also, YouTube has only one comment reply level
            collector.commit(new YoutubeCommentsInfoItemExtractor(
                    content.getObject(COMMENT_RENDERER_KEY),
                    null,
                    videoUrl,
                    timeAgoParser));
        }
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final Localization localization = getExtractorLocalization();
        // @formatter:off
        final byte[] body = JsonWriter.string(
                        prepareDesktopJsonBuilder(localization, getExtractorContentCountry())
                                .value("videoId", getId())
                                .done())
                .getBytes(StandardCharsets.UTF_8);
        // @formatter:on

        final String initialToken =
                findInitialCommentsToken(getJsonPostResponse("next", body, localization));

        if (initialToken == null) {
            return;
        }

        // @formatter:off
        final byte[] ajaxBody = JsonWriter.string(
                        prepareDesktopJsonBuilder(localization, getExtractorContentCountry())
                                .value("continuation", initialToken)
                                .done())
                .getBytes(StandardCharsets.UTF_8);
        // @formatter:on

        String resp = getJsonPostResponseRaw("next", ajaxBody, localization);
        ajaxJson = JsonUtils.toJsonObject(resp);
        try {
            ajaxJsonSafe = new JSONObject(resp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean isCommentsDisabled() {
        return commentsDisabled;
    }
}
