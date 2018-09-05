package org.schabi.newpipe.settings.tabs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

public class AddTabDialog {
    private final AlertDialog dialog;

    AddTabDialog(@NonNull final Context context,
                 @NonNull final ChooseTabListItem[] items,
                 @NonNull final DialogInterface.OnClickListener actions) {

        dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.tab_choose))
                .setAdapter(new DialogListAdapter(context, items), actions)
                .create();
    }

    public void show() {
        dialog.show();
    }

    public static final class ChooseTabListItem {
        final int tabId;
        final String itemName;
        @DrawableRes final int itemIcon;

        ChooseTabListItem(Context context, Tab tab) {
            this(tab.getTabId(), tab.getTabName(context), tab.getTabIconRes(context));
        }

        ChooseTabListItem(int tabId, String itemName, @DrawableRes int itemIcon) {
            this.tabId = tabId;
            this.itemName = itemName;
            this.itemIcon = itemIcon;
        }
    }

    private static class DialogListAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final ChooseTabListItem[] items;

        @DrawableRes private final int fallbackIcon;

        private DialogListAdapter(Context context, ChooseTabListItem[] items) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.fallbackIcon = ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_hot);
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public ChooseTabListItem getItem(int position) {
            return items[position];
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).tabId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_choose_tabs_dialog, parent, false);
            }

            final ChooseTabListItem item = getItem(position);
            final AppCompatImageView tabIconView = convertView.findViewById(R.id.tabIcon);
            final TextView tabNameView = convertView.findViewById(R.id.tabName);

            tabIconView.setImageResource(item.itemIcon > 0 ? item.itemIcon : fallbackIcon);
            tabNameView.setText(item.itemName);

            return convertView;
        }
    }
}
