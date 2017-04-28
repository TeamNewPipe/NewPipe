package org.schabi.newpipe.subscription;

import android.provider.BaseColumns;

class Subscription {
    private Subscription() {
    }

    static class Entry implements BaseColumns {
        static final String TABLE_NAME = "subscriptions";
        static final String COLUMN_NAME = "name";
        static final String COLUMN_LINK = "link";
        static final String COLUMN_AVATAR = "avatar";
        static final String COLUMN_SERVICE_ID = "service_id";
    }
}