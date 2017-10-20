package org.schabi.newpipe.util;

import android.os.AsyncTask;

import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.fragments.subscription.SubscriptionService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

/**
 * Created by gui on 20/10/17.
 */

public class SubscriptionExporter extends AsyncTask<String, Void, Void> {
    @Override
    protected Void doInBackground(String... paths) {
        String opml = "<opml version=\"1.1\">\n" +
                "<body><outline text=\"YouTube Subscriptions\" title=\"YouTube Subscriptions\">";
        File path = new File(paths[0]);
        FileOutputStream out;
        try {
            out = new FileOutputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        PrintStream ps = new PrintStream(out);
        SubscriptionService subscriptionService = SubscriptionService.getInstance();
        List<SubscriptionEntity> subscriptions = subscriptionService.subscriptionTable().getAll().blockingFirst();
        for (SubscriptionEntity subscription : subscriptions) {
            String name = subscription.getName();
            String url = subscription.getUrl();
            String id = url.split("/channel/")[1];
            String xmlUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + id;
            opml += "<outline text=\"" + name + "\" title=\"" + name + "\" type=\"rss\" xmlUrl=\"" + xmlUrl + "\" />";
        }
        opml += "</outline></body></opml>";
        ps.print(opml);
        ps.close();
        return null;
    }
}
