/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * SubscriptionsExportService.java is part of NewPipe
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
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.schedulers.Schedulers
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.subscription.SubscriptionItem
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import java.io.IOException
import java.io.OutputStream

class SubscriptionsExportService() : BaseImportExportService() {
    private var subscription: Subscription? = null
    private var outFile: StoredFileHelper? = null
    private var outputStream: OutputStream? = null
    public override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent == null || subscription != null) {
            return START_NOT_STICKY
        }
        val path: Uri? = IntentCompat.getParcelableExtra(intent, KEY_FILE_PATH, Uri::class.java)
        if (path == null) {
            stopAndReportError(IllegalStateException(
                    "Exporting to a file, but the path is null"),
                    "Exporting subscriptions")
            return START_NOT_STICKY
        }
        try {
            outFile = StoredFileHelper(this, path, "application/json")
            outputStream = SharpOutputStream(outFile!!.getStream())
        } catch (e: IOException) {
            handleError(e)
            return START_NOT_STICKY
        }
        startExport()
        return START_NOT_STICKY
    }

    protected override val notificationId: Int
        protected get() {
            return 4567
        }
    override val title: Int
        get() {
            return R.string.export_ongoing
        }

    override fun disposeAll() {
        super.disposeAll()
        if (subscription != null) {
            subscription!!.cancel()
        }
    }

    private fun startExport() {
        showToast(R.string.export_ongoing)
        subscriptionManager!!.subscriptionTable().getAll().take(1)
                .map(Function<List<SubscriptionEntity>, List<SubscriptionItem>>({ subscriptionEntities: List<SubscriptionEntity> ->
                    val result: MutableList<SubscriptionItem> = ArrayList(subscriptionEntities.size)
                    for (entity: SubscriptionEntity in subscriptionEntities) {
                        result.add(SubscriptionItem(entity.getServiceId(), entity.getUrl(),
                                entity.getName()))
                    }
                    result
                }))
                .map(exportToFile())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber)
    }

    private val subscriber: Subscriber<StoredFileHelper?>
        private get() {
            return object : Subscriber<StoredFileHelper> {
                public override fun onSubscribe(s: Subscription) {
                    subscription = s
                    s.request(1)
                }

                public override fun onNext(file: StoredFileHelper) {
                    if (MainActivity.Companion.DEBUG) {
                        Log.d(TAG, "startExport() success: file = " + file)
                    }
                }

                public override fun onError(error: Throwable) {
                    Log.e(TAG, "onError() called with: error = [" + error + "]", error)
                    handleError(error)
                }

                public override fun onComplete() {
                    LocalBroadcastManager.getInstance(this@SubscriptionsExportService)
                            .sendBroadcast(Intent(EXPORT_COMPLETE_ACTION))
                    showToast(R.string.export_complete_toast)
                    stopService()
                }
            }
        }

    private fun exportToFile(): Function<List<SubscriptionItem>, StoredFileHelper?> {
        return Function({ subscriptionItems: List<SubscriptionItem>? ->
            ImportExportJsonHelper.writeTo(subscriptionItems, outputStream, eventListener)
            outFile
        })
    }

    protected fun handleError(error: Throwable) {
        super.handleError(R.string.subscriptions_export_unsuccessful, error)
    }

    companion object {
        val KEY_FILE_PATH: String = "key_file_path"

        /**
         * A [local broadcast][LocalBroadcastManager] will be made with this action
         * when the export is successfully completed.
         */
        val EXPORT_COMPLETE_ACTION: String = (App.Companion.PACKAGE_NAME + ".local.subscription"
                + ".services.SubscriptionsExportService.EXPORT_COMPLETE")
    }
}
