package org.schabi.newpipe.util;

// CHECKSTYLE:OFF

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.io.StringReader;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.InfoItem;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;


public final class FilterUrlsOnline {

    private FilterUrlsOnline() { }
    static String ServerUrl = "https://goodkids.freemyip.com/api/";

    private static String getFilterQueryNextPage(final List<? extends InfoItem> items) {
        String query = "https://goodkids.freemyip.com/api/videos?url=";
        boolean skipFirst = true;
        for (final InfoItem item : items) {
            final String videoUrl = item.getUrl().split("v=")[1];
            if (skipFirst) {
                query = query.concat(videoUrl);
                skipFirst = false;
            } else {
                query = query.concat("&url=");
                query = query.concat(videoUrl);
            }
        }
        return query;
    }

    private static String getFilterQuery(final List<InfoItem> items) {
        String query = ServerUrl.concat("videos?url=");
        boolean skipFirst = true;
        for (final InfoItem item : items) {
            final String videoUrl = item.getUrl().split("v=")[1];
            if (skipFirst) {
                query = query.concat(videoUrl);
                skipFirst = false;
            } else {
                query = query.concat("&url=");
                query = query.concat(videoUrl);
            }
        }
        return query;
    }

    public static String syncHttpGet(String url) {
            OkHttpClient client = new OkHttpClient();
            final AtomicReference<String> responseData = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(1);

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        responseData.set(response.body().string());
                    } else {
                        responseData.set("Request failed");
                    }
                    latch.countDown();
                }

                @Override
                public void onFailure(Call call, IOException e) {
                    responseData.set("IOException occurred: " + e.getMessage());
                    latch.countDown();
                }
            });

            try {
                latch.await(); // Wait until countDown() is called
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return responseData.get();
    }

    public static List<InfoItem> filterNextItems(final List<? extends InfoItem> items) {
        List<InfoItem> RelatedItems = new ArrayList<>();
        String query = getFilterQueryNextPage(items);
        String response = syncHttpGet(query);
        try {
            JsonArray array = JsonParser.array().from(response);
            for (final InfoItem item : items) {
                for (int i = 0; i < array.size(); i++) {
                    JsonObject jsonObject = array.getObject(i);
                    final String videoUrl = item.getUrl().split("v=")[1];
                    if ( jsonObject.getString("url").equals(videoUrl) ){
                        RelatedItems.add(item);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return RelatedItems;
    }
    public static List<InfoItem> filterItems(List<InfoItem> items) {
        List<InfoItem> RelatedItems = new ArrayList<>();
        String query = getFilterQuery(items);
        String response = syncHttpGet(query);
        try {
            JsonArray array = JsonParser.array().from(response);
            for (final InfoItem item : items) {
                for (int i = 0; i < array.size(); i++) {
                    JsonObject jsonObject = array.getObject(i);
                    final String videoUrl = item.getUrl().split("v=")[1];
                    if ( jsonObject.getString("url").equals(videoUrl) ){
                        RelatedItems.add(item);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return RelatedItems;

    }

}
