package org.schabi.newpipe.about;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.webkit.WebView;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LicenseFragmentHelper extends AsyncTask<Object, Void, Integer> {

    private Context context;
    private License license;

    @Override
    protected Integer doInBackground(Object... objects) {
        context = (Context) objects[0];
        license = (License) objects[1];
        return 1;
    }

    @Override
    protected void onPostExecute(Integer result){
        String webViewData = getFormattedLicense(context, license);
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(license.getName());

        WebView wv = new WebView(context);
        wv.loadData(webViewData, "text/html; charset=UTF-8", null);

        alert.setView(wv);
        alert.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    /**
     * @param context the context to use
     * @param license the license
     * @return String which contains a HTML formatted license page styled according to the context's theme
     */
    public static String getFormattedLicense(Context context, License license) {
        if(context == null) {
            throw new NullPointerException("context is null");
        }
        if(license == null) {
            throw new NullPointerException("license is null");
        }

        String licenseContent = "";
        String webViewData;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(context.getAssets().open(license.getFilename()), "UTF-8"));
            String str;
            while ((str = in.readLine()) != null) {
                licenseContent += str;
            }
            in.close();

            // split the HTML file and insert the stylesheet into the HEAD of the file
            String[] insert = licenseContent.split("</head>");
            webViewData = insert[0] + "<style type=\"text/css\">"
                    + getLicenseStylesheet(context) + "</style></head>"
                    + insert[1];
        } catch (Exception e) {
            throw new NullPointerException("could not get license file:" + getLicenseStylesheet(context));
        }
        return webViewData;
    }

    /**
     *
     * @param context
     * @return String which is a CSS stylesheet according to the context's theme
     */
    public static String getLicenseStylesheet(Context context) {
        boolean isLightTheme = ThemeHelper.isLightThemeSelected(context);
        return "body{padding:12px 15px;margin:0;background:#"
                + getHexRGBColor(context, isLightTheme
                    ? R.color.light_license_background_color
                    : R.color.dark_license_background_color)
                + ";color:#"
                + getHexRGBColor(context, isLightTheme
                    ? R.color.light_license_text_color
                    : R.color.dark_license_text_color) + ";}"
                + "a[href]{color:#"
                + getHexRGBColor(context, isLightTheme
                    ? R.color.light_youtube_primary_color
                    : R.color.dark_youtube_primary_color) + ";}"
                + "pre{white-space: pre-wrap;}";
    }

    /**
     * Cast R.color to a hexadecimal color value
     * @param context the context to use
     * @param color the color number from R.color
     * @return a six characters long String with hexadecimal RGB values
     */
    public static String getHexRGBColor(Context context, int color) {
        return context.getResources().getString(color).substring(3);
    }

}
