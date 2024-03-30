/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * BaseImportExportService.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.local.subscription.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.processors.PublishProcessor
import org.reactivestreams.Publisher
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.createNotification
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.InvalidSourceException
import org.schabi.newpipe.ktx.isNetworkRelated
import org.schabi.newpipe.local.subscription.SubscriptionManager
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

abstract class BaseImportExportService() : Service() {
    protected val TAG: String = this.javaClass.getSimpleName()
    protected val disposables: CompositeDisposable = CompositeDisposable()
    protected val notificationUpdater: PublishProcessor<String> = PublishProcessor.create()
    protected var notificationManager: NotificationManagerCompat? = null
    protected var notificationBuilder: NotificationCompat.Builder? = null
    protected var subscriptionManager: SubscriptionManager? = null
    protected val currentProgress: AtomicInteger = AtomicInteger(-1)
    protected val maxProgress: AtomicInteger = AtomicInteger(-1)
    protected val eventListener: ImportExportEventListener = object : ImportExportEventListener {
        public override fun onSizeReceived(size: Int) {
            maxProgress.set(size)
            currentProgress.set(0)
        }

        public override fun onItemCompleted(itemName: String) {
            currentProgress.incrementAndGet()
            notificationUpdater.onNext(itemName)
        }
    }
    protected var toast: Toast? = null
    public override fun onBind(intent: Intent): IBinder? {
        return null
    }

    public override fun onCreate() {
        super.onCreate()
        subscriptionManager = SubscriptionManager(this)
        setupNotification()
    }

    public override fun onDestroy() {
        super.onDestroy()
        disposeAll()
    }

    protected open fun disposeAll() {
        disposables.clear()
    }

    protected abstract val notificationId: Int

    @get:StringRes
    abstract val title: Int
    protected fun setupNotification() {
        notificationManager = NotificationManagerCompat.from(this)
        notificationBuilder = createNotification()
        startForeground(notificationId, notificationBuilder!!.build())
        val throttleAfterFirstEmission: Function<Flowable<String?>, Publisher<String?>> = Function({ flow: Flowable<String?> ->
            flow.take(1).concatWith(flow.skip(1)
                    .throttleLast(NOTIFICATION_SAMPLING_PERIOD.toLong(), TimeUnit.MILLISECONDS))
        })
        disposables.add(notificationUpdater
                .filter(Predicate({ s: String -> !s.isEmpty() }))
                .publish(throttleAfterFirstEmission)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer({ text: String -> updateNotification(text) })))
    }

    protected fun updateNotification(text: String) {
        notificationBuilder
                .setProgress(maxProgress.get(), currentProgress.get(), maxProgress.get() == -1)
        val progressText: String = currentProgress.toString() + "/" + maxProgress
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!TextUtils.isEmpty(text)) {
                notificationBuilder!!.setContentText(text + "  (" + progressText + ")")
            }
        } else {
            notificationBuilder!!.setContentInfo(progressText)
            notificationBuilder!!.setContentText(text)
        }
        notificationManager!!.notify(notificationId, notificationBuilder!!.build())
    }

    protected fun stopService() {
        postErrorResult(null, null)
    }

    protected fun stopAndReportError(throwable: Throwable?, request: String?) {
        stopService()
        createNotification(this, ErrorInfo(
                (throwable)!!, UserAction.SUBSCRIPTION_IMPORT_EXPORT, (request)!!))
    }

    protected fun postErrorResult(title: String?, text: String?) {
        disposeAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (title == null) {
            return
        }
        val textOrEmpty: String = if (text == null) "" else text
        notificationBuilder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(textOrEmpty))
                .setContentText(textOrEmpty)
        notificationManager!!.notify(notificationId, notificationBuilder!!.build())
    }

    protected fun createNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setProgress(-1, -1, true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(getString(title))
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Toast
    ////////////////////////////////////////////////////////////////////////// */
    protected fun showToast(@StringRes message: Int) {
        showToast(getString(message))
    }

    protected fun showToast(message: String?) {
        if (toast != null) {
            toast!!.cancel()
        }
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.show()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error handling
    ////////////////////////////////////////////////////////////////////////// */
    protected fun handleError(@StringRes errorTitle: Int, error: Throwable) {
        var message: String? = getErrorMessage(error)
        if (TextUtils.isEmpty(message)) {
            val errorClassName: String = error.javaClass.getName()
            message = getString(R.string.error_occurred_detail, errorClassName)
        }
        showToast(errorTitle)
        postErrorResult(getString(errorTitle), message)
    }

    protected fun getErrorMessage(error: Throwable): String? {
        var message: String? = null
        if (error is InvalidSourceException) {
            message = getString(R.string.invalid_source)
        } else if (error is FileNotFoundException) {
            message = getString(R.string.invalid_file)
        } else if (error.isNetworkRelated) {
            message = getString(R.string.network_error)
        }
        return message
    }

    companion object {
        private val NOTIFICATION_SAMPLING_PERIOD: Int = 2500
    }
}
