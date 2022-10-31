package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.search.filter.LibraryStringIds;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import static org.schabi.newpipe.extractor.ServiceList.SoundCloud;

public final class ServiceHelper {
    private static final StreamingService DEFAULT_FALLBACK_SERVICE = ServiceList.YouTube;
    /**
     * Map all available {@link LibraryStringIds} ids to resource ids available in strings.xml.
     */
    private static final Map<LibraryStringIds, Integer> LIBRARY_STRING_ID_TO_RES_ID_MAP =
            new EnumMap<>(LibraryStringIds.class);

    static {
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_10_30_MIN,
                R.string.search_filters_10_30_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_2_10_MIN,
                R.string.search_filters_2_10_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_360,
                R.string.search_filters_360);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_3D,
                R.string.search_filters_3d);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_4_20_MIN,
                R.string.search_filters_4_20_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_4K,
                R.string.search_filters_4k);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_ADDED,
                R.string.search_filters_added);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_ALBUMS,
                R.string.albums);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_ANY_TIME,
                R.string.search_filters_any_time);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_ALL,
                R.string.all);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_ARTISTS_AND_LABELS,
                R.string.search_filters_artists_and_labels);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_ASCENDING,
                R.string.search_filters_ascending);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_CCOMMONS,
                R.string.search_filters_ccommons);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_CHANNELS,
                R.string.channels);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_CONFERENCES,
                R.string.conferences);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_CREATION_DATE,
                R.string.search_filters_creation_date);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_DATE,
                R.string.search_filters_date);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_DURATION,
                R.string.search_filters_duration);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_EVENTS,
                R.string.events);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_FEATURES,
                R.string.search_filters_features);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_GREATER_30_MIN,
                R.string.search_filters_greater_30_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_HD,
                R.string.search_filters_hd);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_HDR,
                R.string.search_filters_hdr);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_KIND,
                R.string.search_filters_kind);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LAST_30_DAYS,
                R.string.search_filters_last_30_days);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LAST_7_DAYS,
                R.string.search_filters_last_7_days);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LAST_HOUR,
                R.string.search_filters_last_hour);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LAST_YEAR,
                R.string.search_filters_last_year);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LENGTH,
                R.string.search_filters_length);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LESS_2_MIN,
                R.string.search_filters_less_2_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LICENSE,
                R.string.search_filters_license);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LIKES,
                R.string.detail_likes_img_view_description);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LIVE,
                R.string.duration_live);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LOCATION,
                R.string.search_filters_location);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_LONG_GREATER_10_MIN,
                R.string.search_filters_long_greater_10_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_MEDIUM_4_10_MIN,
                R.string.search_filters_medium_4_10_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_THIS_MONTH,
                R.string.search_filters_this_month);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_ARTISTS,
                R.string.artists);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_SONGS,
                R.string.songs);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_NAME,
                R.string.name);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_NO,
                R.string.search_filters_no);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_OVER_20_MIN,
                R.string.search_filters_over_20_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PAST_DAY,
                R.string.search_filters_past_day);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PAST_HOUR,
                R.string.search_filters_past_hour);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PAST_MONTH,
                R.string.search_filters_past_month);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PAST_WEEK,
                R.string.search_filters_past_week);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PAST_YEAR,
                R.string.search_filters_past_year);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PLAYLISTS,
                R.string.playlists);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PUBLISH_DATE,
                R.string.search_filters_publish_date);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PUBLISHED,
                R.string.search_filters_published);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_PURCHASED,
                R.string.search_filters_published);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_RATING,
                R.string.search_filters_rating);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_RELEVANCE,
                R.string.search_filters_relevance);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_SENSITIVE,
                R.string.search_filters_sensitive);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_SEPIASEARCH,
                R.string.search_filters_sepiasearch);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_SHORT_LESS_4_MIN,
                R.string.search_filters_short_less_4_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_SORT_BY,
                R.string.search_filters_sort_by);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_SORT_ORDER,
                R.string.search_filters_sort_order);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_SUBTITLES,
                R.string.search_filters_subtitles);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_TO_MODIFY_COMMERCIALLY,
                R.string.search_filters_to_modify_commercially);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_TODAY,
                R.string.search_filters_today);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_TRACKS,
                R.string.tracks);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_UNDER_4_MIN,
                R.string.search_filters_under_4_min);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_UPLOAD_DATE,
                R.string.search_filters_upload_date);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_USERS,
                R.string.users);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_VIDEOS,
                R.string.videos_string);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_VIEWS,
                R.string.search_filters_views);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_VOD_VIDEOS,
                R.string.search_filters_vod_videos);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_VR180,
                R.string.search_filters_vr180);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_THIS_WEEK,
                R.string.search_filters_this_week);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_THIS_YEAR,
                R.string.search_filters_this_year);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_YES,
                R.string.search_filters_yes);
        LIBRARY_STRING_ID_TO_RES_ID_MAP.put(LibraryStringIds.SEARCH_FILTERS_YOUTUBE_MUSIC,
                R.string.search_filters_youtube_music);
    }

    private ServiceHelper() {
    }

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
        return Optional.ofNullable(getSelectedService(context))
                .orElse(DEFAULT_FALLBACK_SERVICE)
                .getServiceId();
    }

    @Nullable
    public static StreamingService getSelectedService(final Context context) {
        final String serviceName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.current_service_key),
                        context.getString(R.string.default_service_value));

        try {
            return NewPipe.getService(serviceName);
        } catch (final ExtractionException e) {
            return null;
        }
    }

    @NonNull
    public static String getNameOfServiceById(final int serviceId) {
        return ServiceList.all().stream()
                .filter(s -> s.getServiceId() == serviceId)
                .findFirst()
                .map(StreamingService::getServiceInfo)
                .map(StreamingService.ServiceInfo::getName)
                .orElse("<unknown>");
    }

    /**
     * @param serviceId the id of the service
     * @return the service corresponding to the provided id
     * @throws java.util.NoSuchElementException if there is no service with the provided id
     */
    @NonNull
    public static StreamingService getServiceById(final int serviceId) {
        return ServiceList.all().stream()
                .filter(s -> s.getServiceId() == serviceId)
                .findFirst()
                .orElseThrow();
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

    public static String getTranslatedFilterString(@NonNull final LibraryStringIds stringId,
                                                   @NonNull final Context context) {
        if (LIBRARY_STRING_ID_TO_RES_ID_MAP.containsKey(stringId)) {
            return context.getString(
                    Objects.requireNonNull(LIBRARY_STRING_ID_TO_RES_ID_MAP.get(stringId)));
        } else {
            return stringId.toString();
        }
    }
}
