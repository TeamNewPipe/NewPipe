package org.schabi.newpipe.settings.serviceinstances.youtubelike;

import android.content.Context;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.instance.Instance;
import org.schabi.newpipe.extractor.services.youtube.YoutubeLikeInstance;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousInstance;
import org.schabi.newpipe.extractor.services.youtube.youtube.YoutubeInstance;
import org.schabi.newpipe.settings.serviceinstances.AbstractInstanceTypeCreator;
import org.schabi.newpipe.settings.serviceinstances.AbstractServiceInstanceListFragment;
import org.schabi.newpipe.settings.serviceinstances.UrlMultiInstanceTypeCreator;
import org.schabi.newpipe.util.services.YoutubeLikeInstanceManager;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class YouTubeLikeInstanceListFragment
        extends AbstractServiceInstanceListFragment<YoutubeLikeInstance<?>> {

    public YouTubeLikeInstanceListFragment() {
        super(
                R.string.yt_like_instance_url_title,
                YoutubeLikeInstanceManager.MANAGER,
                Arrays.asList(
                        new YouTubeInstanceTypeCreator(),
                        new InvidiousInstanceTypeCreator()));
    }


    public static class YouTubeInstanceTypeCreator
            extends AbstractInstanceTypeCreator<YoutubeInstance> {
        public YouTubeInstanceTypeCreator() {
            super(
                    YoutubeInstance.SERVICE_NAME,
                    R.drawable.ic_placeholder_yt,
                    YoutubeInstance.class);
        }

        @Override
        public boolean canNewInstanceBeCreated(
                @NonNull final List<? extends Instance> existingInstances
        ) {
            return existingInstances.stream()
                    .noneMatch(createdClass()::isInstance);
        }

        @Override
        public void createNewInstance(
                @NonNull final Context context,
                @NonNull final List<? extends Instance> existingInstances,
                @NonNull final Consumer<YoutubeInstance> onInstanceCreated
        ) {
            onInstanceCreated.accept(YoutubeInstance.YOUTUBE);
        }
    }

    public static class InvidiousInstanceTypeCreator
            extends UrlMultiInstanceTypeCreator<InvidiousInstance> {
        public InvidiousInstanceTypeCreator() {
            super(
                    InvidiousInstance.SERVICE_NAME,
                    R.drawable.ic_placeholder_circle, // TODO
                    InvidiousInstance.class,
                    InvidiousInstance::new,
                    R.string.invidious_instance_list_url);
        }
    }
}
