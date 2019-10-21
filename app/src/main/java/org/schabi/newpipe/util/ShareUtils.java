package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import android.preference.PreferenceManager;
import org.schabi.newpipe.R;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ShareUtils {
    public static void openUrlInBrowser(Context context, String url) {
        String shareUrl = getShareUrl(PreferenceManager.getDefaultSharedPreferences(context), url);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl));
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_dialog_title)));
    }

    public static void shareUrl(Context context, String subject, String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        String shareUrl = getShareUrl(PreferenceManager.getDefaultSharedPreferences(context), url);
        intent.putExtra(Intent.EXTRA_TEXT, shareUrl);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_dialog_title)));
    }

    private static String getShareUrl(SharedPreferences prefs, String url) {
        if (prefs.getBoolean("use_custom_share_website", false)) {
            return prefs.getString("custom_share_website", "https://youtube.com")
                + extractWatchParameter(url);
        } else {
            return url;
        }
    }

    private static String extractWatchParameter(String url) {
        Pattern pattern = Pattern.compile("(watch\\?v=[^&]+)");
        Matcher matcher = pattern.matcher(url);
        matcher.find();
        return matcher.group(1);
    }
}
