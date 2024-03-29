package org.schabi.newpipe.error

import android.os.Parcelable
import androidx.annotation.StringRes
import com.google.android.exoplayer2.ExoPlaybackException
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.ktx.isNetworkRelated
import org.schabi.newpipe.util.ServiceHelper

@Parcelize
class ErrorInfo(
    val stackTraces: Array<String>,
    val userAction: UserAction,
    val serviceName: String,
    val request: String,
    val messageStringId: Int
) : Parcelable {

    // no need to store throwable, all data for report is in other variables
    // also, the throwable might not be serializable, see TeamNewPipe/NewPipe#7302
    @IgnoredOnParcel
    var throwable: Throwable? = null

    private constructor(
        throwable: Throwable,
        userAction: UserAction,
        serviceName: String,
        request: String
    ) : this(
        throwableToStringList(throwable),
        userAction,
        serviceName,
        request,
        getMessageStringId(throwable, userAction)
    ) {
        this.throwable = throwable
    }

    private constructor(
        throwable: List<Throwable>,
        userAction: UserAction,
        serviceName: String,
        request: String
    ) : this(
        throwableListToStringList(throwable),
        userAction,
        serviceName,
        request,
        getMessageStringId(throwable.firstOrNull(), userAction)
    ) {
        this.throwable = throwable.firstOrNull()
    }

    // constructors with single throwable
    constructor(throwable: Throwable, userAction: UserAction, request: String) :
        this(throwable, userAction, SERVICE_NONE, request)
    constructor(throwable: Throwable, userAction: UserAction, request: String, serviceId: Int) :
        this(throwable, userAction, ServiceHelper.getNameOfServiceById(serviceId), request)
    constructor(throwable: Throwable, userAction: UserAction, request: String, info: Info?) :
        this(throwable, userAction, getInfoServiceName(info), request)

    // constructors with list of throwables
    constructor(throwable: List<Throwable>, userAction: UserAction, request: String) :
        this(throwable, userAction, SERVICE_NONE, request)
    constructor(throwable: List<Throwable>, userAction: UserAction, request: String, serviceId: Int) :
        this(throwable, userAction, ServiceHelper.getNameOfServiceById(serviceId), request)
    constructor(throwable: List<Throwable>, userAction: UserAction, request: String, info: Info?) :
        this(throwable, userAction, getInfoServiceName(info), request)

    companion object {
        const val SERVICE_NONE = "none"

        fun throwableToStringList(throwable: Throwable) = arrayOf(throwable.stackTraceToString())

        fun throwableListToStringList(throwableList: List<Throwable>) =
            throwableList.map { it.stackTraceToString() }.toTypedArray()

        private fun getInfoServiceName(info: Info?) =
            if (info == null) SERVICE_NONE else ServiceHelper.getNameOfServiceById(info.serviceId)

        @StringRes
        private fun getMessageStringId(
            throwable: Throwable?,
            action: UserAction
        ): Int {
            return when {
                throwable is AccountTerminatedException -> R.string.account_terminated
                throwable is ContentNotAvailableException -> R.string.content_not_available
                throwable != null && throwable.isNetworkRelated -> R.string.network_error
                throwable is ContentNotSupportedException -> R.string.content_not_supported
                throwable is ExtractionException -> R.string.parsing_error
                throwable is ExoPlaybackException -> {
                    when (throwable.type) {
                        ExoPlaybackException.TYPE_SOURCE -> R.string.player_stream_failure
                        ExoPlaybackException.TYPE_UNEXPECTED -> R.string.player_recoverable_failure
                        else -> R.string.player_unrecoverable_failure
                    }
                }
                action == UserAction.UI_ERROR -> R.string.app_ui_crash
                action == UserAction.REQUESTED_COMMENTS -> R.string.error_unable_to_load_comments
                action == UserAction.SUBSCRIPTION_CHANGE -> R.string.subscription_change_failed
                action == UserAction.SUBSCRIPTION_UPDATE -> R.string.subscription_update_failed
                action == UserAction.LOAD_IMAGE -> R.string.could_not_load_thumbnails
                action == UserAction.DOWNLOAD_OPEN_DIALOG -> R.string.could_not_setup_download_menu
                else -> R.string.general_error
            }
        }
    }
}
