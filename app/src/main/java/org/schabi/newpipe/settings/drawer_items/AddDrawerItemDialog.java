package org.schabi.newpipe.settings.drawer_items;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

import org.schabi.newpipe.R;

public final class AddDrawerItemDialog {
    private final AlertDialog dialog;

    public AddDrawerItemDialog(@NonNull final Context context,
                               @NonNull final ChooseDrawerItemListItem[] items,
                               @NonNull final DialogInterface.OnClickListener actions) {

        dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.drawer_item_choose))
                .setAdapter(new DialogListAdapter(context, items), actions)
                .create();
    }

    public void show() {
        dialog.show();
    }

    public static final class ChooseDrawerItemListItem {
        public final int drawerItemId;
        final String itemName;
        @DrawableRes
        final int itemIcon;

        public ChooseDrawerItemListItem(final Context context, final DrawerItem drawerItem) {
            this(drawerItem.getDrawerItemId(), drawerItem.getDrawerItemName(context),
                    drawerItem.getDrawerItemIconRes(context));
        }

        public ChooseDrawerItemListItem(final int drawerItemId, final String itemName,
                                        @DrawableRes final int itemIcon) {
            this.drawerItemId = drawerItemId;
            this.itemName = itemName;
            this.itemIcon = itemIcon;
        }
    }

    private static final class DialogListAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final ChooseDrawerItemListItem[] items;

        @DrawableRes
        private final int fallbackIcon;

        private DialogListAdapter(final Context context, final ChooseDrawerItemListItem[] items) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.fallbackIcon = R.drawable.ic_whatshot;
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public ChooseDrawerItemListItem getItem(final int position) {
            return items[position];
        }

        @Override
        public long getItemId(final int position) {
            return getItem(position).drawerItemId;
        }

        @Override
        public View getView(final int position, final View view, final ViewGroup parent) {
            View convertView = view;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_choose_tabs_dialog, parent, false);
            }

            final ChooseDrawerItemListItem item = getItem(position);
            final AppCompatImageView drawerItemIconView = convertView.findViewById(R.id.tabIcon);
            final TextView drawerItemNameView = convertView.findViewById(R.id.tabName);

            drawerItemIconView.setImageResource(item.itemIcon > 0 ? item.itemIcon : fallbackIcon);
            drawerItemNameView.setText(item.itemName);

            return convertView;
        }
    }
}
