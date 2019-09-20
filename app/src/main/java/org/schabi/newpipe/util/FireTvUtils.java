package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;

import org.schabi.newpipe.App;

public class FireTvUtils {
    @SuppressLint("InlinedApi")
    public static boolean isFireTv(){
        final String AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv";

        PackageManager pm =  App.getApp().getPackageManager();

        return pm.hasSystemFeature(AMAZON_FEATURE_FIRE_TV)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
