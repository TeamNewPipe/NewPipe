package org.schabi.newpipe.util;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

public class ServiceHelper {
    private static final StreamingService DEFAULT_FALLBACK_SERVICE = ServiceList.YouTube;

    @DrawableRes
    public static int getIcon(int serviceId) {
        switch (serviceId) {
            case 0:
                return R.drawable.place_holder_youtube;
            case 1:
                return R.drawable.place_holder_circle;
            default:
                return R.drawable.service;
        }
    }

    public static int getSelectedServiceId(Context context) {
        if (BuildConfig.BUILD_TYPE.equals("release")) return DEFAULT_FALLBACK_SERVICE.getServiceId();

        final String serviceName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.current_service_key), context.getString(R.string.default_service_value));

        int serviceId;
        try {
            serviceId = NewPipe.getService(serviceName).getServiceId();
        } catch (ExtractionException e) {
            serviceId = DEFAULT_FALLBACK_SERVICE.getServiceId();
        }

        return serviceId;
    }

    public static void setSelectedServiceId(Context context, int serviceId) {
        String serviceName;
        try {
            serviceName = NewPipe.getService(serviceId).getServiceInfo().getName();
        } catch (ExtractionException e) {
            serviceName = DEFAULT_FALLBACK_SERVICE.getServiceInfo().getName();
        }

        setSelectedServicePreferences(context, serviceName);
    }

    public static void setSelectedServiceId(Context context, String serviceName) {
        int serviceId = NewPipe.getIdOfService(serviceName);
        if (serviceId == -1) serviceName = DEFAULT_FALLBACK_SERVICE.getServiceInfo().getName();

        setSelectedServicePreferences(context, serviceName);
    }

    private static void setSelectedServicePreferences(Context context, String serviceName) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().
                putString(context.getString(R.string.current_service_key), serviceName).apply();
    }
}
