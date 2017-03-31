package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;

import org.schabi.newpipe.ChannelActivity;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.detail.VideoItemDetailActivity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;

import java.util.ArrayList;
import java.util.Stack;

/**
 * Created by Christian Schabesberger on 16.02.17.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * NavStack.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * class helps to navigate within the app
 * IMPORTAND: the top of the stack is the current activity !!!
 */
public class NavStack {
    private static final String TAG = NavStack.class.toString();
    public static final String SERVICE_ID = "service_id";
    public static final String URL = "url";

    private static final String NAV_STACK="nav_stack";

    private enum ActivityId {
        CHANNEL,
        DETAIL
    }

    private class NavEntry {
        public NavEntry(String url, int serviceId) {
            this.url = url;
            this.serviceId = serviceId;
        }
        public String url;
        public int serviceId;
    }

    private static NavStack instance = new NavStack();
    private Stack<NavEntry> stack = new Stack<NavEntry>();

    private NavStack() {
    }

    public static NavStack getInstance() {
        return instance;
    }

    public void navBack(Activity activity) throws Exception {
        if(stack.size() == 0) { // if stack is already empty here, activity was probably called
            // from another app
            activity.finish();
            return;
        }
        stack.pop(); // remove curent activty, since we dont want to return to itself
        if (stack.size() == 0) {
            openMainActivity(activity); // if no more page is on the stack this means we are home
            return;
        }
        NavEntry entry = stack.pop();  // this element will reapear, since by calling the old page
        // this element will be pushed on top again
        try {
            StreamingService service = NewPipe.getService(entry.serviceId);
            switch (service.getLinkTypeByUrl(entry.url)) {
                case STREAM:
                    openDetailActivity(activity, entry.url, entry.serviceId);
                    break;
                case CHANNEL:
                    openChannelActivity(activity, entry.url, entry.serviceId);
                    break;
                case NONE:
                    throw new Exception("Url not known to service. service="
                            + Integer.toString(entry.serviceId) + " url=" + entry.url);
                default:
                    openMainActivity(activity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void openChannelActivity(Context context, String url, int serviceId) {
        openActivity(context, url, serviceId, ChannelActivity.class);
    }

    public void openDetailActivity(Context context, String url, int serviceId) {
        openActivity(context, url, serviceId, VideoItemDetailActivity.class);
    }

    private void openActivity(Context context, String url, int serviceId, Class acitivtyClass) {
        //if last element has the same url do not push to stack again
        if(stack.isEmpty() || !stack.peek().url.equals(url)) {
            stack.push(new NavEntry(url, serviceId));
        }
        Intent i = new Intent(context, acitivtyClass);
        i.putExtra(SERVICE_ID, serviceId);
        i.putExtra(URL, url);
        context.startActivity(i);
    }

    public void openMainActivity(Activity a) {
        stack.clear();
        Intent i = new Intent(a, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NavUtils.navigateUpTo(a, i);
    }

    public void onSaveInstanceState(Bundle state) {
        ArrayList<String> sa = new ArrayList<>();
        for(NavEntry entry : stack) {
            sa.add(entry.url);
        }
        state.putStringArrayList(NAV_STACK, sa);
    }

    public void restoreSavedInstanceState(Bundle state) {
        ArrayList<String> sa = state.getStringArrayList(NAV_STACK);
        stack.clear();
        for(String url : sa) {
            stack.push(new NavEntry(url, NewPipe.getServiceByUrl(url).getServiceId()));
        }
    }
}
