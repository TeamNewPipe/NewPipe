package org.schabi.newpipe.extractor.services.youtube.invidious;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.comments.CommentSortOrder;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

public final class InvidiousCommentsExtractor extends CommentsExtractor {
    private final InvidiousApi api = new InvidiousApi();
    private final String path;
    private JsonObject first;
    private CommentSortOrder sortOrder = CommentSortOrder.TOP;

    public InvidiousCommentsExtractor(final StreamingService service,
            final ListLinkHandler handler) {
        super(service, handler);
        path = "comments/" + InvidiousApi.encode(handler.getId());
    }

    @Override
    public String getName() {
        return "Comments";
    }

    @Override
    public void setSortOrder(final CommentSortOrder order) {
        sortOrder = order;
    }

    @Override
    public CommentSortOrder getSortOrder() {
        return sortOrder;
    }

    private String query() {
        return "?source=youtube&sort_by=" + (sortOrder == CommentSortOrder.NEWEST ? "new" : "top");
    }
    @Override
    public void onFetchPage(final Downloader downloader) throws IOException, ExtractionException {
        first = api.object(api.url(path + query()));
    }

    private Page continuation(final JsonObject json) {
        final String token = json.getString("continuation", "");
        return token.isEmpty() ? null : new Page(api.url(path + query() + "&continuation="
                + InvidiousApi
                .encode(token)));
    }

    private InfoItemsPage<CommentsInfoItem> result(final JsonObject json) {
        return parseComments(json, getServiceId(), api,
                commentId -> "https://www.youtube.com/watch?v=" + getLinkHandler().getId()
                        + "&lc=" + InvidiousApi.encode(commentId), this::continuation);
    }

    static InfoItemsPage<CommentsInfoItem> parseComments(final JsonObject json,
            final int serviceId, final InvidiousApi api,
            final Function<String, String> commentUrl,
            final Function<JsonObject, Page> continuation) {
        final CommentsInfoItemsCollector collector = new CommentsInfoItemsCollector(serviceId);
        final JsonArray comments = json.getArray("comments", new JsonArray());
        for (final Object item : comments) {
            final JsonObject comment = (JsonObject) item;
            collector.commit(new CommentsInfoItemExtractor() {
                @Override
                public String getName() {
                    return comment.getString("author", "");
                }
                @Override
                public String getUrl() {
                    return commentUrl.apply(getCommentId());
                }
                @Override
                public String getCommentId() {
                    return comment.getString("commentId", "");
                }
                @Override
                public String getCommentText() {
                    return comment.getString("content", "");
                }
                @Override
                public int getLikeCount() {
                    return comment.getInt("likeCount", -1);
                }
                @Override
                public String getUploaderName() {
                    return getName();
                }
                @Override
                public String getUploaderUrl() {
                    return InvidiousApi.channelUrl(comment);
                }
                @Override
                public List<Image> getUploaderAvatars() {
                    return InvidiousMetadata.commentAvatars(comment, api);
                }
                @Override
                public DateWrapper getUploadDate() {
                    return InvidiousApi.date(comment);
                }
                @Override
                public String getTextualUploadDate() {
                    return comment.getString("publishedText", "");
                }
                @Override
                public boolean isPinned() {
                    return comment.getBoolean("isPinned");
                }
                @Override
                public boolean isHeartedByUploader() {
                    return comment.getObject("creatorHeart") != null;
                }
                @Override
                public int getReplyCount() {
                    return comment.getObject("replies", new JsonObject()).getInt("replyCount", 0);
                }
                @Override
                public Page getReplies() {
                    return continuation.apply(comment.getObject("replies", new JsonObject()));
                }
            });
        }
        return new InfoItemsPage<>(collector, continuation.apply(json));
    }

    @Override
    public InfoItemsPage<CommentsInfoItem> getInitialPage() {
        return result(first);
    }
    @Override
    public InfoItemsPage<CommentsInfoItem> getPage(final Page page) throws IOException,
            ExtractionException {
        if (!Page.isValid(page)) {
            throw new ExtractionException("Invalid Invidious comments page");
        }
        return result(api.object(page.getUrl()));
    }
}
