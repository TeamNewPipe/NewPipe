package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonStringWriter
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance

object PeertubeHelper {
    fun getInstanceList(context: Context): List<PeertubeInstance> {
        val sharedPreferences: SharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context)
        val savedInstanceListKey: String = context.getString(R.string.peertube_instance_list_key)
        val savedJson: String? = sharedPreferences.getString(savedInstanceListKey, null)
        if (null == savedJson) {
            return java.util.List.of(currentInstance)
        }
        try {
            val array: JsonArray = JsonParser.`object`().from(savedJson).getArray("instances")
            val result: MutableList<PeertubeInstance> = ArrayList()
            for (o: Any? in array) {
                if (o is JsonObject) {
                    val instance: JsonObject = o
                    val name: String = instance.getString("name")
                    val url: String = instance.getString("url")
                    result.add(PeertubeInstance(url, name))
                }
            }
            return result
        } catch (e: JsonParserException) {
            return java.util.List.of(currentInstance)
        }
    }

    fun selectInstance(instance: PeertubeInstance?,
                       context: Context): PeertubeInstance? {
        val sharedPreferences: SharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context)
        val selectedInstanceKey: String = context.getString(R.string.peertube_selected_instance_key)
        val jsonWriter: JsonStringWriter = JsonWriter.string().`object`()
        jsonWriter.value("name", instance!!.getName())
        jsonWriter.value("url", instance.getUrl())
        val jsonToSave: String = jsonWriter.end().done()
        sharedPreferences.edit().putString(selectedInstanceKey, jsonToSave).apply()
        ServiceList.PeerTube.setInstance(instance)
        return instance
    }

    val currentInstance: PeertubeInstance
        get() {
            return ServiceList.PeerTube.getInstance()
        }
}
