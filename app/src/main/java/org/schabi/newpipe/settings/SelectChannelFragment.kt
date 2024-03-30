package org.schabi.newpipe.settings

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.settings.SelectChannelFragment.SelectChannelAdapter.SelectChannelItemHolder
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.PicassoHelper
import java.util.Vector

/**
 * Created by Christian Schabesberger on 26.09.17.
 * SelectChannelFragment.java is part of NewPipe.
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
 * along with NewPipe.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 *
 */
class SelectChannelFragment() : DialogFragment() {
    private var onSelectedListener: OnSelectedListener? = null
    private var onCancelListener: OnCancelListener? = null
    private var progressBar: ProgressBar? = null
    private var emptyView: TextView? = null
    private var recyclerView: RecyclerView? = null
    private var subscriptions: List<SubscriptionEntity> = Vector()
    fun setOnSelectedListener(listener: OnSelectedListener?) {
        onSelectedListener = listener
    }

    fun setOnCancelListener(listener: OnCancelListener?) {
        onCancelListener = listener
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
        val v: View = inflater.inflate(R.layout.select_channel_fragment, container, false)
        recyclerView = v.findViewById(R.id.items_list)
        recyclerView.setLayoutManager(LinearLayoutManager(getContext()))
        val channelAdapter: SelectChannelAdapter = SelectChannelAdapter()
        recyclerView.setAdapter(channelAdapter)
        progressBar = v.findViewById(R.id.progressBar)
        emptyView = v.findViewById(R.id.empty_state_view)
        progressBar.setVisibility(View.VISIBLE)
        recyclerView.setVisibility(View.GONE)
        emptyView.setVisibility(View.GONE)
        val subscriptionManager: SubscriptionManager = SubscriptionManager(requireContext())
        subscriptionManager.subscriptions().toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriptionObserver())
        return v
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCancel(dialogInterface: DialogInterface) {
        super.onCancel(dialogInterface)
        if (onCancelListener != null) {
            onCancelListener!!.onCancel()
        }
    }

    private fun clickedItem(position: Int) {
        if (onSelectedListener != null) {
            val entry: SubscriptionEntity = subscriptions.get(position)
            onSelectedListener!!
                    .onChannelSelected(entry.getServiceId(), entry.getUrl(), entry.getName())
        }
        dismiss()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Item handling
    ////////////////////////////////////////////////////////////////////////// */
    private fun displayChannels(newSubscriptions: List<SubscriptionEntity>) {
        subscriptions = newSubscriptions
        progressBar!!.setVisibility(View.GONE)
        if (newSubscriptions.isEmpty()) {
            emptyView!!.setVisibility(View.VISIBLE)
            return
        }
        recyclerView!!.setVisibility(View.VISIBLE)
    }

    private fun getSubscriptionObserver(): Observer<List<SubscriptionEntity>> {
        return object : Observer<List<SubscriptionEntity>> {
            public override fun onSubscribe(disposable: Disposable) {}
            public override fun onNext(newSubscriptions: List<SubscriptionEntity>) {
                displayChannels(newSubscriptions)
            }

            public override fun onError(exception: Throwable) {
                showUiErrorSnackbar(this@SelectChannelFragment,
                        "Loading subscription", exception)
            }

            public override fun onComplete() {}
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    ////////////////////////////////////////////////////////////////////////// */
    open interface OnSelectedListener {
        fun onChannelSelected(serviceId: Int, url: String?, name: String?)
    }

    open interface OnCancelListener {
        fun onCancel()
    }

    private inner class SelectChannelAdapter() : RecyclerView.Adapter<SelectChannelItemHolder>() {
        public override fun onCreateViewHolder(parent: ViewGroup,
                                               viewType: Int): SelectChannelItemHolder {
            val item: View = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.select_channel_item, parent, false)
            return SelectChannelItemHolder(item)
        }

        public override fun onBindViewHolder(holder: SelectChannelItemHolder, position: Int) {
            val entry: SubscriptionEntity = subscriptions.get(position)
            holder.titleView.setText(entry.getName())
            holder.view.setOnClickListener(View.OnClickListener({ view: View? -> clickedItem(position) }))
            PicassoHelper.loadAvatar(entry.getAvatarUrl()).into(holder.thumbnailView)
        }

        public override fun getItemCount(): Int {
            return subscriptions.size
        }

        inner class SelectChannelItemHolder internal constructor(val view: View) : RecyclerView.ViewHolder(v) {
            val thumbnailView: ImageView
            val titleView: TextView

            init {
                thumbnailView = v.findViewById<ImageView>(R.id.itemThumbnailView)
                titleView = v.findViewById<TextView>(R.id.itemTitleView)
            }
        }
    }
}
