package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ReadyChannelTabListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.PlayButtonHelper;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

public class ChannelTabFragment extends BaseListInfoFragment<InfoItem, ChannelTabInfo>
        implements PlaylistControlViewHolder {

    // states must be protected and not private for State being able to access them
    @State
    protected ListLinkHandler tabHandler;
    @State
    protected String channelName;

    private PlaylistControlBinding playlistControlBinding;

    // Lightweight replacement for tabHandler, created on a background thread in loadResult() and
    // applied on the main thread in handleResult()
    @Nullable
    private ListLinkHandler lightweightTabHandler;

    @NonNull
    public static ChannelTabFragment getInstance(final int serviceId,
                                                 final ListLinkHandler tabHandler,
                                                 final String channelName) {
        final ChannelTabFragment instance = new ChannelTabFragment();
        instance.serviceId = serviceId;
        instance.tabHandler = tabHandler;
        instance.channelName = channelName;
        return instance;
    }

    public ChannelTabFragment() {
        super(UserAction.REQUESTED_CHANNEL);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_tab, container, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playlistControlBinding = null;
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        if (ChannelTabHelper.isStreamsTab(tabHandler)) {
            playlistControlBinding = PlaylistControlBinding
                    .inflate(activity.getLayoutInflater(), itemsList, false);
            return playlistControlBinding::getRoot;
        }
        return null;
    }

    @Override
    protected Single<ChannelTabInfo> loadResult(final boolean forceLoad) {
        final ListLinkHandler handler = tabHandler;
        return ExtractorHelper.getChannelTab(serviceId, handler, forceLoad)
                // Creating the lightweight handler, may perform network requests so it must not
                // happen on the main thread.
                // doOnSuccess runs on the same background thread that loaded the tab.
                .doOnSuccess(result ->
                        lightweightTabHandler = createLightweightTabHandler(result, handler));
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreChannelTabItems(serviceId, tabHandler, currentNextPage);
    }

    @Override
    public void setTitle(final String title) {
        // The channel name is displayed as title in the toolbar.
        // The title is always a description of the content of the tab fragment.
        // It should be unique for each channel because multiple channel tabs
        // can be added to the main page. Therefore, the channel name is used.
        // Using the title variable would cause the title to be the same for all channel tabs.
        super.setTitle(channelName);
    }

    /**
     * Creates a link handler with the same properties as {@code handler}, but without the raw
     * data a {@link ReadyChannelTabListLinkHandler} may contain. Must not be called on the main
     * thread, since some services perform network requests to build the link handler.
     *
     * @param result  the loaded tab info, used to get the service's channel tab factory
     * @param handler the link handler that was used to load the tab
     * @return the lightweight link handler, or {@code null} if it is not needed or could not
     *         be created
     */
    @Nullable
    private ListLinkHandler createLightweightTabHandler(final ChannelTabInfo result,
                                                        final ListLinkHandler handler) {
        // FIXME this is a really hacky workaround, to avoid storing useless data in the fragment
        //  state. The problem is, `ReadyChannelTabListLinkHandler` might contain raw JSON data that
        //  uses a lot of memory (e.g. ~800KB for YouTube). While 800KB doesn't seem much, if
        //  you combine just a couple of channel tab fragments you easily go over the 1MB
        //  save&restore transaction limit, and get `TransactionTooLargeException`s. A proper
        //  solution would require rethinking about `ReadyChannelTabListLinkHandler`s.
        if (handler instanceof ReadyChannelTabListLinkHandler) {
            try {
                // once the tab info has been loaded, the parsed data was already saved to cache, so
                // we can discard any raw data in ReadyChannelTabListLinkHandler and create a
                // link handler with identical properties, but without any raw data
                final ListLinkHandlerFactory channelTabLHFactory = result.getService()
                        .getChannelTabLHFactory();
                if (channelTabLHFactory != null) {
                    // some services do not have a ChannelTabLHFactory
                    return channelTabLHFactory.fromQuery(handler.getId(),
                            handler.getContentFilters(), handler.getSortFilter());
                } else {
                    return null;
                }
            } catch (final ParsingException e) {
                // silently ignore the error, as the app can continue to function normally
                Log.w(TAG, "Could not recreate channel tab handler", e);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public void handleResult(@NonNull final ChannelTabInfo result) {
        super.handleResult(result);

        // Swap in the lightweight handler prepared in loadResult(), now that we are on main thread
        if (lightweightTabHandler != null) {
            tabHandler = lightweightTabHandler;
            lightweightTabHandler = null;
        }

        if (playlistControlBinding != null) {
            // PlaylistControls should be visible only if there is some item in
            // infoListAdapter other than header
            if (infoListAdapter.getItemCount() > 1) {
                playlistControlBinding.getRoot().setVisibility(View.VISIBLE);
            } else {
                playlistControlBinding.getRoot().setVisibility(View.GONE);
            }

            PlayButtonHelper.initPlaylistControlClickListener(
                    activity, playlistControlBinding, this);
        }
    }

    @Override
    public PlayQueue getPlayQueue() {
        final List<StreamInfoItem> streamItems = infoListAdapter.getItemsList().stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList());

        return new ChannelTabPlayQueue(currentInfo.getServiceId(), tabHandler,
                currentInfo.getNextPage(), streamItems, 0);
    }
}
