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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    private static final List<StreamingService> SERVICES = Collections.unmodifiableList(
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

    /**
     * Get all the supported services.
     *
     * @return a unmodifiable list of all the supported services
     */
    public static List<StreamingService> all() {
        return SERVICES;
    }
}
