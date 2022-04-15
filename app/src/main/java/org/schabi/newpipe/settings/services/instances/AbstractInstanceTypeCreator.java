package org.schabi.newpipe.settings.services.instances;

import androidx.annotation.DrawableRes;

import org.schabi.newpipe.extractor.instance.Instance;

public abstract class AbstractInstanceTypeCreator<I extends Instance>
        implements InstanceTypeCreator<I> {

    protected final String instanceServiceName;
    @DrawableRes
    protected final int icon;
    protected final Class<I> createdClass;

    protected AbstractInstanceTypeCreator(
            final String instanceServiceName,
            final int icon,
            final Class<I> createdClass
    ) {
        this.instanceServiceName = instanceServiceName;
        this.icon = icon;
        this.createdClass = createdClass;
    }

    @Override
    public Class<I> createdClass() {
        return createdClass;
    }

    @Override
    public String instanceServiceName() {
        return instanceServiceName;
    }

    @Override
    public int icon() {
        return icon;
    }
}
