package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PeertubeHelper {
    private PeertubeHelper() { }

    public static List<PeertubeInstance> getInstanceList(final Context context) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        String savedInstanceListKey = context.getString(R.string.peertube_instance_list_key);
        final String savedJson = sharedPreferences.getString(savedInstanceListKey, null);
        if (null == savedJson) {
            return Collections.singletonList(getCurrentInstance());
        }

        try {
            JsonArray array = JsonParser.object().from(savedJson).getArray("instances");
            List<PeertubeInstance> result = new ArrayList<>();
            for (Object o : array) {
                if (o instanceof JsonObject) {
                    JsonObject instance = (JsonObject) o;
                    String name = instance.getString("name");
                    String url = instance.getString("url");
                    result.add(new PeertubeInstance(url, name));
                }
            }
            return result;
        } catch (JsonParserException e) {
            return Collections.singletonList(getCurrentInstance());
        }

    }

    public static PeertubeInstance selectInstance(final PeertubeInstance instance,
                                                  final Context context) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        String selectedInstanceKey = context.getString(R.string.peertube_selected_instance_key);
        JsonStringWriter jsonWriter = JsonWriter.string().object();
        jsonWriter.value("name", instance.getName());
        jsonWriter.value("url", instance.getUrl());
        String jsonToSave = jsonWriter.end().done();
        sharedPreferences.edit().putString(selectedInstanceKey, jsonToSave).apply();
        ServiceList.PeerTube.setInstance(instance);
        return instance;
    }

    public static PeertubeInstance getCurrentInstance() {
        return ServiceList.PeerTube.getInstance();
    }
}
