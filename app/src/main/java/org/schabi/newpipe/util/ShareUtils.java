package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.schabi.newpipe.R;

public class ShareUtils {
    public static void openUrlInBrowser(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_dialog_title)));
    }

    public static void shareUrl(Context context, String subject, String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, url);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_dialog_title)));
    }
}
