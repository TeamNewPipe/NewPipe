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

package org.schabi.newpipe.local.subscription.workers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.InvalidSourceException
import java.io.InputStream
import java.io.OutputStream

/**
 * A JSON implementation capable of importing and exporting subscriptions, it has the advantage
 * of being able to transfer subscriptions to any device.
 */
object ImportExportJsonHelper {
    private val json = Json { encodeDefaults = true }

    /**
     * Read a JSON source through the input stream.
     *
     * @param in            the input stream (e.g. a file)
     * @return the parsed subscription items
     */
    @JvmStatic
    @Throws(InvalidSourceException::class)
    fun readFrom(`in`: InputStream?): List<SubscriptionItem> {
        if (`in` == null) {
            throw InvalidSourceException("input is null")
        }

        try {
            @OptIn(ExperimentalSerializationApi::class)
            return json.decodeFromStream<SubscriptionData>(`in`).subscriptions
        } catch (e: Throwable) {
            throw InvalidSourceException("Couldn't parse json", e)
        }
    }

    /**
     * Write the subscriptions items list as JSON to the output.
     *
     * @param items         the list of subscriptions items
     * @param out           the output stream (e.g. a file)
     */
    @OptIn(ExperimentalSerializationApi::class)
    @JvmStatic
    fun writeTo(
        items: List<SubscriptionItem>,
        out: OutputStream,
    ) {
        json.encodeToStream(SubscriptionData(items), out)
    }
}
