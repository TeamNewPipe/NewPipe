package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.ktx.slideUp
import org.schabi.newpipe.util.ExtractorHelper

class CommentsFragment() : BaseListInfoFragment<CommentsInfoItem?, CommentsInfo>(UserAction.REQUESTED_COMMENTS) {
    private val disposables: CompositeDisposable = CompositeDisposable()
    private var emptyStateDesc: TextView? = null
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        emptyStateDesc = rootView.findViewById(R.id.empty_state_desc)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_comments, container, false)
    }

    public override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    ////////////////////////////////////////////////////////////////////////// */
    override fun loadMoreItemsLogic(): Single<InfoItemsPage<CommentsInfoItem?>?>? {
        return ExtractorHelper.getMoreCommentItems(serviceId, currentInfo, currentNextPage)
    }

    override fun loadResult(forceLoad: Boolean): Single<CommentsInfo>? {
        return ExtractorHelper.getCommentsInfo(serviceId, (url)!!, forceLoad)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    public override fun handleResult(result: CommentsInfo) {
        super.handleResult(result)
        emptyStateDesc!!.setText(
                if (result.isCommentsDisabled()) R.string.comments_are_disabled else R.string.no_comments)
        requireView().slideUp(120, 150, 0.06f)
        disposables.clear()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    public override fun setTitle(title: String?) {}
    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
    }

    override fun getItemViewMode(): ItemViewMode? {
        return ItemViewMode.LIST
    }

    fun scrollToComment(comment: CommentsInfoItem?): Boolean {
        val position: Int = infoListAdapter!!.getItemsList().indexOf(comment)
        if (position < 0) {
            return false
        }
        itemsList!!.scrollToPosition(position)
        return true
    }

    companion object {
        fun getInstance(serviceId: Int, url: String?,
                        name: String?): CommentsFragment {
            val instance: CommentsFragment = CommentsFragment()
            instance.setInitialData(serviceId, url, name)
            return instance
        }
    }
}
