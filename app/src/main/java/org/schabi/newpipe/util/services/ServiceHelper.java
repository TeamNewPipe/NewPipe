package org.schabi.newpipe.util.services;

import static org.schabi.newpipe.extractor.ServiceList.SoundCloud;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.instance.Instance;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousInstance;

import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

public final class ServiceHelper {
    private static final StreamingService DEFAULT_FALLBACK_SERVICE = ServiceList.YouTube;

    private ServiceHelper() { }

    @DrawableRes
    public static int getIcon(final int serviceId) {
        switch (serviceId) {
            case 0:
                return R.drawable.ic_smart_display;
            case 1:
                return R.drawable.ic_cloud;
            case 2:
                return R.drawable.ic_placeholder_media_ccc;
            case 3:
                return R.drawable.ic_placeholder_peertube;
            case 4:
                return R.drawable.ic_placeholder_bandcamp;
            default:
                return R.drawable.ic_circle;
        }
    }

    public static OptionalInt getOverrideIconForInstance(final Instance instance) {
        if (instance instanceof InvidiousInstance) {
            return OptionalInt.of(R.drawable.ic_placeholder_invidious);
        }
        return OptionalInt.empty();
    }

    public static String getTranslatedFilterString(final String filter, final Context c) {
        switch (filter) {
            case "all":
                return c.getString(R.string.all);
            case "videos":
            case "sepia_videos":
            case "music_videos":
                return c.getString(R.string.videos_string);
            case "channels":
                return c.getString(R.string.channels);
            case "playlists":
            case "music_playlists":
                return c.getString(R.string.playlists);
            case "tracks":
                return c.getString(R.string.tracks);
            case "users":
                return c.getString(R.string.users);
            case "conferences":
                return c.getString(R.string.conferences);
            case "events":
                return c.getString(R.string.events);
            case "music_songs":
                return c.getString(R.string.songs);
            case "music_albums":
                return c.getString(R.string.albums);
            case "music_artists":
                return c.getString(R.string.artists);
            default:
                return filter;
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @param serviceId service to get the instructions for
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @StringRes
    public static int getImportInstructions(final int serviceId) {
        switch (serviceId) {
            case 0:
                return R.string.import_youtube_instructions;
            case 1:
                return R.string.import_soundcloud_instructions;
            default:
                return -1;
        }
    }

    /**
     * For services that support importing from a channel url, return a hint that will
     * be used in the EditText that the user will type in his channel url.
     *
     * @param serviceId service to get the hint for
     * @return the hint's string resource or -1 if the service don't support it
     */
    @StringRes
    public static int getImportInstructionsHint(final int serviceId) {
        if (serviceId == 1) {
            return R.string.import_soundcloud_instructions_hint;
        }
        return -1;
    }

    public static int getSelectedServiceId(final Context context) {
        final String serviceName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.current_service_key),
                        context.getString(R.string.default_service_value));

        int serviceId;
        try {
            serviceId = NewPipe.getService(serviceName).getServiceId();
        } catch (final ExtractionException e) {
            serviceId = DEFAULT_FALLBACK_SERVICE.getServiceId();
        }

        return serviceId;
    }

    public static void setSelectedServiceId(final Context context, final int serviceId) {
        String serviceName;
        try {
            serviceName = NewPipe.getService(serviceId).getServiceInfo().getName();
        } catch (final ExtractionException e) {
            serviceName = DEFAULT_FALLBACK_SERVICE.getServiceInfo().getName();
        }

        setSelectedServicePreferences(context, serviceName);
    }

    public static void setSelectedServiceId(final Context context, final String serviceName) {
        final int serviceId = NewPipe.getIdOfService(serviceName);
        if (serviceId == -1) {
            setSelectedServicePreferences(context,
                    DEFAULT_FALLBACK_SERVICE.getServiceInfo().getName());
        } else {
            setSelectedServicePreferences(context, serviceName);
        }
    }

    private static void setSelectedServicePreferences(final Context context,
                                                      final String serviceName) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().
                putString(context.getString(R.string.current_service_key), serviceName).apply();
    }

    public static long getCacheExpirationMillis(final int serviceId) {
        if (serviceId == SoundCloud.getServiceId()) {
            return TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
        }
        return TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
    }

    public static boolean isBeta(final StreamingService s) {
        return !"YouTube".equals(s.getServiceInfo().getName());
    }

    public static void initService(final Context context, final int serviceId) {
        InstanceManagerHelper.getManagerForServiceId(serviceId)
                .ifPresent(im -> im.reloadCurrentInstanceFromPersistence(context));
    }

    public static void initServices(final Context context) {
        for (final StreamingService s : ServiceList.all()) {
            initService(context, s.getServiceId());
        }
    }
}
