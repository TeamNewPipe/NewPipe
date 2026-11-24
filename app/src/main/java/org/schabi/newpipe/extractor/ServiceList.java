package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.services.bandcamp.BandcampService;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteService;
import org.schabi.newpipe.extractor.services.media_ccc.MediaCCCService;
import org.schabi.newpipe.extractor.services.peertube.PeertubeService;
import org.schabi.newpipe.extractor.services.rumble.RumbleService;
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudService;
import org.schabi.newpipe.extractor.services.youtube.YoutubeService;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.services.odysee.OdyseeService;
import org.schabi.newpipe.extractor.services.vimeo.VimeoService;
import org.schabi.newpipe.extractor.plugin.PluginServiceIdentity;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/*
 * Copyright (C) Christian Schabesberger 2018 <chris.schabesberger@mailbox.org>
 * ServiceList.java is part of NewPipe.
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

/**
 * A list of supported services.
 */
@SuppressWarnings({"ConstantName", "InnerAssignment"}) // keep unusual names and inner assignments
public final class ServiceList {
    private ServiceList() {
        //no instance
    }

    public static final YoutubeService YouTube;
    public static final SoundcloudService SoundCloud;
    public static final MediaCCCService MediaCCC;
    public static final PeertubeService PeerTube;
    public static final BandcampService Bandcamp;
    public static final NiconicoService NicoNico;
    public static final BilibiliService BiliBili;
    public static final BitchuteService BitChute;
    public static final RumbleService Rumble;
    public static final OdyseeService Odysee;
    public static final VimeoService Vimeo;
    /**
     * When creating a new service, put this service in the end of this list,
     * and give it the next free id.
     */
    private static final List<StreamingService> BUILT_IN = Collections.unmodifiableList(
            Arrays.asList(
                    YouTube = new YoutubeService(0),
                    SoundCloud = new SoundcloudService(1),
                    MediaCCC = new MediaCCCService(2),
                    PeerTube = new PeertubeService(3),
                    Bandcamp = new BandcampService(4),
                    BiliBili = new BilibiliService(5),
                    NicoNico = new NiconicoService(6),
                    BitChute = new BitchuteService(7),
                    Rumble = new RumbleService(8),
                    Odysee = new OdyseeService(9),
                    Vimeo = new VimeoService(10)
            ));

    /** Dynamic IDs are persistent identities, never positions in the service list. */
    public static final int FIRST_DYNAMIC_PLUGIN_ID = 1000;

    private static final Object LOCK = new Object();
    private static volatile List<StreamingService> services = BUILT_IN;

    /**
     * Get all the supported services.
     *
     * @return a unmodifiable list of all the supported services
     */
    public static List<StreamingService> all() {
        return services;
    }

    public static List<StreamingService> builtIn() {
        return BUILT_IN;
    }

    /** Publish an immutable snapshot only after checking both persistent identity and name. */
    public static void register(@Nonnull final StreamingService service) {
        synchronized (LOCK) {
            if (service.getServiceId() < FIRST_DYNAMIC_PLUGIN_ID) {
                throw new IllegalArgumentException("Service id is reserved for built-in services: "
                        + service.getServiceId());
            }
            final String name = service.getServiceInfo().getName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Service name is empty");
            }
            final String baseUrl = PluginServiceIdentity.normalizedBaseUrl(service);
            for (final StreamingService existing : services) {
                if (existing.getServiceId() == service.getServiceId()) {
                    throw new IllegalArgumentException(
                            "Service id already registered: " + service.getServiceId());
                }
                if (existing.getServiceInfo().getName().trim().equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException("Service name already registered: " + name);
                }
                if (baseUrl != null
                        && baseUrl.equals(PluginServiceIdentity.normalizedBaseUrl(existing))) {
                    throw new IllegalArgumentException("Provider is already available as "
                            + existing.getServiceInfo().getName());
                }
            }
            final List<StreamingService> copy = new ArrayList<>(services);
            copy.add(service);
            services = Collections.unmodifiableList(copy);
        }
    }

    /** Built-in services remain available regardless of plugin installation state. */
    public static void unregister(final int serviceId) {
        synchronized (LOCK) {
            final List<StreamingService> copy = new ArrayList<>(services);
            copy.removeIf(service -> service.getServiceId() == serviceId
                    && !BUILT_IN.contains(service));
            services = Collections.unmodifiableList(copy);
        }
    }

    @Nullable
    public static StreamingService getById(final int serviceId) {
        for (final StreamingService service : services) {
            if (service.getServiceId() == serviceId) {
                return service;
            }
        }
        return null;
    }
}
