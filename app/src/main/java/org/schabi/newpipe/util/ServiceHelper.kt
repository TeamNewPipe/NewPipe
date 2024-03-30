package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Predicate

object ServiceHelper {
    private val DEFAULT_FALLBACK_SERVICE: StreamingService = ServiceList.YouTube
    @DrawableRes
    fun getIcon(serviceId: Int): Int {
        when (serviceId) {
            0 -> return R.drawable.ic_smart_display
            1 -> return R.drawable.ic_cloud
            2 -> return R.drawable.ic_placeholder_media_ccc
            3 -> return R.drawable.ic_placeholder_peertube
            4 -> return R.drawable.ic_placeholder_bandcamp
            else -> return R.drawable.ic_circle
        }
    }

    fun getTranslatedFilterString(filter: String, c: Context?): String {
        when (filter) {
            "all" -> return c!!.getString(R.string.all)
            "videos", "sepia_videos", "music_videos" -> return c!!.getString(R.string.videos_string)
            "channels" -> return c!!.getString(R.string.channels)
            "playlists", "music_playlists" -> return c!!.getString(R.string.playlists)
            "tracks" -> return c!!.getString(R.string.tracks)
            "users" -> return c!!.getString(R.string.users)
            "conferences" -> return c!!.getString(R.string.conferences)
            "events" -> return c!!.getString(R.string.events)
            "music_songs" -> return c!!.getString(R.string.songs)
            "music_albums" -> return c!!.getString(R.string.albums)
            "music_artists" -> return c!!.getString(R.string.artists)
            else -> return filter
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @param serviceId service to get the instructions for
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @StringRes
    fun getImportInstructions(serviceId: Int): Int {
        when (serviceId) {
            0 -> return R.string.import_youtube_instructions
            1 -> return R.string.import_soundcloud_instructions
            else -> return -1
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
    fun getImportInstructionsHint(serviceId: Int): Int {
        when (serviceId) {
            1 -> return R.string.import_soundcloud_instructions_hint
            else -> return -1
        }
    }

    fun getSelectedServiceId(context: Context): Int {
        return Optional.ofNullable(getSelectedService(context))
                .orElse(DEFAULT_FALLBACK_SERVICE)
                .getServiceId()
    }

    fun getSelectedService(context: Context): StreamingService? {
        val serviceName: String? = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.current_service_key),
                        context.getString(R.string.default_service_value))
        try {
            return NewPipe.getService(serviceName)
        } catch (e: ExtractionException) {
            return null
        }
    }

    fun getNameOfServiceById(serviceId: Int): String {
        return ServiceList.all().stream()
                .filter(Predicate<StreamingService>({ s: StreamingService -> s.getServiceId() == serviceId }))
                .findFirst()
                .map<StreamingService.ServiceInfo>(Function<StreamingService, StreamingService.ServiceInfo>({ obj: StreamingService -> obj.getServiceInfo() }))
                .map<String>(Function<StreamingService.ServiceInfo, String>({ StreamingService.ServiceInfo.getName() }))
                .orElse("<unknown>")
    }

    /**
     * @param serviceId the id of the service
     * @return the service corresponding to the provided id
     * @throws java.util.NoSuchElementException if there is no service with the provided id
     */
    fun getServiceById(serviceId: Int): StreamingService {
        return ServiceList.all().stream()
                .filter(Predicate({ s: StreamingService -> s.getServiceId() == serviceId }))
                .findFirst()
                .orElseThrow()
    }

    fun setSelectedServiceId(context: Context, serviceId: Int) {
        var serviceName: String
        try {
            serviceName = NewPipe.getService(serviceId).getServiceInfo().getName()
        } catch (e: ExtractionException) {
            serviceName = DEFAULT_FALLBACK_SERVICE.getServiceInfo().getName()
        }
        setSelectedServicePreferences(context, serviceName)
    }

    private fun setSelectedServicePreferences(context: Context,
                                              serviceName: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(context.getString(R.string.current_service_key), serviceName).apply()
    }

    fun getCacheExpirationMillis(serviceId: Int): Long {
        if (serviceId == ServiceList.SoundCloud.getServiceId()) {
            return TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES)
        } else {
            return TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)
        }
    }

    fun initService(context: Context, serviceId: Int) {
        if (serviceId == ServiceList.PeerTube.getServiceId()) {
            val sharedPreferences: SharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(context)
            val json: String? = sharedPreferences.getString(context.getString(
                    R.string.peertube_selected_instance_key), null)
            if (null == json) {
                return
            }
            val jsonObject: JsonObject
            try {
                jsonObject = JsonParser.`object`().from(json)
            } catch (e: JsonParserException) {
                return
            }
            val name: String = jsonObject.getString("name")
            val url: String = jsonObject.getString("url")
            val instance: PeertubeInstance = PeertubeInstance(url, name)
            ServiceList.PeerTube.setInstance(instance)
        }
    }

    fun initServices(context: Context) {
        for (s: StreamingService in ServiceList.all()) {
            initService(context, s.getServiceId())
        }
    }
}
