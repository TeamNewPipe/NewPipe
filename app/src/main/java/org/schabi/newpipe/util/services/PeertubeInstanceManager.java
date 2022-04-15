package org.schabi.newpipe.util.services;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonStringWriter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InstanceBasedStreamingService;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;

public final class PeertubeInstanceManager extends AbstractInstanceManager<PeertubeInstance> {

    public static final PeertubeInstanceManager MANAGER = new PeertubeInstanceManager();

    @Override
    protected InstanceBasedStreamingService<PeertubeInstance> getRelatedStreamingService() {
        return ServiceList.PeerTube;
    }

    @Override
    protected PeertubeInstance createInstanceFromPersistence(final JsonObject jsonObject) {
        return new PeertubeInstance(
                jsonObject.getString("url"),
                jsonObject.getString("name")
        );
    }

    @Override
    protected void convertInstanceToPersist(
            final JsonStringWriter jsonWriter,
            final PeertubeInstance instance
    ) {
        jsonWriter.value("name", instance.getName());
        jsonWriter.value("url", instance.getUrl());
    }

    @Override
    protected int getListPersistenceKey() {
        return R.string.peertube_instance_list_key;
    }

    @Override
    protected int getSelectedInstancePersistenceKey() {
        return R.string.peertube_selected_instance_key;
    }

    @Override
    protected PeertubeInstance getDefaultInstance() {
        return PeertubeInstance.DEFAULT_INSTANCE;
    }
}
