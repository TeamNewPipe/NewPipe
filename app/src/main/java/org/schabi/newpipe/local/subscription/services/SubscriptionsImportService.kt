/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * SubscriptionsImportService.java is part of NewPipe
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

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import androidx.core.content.IntentCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.schedulers.Schedulers
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.subscription.SubscriptionItem
import org.schabi.newpipe.ktx.isNetworkRelated
import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ExtractorHelper
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.concurrent.Callable

class SubscriptionsImportService() : BaseImportExportService() {
    private var subscription: Subscription? = null
    private var currentMode: Int = 0
    private var currentServiceId: Int = 0
    private var channelUrl: String? = null
    private var inputStream: InputStream? = null
    private var inputStreamType: String? = null
    public override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent == null || subscription != null) {
            return START_NOT_STICKY
        }
        currentMode = intent.getIntExtra(KEY_MODE, -1)
        currentServiceId = intent.getIntExtra(KEY_SERVICE_ID, NO_SERVICE_ID)
        if (currentMode == CHANNEL_URL_MODE) {
            channelUrl = intent.getStringExtra(KEY_VALUE)
        } else {
            val uri: Uri? = IntentCompat.getParcelableExtra(intent, KEY_VALUE, Uri::class.java)
            if (uri == null) {
                stopAndReportError(IllegalStateException(
                        "Importing from input stream, but file path is null"),
                        "Importing subscriptions")
                return START_NOT_STICKY
            }
            try {
                val fileHelper: StoredFileHelper = StoredFileHelper(this, uri, StoredFileHelper.Companion.DEFAULT_MIME)
                inputStream = SharpInputStream(fileHelper.getStream())
                inputStreamType = fileHelper.getType()
                if (inputStreamType == null || (inputStreamType == StoredFileHelper.Companion.DEFAULT_MIME)) {
                    // mime type could not be determined, just take file extension
                    val name: String? = fileHelper.getName()
                    val pointIndex: Int = name!!.lastIndexOf('.')
                    if (pointIndex == -1 || pointIndex >= name.length - 1) {
                        inputStreamType = StoredFileHelper.Companion.DEFAULT_MIME // no extension, will fail in the extractor
                    } else {
                        inputStreamType = name.substring(pointIndex + 1)
                    }
                }
            } catch (e: IOException) {
                handleError(e)
                return START_NOT_STICKY
            }
        }
        if (currentMode == -1 || currentMode == CHANNEL_URL_MODE && channelUrl == null) {
            val errorDescription: String = ("Some important field is null or in illegal state: "
                    + "currentMode=[" + currentMode + "], "
                    + "channelUrl=[" + channelUrl + "], "
                    + "inputStream=[" + inputStream + "]")
            stopAndReportError(IllegalStateException(errorDescription),
                    "Importing subscriptions")
            return START_NOT_STICKY
        }
        startImport()
        return START_NOT_STICKY
    }

    protected override val notificationId: Int
        protected get() {
            return 4568
        }
    override val title: Int
        get() {
            return R.string.import_ongoing
        }

    override fun disposeAll() {
        super.disposeAll()
        if (subscription != null) {
            subscription!!.cancel()
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Imports
    ////////////////////////////////////////////////////////////////////////// */
    private fun startImport() {
        showToast(R.string.import_ongoing)
        var flowable: Flowable<List<SubscriptionItem?>?>? = null
        when (currentMode) {
            CHANNEL_URL_MODE -> flowable = importFromChannelUrl()
            INPUT_STREAM_MODE -> flowable = importFromInputStream()
            PREVIOUS_EXPORT_MODE -> flowable = importFromPreviousExport()
        }
        if (flowable == null) {
            val message: String = ("Flowable given by \"importFrom\" is null "
                    + "(current mode: " + currentMode + ")")
            stopAndReportError(IllegalStateException(message), "Importing subscriptions")
            return
        }
        flowable.doOnNext(Consumer({ subscriptionItems: List<SubscriptionItem?>? -> eventListener.onSizeReceived(subscriptionItems!!.size) }))
                .flatMap(Function<List<SubscriptionItem?>?, Publisher<out SubscriptionItem>>({ source: List<SubscriptionItem?>? -> Flowable.fromIterable(source) }))
                .parallel(PARALLEL_EXTRACTIONS)
                .runOn(Schedulers.io())
                .map(Function({ subscriptionItem: SubscriptionItem ->
                    try {
                        val channelInfo: ChannelInfo? = ExtractorHelper.getChannelInfo(subscriptionItem.getServiceId(),
                                subscriptionItem.getUrl(), true)
                                .blockingGet()
                        return@Function Notification.createOnNext<Pair<ChannelInfo?, List<ChannelTabInfo?>>>(Pair<ChannelInfo?, List<ChannelTabInfo?>>(channelInfo, listOf<ChannelTabInfo?>(
                                ExtractorHelper.getChannelTab(
                                        subscriptionItem.getServiceId(),
                                        channelInfo!!.getTabs().get(0), true).blockingGet()
                        )))
                    } catch (e: Throwable) {
                        return@Function Notification.createOnError<Pair<ChannelInfo?, List<ChannelTabInfo?>>>(e)
                    }
                }) as Function<SubscriptionItem, Notification<Pair<ChannelInfo?, List<ChannelTabInfo?>>>>?)
                .sequential()
                .observeOn(Schedulers.io())
                .doOnNext(notificationsConsumer)
                .buffer(BUFFER_COUNT_BEFORE_INSERT)
                .map(upsertBatch())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber)
    }

    private val subscriber: Subscriber<List<SubscriptionEntity>>
        private get() {
            return object : Subscriber<List<SubscriptionEntity?>> {
                public override fun onSubscribe(s: Subscription) {
                    subscription = s
                    s.request(Long.MAX_VALUE)
                }

                public override fun onNext(successfulInserted: List<SubscriptionEntity?>) {
                    if (MainActivity.Companion.DEBUG) {
                        Log.d(TAG, ("startImport() " + successfulInserted.size
                                + " items successfully inserted into the database"))
                    }
                }

                public override fun onError(error: Throwable) {
                    Log.e(TAG, "Got an error!", error)
                    handleError(error)
                }

                public override fun onComplete() {
                    LocalBroadcastManager.getInstance(this@SubscriptionsImportService)
                            .sendBroadcast(Intent(IMPORT_COMPLETE_ACTION))
                    showToast(R.string.import_complete_toast)
                    stopService()
                }
            }
        }
    private val notificationsConsumer: Consumer<Notification<Pair<ChannelInfo?, List<ChannelTabInfo?>>>>
        private get() {
            return Consumer({ notification: Notification<Pair<ChannelInfo?, List<ChannelTabInfo?>>> ->
                if (notification.isOnNext()) {
                    val name: String = notification.getValue()!!.first!!.getName()
                    eventListener.onItemCompleted(if (!TextUtils.isEmpty(name)) name else "")
                } else if (notification.isOnError()) {
                    val error: Throwable? = notification.getError()
                    val cause: Throwable? = error!!.cause
                    if (error is IOException) {
                        throw error
                    } else if (cause is IOException) {
                        throw cause
                    } else if (error.isNetworkRelated) {
                        throw IOException(error)
                    }
                    eventListener.onItemCompleted("")
                }
            })
        }

    private fun upsertBatch(): Function<List<Notification<Pair<ChannelInfo?, List<ChannelTabInfo?>>>>, List<SubscriptionEntity>> {
        return Function({ notificationList: List<Notification<Pair<ChannelInfo?, List<ChannelTabInfo?>>>> ->
            val infoList: MutableList<Pair<ChannelInfo?, List<ChannelTabInfo?>>> = ArrayList(notificationList.size)
            for (n: Notification<Pair<ChannelInfo?, List<ChannelTabInfo?>>> in notificationList) {
                if (n.isOnNext()) {
                    infoList.add((n.getValue())!!)
                }
            }
            subscriptionManager!!.upsertAll(infoList)
        })
    }

    private fun importFromChannelUrl(): Flowable<List<SubscriptionItem?>?> {
        return Flowable.fromCallable(Callable({
            NewPipe.getService(currentServiceId)
                    .getSubscriptionExtractor()
                    .fromChannelUrl(channelUrl)
        }))
    }

    private fun importFromInputStream(): Flowable<List<SubscriptionItem?>?> {
        Objects.requireNonNull(inputStream)
        Objects.requireNonNull(inputStreamType)
        return Flowable.fromCallable(Callable({
            NewPipe.getService(currentServiceId)
                    .getSubscriptionExtractor()
                    .fromInputStream((inputStream)!!, (inputStreamType)!!)
        }))
    }

    private fun importFromPreviousExport(): Flowable<List<SubscriptionItem?>?> {
        return Flowable.fromCallable(Callable({ ImportExportJsonHelper.readFrom(inputStream, null) }))
    }

    protected fun handleError(error: Throwable) {
        super.handleError(R.string.subscriptions_import_unsuccessful, error)
    }

    companion object {
        val CHANNEL_URL_MODE: Int = 0
        val INPUT_STREAM_MODE: Int = 1
        val PREVIOUS_EXPORT_MODE: Int = 2
        val KEY_MODE: String = "key_mode"
        val KEY_VALUE: String = "key_value"

        /**
         * A [local broadcast][LocalBroadcastManager] will be made with this action
         * when the import is successfully completed.
         */
        val IMPORT_COMPLETE_ACTION: String = (App.Companion.PACKAGE_NAME + ".local.subscription"
                + ".services.SubscriptionsImportService.IMPORT_COMPLETE")

        /**
         * How many extractions running in parallel.
         */
        val PARALLEL_EXTRACTIONS: Int = 8

        /**
         * Number of items to buffer to mass-insert in the subscriptions table,
         * this leads to a better performance as we can then use db transactions.
         */
        val BUFFER_COUNT_BEFORE_INSERT: Int = 50
    }
}
