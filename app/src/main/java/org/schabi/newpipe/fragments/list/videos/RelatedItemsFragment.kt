package org.schabi.newpipe.fragments.list.videos

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.RelatedItemsHeaderBinding
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.ktx.slideUp
import java.io.Serializable
import java.util.concurrent.Callable
import java.util.function.Supplier

class RelatedItemsFragment() : BaseListInfoFragment<InfoItem, RelatedItemsInfo>(UserAction.REQUESTED_STREAM), OnSharedPreferenceChangeListener {
    private var relatedItemsInfo: RelatedItemsInfo? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */
    private var headerBinding: RelatedItemsHeaderBinding? = null

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_related_items, container, false)
    }

    public override fun onDestroyView() {
        headerBinding = null
        super.onDestroyView()
    }

    override fun getListHeaderSupplier(): Supplier<View>? {
        if (relatedItemsInfo == null || relatedItemsInfo!!.getRelatedItems() == null) {
            return null
        }
        headerBinding = RelatedItemsHeaderBinding
                .inflate(activity!!.getLayoutInflater(), itemsList, false)
        val pref: SharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
        val autoplay: Boolean = pref.getBoolean(getString(R.string.auto_queue_key), false)
        headerBinding!!.autoplaySwitch.setChecked(autoplay)
        headerBinding!!.autoplaySwitch.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener({ compoundButton: CompoundButton?, b: Boolean ->
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putBoolean(getString(R.string.auto_queue_key), b).apply()
        }))
        return Supplier({ headerBinding!!.getRoot() })
    }

    override fun loadMoreItemsLogic(): Single<InfoItemsPage<InfoItem>>? {
        return Single.fromCallable<InfoItemsPage<InfoItem>?>(Callable({ InfoItemsPage.emptyPage() }))
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    override fun loadResult(forceLoad: Boolean): Single<RelatedItemsInfo>? {
        return Single.fromCallable((Callable({ relatedItemsInfo })))
    }

    public override fun showLoading() {
        super.showLoading()
        if (headerBinding != null) {
            headerBinding!!.getRoot().setVisibility(View.INVISIBLE)
        }
    }

    public override fun handleResult(result: RelatedItemsInfo) {
        super.handleResult(result)
        if (headerBinding != null) {
            headerBinding!!.getRoot().setVisibility(View.VISIBLE)
        }
        requireView().slideUp(120, 96, 0.06f)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    public override fun setTitle(title: String?) {
        // Nothing to do - override parent
    }

    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        // Nothing to do - override parent
    }

    private fun setInitialData(info: StreamInfo) {
        super.setInitialData(info.getServiceId(), info.getUrl(), info.getName())
        if (relatedItemsInfo == null) {
            relatedItemsInfo = RelatedItemsInfo(info)
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(INFO_KEY, relatedItemsInfo)
    }

    override fun onRestoreInstanceState(savedState: Bundle) {
        super.onRestoreInstanceState(savedState)
        val serializable: Serializable? = savedState.getSerializable(INFO_KEY)
        if (serializable is RelatedItemsInfo) {
            relatedItemsInfo = serializable
        }
    }

    public override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                                  key: String?) {
        if (headerBinding != null && (getString(R.string.auto_queue_key) == key)) {
            headerBinding!!.autoplaySwitch.setChecked(sharedPreferences.getBoolean(key, false))
        }
    }

    override fun getItemViewMode(): ItemViewMode? {
        var mode: ItemViewMode? = super.getItemViewMode()
        // Only list mode is supported. Either List or card will be used.
        if (mode != ItemViewMode.LIST && mode != ItemViewMode.CARD) {
            mode = ItemViewMode.LIST
        }
        return mode
    }

    companion object {
        private val INFO_KEY: String = "related_info_key"
        fun getInstance(info: StreamInfo): RelatedItemsFragment {
            val instance: RelatedItemsFragment = RelatedItemsFragment()
            instance.setInitialData(info)
            return instance
        }
    }
}
