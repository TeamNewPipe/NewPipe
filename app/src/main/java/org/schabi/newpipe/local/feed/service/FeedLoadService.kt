/*
 * Copyright 2019 Mauricio Colli <mauriciocolli@outlook.com>
 * FeedLoadService.kt is part of NewPipe
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

package org.schabi.newpipe.local.feed.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.reactivex.Flowable
import io.reactivex.Notification
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.MainActivity.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.IdleEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ProgressEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.SuccessResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.postEvent
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.ExceptionUtils
import org.schabi.newpipe.util.ExtractorHelper

class FeedLoadService : Service() {
    companion object {
        private val TAG = FeedLoadService::class.java.simpleName
        private const val NOTIFICATION_ID = 7293450
        private const val ACTION_CANCEL = "org.schabi.newpipe.local.feed.service.FeedLoadService.CANCEL"

        /**
         * How often the notification will be updated.
         */
        private const val NOTIFICATION_SAMPLING_PERIOD = 1500

        /**
         * How many extractions will be running in parallel.
         */
        private const val PARALLEL_EXTRACTIONS = 6

        /**
         * Number of items to buffer to mass-insert in the database.
         */
        private const val BUFFER_COUNT_BEFORE_INSERT = 20

        const val EXTRA_GROUP_ID: String = "FeedLoadService.EXTRA_GROUP_ID"
    }

    private var loadingSubscription: Subscription? = null
    private lateinit var subscriptionManager: SubscriptionManager

    private lateinit var feedDatabaseManager: FeedDatabaseManager
    private lateinit var feedResultsHolder: ResultsHolder

    private var disposables = CompositeDisposable()
    private var notificationUpdater = PublishProcessor.create<String>()

    // /////////////////////////////////////////////////////////////////////////
    // Lifecycle
    // /////////////////////////////////////////////////////////////////////////

    override fun onCreate() {
        super.onCreate()
        subscriptionManager = SubscriptionManager(this)
        feedDatabaseManager = FeedDatabaseManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent + "]," +
                    " flags = [" + flags + "], startId = [" + startId + "]")
        }

        if (intent == null || loadingSubscription != null) {
            return START_NOT_STICKY
        }

        setupNotification()
        setupBroadcastReceiver()
        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
        val useFeedExtractor = defaultSharedPreferences
                .getBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false)

        val thresholdOutdatedSecondsString = defaultSharedPreferences
                .getString(getString(R.string.feed_update_threshold_key), getString(R.string.feed_update_threshold_default_value))
        val thresholdOutdatedSeconds = thresholdOutdatedSecondsString!!.toInt()

        startLoading(groupId, useFeedExtractor, thresholdOutdatedSeconds)

        return START_NOT_STICKY
    }

    private fun disposeAll() {
        unregisterReceiver(broadcastReceiver)

        loadingSubscription?.cancel()
        loadingSubscription = null

        disposables.dispose()
    }

    private fun stopService() {
        disposeAll()
        stopForeground(true)
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // /////////////////////////////////////////////////////////////////////////
    // Loading & Handling
    // /////////////////////////////////////////////////////////////////////////

    private class RequestException(val subscriptionId: Long, message: String, cause: Throwable) : Exception(message, cause) {
        companion object {
            fun wrapList(subscriptionId: Long, info: ListInfo<StreamInfoItem>): List<Throwable> {
                val toReturn = ArrayList<Throwable>(info.errors.size)
                for (error in info.errors) {
                    toReturn.add(RequestException(subscriptionId, info.serviceId.toString() + ":" + info.url, error))
                }
                return toReturn
            }
        }
    }

    private fun startLoading(groupId: Long = FeedGroupEntity.GROUP_ALL_ID, useFeedExtractor: Boolean, thresholdOutdatedSeconds: Int) {
        feedResultsHolder = ResultsHolder()

        val outdatedThreshold = Calendar.getInstance().apply {
            add(Calendar.SECOND, -thresholdOutdatedSeconds)
        }.time

        val subscriptions = when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedDatabaseManager.outdatedSubscriptions(outdatedThreshold)
            else -> feedDatabaseManager.outdatedSubscriptionsForGroup(groupId, outdatedThreshold)
        }

        subscriptions
                .limit(1)

                .doOnNext {
                    currentProgress.set(0)
                    maxProgress.set(it.size)
                }
                .filter { it.isNotEmpty() }

                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    startForeground(NOTIFICATION_ID, notificationBuilder.build())
                    updateNotificationProgress(null)
                    broadcastProgress()
                }

                .observeOn(Schedulers.io())
                .flatMap { Flowable.fromIterable(it) }
                .takeWhile { !cancelSignal.get() }

                .parallel(PARALLEL_EXTRACTIONS, PARALLEL_EXTRACTIONS * 2)
                .runOn(Schedulers.io(), PARALLEL_EXTRACTIONS * 2)
                .filter { !cancelSignal.get() }

                .map { subscriptionEntity ->
                    try {
                        val listInfo = if (useFeedExtractor) {
                            ExtractorHelper
                                    .getFeedInfoFallbackToChannelInfo(subscriptionEntity.serviceId, subscriptionEntity.url)
                                    .blockingGet()
                        } else {
                            ExtractorHelper
                                    .getChannelInfo(subscriptionEntity.serviceId, subscriptionEntity.url, true)
                                    .blockingGet()
                        } as ListInfo<StreamInfoItem>

                        return@map Notification.createOnNext(Pair(subscriptionEntity.uid, listInfo))
                    } catch (e: Throwable) {
                        val request = "${subscriptionEntity.serviceId}:${subscriptionEntity.url}"
                        val wrapper = RequestException(subscriptionEntity.uid, request, e)
                        return@map Notification.createOnError<Pair<Long, ListInfo<StreamInfoItem>>>(wrapper)
                    }
                }
                .sequential()

                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(errorHandlingConsumer)

                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(notificationsConsumer)

                .observeOn(Schedulers.io())
                .buffer(BUFFER_COUNT_BEFORE_INSERT)
                .doOnNext(databaseConsumer)

                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(resultSubscriber)
    }

    private fun broadcastProgress() {
        postEvent(ProgressEvent(currentProgress.get(), maxProgress.get()))
    }

    private val resultSubscriber
        get() = object : Subscriber<List<Notification<Pair<Long, ListInfo<StreamInfoItem>>>>> {

            override fun onSubscribe(s: Subscription) {
                loadingSubscription = s
                s.request(java.lang.Long.MAX_VALUE)
            }

            override fun onNext(notification: List<Notification<Pair<Long, ListInfo<StreamInfoItem>>>>) {
                if (DEBUG) Log.v(TAG, "onNext() → $notification")
            }

            override fun onError(error: Throwable) {
                handleError(error)
            }

            override fun onComplete() {
                if (maxProgress.get() == 0) {
                    postEvent(IdleEvent)
                    stopService()

                    return
                }

                currentProgress.set(-1)
                maxProgress.set(-1)

                notificationUpdater.onNext(getString(R.string.feed_processing_message))
                postEvent(ProgressEvent(R.string.feed_processing_message))

                disposables.add(Single
                        .fromCallable {
                            feedResultsHolder.ready()

                            postEvent(ProgressEvent(R.string.feed_processing_message))
                            feedDatabaseManager.removeOrphansOrOlderStreams()

                            postEvent(SuccessResultEvent(feedResultsHolder.itemsErrors))
                            true
                        }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { _, throwable ->
                            if (throwable != null) {
                                Log.e(TAG, "Error while storing result", throwable)
                                handleError(throwable)
                                return@subscribe
                            }
                            stopService()
                        })
            }
        }

    private val databaseConsumer: Consumer<List<Notification<Pair<Long, ListInfo<StreamInfoItem>>>>>
        get() = Consumer {
            feedDatabaseManager.database().runInTransaction {
                for (notification in it) {

                    if (notification.isOnNext) {
                        val subscriptionId = notification.value!!.first
                        val info = notification.value!!.second

                        feedDatabaseManager.upsertAll(subscriptionId, info.relatedItems)
                        subscriptionManager.updateFromInfo(subscriptionId, info)

                        if (info.errors.isNotEmpty()) {
                            feedResultsHolder.addErrors(RequestException.wrapList(subscriptionId, info))
                            feedDatabaseManager.markAsOutdated(subscriptionId)
                        }
                    } else if (notification.isOnError) {
                        val error = notification.error!!
                        feedResultsHolder.addError(error)

                        if (error is RequestException) {
                            feedDatabaseManager.markAsOutdated(error.subscriptionId)
                        }
                    }
                }
            }
        }

    private val errorHandlingConsumer: Consumer<Notification<Pair<Long, ListInfo<StreamInfoItem>>>>
        get() = Consumer {
            if (it.isOnError) {
                var error = it.error!!
                if (error is RequestException) error = error.cause!!
                val cause = error.cause

                when {
                    error is ReCaptchaException -> throw error
                    cause is ReCaptchaException -> throw cause

                    error is IOException -> throw error
                    cause is IOException -> throw cause
                    ExceptionUtils.isNetworkRelated(error) -> throw IOException(error)
                }
            }
        }

    private val notificationsConsumer: Consumer<Notification<Pair<Long, ListInfo<StreamInfoItem>>>>
        get() = Consumer { onItemCompleted(it.value?.second?.name) }

    private fun onItemCompleted(updateDescription: String?) {
        currentProgress.incrementAndGet()
        notificationUpdater.onNext(updateDescription ?: "")

        broadcastProgress()
    }

    // /////////////////////////////////////////////////////////////////////////
    // Notification
    // /////////////////////////////////////////////////////////////////////////

    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private var currentProgress = AtomicInteger(-1)
    private var maxProgress = AtomicInteger(-1)

    private fun createNotification(): NotificationCompat.Builder {
        val cancelActionIntent = PendingIntent.getBroadcast(this,
                NOTIFICATION_ID, Intent(ACTION_CANCEL), 0)

        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setProgress(-1, -1, true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, getString(R.string.cancel), cancelActionIntent)
                .setContentTitle(getString(R.string.feed_notification_loading))
    }

    private fun setupNotification() {
        notificationManager = NotificationManagerCompat.from(this)
        notificationBuilder = createNotification()

        val throttleAfterFirstEmission = Function { flow: Flowable<String> ->
            flow.limit(1).concatWith(flow.skip(1).throttleLatest(NOTIFICATION_SAMPLING_PERIOD.toLong(), TimeUnit.MILLISECONDS))
        }

        disposables.add(notificationUpdater
                .publish(throttleAfterFirstEmission)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateNotificationProgress))
    }

    private fun updateNotificationProgress(updateDescription: String?) {
        notificationBuilder.setProgress(maxProgress.get(), currentProgress.get(), maxProgress.get() == -1)

        if (maxProgress.get() == -1) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) notificationBuilder.setContentInfo(null)
            if (!updateDescription.isNullOrEmpty()) notificationBuilder.setContentText(updateDescription)
            notificationBuilder.setContentText(updateDescription)
        } else {
            val progressText = this.currentProgress.toString() + "/" + maxProgress

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!updateDescription.isNullOrEmpty()) notificationBuilder.setContentText("$updateDescription  ($progressText)")
            } else {
                notificationBuilder.setContentInfo(progressText)
                if (!updateDescription.isNullOrEmpty()) notificationBuilder.setContentText(updateDescription)
            }
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // /////////////////////////////////////////////////////////////////////////
    // Notification Actions
    // /////////////////////////////////////////////////////////////////////////

    private lateinit var broadcastReceiver: BroadcastReceiver
    private val cancelSignal = AtomicBoolean()

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_CANCEL) {
                    cancelSignal.set(true)
                }
            }
        }
        registerReceiver(broadcastReceiver, IntentFilter(ACTION_CANCEL))
    }

    // /////////////////////////////////////////////////////////////////////////
    // Error handling
    // /////////////////////////////////////////////////////////////////////////

    private fun handleError(error: Throwable) {
        postEvent(ErrorResultEvent(error))
        stopService()
    }

    // /////////////////////////////////////////////////////////////////////////
    // Results Holder
    // /////////////////////////////////////////////////////////////////////////

    class ResultsHolder {
        /**
         * List of errors that may have happen during loading.
         */
        internal lateinit var itemsErrors: List<Throwable>

        private val itemsErrorsHolder: MutableList<Throwable> = ArrayList()

        fun addError(error: Throwable) {
            itemsErrorsHolder.add(error)
        }

        fun addErrors(errors: List<Throwable>) {
            itemsErrorsHolder.addAll(errors)
        }

        fun ready() {
            itemsErrors = itemsErrorsHolder.toList()
        }
    }
}
