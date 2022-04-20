package org.schabi.newpipe.util.services;

import android.content.Context;

import org.schabi.newpipe.extractor.instance.Instance;

import java.util.List;

/**
 * Manages service instances.
 *
 * @param <I>
 */
public interface InstanceManager<I extends Instance> {
    /**
     * Saves the instance list (to persistence/preferences).
     *
     * @param instances instance list
     * @param context   Context
     */
    void saveInstanceList(List<I> instances, Context context);

    /**
     * Returns the current instance list.
     *
     * @param context Context
     * @return instance list
     */
    List<I> getInstanceList(Context context);

    /**
     * Returns the current instance (from memory / the corresponding streaming-service).
     *
     * @return The current instance
     */
    I getCurrentInstance();

    /**
     * Reloads the current instance from the persistence layer (preferences).
     *
     * @param context Context
     */
    void reloadCurrentInstanceFromPersistence(Context context);

    /**
     * Saves the instance as the currently used one (also to the persistence).
     *
     * @param instance The instance
     * @param context  Context
     * @return the saved instance
     */
    I saveCurrentInstance(I instance, Context context);

    /**
     * Restores the default values.
     *
     * @param context Context
     */
    void restoreDefaults(Context context);
}
