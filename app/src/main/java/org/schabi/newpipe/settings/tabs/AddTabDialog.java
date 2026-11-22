package org.schabi.newpipe.settings.tabs;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;

import org.schabi.newpipe.R;

public final class AddTabDialog {
    private final AlertDialog dialog;

    AddTabDialog(@NonNull final Context context, @NonNull final ChooseTabListItem[] items,
                 @NonNull final DialogInterface.OnClickListener actions) {

        dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.tab_choose))
                .setAdapter(new DialogListAdapter(context, items), (dialogInterface, which) -> {
                    if (!items[which].header) {
                        actions.onClick(dialogInterface, which);
                    }
                })
                .create();
    }

    public void show() {
        dialog.show();
    }

    public static final class ChooseTabListItem {
        final int tabId;
        @Nullable
        final Tab tab;
        final String itemName;
        @Nullable
        final String itemSubtitle;
        @DrawableRes
        final int itemIcon;
        final boolean header;

        ChooseTabListItem(final Context context, final Tab tab) {
            this(tab, tab.getTabName(context), null, tab.getTabIconRes(context));
        }

        ChooseTabListItem(final int tabId, final String itemName,
                          @DrawableRes final int itemIcon) {
            this(tabId, itemName, null, itemIcon);
        }

        ChooseTabListItem(final int tabId, final String itemName,
                          @Nullable final String itemSubtitle,
                          @DrawableRes final int itemIcon) {
            this.tabId = tabId;
            this.tab = null;
            this.itemName = itemName;
            this.itemSubtitle = itemSubtitle;
            this.itemIcon = itemIcon;
            this.header = false;
        }

        ChooseTabListItem(final Tab tab, final String itemName,
                          @Nullable final String itemSubtitle,
                          @DrawableRes final int itemIcon) {
            this.tabId = tab.getTabId();
            this.tab = tab;
            this.itemName = itemName;
            this.itemSubtitle = itemSubtitle;
            this.itemIcon = itemIcon;
            this.header = false;
        }

        static ChooseTabListItem header(final String itemName) {
            return new ChooseTabListItem(itemName);
        }

        private ChooseTabListItem(final String itemName) {
            this.tabId = -1;
            this.tab = null;
            this.itemName = itemName;
            this.itemSubtitle = null;
            this.itemIcon = 0;
            this.header = true;
        }
    }

    private static final class DialogListAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final ChooseTabListItem[] items;

        @DrawableRes
        private final int fallbackIcon;

        private DialogListAdapter(final Context context, final ChooseTabListItem[] items) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.fallbackIcon = R.drawable.ic_whatshot;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(final int position) {
            return !getItem(position).header;
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public ChooseTabListItem getItem(final int position) {
            return items[position];
        }

        @Override
        public long getItemId(final int position) {
            return getItem(position).header ? position : getItem(position).tabId;
        }

        @Override
        public View getView(final int position, final View view, final ViewGroup parent) {
            View convertView = view;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_choose_tabs_dialog, parent, false);
            }

            final ChooseTabListItem item = getItem(position);
            final AppCompatImageView tabIconView = convertView.findViewById(R.id.tabIcon);
            final TextView tabNameView = convertView.findViewById(R.id.tabName);
            final TextView tabSubtitleView = convertView.findViewById(R.id.tabSubtitle);

            if (item.header) {
                tabIconView.setVisibility(View.GONE);
                tabNameView.setText(item.itemName);
                tabNameView.setTypeface(Typeface.DEFAULT_BOLD);
                tabSubtitleView.setVisibility(View.GONE);
                convertView.setEnabled(false);
            } else {
                tabIconView.setVisibility(View.VISIBLE);
                tabIconView.setImageResource(item.itemIcon > 0 ? item.itemIcon : fallbackIcon);
                tabNameView.setText(item.itemName);
                tabNameView.setTypeface(Typeface.DEFAULT);
                if (item.itemSubtitle == null || item.itemSubtitle.isEmpty()) {
                    tabSubtitleView.setVisibility(View.GONE);
                } else {
                    tabSubtitleView.setText(item.itemSubtitle);
                    tabSubtitleView.setVisibility(View.VISIBLE);
                }
                convertView.setEnabled(true);
            }

            return convertView;
        }
    }
}
