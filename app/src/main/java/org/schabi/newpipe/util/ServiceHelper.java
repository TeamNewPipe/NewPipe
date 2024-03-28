package org.schabi.newpipe.util;

import static org.schabi.newpipe.extractor.ServiceList.SoundCloud;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ServiceHelper {
    private static final StreamingService DEFAULT_FALLBACK_SERVICE = ServiceList.YouTube;

    private ServiceHelper() { }

    @DrawableRes
    public static int getIcon(final int serviceId) {
        return switch (serviceId) {
            case 0 -> R.drawable.ic_smart_display;
            case 1 -> R.drawable.ic_cloud;
            case 2 -> R.drawable.ic_placeholder_media_ccc;
            case 3 -> R.drawable.ic_placeholder_peertube;
            case 4 -> R.drawable.ic_placeholder_bandcamp;
            default -> R.drawable.ic_circle;
        };
    }

    public static String getTranslatedFilterString(final String filter, final Context c) {
        return switch (filter) {
            case "all" -> c.getString(R.string.all);
            case "videos", "sepia_videos", "music_videos" -> c.getString(R.string.videos_string);
            case "channels" -> c.getString(R.string.channels);
            case "playlists", "music_playlists" -> c.getString(R.string.playlists);
            case "tracks" -> c.getString(R.string.tracks);
            case "users" -> c.getString(R.string.users);
            case "conferences" -> c.getString(R.string.conferences);
            case "events" -> c.getString(R.string.events);
            case "music_songs" -> c.getString(R.string.songs);
            case "music_albums" -> c.getString(R.string.albums);
            case "music_artists" -> c.getString(R.string.artists);
            default -> filter;
        };
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @param serviceId service to get the instructions for
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @StringRes
    public static int getImportInstructions(final int serviceId) {
        return switch (serviceId) {
            case 0 -> R.string.import_youtube_instructions;
            case 1 -> R.string.import_soundcloud_instructions;
            default -> -1;
        };
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

    public static int getSelectedServiceIdOrFallback(final Context context) {
        return getSelectedService(context)
                .orElse(DEFAULT_FALLBACK_SERVICE)
                .getServiceId();
    }

    public static StreamingService getSelectedServiceOrFallback(final Context context) {
        return getSelectedService(context)
                .orElse(DEFAULT_FALLBACK_SERVICE);
    }

    public static Optional<StreamingService> getSelectedService(final Context context) {
        final String serviceName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.current_service_key),
                        context.getString(R.string.default_service_value));
        try {
            return Optional.of(NewPipe.getService(serviceName));
        } catch (final ExtractionException e) {
            return Optional.empty();
        }
    }

    /** Get the name of the selected service.
     *
     * @param context
     * @return The name of the service or {@literal "<unknown>"}
     */
    @NonNull
    public static String getSelectedServiceNameOrUnknown(final Context context) {
        return getSelectedService(context)
                .map(s -> s.getServiceInfo().getName())
                .orElse("<unknown>");
    }

    /** Get the name of the service if it exists.
     *
     * @param serviceId
     * @return The name of the service or {@literal "<unknown>"}
     */
    @NonNull
    public static String getNameOfServiceByIdOrUnknown(final int serviceId) {
        return ServiceList.all().stream()
                .filter(s -> s.getServiceId() == serviceId)
                .findFirst()
                .map(StreamingService::getServiceInfo)
                .map(StreamingService.ServiceInfo::getName)
                .orElse("<unknown>");
    }

    /** Return the service for the given InfoItem.
     * <p>
     * This should always succeed, except in the (very unlikely) case
     * that we remove a service from NewPipeExtractor and the `InfoItem` is deserialized
     * from an old version where the service still existed.
     * <p>
     * NB: this function should exist as member on {@link InfoItem}.
     *
     * @param infoItem
     * @param <Item>
     * @return The service.
     */
    public static <Item extends InfoItem> StreamingService getServiceFromInfoItem(
            final Item infoItem) {
        try {
            return NewPipe.getService(infoItem.getServiceId());
        } catch (final ExtractionException e) {
            throw new AssertionError("InfoItem should never have a bad service id");
        }
    }

    /** Return the service for the given Info.
     * <p>
     * This should always succeed, except in the (very unlikely) case
     * that we remove a service from NewPipeExtractor and the `Info` is deserialized
     * from an old version where the service still existed.
     * <p>
     * NB: this function should exist as member on {@link Info}.
     *
     * @param info
     * @param <Item>
     * @return The service.
     */
    public static <Item extends Info> StreamingService getServiceFromInfo(final Item info) {
        try {
            return NewPipe.getService(info.getServiceId());
        } catch (final ExtractionException e) {
            throw new AssertionError("InfoItem should never have a bad service id");
        }
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

    private static void setSelectedServicePreferences(final Context context,
                                                      final String serviceName) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().
                putString(context.getString(R.string.current_service_key), serviceName).apply();
    }

    public static long getCacheExpirationMillis(final int serviceId) {
        if (serviceId == SoundCloud.getServiceId()) {
            return TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
        } else {
            return TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        }
    }

    public static void initService(final Context context, final int serviceId) {
        if (serviceId == ServiceList.PeerTube.getServiceId()) {
            final SharedPreferences sharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(context);
            final String json = sharedPreferences.getString(context.getString(
                    R.string.peertube_selected_instance_key), null);
            if (null == json) {
                return;
            }

            final JsonObject jsonObject;
            try {
                jsonObject = JsonParser.object().from(json);
            } catch (final JsonParserException e) {
                return;
            }
            final String name = jsonObject.getString("name");
            final String url = jsonObject.getString("url");
            final PeertubeInstance instance = new PeertubeInstance(url, name);
            ServiceList.PeerTube.setInstance(instance);
        }
    }

    public static void initServices(final Context context) {
        for (final StreamingService s : ServiceList.all()) {
            initService(context, s.getServiceId());
        }
    }
}
