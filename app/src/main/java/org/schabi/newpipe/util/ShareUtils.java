package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import android.preference.PreferenceManager;
import org.schabi.newpipe.R;

import java.net.MalformedURLException;
import java.net.URL;

public class ShareUtils {
    public static void openUrlInBrowser(Context context, String url) {
        try {
            String shareUrl = getShareUrl(PreferenceManager.getDefaultSharedPreferences(context), url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl));
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_dialog_title)));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        }

    public static void shareUrl(Context context, String subject, String url) {
        try {
            String shareUrl = getShareUrl(PreferenceManager.getDefaultSharedPreferences(context), url);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT, shareUrl);
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_dialog_title)));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }

    private static String getShareUrl(SharedPreferences prefs, String url)
        throws MalformedURLException {
        if (prefs.getBoolean("use_custom_share_website", false)) {
            return prefs.getString("custom_share_website", "https://youtube.com")
                + extractPathAndQuery(url);
        } else {
            return url;
        }
    }

    private static String extractPathAndQuery(String link) throws MalformedURLException {
        URL url = new URL(link);
       return url.getPath() + "?" + url.getQuery();
    }
}
