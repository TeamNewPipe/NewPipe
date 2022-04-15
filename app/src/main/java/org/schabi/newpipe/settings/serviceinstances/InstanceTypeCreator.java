package org.schabi.newpipe.settings.serviceinstances;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.instance.Instance;

import java.util.List;
import java.util.function.Consumer;

public interface InstanceTypeCreator<I extends Instance> {

    /**
     * Type of the created class.
     *
     * @return the created class
     */
    Class<I> createdClass();

    /**
     * Get's the service name of the instance that's create. E.g. PeerTube or Invidious
     *
     * @return service name
     */
    String instanceServiceName();

    /**
     * Returns a resource that represents the instance.
     *
     * @return icon
     */
    @DrawableRes
    int icon();

    /**
     * Determines if a new instance can be created, based on the list of existing instances.
     *
     * @param existingInstances List of existing instances
     * @return <code>true</code> if a new instance of this type can be created
     */
    default boolean canNewInstanceBeCreated(
            @NonNull final List<? extends Instance> existingInstances
    ) {
        return true;
    }


    /**
     * Creates a new instance. Using the UI for e.g. adding URLs is possible.
     *
     * @param context           Context
     * @param existingInstances List of existing instances
     * @param onInstanceCreated Consumer that should be used when the instance is created
     */
    void createNewInstance(
            @NonNull Context context,
            @NonNull List<? extends Instance> existingInstances,
            @NonNull Consumer<I> onInstanceCreated);

}
