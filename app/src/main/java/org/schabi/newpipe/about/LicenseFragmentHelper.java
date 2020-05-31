package org.schabi.newpipe.about;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class LicenseFragmentHelper extends AsyncTask<Object, Void, Integer> {
    private final WeakReference<Activity> weakReference;
    private License license;

    public LicenseFragmentHelper(@Nullable final Activity activity) {
        weakReference = new WeakReference<>(activity);
    }

    /**
     * @param context the context to use
     * @param license the license
     * @return String which contains a HTML formatted license page
     * styled according to the context's theme
     */
    private static String getFormattedLicense(@NonNull final Context context,
                                              @NonNull final License license) {
        final StringBuilder licenseContent = new StringBuilder();
        final String webViewData;
        try {
            final BufferedReader in = new BufferedReader(new InputStreamReader(
                    context.getAssets().open(license.getFilename()), StandardCharsets.UTF_8));
            String str;
            while ((str = in.readLine()) != null) {
                licenseContent.append(str);
            }
            in.close();

            // split the HTML file and insert the stylesheet into the HEAD of the file
            webViewData = licenseContent.toString().replace("</head>",
                    "<style>" + getLicenseStylesheet(context) + "</style></head>");
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Could not get license file: " + license.getFilename(), e);
        }
        return webViewData;
    }

    /**
     * @param context
     * @return String which is a CSS stylesheet according to the context's theme
     */
    private static String getLicenseStylesheet(final Context context) {
        final boolean isLightTheme = ThemeHelper.isLightThemeSelected(context);
        return "body{padding:12px 15px;margin:0;"
                + "background:#" + getHexRGBColor(context, isLightTheme
                ? R.color.light_license_background_color
                : R.color.dark_license_background_color) + ";"
                + "color:#" + getHexRGBColor(context, isLightTheme
                ? R.color.light_license_text_color
                : R.color.dark_license_text_color) + "}"
                + "a[href]{color:#" + getHexRGBColor(context, isLightTheme
                ? R.color.light_youtube_primary_color
                : R.color.dark_youtube_primary_color) + "}"
                + "pre{white-space:pre-wrap}";
    }

    /**
     * Cast R.color to a hexadecimal color value.
     *
     * @param context the context to use
     * @param color   the color number from R.color
     * @return a six characters long String with hexadecimal RGB values
     */
    private static String getHexRGBColor(final Context context, final int color) {
        return context.getResources().getString(color).substring(3);
    }

    @Nullable
    private Activity getActivity() {
        final Activity activity = weakReference.get();

        if (activity != null && activity.isFinishing()) {
            return null;
        } else {
            return activity;
        }
    }

    @Override
    protected Integer doInBackground(final Object... objects) {
        license = (License) objects[0];
        return 1;
    }

    @Override
    protected void onPostExecute(final Integer result) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final String webViewData = Base64.encodeToString(getFormattedLicense(activity, license)
                .getBytes(StandardCharsets.UTF_8), Base64.NO_PADDING);
        final WebView webView = new WebView(activity);
        webView.loadData(webViewData, "text/html; charset=UTF-8", "base64");

        final AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setTitle(license.getName());
        alert.setView(webView);
        assureCorrectAppLanguage(activity);
        alert.setNegativeButton(activity.getString(R.string.finish),
                (dialog, which) -> dialog.dismiss());
        alert.show();
    }
}
