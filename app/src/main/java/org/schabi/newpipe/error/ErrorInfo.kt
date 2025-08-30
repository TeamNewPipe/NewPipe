package org.schabi.newpipe.error

import android.content.Context
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.Loader
import kotlinx.parcelize.IgnoredOnParcel
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

@Parcelize
class ErrorInfo private constructor(
    val stackTraces: Array<String>,
    val userAction: UserAction,
    val serviceId: Int?,
    val request: String,
    private val message: ErrorMessage,
) : Parcelable {

    // no need to store throwable, all data for report is in other variables
    // also, the throwable might not be serializable, see TeamNewPipe/NewPipe#7302
    @IgnoredOnParcel
    var throwable: Throwable? = null

    private constructor(
        throwable: Throwable,
        userAction: UserAction,
        serviceId: Int?,
        request: String
    ) : this(
        throwableToStringList(throwable),
        userAction,
        serviceId,
        request,
        getMessage(throwable, userAction, serviceId)
    ) {
        this.throwable = throwable
    }

    private constructor(
        throwable: List<Throwable>,
        userAction: UserAction,
        serviceId: Int?,
        request: String
    ) : this(
        throwableListToStringList(throwable),
        userAction,
        serviceId,
        request,
        getMessage(throwable.firstOrNull(), userAction, serviceId)
    ) {
        this.throwable = throwable.firstOrNull()
    }

    // constructor to manually build ErrorInfo
    constructor(stackTraces: Array<String>, userAction: UserAction, serviceId: Int?, request: String, @StringRes message: Int) :
        this(stackTraces, userAction, serviceId, request, ErrorMessage(message))

    // constructors with single throwable
    constructor(throwable: Throwable, userAction: UserAction, request: String) :
        this(throwable, userAction, null, request)
    constructor(throwable: Throwable, userAction: UserAction, request: String, serviceId: Int) :
        this(throwable, userAction, serviceId, request)
    constructor(throwable: Throwable, userAction: UserAction, request: String, info: Info?) :
        this(throwable, userAction, info?.serviceId, request)

    // constructors with list of throwables
    constructor(throwable: List<Throwable>, userAction: UserAction, request: String) :
        this(throwable, userAction, null, request)
    constructor(throwable: List<Throwable>, userAction: UserAction, request: String, serviceId: Int) :
        this(throwable, userAction, serviceId, request)
    constructor(throwable: List<Throwable>, userAction: UserAction, request: String, info: Info?) :
        this(throwable, userAction, info?.serviceId, request)

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
            private vararg val formatArgs: String,
        ) : Parcelable {
            fun getString(context: Context): String {
                return if (formatArgs.isEmpty()) {
                    // use ContextCompat.getString() just in case context is not AppCompatActivity
                    ContextCompat.getString(context, stringRes)
                } else {
                    // ContextCompat.getString() with formatArgs does not exist, so we just
                    // replicate its source code but with formatArgs
                    ContextCompat.getContextForLanguage(context).getString(stringRes, *formatArgs)
                }
            }
        }

        const val SERVICE_NONE = "<unknown_service>"

        private fun getServiceName(serviceId: Int?) =
            // not using getNameOfServiceById since we want to accept a nullable serviceId and we
            // want to default to SERVICE_NONE
            ServiceList.all()?.firstOrNull { it.serviceId == serviceId }?.serviceInfo?.name
                ?: SERVICE_NONE

        fun throwableToStringList(throwable: Throwable) = arrayOf(throwable.stackTraceToString())

        fun throwableListToStringList(throwableList: List<Throwable>) =
            throwableList.map { it.stackTraceToString() }.toTypedArray()

        fun getMessage(
            throwable: Throwable?,
            action: UserAction?,
            serviceId: Int?,
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
                // ReCaptchas should have already been handled elsewhere,
                // but return an error message here just in case
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
    }
}
