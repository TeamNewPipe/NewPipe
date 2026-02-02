package org.schabi.newpipe.error

import android.content.Context
import android.os.Parcelable
import androidx.annotation.StringRes
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.Loader
import java.net.UnknownHostException
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.ktx.isNetworkRelated
import org.schabi.newpipe.player.mediasource.FailedMediaSource
import org.schabi.newpipe.player.resolver.PlaybackResolver
import org.schabi.newpipe.util.Localization

/**
 * An error has occurred in the app. This class contains plain old parcelable data that can be used
 * to report the error and to show it to the user along with correct action buttons.
 */
@Parcelize
class ErrorInfo private constructor(
    val stackTraces: Array<String>,
    val userAction: UserAction,
    val request: String,
    val serviceId: Int?,
    private val message: ErrorMessage,
    /**
     * If `true`, a report button will be shown for this error. Otherwise the error is not something
     * that can really be reported (e.g. a network issue, or content not being available at all).
     */
    val isReportable: Boolean,
    /**
     * If `true`, the process causing this error can be retried, otherwise not.
     */
    val isRetryable: Boolean,
    /**
     * If present, indicates that the exception was a ReCaptchaException, and this is the URL
     * provided by the service that can be used to solve the ReCaptcha challenge.
     */
    val recaptchaUrl: String?,
    /**
     * If present, this resource can alternatively be opened in browser (useful if NewPipe is
     * badly broken).
     */
    val openInBrowserUrl: String?
) : Parcelable {

    @JvmOverloads
    constructor(
        throwable: Throwable,
        userAction: UserAction,
        request: String,
        serviceId: Int? = null,
        openInBrowserUrl: String? = null
    ) : this(
        throwableToStringList(throwable),
        userAction,
        request,
        serviceId,
        getMessage(throwable, userAction, serviceId),
        isReportable(throwable),
        isRetryable(throwable),
        (throwable as? ReCaptchaException)?.url,
        openInBrowserUrl
    )

    @JvmOverloads
    constructor(
        throwables: List<Throwable>,
        userAction: UserAction,
        request: String,
        serviceId: Int? = null,
        openInBrowserUrl: String? = null
    ) : this(
        throwableListToStringList(throwables),
        userAction,
        request,
        serviceId,
        getMessage(throwables.firstOrNull(), userAction, serviceId),
        throwables.any(::isReportable),
        throwables.isEmpty() || throwables.any(::isRetryable),
        throwables.firstNotNullOfOrNull { it as? ReCaptchaException }?.url,
        openInBrowserUrl
    )

    // constructor to manually build ErrorInfo when no throwable is available
    constructor(
        stackTraces: Array<String>,
        userAction: UserAction,
        request: String,
        serviceId: Int?,
        @StringRes message: Int
    ) :
        this(
            stackTraces, userAction, request, serviceId, ErrorMessage(message),
            true, false, null, null
        )

    // constructor with only one throwable to extract service id and openInBrowserUrl from an Info
    constructor(
        throwable: Throwable,
        userAction: UserAction,
        request: String,
        info: Info?
    ) :
        this(throwable, userAction, request, info?.serviceId, info?.url)

    // constructor with multiple throwables to extract service id and openInBrowserUrl from an Info
    constructor(
        throwables: List<Throwable>,
        userAction: UserAction,
        request: String,
        info: Info?
    ) :
        this(throwables, userAction, request, info?.serviceId, info?.url)

    fun getServiceName(): String {
        return getServiceName(serviceId)
    }

    fun getMessage(context: Context): String {
        return message.getString(context)
    }

    companion object {
        @Parcelize
        class ErrorMessage(
            @StringRes
            private val stringRes: Int,
            private vararg val formatArgs: String
        ) : Parcelable {
            fun getString(context: Context): String {
                // use Localization.compatGetString() just in case context is not AppCompatActivity
                return if (formatArgs.isEmpty()) {
                    Localization.compatGetString(context, stringRes)
                } else {
                    Localization.compatGetString(context, stringRes, *formatArgs)
                }
            }
        }

        const val SERVICE_NONE = "<unknown_service>"

        private fun getServiceName(serviceId: Int?) = // not using getNameOfServiceById since we want to accept a nullable serviceId and we
            // want to default to SERVICE_NONE
            ServiceList.all()?.firstOrNull { it.serviceId == serviceId }?.serviceInfo?.name
                ?: SERVICE_NONE

        fun throwableToStringList(throwable: Throwable) = arrayOf(throwable.stackTraceToString())

        fun throwableListToStringList(throwableList: List<Throwable>) = throwableList.map { it.stackTraceToString() }.toTypedArray()

        fun getMessage(
            throwable: Throwable?,
            action: UserAction?,
            serviceId: Int?
        ): ErrorMessage {
            return when {
                // player exceptions
                // some may be IOException, so do these checks before isNetworkRelated!
                throwable is ExoPlaybackException -> {
                    val cause = throwable.cause
                    when {
                        cause is HttpDataSource.InvalidResponseCodeException -> {
                            if (cause.responseCode == 403) {
                                if (serviceId == YouTube.serviceId) {
                                    ErrorMessage(R.string.youtube_player_http_403)
                                } else {
                                    ErrorMessage(R.string.player_http_403)
                                }
                            } else {
                                ErrorMessage(R.string.player_http_invalid_status, cause.responseCode.toString())
                            }
                        }

                        cause is Loader.UnexpectedLoaderException && cause.cause is ExtractionException ->
                            getMessage(throwable, action, serviceId)

                        throwable.type == ExoPlaybackException.TYPE_SOURCE ->
                            ErrorMessage(R.string.player_stream_failure)

                        throwable.type == ExoPlaybackException.TYPE_UNEXPECTED ->
                            ErrorMessage(R.string.player_recoverable_failure)

                        else ->
                            ErrorMessage(R.string.player_unrecoverable_failure)
                    }
                }

                throwable is FailedMediaSource.FailedMediaSourceException ->
                    getMessage(throwable.cause, action, serviceId)

                throwable is PlaybackResolver.ResolverException ->
                    ErrorMessage(R.string.player_stream_failure)

                // content not available exceptions
                throwable is AccountTerminatedException ->
                    throwable.message
                        ?.takeIf { reason -> !reason.isEmpty() }
                        ?.let { reason ->
                            ErrorMessage(
                                R.string.account_terminated_service_provides_reason,
                                getServiceName(serviceId),
                                reason
                            )
                        }
                        ?: ErrorMessage(R.string.account_terminated)

                throwable is AgeRestrictedContentException ->
                    ErrorMessage(R.string.restricted_video_no_stream)

                throwable is GeographicRestrictionException ->
                    ErrorMessage(R.string.georestricted_content)

                throwable is PaidContentException ->
                    ErrorMessage(R.string.paid_content)

                throwable is PrivateContentException ->
                    ErrorMessage(R.string.private_content)

                throwable is SoundCloudGoPlusContentException ->
                    ErrorMessage(R.string.soundcloud_go_plus_content)

                throwable is UnsupportedContentInCountryException ->
                    ErrorMessage(R.string.unsupported_content_in_country)

                throwable is YoutubeMusicPremiumContentException ->
                    ErrorMessage(R.string.youtube_music_premium_content)

                throwable is SignInConfirmNotBotException ->
                    ErrorMessage(R.string.sign_in_confirm_not_bot_error, getServiceName(serviceId))

                throwable is ContentNotAvailableException ->
                    ErrorMessage(R.string.content_not_available)

                // other extractor exceptions
                throwable is ContentNotSupportedException ->
                    ErrorMessage(R.string.content_not_supported)

                // ReCaptchas will be handled in a special way anyway
                throwable is ReCaptchaException ->
                    ErrorMessage(R.string.recaptcha_request_toast)

                // test this at the end as many exceptions could be a subclass of IOException
                throwable != null && throwable.isNetworkRelated ->
                    ErrorMessage(R.string.network_error)

                // an extraction exception unrelated to the network
                // is likely an issue with parsing the website
                throwable is ExtractionException ->
                    ErrorMessage(R.string.parsing_error)

                // user actions (in case the exception is null or unrecognizable)
                action == UserAction.UI_ERROR ->
                    ErrorMessage(R.string.app_ui_crash)

                action == UserAction.REQUESTED_COMMENTS ->
                    ErrorMessage(R.string.error_unable_to_load_comments)

                action == UserAction.SUBSCRIPTION_CHANGE ->
                    ErrorMessage(R.string.subscription_change_failed)

                action == UserAction.SUBSCRIPTION_UPDATE ->
                    ErrorMessage(R.string.subscription_update_failed)

                action == UserAction.LOAD_IMAGE ->
                    ErrorMessage(R.string.could_not_load_thumbnails)

                action == UserAction.DOWNLOAD_OPEN_DIALOG ->
                    ErrorMessage(R.string.could_not_setup_download_menu)

                else ->
                    ErrorMessage(R.string.error_snackbar_message)
            }
        }

        fun isReportable(throwable: Throwable?): Boolean {
            return when (throwable) {
                // we don't have an exception, so this is a manually built error, which likely
                // indicates that it's important and is thus reportable
                null -> true

                // if the service explicitly said that content is not available (e.g. age
                // restrictions, video deleted, etc.), there is no use in letting users report it
                is ContentNotAvailableException -> !isContentSurelyNotAvailable(throwable)

                // we know the content is not supported, no need to let the user report it
                is ContentNotSupportedException -> false

                // happens often when there is no internet connection; we don't use
                // `throwable.isNetworkRelated` since any `IOException` would make that function
                // return true, but not all `IOException`s are network related
                is UnknownHostException -> false

                // by default, this is an unexpected exception, which the user could report
                else -> true
            }
        }

        fun isRetryable(throwable: Throwable?): Boolean {
            return when (throwable) {
                // if we know the content is surely not available, retrying won't help
                is ContentNotAvailableException -> !isContentSurelyNotAvailable(throwable)

                // we know the content is not supported, retrying won't help
                is ContentNotSupportedException -> false

                // by default (including if throwable is null), enable retrying (though the retry
                // button will be shown only if a way to perform the retry is implemented)
                else -> true
            }
        }

        /**
         * Unfortunately sometimes [ContentNotAvailableException] may not indicate that the content
         * is blocked/deleted/paid, but may just indicate that we could not extract it. This is an
         * inconsistency in the exceptions thrown by the extractor, but until it is fixed, this
         * function will distinguish between the two types.
         * @return `true` if the content is not available because of a limitation imposed by the
         * service or the owner, `false` if the extractor could not extract info about it
         */
        fun isContentSurelyNotAvailable(e: ContentNotAvailableException): Boolean {
            return when (e) {
                is AccountTerminatedException,
                is AgeRestrictedContentException,
                is GeographicRestrictionException,
                is PaidContentException,
                is PrivateContentException,
                is SoundCloudGoPlusContentException,
                is UnsupportedContentInCountryException,
                is YoutubeMusicPremiumContentException -> true

                else -> false
            }
        }
    }
}
