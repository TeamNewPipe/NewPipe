package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.schabi.newpipe.R;

public final class ShareUtils {
    private ShareUtils() { }

    public static void openUrlInBrowser(final Context context, final String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(Intent.createChooser(
                intent, context.getString(R.string.share_dialog_title)));
    }

    public static void shareUrl(final Context context, final String subject, final String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, url);
        context.startActivity(Intent.createChooser(
                intent, context.getString(R.string.share_dialog_title)));
    }
}
