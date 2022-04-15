package org.schabi.newpipe.util.services;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonStringWriter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InstanceBasedStreamingService;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.YoutubeLikeInstance;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousInstance;
import org.schabi.newpipe.extractor.services.youtube.youtube.YoutubeInstance;

public final class YoutubeLikeInstanceManager
        extends AbstractInstanceManager<YoutubeLikeInstance<?>> {

    public static final YoutubeLikeInstanceManager MANAGER = new YoutubeLikeInstanceManager();

    @Override
    protected InstanceBasedStreamingService<YoutubeLikeInstance<?>> getRelatedStreamingService() {
        return ServiceList.YouTube;
    }

    @Override
    protected YoutubeLikeInstance<?> createInstanceFromPersistence(final JsonObject jsonObject) {
        if ("invidious".equals(jsonObject.getString("type"))) {
            return new InvidiousInstance(
                    jsonObject.getString("url"),
                    jsonObject.getString("name")
            );
        }
        return getDefaultInstance();
    }

    @Override
    protected void convertInstanceToPersist(
            final JsonStringWriter jsonWriter,
            final YoutubeLikeInstance<?> instance) {
        jsonWriter.value("type", instance instanceof InvidiousInstance ? "invidious" : "yt");
        if (instance instanceof InvidiousInstance) {
            jsonWriter.value("name", instance.getName());
            jsonWriter.value("url", instance.getUrl());
        }
    }

    @Override
    protected int getListPersistenceKey() {
        return R.string.yt_like_instance_list_key;
    }

    @Override
    protected int getSelectedInstancePersistenceKey() {
        return R.string.yt_like_instance_selected_instance_key;
    }

    @Override
    protected YoutubeLikeInstance<?> getDefaultInstance() {
        return YoutubeInstance.YOUTUBE;
    }
}
