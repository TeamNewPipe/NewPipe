/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * ImportExportJsonHelper.java is part of NewPipe
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

import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.extractor.subscription.SubscriptionItem
import java.io.InputStream
import java.io.OutputStream

/**
 * A JSON implementation capable of importing and exporting subscriptions, it has the advantage
 * of being able to transfer subscriptions to any device.
 */
object ImportExportJsonHelper {
    /*//////////////////////////////////////////////////////////////////////////
    // Json implementation
    ////////////////////////////////////////////////////////////////////////// */
    private val JSON_APP_VERSION_KEY: String = "app_version"
    private val JSON_APP_VERSION_INT_KEY: String = "app_version_int"
    private val JSON_SUBSCRIPTIONS_ARRAY_KEY: String = "subscriptions"
    private val JSON_SERVICE_ID_KEY: String = "service_id"
    private val JSON_URL_KEY: String = "url"
    private val JSON_NAME_KEY: String = "name"

    /**
     * Read a JSON source through the input stream.
     *
     * @param in            the input stream (e.g. a file)
     * @param eventListener listener for the events generated
     * @return the parsed subscription items
     */
    @JvmStatic
    @Throws(InvalidSourceException::class)
    fun readFrom(
            `in`: InputStream?, eventListener: ImportExportEventListener?): List<SubscriptionItem> {
        if (`in` == null) {
            throw InvalidSourceException("input is null")
        }
        val channels: MutableList<SubscriptionItem> = ArrayList()
        try {
            val parentObject: JsonObject = JsonParser.`object`().from(`in`)
            if (!parentObject.has(JSON_SUBSCRIPTIONS_ARRAY_KEY)) {
                throw InvalidSourceException("Channels array is null")
            }
            val channelsArray: JsonArray = parentObject.getArray(JSON_SUBSCRIPTIONS_ARRAY_KEY)
            if (eventListener != null) {
                eventListener.onSizeReceived(channelsArray.size)
            }
            for (o: Any in channelsArray) {
                if (o is JsonObject) {
                    val itemObject: JsonObject = o as JsonObject
                    val serviceId: Int = itemObject.getInt(JSON_SERVICE_ID_KEY, 0)
                    val url: String? = itemObject.getString(JSON_URL_KEY)
                    val name: String? = itemObject.getString(JSON_NAME_KEY)
                    if ((url != null) && (name != null) && !url.isEmpty() && !name.isEmpty()) {
                        channels.add(SubscriptionItem(serviceId, url, name))
                        if (eventListener != null) {
                            eventListener.onItemCompleted(name)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            throw InvalidSourceException("Couldn't parse json", e)
        }
        return channels
    }

    /**
     * Write the subscriptions items list as JSON to the output.
     *
     * @param items         the list of subscriptions items
     * @param out           the output stream (e.g. a file)
     * @param eventListener listener for the events generated
     */
    fun writeTo(items: List<SubscriptionItem?>?, out: OutputStream?,
                eventListener: ImportExportEventListener?) {
        val writer: JsonAppendableWriter = JsonWriter.on(out)
        writeTo(items, writer, eventListener)
        writer.done()
    }

    /**
     * @see .writeTo
     * @param items         the list of subscriptions items
     * @param writer        the output [JsonAppendableWriter]
     * @param eventListener listener for the events generated
     */
    fun writeTo(items: List<SubscriptionItem>,
                writer: JsonAppendableWriter,
                eventListener: ImportExportEventListener?) {
        if (eventListener != null) {
            eventListener.onSizeReceived(items.size)
        }
        writer.`object`()
        writer.value(JSON_APP_VERSION_KEY, BuildConfig.VERSION_NAME)
        writer.value(JSON_APP_VERSION_INT_KEY, BuildConfig.VERSION_CODE)
        writer.array(JSON_SUBSCRIPTIONS_ARRAY_KEY)
        for (item: SubscriptionItem in items) {
            writer.`object`()
            writer.value(JSON_SERVICE_ID_KEY, item.getServiceId())
            writer.value(JSON_URL_KEY, item.getUrl())
            writer.value(JSON_NAME_KEY, item.getName())
            writer.end()
            if (eventListener != null) {
                eventListener.onItemCompleted(item.getName())
            }
        }
        writer.end()
        writer.end()
    }
}
