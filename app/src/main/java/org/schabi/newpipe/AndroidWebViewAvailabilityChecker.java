package org.schabi.newpipe;

import android.content.Context;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewCompat;

import org.schabi.newpipe.extractor.WebViewAvailabilityChecker;
import org.schabi.newpipe.extractor.exceptions.WebViewUnavailableException;

public final class AndroidWebViewAvailabilityChecker implements WebViewAvailabilityChecker {
    private final SharedWebViewRuntime runtime;
    @Nullable
    private final PackageInfo packageInfo;
    @Nullable
    private volatile WebViewUnavailableException unavailableException;

    public AndroidWebViewAvailabilityChecker(@NonNull final Context context) {
        final Context appContext = context.getApplicationContext();
        runtime = SharedWebViewRuntime.get(appContext);

        final PackageInfo currentPackageInfo = WebViewCompat.getCurrentWebViewPackage(appContext);
        packageInfo = currentPackageInfo;
        if (currentPackageInfo == null) {
            unavailableException =
                    new WebViewUnavailableException("No Android WebView provider is available");
        }
    }

    public void warmUp() {
        if (unavailableException == null) {
            runtime.warmUp(this::onInitializationFailure);
        }
    }

    @Override
    public void checkWebViewAvailable() throws WebViewUnavailableException {
        final WebViewUnavailableException exception = unavailableException;
        if (exception != null) {
            throw exception;
        }
    }

    private void onInitializationFailure(@NonNull final Throwable throwable) {
        final PackageInfo info = packageInfo;
        if (info == null) {
            unavailableException = new WebViewUnavailableException(
                    "Android WebView runtime failed to become ready",
                    throwable);
            return;
        }
        unavailableException = new WebViewUnavailableException(
                "Android WebView runtime using provider " + info.packageName + " version "
                        + info.versionName + " failed to become ready",
                throwable);
    }

}
