package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.apache.commons.lang3.StringEscapeUtils;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bilibili.linkHandler.BilibiliChannelLinkHandlerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.COMMENT_REPLIES_URL;
import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getDefaultCookies;

public class BilibiliCommentsInfoItemExtractor implements CommentsInfoItemExtractor {
    public JsonObject data;

    BilibiliCommentsInfoItemExtractor(JsonObject json) {
        this.data = json;
    }

    @Override
    public String getName() throws ParsingException {
        return data.getObject("member").getString("uname");
    }

    @Override
    public String getUrl() throws ParsingException {
        return COMMENT_REPLIES_URL + data.getLong("oid") + "&root=" + data.getLong("rpid") + "&pn=1";
    }

    @Override
    public int getLikeCount() throws ParsingException {
        return data.getInt("like");
    }

    @Override
    public String getTextualLikeCount() throws ParsingException {
        return String.valueOf(getLikeCount());
    }

    @Override
    public String getCommentTextContent() throws ParsingException {
        String result = StringEscapeUtils.unescapeHtml4((data.getObject("content").getString("message")));
        try {
            if (result.endsWith("...") && data.getObject("content").getObject("jump_url") != null && data.getObject("content").getObject("jump_url").keySet().size() > 0) {
                result += "\n\n" + new ArrayList<>(data.getObject("content").getObject("jump_url").keySet()).stream().filter(x -> x.startsWith("https://")).collect(Collectors.toList()).get(0);
            }
        } catch (Exception e) {
        }
        return result;
    }

    @SuppressWarnings("SimpleDateFormat")
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data.getInt("ctime") * 1000L));
    }

    @Override
    public String getCommentId() throws ParsingException {
        return data.getString("rpid_str");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return BilibiliChannelLinkHandlerFactory.baseUrl + data.get("mid");
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return data.getObject("member").getString("uname");
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return data.getObject("member").getString("avatar").replace("http:", "https:");
    }

    @Nullable
    @Override
    public Page getReplies() throws ParsingException {
        if (data.getArray("replies") == null || data.getArray("replies").size() == 0) {
            return null;
        }
        if (data.getLong("root") == data.getLong("parent") && data.getLong("root") == data.getLong("rpid")) {
            return null;
        }
        try {
            return new Page(getUrl(), getDefaultCookies());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(+8)));
    }

    @Override
    public int getReplyCount() {
        return (int) data.getLong("rcount");
    }

    @Override
    public boolean isHeartedByUploader() throws ParsingException {
        return data.getObject("up_action").getBoolean("like");
    }

    @Override
    public boolean isPinned() throws ParsingException {
        return data.getBoolean("isTop");
    }

    @Nonnull
    @Override
    public Collection<Image> getPictures() throws ParsingException {
        JsonArray array = data.getObject("content").getArray("pictures");
        if (array.size() != 0) {
            return array.stream()
                    .map(x -> {
                        JsonObject jsonObject = (JsonObject) x;
                        String src = jsonObject.getString("img_src");
                        int width = jsonObject.getInt("img_width");
                        int height = jsonObject.getInt("img_height");
                        return new Image(src, height, width, Image.ResolutionLevel.fromHeight(height));
                    })
                    .collect(Collectors.toList());
        } else {
            return CommentsInfoItemExtractor.super.getPictures();
        }
    }
}
