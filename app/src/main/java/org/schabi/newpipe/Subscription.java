package org.schabi.newpipe;

import android.provider.BaseColumns;

public class Subscription {
    private Subscription() {
    }

    public static class Entry implements BaseColumns {
        public static final String TABLE_NAME = "subscriptions";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_LINK = "link";
        public static final String COLUMN_AVATAR = "avatar";
    }
}