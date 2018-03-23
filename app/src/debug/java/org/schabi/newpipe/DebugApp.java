package org.schabi.newpipe;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDex;

import com.facebook.stetho.Stetho;
import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.squareup.leakcanary.AndroidHeapDumper;
import com.squareup.leakcanary.DefaultLeakDirectoryProvider;
import com.squareup.leakcanary.HeapDumper;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.LeakDirectoryProvider;
import com.squareup.leakcanary.RefWatcher;

import org.schabi.newpipe.extractor.Downloader;

import java.io.File;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class DebugApp extends App {
    private static final String TAG = DebugApp.class.toString();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initStetho();
    }

    @Override
    protected Downloader getDownloader() {
        return org.schabi.newpipe.Downloader.init(new OkHttpClient.Builder()
                .addNetworkInterceptor(new StethoInterceptor()));
    }

    private void initStetho() {
        // Create an InitializerBuilder
        Stetho.InitializerBuilder initializerBuilder =
                Stetho.newInitializerBuilder(this);

        // Enable Chrome DevTools
        initializerBuilder.enableWebKitInspector(
                Stetho.defaultInspectorModulesProvider(this)
        );

        // Enable command line interface
        initializerBuilder.enableDumpapp(
                Stetho.defaultDumperPluginsProvider(getApplicationContext())
        );

        // Use the InitializerBuilder to generate an Initializer
        Stetho.Initializer initializer = initializerBuilder.build();

        // Initialize Stetho with the Initializer
        Stetho.initialize(initializer);
    }

    @Override
    protected boolean isDisposedRxExceptionsReported() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.allow_disposed_exceptions_key), false);
    }

    @Override
    protected RefWatcher installLeakCanary() {
        return LeakCanary.refWatcher(this)
                .heapDumper(new ToggleableHeapDumper(this))
                // give each object 10 seconds to be gc'ed, before leak canary gets nosy on it
                .watchDelay(10, TimeUnit.SECONDS)
                .buildAndInstall();
    }

    public static class ToggleableHeapDumper implements HeapDumper {
        private final HeapDumper dumper;
        private final SharedPreferences preferences;
        private final String dumpingAllowanceKey;

        ToggleableHeapDumper(@NonNull final Context context) {
            LeakDirectoryProvider leakDirectoryProvider = new DefaultLeakDirectoryProvider(context);
            this.dumper = new AndroidHeapDumper(context, leakDirectoryProvider);
            this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
            this.dumpingAllowanceKey = context.getString(R.string.allow_heap_dumping_key);
        }

        private boolean isDumpingAllowed() {
            return preferences.getBoolean(dumpingAllowanceKey, false);
        }

        @Override
        public File dumpHeap() {
            return isDumpingAllowed() ? dumper.dumpHeap() : HeapDumper.RETRY_LATER;
        }
    }
}
