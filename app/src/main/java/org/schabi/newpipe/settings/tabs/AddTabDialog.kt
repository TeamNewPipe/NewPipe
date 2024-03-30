package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import org.schabi.newpipe.R

class AddTabDialog internal constructor(context: Context, items: Array<ChooseTabListItem>,
                                        actions: DialogInterface.OnClickListener) {
    private val dialog: AlertDialog

    init {
        dialog = AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.tab_choose))
                .setAdapter(DialogListAdapter(context, items), actions)
                .create()
    }

    fun show() {
        dialog.show()
    }

    internal class ChooseTabListItem(val tabId: Int, val itemName: String?,
                                     @field:DrawableRes @param:DrawableRes val itemIcon: Int) {
        constructor(context: Context, tab: Tab?) : this(tab!!.getTabId(), tab.getTabName(context), tab.getTabIconRes(context))
    }

    private class DialogListAdapter(context: Context, private val items: Array<ChooseTabListItem>) : BaseAdapter() {
        private val inflater: LayoutInflater

        @DrawableRes
        private val fallbackIcon: Int

        init {
            inflater = LayoutInflater.from(context)
            fallbackIcon = R.drawable.ic_whatshot
        }

        public override fun getCount(): Int {
            return items.size
        }

        public override fun getItem(position: Int): ChooseTabListItem {
            return items.get(position)
        }

        public override fun getItemId(position: Int): Long {
            return getItem(position).tabId.toLong()
        }

        public override fun getView(position: Int, view: View, parent: ViewGroup): View {
            var convertView: View = view
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_choose_tabs_dialog, parent, false)
            }
            val item: ChooseTabListItem = getItem(position)
            val tabIconView: AppCompatImageView = convertView.findViewById(R.id.tabIcon)
            val tabNameView: TextView = convertView.findViewById(R.id.tabName)
            tabIconView.setImageResource(if (item.itemIcon > 0) item.itemIcon else fallbackIcon)
            tabNameView.setText(item.itemName)
            return convertView
        }
    }
}
