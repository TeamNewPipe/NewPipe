package org.schabi.newpipe.util;

import android.app.Application;

public class FireTvUtils {
    public static boolean isFireTv(Application application){
        final String AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv";
        return application.getPackageManager().hasSystemFeature(AMAZON_FEATURE_FIRE_TV);
    }
}
