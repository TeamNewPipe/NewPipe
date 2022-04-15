package org.schabi.newpipe.util.services;

import android.content.Context;

import org.schabi.newpipe.extractor.instance.Instance;

import java.util.List;

public interface InstanceManager<I extends Instance> {
    List<I> saveInstanceList(List<I> instances, Context context);

    List<I> getInstanceList(Context context);

    I getCurrentInstance();

    I saveCurrentInstance(I instance, Context context);

    void restoreDefaults(Context context);
}
