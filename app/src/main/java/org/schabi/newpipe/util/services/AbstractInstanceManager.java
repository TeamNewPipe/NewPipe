package org.schabi.newpipe.util.services;

import android.content.Context;

import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.InstanceBasedStreamingService;
import org.schabi.newpipe.extractor.instance.Instance;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractInstanceManager<I extends Instance> implements InstanceManager<I> {
    protected abstract InstanceBasedStreamingService<I> getRelatedStreamingService();

    protected abstract I createInstanceFromPersistence(JsonObject jsonObject);

    protected abstract void convertInstanceToPersist(JsonStringWriter jsonWriter, I instance);

    @StringRes
    protected abstract int getListPersistenceKey();


    @Override
    public List<I> saveInstanceList(final List<I> instances, final Context context) {
        final JsonStringWriter jsonWriter = JsonWriter.string().object().array("instances");
        for (final I instance : instances) {
            jsonWriter.object();
            convertInstanceToPersist(jsonWriter, instance);
            jsonWriter.end();
        }
        final String jsonToSave = jsonWriter.end().end().done();
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(context.getString(getListPersistenceKey()), jsonToSave)
                .apply();
        return null;
    }

    @Override
    public List<I> getInstanceList(final Context context) {
        final String savedInstanceListKey = context.getString(getListPersistenceKey());
        final String savedJson = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(savedInstanceListKey, null);
        if (null == savedJson) {
            return getDefaultInstanceList();
        }

        try {
            return JsonParser.object().from(savedJson)
                    .getArray("instances")
                    .stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(this::createInstanceFromPersistence)
                    .collect(Collectors.toList());
        } catch (final JsonParserException e) {
            return getDefaultInstanceList();
        }
    }

    protected List<I> getDefaultInstanceList() {
        return Collections.singletonList(getDefaultInstance());
    }

    @StringRes
    protected abstract int getSelectedInstancePersistenceKey();

    @Override
    public I saveCurrentInstance(final I instance, final Context context) {
        getRelatedStreamingService().setInstance(instance);

        final String selectedInstanceKey = context.getString(getSelectedInstancePersistenceKey());
        final JsonStringWriter jsonWriter = JsonWriter.string().object();
        convertInstanceToPersist(jsonWriter, instance);
        final String jsonToSave = jsonWriter.end().done();
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(selectedInstanceKey, jsonToSave)
                .apply();

        return instance;
    }

    @Override
    public I getCurrentInstance() {
        try {
            final I instance = getRelatedStreamingService().getInstance();
            if (instance != null) {
                return instance;
            }
        } catch (final Exception ignored) {
            // Fallthrough
        }
        return getDefaultInstance();
    }

    protected abstract I getDefaultInstance();

    @Override
    public void restoreDefaults(final Context context) {
        saveInstanceList(getDefaultInstanceList(), context);
        saveCurrentInstance(getDefaultInstance(), context);
    }
}
