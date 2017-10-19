package org.schabi.newpipe.util;


import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Xml;

import org.schabi.newpipe.database.subscription.SubscriptionEntity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class OPMLParser extends AsyncTask<InputStream, Void, List<SubscriptionEntity>>{
    private static final String ns = null;
    public AsyncResponse delegate = null;

    public interface AsyncResponse {
        void processPreExecute();
        void processFinish(List<SubscriptionEntity> _subscriptions);
    }

    public OPMLParser(AsyncResponse delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void onPreExecute() {
        delegate.processPreExecute();
    }

    @Override
    protected List<SubscriptionEntity> doInBackground(InputStream... ins){
        if(android.os.Debug.isDebuggerConnected())
            android.os.Debug.waitForDebugger();
        InputStream in = ins[0];
        List<SubscriptionEntity> result;
        try {
            try {
                result = parse(in);
            } finally {
                in.close();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            result = null;
        } catch (IOException e) {
            e.printStackTrace();
            result = null;
        } catch (ExtractionException e) {
            e.printStackTrace();
            result = null;
        }
        return result;
    }

    @Override
    protected void onPostExecute(List<SubscriptionEntity> subscriptions) {
        delegate.processFinish(subscriptions);
    }

    public List<SubscriptionEntity> parse(InputStream in) throws XmlPullParserException, IOException, ExtractionException {
        if(android.os.Debug.isDebuggerConnected())
            android.os.Debug.waitForDebugger();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return readFeed(parser);
        } finally {
            in.close();
        }
    }

    private List<SubscriptionEntity> readFeed(XmlPullParser parser) throws XmlPullParserException, IOException, ExtractionException {
        List<SubscriptionEntity> subscriptions = new LinkedList<>();
        parser.require(XmlPullParser.START_TAG, ns, "opml");
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.END_TAG) {
                continue;
            }
            String type = parser.getAttributeValue(null, "type");
            if ("rss".equals(type)) {
                String xmlUrl = parser.getAttributeValue(null, "xmlUrl");
                String channelId = xmlUrl.split("=")[1];
                String url = "https://www.youtube.com/channel/" + channelId;
                ChannelInfo channel = new ChannelInfo();
                channel = ChannelInfo.getInfo(url);
                SubscriptionEntity subscription = new SubscriptionEntity();
                subscription.setAvatarUrl(channel.avatar_url);
                subscription.setDescription(channel.description);
                subscription.setName(channel.name);
                subscription.setServiceId(channel.service_id);
                subscription.setSubscriberCount(channel.subscriber_count);
                subscription.setUrl(channel.url);
                subscriptions.add(subscription);
            }
        }
        return subscriptions;
    }
}
