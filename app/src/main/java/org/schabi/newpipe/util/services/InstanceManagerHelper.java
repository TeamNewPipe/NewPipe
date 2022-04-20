package org.schabi.newpipe.util.services;

import org.schabi.newpipe.extractor.instance.Instance;

import java.util.Optional;

public final class InstanceManagerHelper {
    private InstanceManagerHelper() {
        // No impl
    }

    @SuppressWarnings("java:S1452") // otherwise generics won't work!
    public static Optional<? extends InstanceManager<? extends Instance>> getManagerForServiceId(
            final int serviceId
    ) {
        if (serviceId == 0) {
            return Optional.of(YoutubeLikeInstanceManager.MANAGER);
        } else if (serviceId == 3) {
            return Optional.of(PeertubeInstanceManager.MANAGER);
        }
        return Optional.empty();
    }
}
