package org.schabi.newpipe.util.service_display

import androidx.annotation.StringRes
import org.schabi.newpipe.extractor.ServiceList

sealed class LocalizationService {

    companion object {
        @JvmStatic
        fun of(serviceId: Int): LocalizationService? =
            when (serviceId) {
                ServiceList.BiliBili.serviceId -> BiliBiliLocalizationService
                else -> null
            }
    }

    @StringRes
    open fun localizeStatKey(key: String): Int = 0

    open fun handleStatContent(key: String, content: String): String = content

    fun processStats(stat: Map<String, String>): Map<Int, String> {
        return stat.toSortedMap()
            .mapValues { (rawKey, content) ->
                handleStatContent(rawKey, content)
            }.mapKeys { (key, _) ->
                localizeStatKey(key)
            }.filter { (res, value) ->
                res > 0 && value.isNotEmpty() && value != "0"
            }
    }

}