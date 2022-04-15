package org.schabi.newpipe.settings.services.instances.peertube;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;
import org.schabi.newpipe.settings.services.instances.AbstractServiceInstanceListFragment;
import org.schabi.newpipe.settings.services.instances.UrlMultiInstanceTypeCreator;
import org.schabi.newpipe.util.services.PeertubeInstanceManager;

import java.util.Collections;

public class PeertubeInstanceListFragment
        extends AbstractServiceInstanceListFragment<PeertubeInstance> {

    public PeertubeInstanceListFragment() {
        super(
                R.string.peertube_instance_url_title,
                PeertubeInstanceManager.MANAGER,
                Collections.singletonList(new PeerTubeInstanceTypeCreator()));
    }

    public static class PeerTubeInstanceTypeCreator
            extends UrlMultiInstanceTypeCreator<PeertubeInstance> {
        public PeerTubeInstanceTypeCreator() {
            super(
                    PeertubeInstance.SERVICE_NAME,
                    R.drawable.ic_placeholder_peertube,
                    PeertubeInstance.class,
                    PeertubeInstance::new,
                    R.string.peertube_instance_list_url,
                    true);
        }
    }
}
