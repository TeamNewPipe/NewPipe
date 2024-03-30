package org.schabi.newpipe.fragments.list.channel

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import icepick.State
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlaylistControlBinding
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.ReadyChannelTabListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.ChannelTabHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.PlayButtonHelper
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors

class ChannelTabFragment() : BaseListInfoFragment<InfoItem?, ChannelTabInfo>(UserAction.REQUESTED_CHANNEL), PlaylistControlViewHolder {
    // states must be protected and not private for IcePick being able to access them
    @State
    protected var tabHandler: ListLinkHandler? = null

    @State
    protected var channelName: String? = null
    private var playlistControlBinding: PlaylistControlBinding? = null

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(false)
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_channel_tab, container, false)
    }

    public override fun onDestroyView() {
        super.onDestroyView()
        playlistControlBinding = null
    }

    override fun getListHeaderSupplier(): Supplier<View>? {
        if (ChannelTabHelper.isStreamsTab(tabHandler)) {
            playlistControlBinding = PlaylistControlBinding
                    .inflate(activity!!.getLayoutInflater(), itemsList, false)
            return Supplier({ playlistControlBinding!!.getRoot() })
        }
        return null
    }

    override fun loadResult(forceLoad: Boolean): Single<ChannelTabInfo>? {
        return ExtractorHelper.getChannelTab(serviceId, tabHandler, forceLoad)
    }

    override fun loadMoreItemsLogic(): Single<InfoItemsPage<InfoItem?>?>? {
        return ExtractorHelper.getMoreChannelTabItems(serviceId, tabHandler, currentNextPage)
    }

    public override fun setTitle(title: String?) {
        // The channel name is displayed as title in the toolbar.
        // The title is always a description of the content of the tab fragment.
        // It should be unique for each channel because multiple channel tabs
        // can be added to the main page. Therefore, the channel name is used.
        // Using the title variable would cause the title to be the same for all channel tabs.
        super.setTitle(channelName)
    }

    public override fun handleResult(result: ChannelTabInfo) {
        super.handleResult(result)

        // FIXME this is a really hacky workaround, to avoid storing useless data in the fragment
        //  state. The problem is, `ReadyChannelTabListLinkHandler` might contain raw JSON data that
        //  uses a lot of memory (e.g. ~800KB for YouTube). While 800KB doesn't seem much, if
        //  you combine just a couple of channel tab fragments you easily go over the 1MB
        //  save&restore transaction limit, and get `TransactionTooLargeException`s. A proper
        //  solution would require rethinking about `ReadyChannelTabListLinkHandler`s.
        if (tabHandler is ReadyChannelTabListLinkHandler) {
            try {
                // once `handleResult` is called, the parsed data was already saved to cache, so
                // we can discard any raw data in ReadyChannelTabListLinkHandler and create a
                // link handler with identical properties, but without any raw data
                val channelTabLHFactory: ListLinkHandlerFactory? = result.getService()
                        .getChannelTabLHFactory()
                if (channelTabLHFactory != null) {
                    // some services do not not have a ChannelTabLHFactory
                    tabHandler = channelTabLHFactory.fromQuery(tabHandler.getId(),
                            tabHandler.getContentFilters(), tabHandler.getSortFilter())
                }
            } catch (e: ParsingException) {
                // silently ignore the error, as the app can continue to function normally
                Log.w(TAG, "Could not recreate channel tab handler", e)
            }
        }
        if (playlistControlBinding != null) {
            // PlaylistControls should be visible only if there is some item in
            // infoListAdapter other than header
            if (infoListAdapter!!.getItemCount() > 1) {
                playlistControlBinding!!.getRoot().setVisibility(View.VISIBLE)
            } else {
                playlistControlBinding!!.getRoot().setVisibility(View.GONE)
            }
            PlayButtonHelper.initPlaylistControlClickListener(
                    (activity)!!, playlistControlBinding!!, this)
        }
    }

    public override fun getPlayQueue(): PlayQueue {
        val streamItems: List<StreamInfoItem> = infoListAdapter!!.getItemsList().stream()
                .filter(Predicate({ obj: InfoItem? -> StreamInfoItem::class.java.isInstance(obj) }))
                .map(Function({ obj: InfoItem? -> StreamInfoItem::class.java.cast(obj) }))
                .collect(Collectors.toList())
        return ChannelTabPlayQueue(currentInfo!!.getServiceId(), tabHandler,
                currentInfo!!.getNextPage(), streamItems, 0)
    }

    companion object {
        fun getInstance(serviceId: Int,
                        tabHandler: ListLinkHandler?,
                        channelName: String?): ChannelTabFragment {
            val instance: ChannelTabFragment = ChannelTabFragment()
            instance.serviceId = serviceId
            instance.tabHandler = tabHandler
            instance.channelName = channelName
            return instance
        }
    }
}
