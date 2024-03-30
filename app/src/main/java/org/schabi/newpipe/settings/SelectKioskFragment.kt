package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.settings.SelectKioskFragment.SelectKioskAdapter.SelectKioskItemHolder
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import java.util.Vector

/**
 * Created by Christian Schabesberger on 09.10.17.
 * SelectKioskFragment.java is part of NewPipe.
 *
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http:></http:>//www.gnu.org/licenses/>.
 *
 */
class SelectKioskFragment() : DialogFragment() {
    private var selectKioskAdapter: SelectKioskAdapter? = null
    private var onSelectedListener: OnSelectedListener? = null
    fun setOnSelectedListener(listener: OnSelectedListener?) {
        onSelectedListener = listener
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, ThemeHelper.getMinWidthDialogTheme(requireContext()))
    }

    public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        val v: View = inflater.inflate(R.layout.select_kiosk_fragment, container, false)
        val recyclerView: RecyclerView = v.findViewById(R.id.items_list)
        recyclerView.setLayoutManager(LinearLayoutManager(getContext()))
        try {
            selectKioskAdapter = SelectKioskAdapter()
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Selecting kiosk", e)
        }
        recyclerView.setAdapter(selectKioskAdapter)
        return v
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    ////////////////////////////////////////////////////////////////////////// */
    private fun clickedItem(entry: SelectKioskAdapter.Entry) {
        if (onSelectedListener != null) {
            onSelectedListener!!.onKioskSelected(entry.serviceId, entry.kioskId, entry.kioskName)
        }
        dismiss()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    ////////////////////////////////////////////////////////////////////////// */
    open interface OnSelectedListener {
        fun onKioskSelected(serviceId: Int, kioskId: String?, kioskName: String?)
    }

    private inner class SelectKioskAdapter internal constructor() : RecyclerView.Adapter<SelectKioskItemHolder>() {
        private val kioskList: MutableList<Entry> = Vector()

        init {
            for (service: StreamingService in NewPipe.getServices()) {
                for (kioskId: String? in service.getKioskList().getAvailableKiosks()) {
                    val name: String = String.format(getString(R.string.service_kiosk_string),
                            service.getServiceInfo().getName(),
                            KioskTranslator.getTranslatedKioskName(kioskId, getContext()))
                    kioskList.add(Entry(ServiceHelper.getIcon(service.getServiceId()),
                            service.getServiceId(), kioskId, name))
                }
            }
        }

        public override fun getItemCount(): Int {
            return kioskList.size
        }

        public override fun onCreateViewHolder(parent: ViewGroup, type: Int): SelectKioskItemHolder {
            val item: View = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.select_kiosk_item, parent, false)
            return SelectKioskItemHolder(item)
        }

        public override fun onBindViewHolder(holder: SelectKioskItemHolder, position: Int) {
            val entry: Entry = kioskList.get(position)
            holder.titleView.setText(entry.kioskName)
            holder.thumbnailView
                    .setImageDrawable(AppCompatResources.getDrawable(requireContext(), entry.icon))
            holder.view.setOnClickListener(View.OnClickListener({ view: View? -> clickedItem(entry) }))
        }

        internal inner class Entry(val icon: Int, val serviceId: Int, val kioskId: String, val kioskName: String)
        inner class SelectKioskItemHolder internal constructor(val view: View) : RecyclerView.ViewHolder(v) {
            val thumbnailView: ImageView
            val titleView: TextView

            init {
                thumbnailView = v.findViewById<ImageView>(R.id.itemThumbnailView)
                titleView = v.findViewById<TextView>(R.id.itemTitleView)
            }
        }
    }
}
