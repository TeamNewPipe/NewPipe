/*
 * SPDX-FileCopyrightText: 2018-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonParser
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItemsCollector
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockApiSettings
import org.schabi.newpipe.ktx.getStringSafe
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryRepository

object ServiceHelper {
    const val YOUTUBE_MODE = "youtube"
    const val YOUTUBE_MUSIC_MODE = "youtube_music"

    private val DEFAULT_FALLBACK_SERVICE: StreamingService = ServiceList.YouTube

    @JvmStatic
    fun getVisibleServices(): List<StreamingService> {
        return ServiceList.all()
    }

    @JvmStatic
    @DrawableRes
    fun getIcon(serviceId: Int): Int {
        return when (serviceId) {
            0 -> R.drawable.ic_smart_display
            1 -> R.drawable.ic_cloud
            2 -> R.drawable.ic_placeholder_media_ccc
            3 -> R.drawable.ic_placeholder_peertube
            4 -> R.drawable.ic_placeholder_bandcamp
            5 -> R.drawable.ic_bilibili
            6 -> R.drawable.ic_niconico
            7 -> R.drawable.ic_placeholder_bitchute
            8 -> R.drawable.ic_placeholder_rumble
            ServiceList.Odysee.serviceId -> R.drawable.ic_placeholder_odysee
            ServiceList.Vimeo.serviceId -> R.drawable.ic_placeholder_vimeo
            else -> R.drawable.ic_extension
        }
    }

    @JvmStatic
    fun getIconDrawable(context: Context, serviceId: Int): Drawable? = PluginManager.getIcon(context, serviceId)
        ?: AppCompatResources.getDrawable(context, getIcon(serviceId))

    @JvmStatic
    fun getSelectedServiceIconDrawable(context: Context): Drawable? = if (isYoutubeMusicMode(context)) {
        AppCompatResources.getDrawable(context, R.drawable.ic_music_note)
    } else {
        getIconDrawable(context, getSelectedServiceId(context))
    }

    @JvmStatic
    fun getTranslatedFilterString(filter: String, context: Context): String {
        return when (filter) {
            "all" -> context.getString(R.string.all)
            "videos", "sepia_videos", "music_videos" -> context.getString(R.string.videos_string)
            "channels" -> context.getString(R.string.channels)
            "playlists", "music_playlists" -> context.getString(R.string.playlists)
            "tracks" -> context.getString(R.string.tracks)
            "users" -> context.getString(R.string.users)
            "conferences" -> context.getString(R.string.conferences)
            "events" -> context.getString(R.string.events)
            "lives" -> context.getString(R.string.lives)
            "animes" -> context.getString(R.string.animes)
            "movies_and_tv" -> context.getString(R.string.movies_and_tv)
            "music_songs" -> context.getString(R.string.songs)
            "music_albums" -> context.getString(R.string.albums)
            "music_artists" -> context.getString(R.string.artists)
            "sortby" -> context.getString(R.string.sort)
            "sortorder" -> context.getString(R.string.sort_order)
            "latest" -> context.getString(R.string.channel_video_sort_latest)
            "popular" -> context.getString(R.string.channel_video_sort_popular)
            "oldest" -> context.getString(R.string.channel_video_sort_oldest)
            "sort_popular" -> context.getString(R.string.channel_video_sort_popular)
            "sort_relevance" -> context.getString(R.string.search_filter_relevance)
            "sort_rating" -> context.getString(R.string.search_filter_rating)
            "sort_view" -> context.getString(R.string.search_filter_view_count)
            "sort_bookmark" -> context.getString(R.string.sort_bookmarks)
            "sort_likes" -> context.getString(R.string.sort_likes)
            "sort_comments" -> context.getString(R.string.sort_comments)
            "sort_bullet_comments" -> context.getString(R.string.sort_bullet_comments)
            "sort_publish_time" -> context.getString(R.string.search_filter_upload_date)
            "sort_last_comment_time" -> context.getString(R.string.sort_last_comment_time)
            "sort_video_count" -> context.getString(R.string.sort_video_count)
            "sort_overall" -> context.getString(R.string.sort_overall)
            "sort_ascending" -> context.getString(R.string.sort_ascending)
            "upload_date" -> context.getString(R.string.search_filter_upload_date)
            "past_hour" -> context.getString(R.string.search_filter_past_hour)
            "past_day" -> context.getString(R.string.search_filter_past_day)
            "past_week" -> context.getString(R.string.search_filter_past_week)
            "past_month" -> context.getString(R.string.search_filter_past_month)
            "past_year" -> context.getString(R.string.search_filter_past_year)
            "duration" -> context.getString(R.string.duration)
            "short_video" -> context.getString(R.string.search_filter_short)
            "medium_length" -> context.getString(R.string.search_filter_medium)
            "long_video" -> context.getString(R.string.search_filter_long)
            "extra_long" -> context.getString(R.string.search_filter_extra_long)
            "features" -> context.getString(R.string.search_filter_features)
            "Subtitles" -> context.getString(R.string.search_filter_subtitles)
            "Ccommons" -> context.getString(R.string.search_filter_creative_commons)
            "Live" -> context.getString(R.string.search_filter_live)
            "Purchased" -> context.getString(R.string.search_filter_purchased)
            "Location" -> context.getString(R.string.search_filter_location)
            "Hdr" -> context.getString(R.string.search_filter_hdr)
            else -> filter
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @param serviceId service to get the instructions for
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @JvmStatic
    @StringRes
    fun getImportInstructions(serviceId: Int): Int {
        return when (serviceId) {
            0 -> R.string.import_youtube_instructions
            1 -> R.string.import_soundcloud_instructions
            else -> -1
        }
    }

    /**
     * For services that support importing from a channel url, return a hint that will
     * be used in the EditText that the user will type in his channel url.
     *
     * @param serviceId service to get the hint for
     * @return the hint's string resource or -1 if the service don't support it
     */
    @JvmStatic
    @StringRes
    fun getImportInstructionsHint(serviceId: Int): Int {
        return when (serviceId) {
            1 -> R.string.import_soundcloud_instructions_hint
            else -> -1
        }
    }

    @JvmStatic
    fun getSelectedServiceId(context: Context): Int {
        return (getSelectedService(context) ?: DEFAULT_FALLBACK_SERVICE).serviceId
    }

    @JvmStatic
    fun isYoutubeMusicMode(context: Context): Boolean {
        if (getSelectedServiceId(context) != ServiceList.YouTube.serviceId) {
            return false
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(R.string.current_service_key), YOUTUBE_MODE) ==
            YOUTUBE_MUSIC_MODE
    }

    @JvmStatic
    fun getSelectedServiceName(context: Context): String {
        return if (isYoutubeMusicMode(context)) {
            context.getString(R.string.youtube_music)
        } else {
            getNameOfServiceById(getSelectedServiceId(context))
        }
    }

    @JvmStatic
    @DrawableRes
    fun getSelectedServiceIcon(context: Context): Int {
        return if (isYoutubeMusicMode(context)) {
            R.drawable.ic_music_note
        } else {
            getIcon(getSelectedServiceId(context))
        }
    }

    @JvmStatic
    fun getSelectedService(context: Context): StreamingService? {
        val selectedService: String = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSafe(
                context.getString(R.string.current_service_key),
                context.getString(R.string.default_service_value)
            )
        val serviceName = if (selectedService == YOUTUBE_MUSIC_MODE) {
            ServiceList.YouTube.serviceInfo.name
        } else {
            selectedService
        }

        return runCatching { NewPipe.getService(serviceName) }
            .getOrNull()
    }

    @JvmStatic
    fun getNameOfServiceById(serviceId: Int): String {
        return ServiceList.all().stream()
            .filter { it.serviceId == serviceId }
            .findFirst()
            .map(StreamingService::getServiceInfo)
            .map(StreamingService.ServiceInfo::getName)
            .orElse("<unknown>")
    }

    /**
     * @param serviceId the id of the service
     * @return the service corresponding to the provided id
     * @throws java.util.NoSuchElementException if there is no service with the provided id
     */
    @JvmStatic
    fun getServiceById(serviceId: Int): StreamingService {
        return ServiceList.all().firstNotNullOf { it.takeIf { it.serviceId == serviceId } }
    }

    @JvmStatic
    fun setSelectedServiceId(context: Context, serviceId: Int) {
        val serviceName = runCatching { NewPipe.getService(serviceId) }
            .getOrNull()
            ?.serviceInfo
            ?.name
            ?: DEFAULT_FALLBACK_SERVICE.serviceInfo.name

        setSelectedServicePreference(context, serviceName)
    }

    @JvmStatic
    fun setYoutubeMusicMode(context: Context) {
        setSelectedServicePreference(context, YOUTUBE_MUSIC_MODE)
    }

    private fun setSelectedServicePreference(context: Context, serviceName: String?) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit {
            putString(context.getString(R.string.current_service_key), serviceName)
        }
    }

    @JvmStatic
    fun getCacheExpirationMillis(serviceId: Int): Long {
        if (serviceId >= ServiceList.FIRST_DYNAMIC_PLUGIN_ID) {
            return ServiceList.getById(serviceId)?.let { service ->
                runCatching { service.infoCacheExpirationMillis.coerceAtLeast(0L) }
                    .getOrDefault(TimeUnit.MINUTES.toMillis(5))
            } ?: 0L
        }
        return when (serviceId) {
            ServiceList.SoundCloud.serviceId ->
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES)

            ServiceList.NicoNico.serviceId ->
                TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES)

            // Vimeo media URLs expire; refresh well before the observed one-hour lifetime.
            ServiceList.Vimeo.serviceId ->
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES)

            else -> TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)
        }
    }

    @JvmStatic
    fun isFetchDislikeEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.show_dislike_key), true)
    }

    fun initService(context: Context, serviceId: Int) {
        if (serviceId == ServiceList.PeerTube.serviceId) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val json = sharedPreferences.getString(
                context.getString(R.string.peertube_selected_instance_key),
                null
            ) ?: return

            val jsonObject = runCatching { JsonParser.`object`().from(json) }
                .getOrElse { return@initService }

            ServiceList.PeerTube.instance = PeertubeInstance(
                jsonObject.getString("url"),
                jsonObject.getString("name")
            )
        }
    }

    @JvmStatic
    fun initServices(context: Context) {
        val fetchDislike = isFetchDislikeEnabled(context)
        val sponsorBlockApiSettings = buildSponsorBlockApiSettings(context)
        ServiceList.all().forEach { initService(context, it.serviceId) }

        // Return YouTube Dislike is YouTube-only. SponsorBlock also supports BiliBili.
        ServiceList.all().forEach {
            it.setFetchDislike(false)
            it.setSponsorBlockApiSettings(null)
        }
        ServiceList.YouTube.setFetchDislike(fetchDislike)
        ServiceList.YouTube.setSponsorBlockApiSettings(sponsorBlockApiSettings)
        ServiceList.BiliBili.setSponsorBlockApiSettings(sponsorBlockApiSettings)
        configureMembersOnlyFilter(context)
    }

    @JvmStatic
    fun configureMembersOnlyFilter(context: Context) {
        setHideMembersOnlyVideos(MembersOnlyContentHelper.shouldHide(context))
    }

    @JvmStatic
    fun setHideMembersOnlyVideos(hideMembersOnlyVideos: Boolean) {
        val existing = ServiceList.YouTube.filterConfig
        ServiceList.YouTube.filterConfig = InfoItemsCollector.FilterConfig(
            ArrayList(existing?.keywords ?: emptyList()),
            ArrayList(existing?.channels ?: emptyList()),
            existing?.isBlockShorts ?: false,
            hideMembersOnlyVideos
        )
    }

    private fun buildSponsorBlockApiSettings(context: Context): SponsorBlockApiSettings? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.getBoolean(context.getString(R.string.sponsor_block_enable_key), false)) {
            return null
        }

        return SponsorBlockApiSettings().apply {
            apiUrl = preferences.getString(context.getString(R.string.sponsor_block_api_url_key), null)
                ?.takeIf(String::isNotBlank)
            userId = preferences.getString(context.getString(R.string.sponsor_block_user_id_key), null)
                ?.takeIf(String::isNotBlank)
            includeSponsorCategory = SponsorBlockCategoryRepository.isEnabled(context, SponsorBlockCategoryConfig.SPONSOR)
            includeIntroCategory = SponsorBlockCategoryRepository.isEnabled(context, SponsorBlockCategoryConfig.INTRO)
            includeOutroCategory = SponsorBlockCategoryRepository.isEnabled(context, SponsorBlockCategoryConfig.OUTRO)
            includeInteractionCategory = SponsorBlockCategoryRepository.isEnabled(context, SponsorBlockCategoryConfig.INTERACTION)
            includeHighlightCategory = SponsorBlockCategoryRepository.isEnabled(context, SponsorBlockCategoryConfig.HIGHLIGHT)
            includeSelfPromoCategory = SponsorBlockCategoryRepository.isEnabled(context, SponsorBlockCategoryConfig.SELF_PROMO)
            includeMusicCategory = SponsorBlockCategoryRepository.isEnabled(context, SponsorBlockCategoryConfig.NON_MUSIC)
            includePreviewCategory = SponsorBlockCategoryRepository.isEnabled(context, SponsorBlockCategoryConfig.PREVIEW)
            includeFillerCategory = SponsorBlockCategoryRepository.isEnabled(
                context,
                SponsorBlockCategoryConfig.FILLER
            )
        }
    }
}
