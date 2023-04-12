package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.databinding.FragmentChannelVideosBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class ChannelVideosFragment extends BaseListInfoFragment<StreamInfoItem, ChannelInfo> {

    private final CompositeDisposable disposables = new CompositeDisposable();

    private FragmentChannelVideosBinding channelBinding;
    private PlaylistControlBinding playlistControlBinding;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructors and lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    // required by the Android framework to restore fragments after saving
    public ChannelVideosFragment() {
        super(UserAction.REQUESTED_CHANNEL);
    }

    public ChannelVideosFragment(final int serviceId, final String url, final String name) {
        this();
        setInitialData(serviceId, url, name);
    }

    public ChannelVideosFragment(@NonNull final ChannelInfo info) {
        this(info.getServiceId(), info.getUrl(), info.getName());
        this.currentInfo = info;
        this.currentNextPage = info.getNextPage();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity != null && useAsFrontPage) {
            setTitle(currentInfo != null ? currentInfo.getName() : name);
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        channelBinding = FragmentChannelVideosBinding.inflate(inflater, container, false);
        return channelBinding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
        channelBinding = null;
        playlistControlBinding = null;
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        playlistControlBinding = PlaylistControlBinding
                .inflate(activity.getLayoutInflater(), itemsList, false);
        return playlistControlBinding::getRoot;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Loading
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreChannelItems(serviceId, url, currentNextPage);
    }

    @Override
    protected Single<ChannelInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getChannelInfo(serviceId, url, forceLoad);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final ChannelInfo result) {
        super.handleResult(result);

        // PlaylistControls should be visible only if there is some item in
        // infoListAdapter other than header
        if (infoListAdapter.getItemCount() != 1) {
            playlistControlBinding.getRoot().setVisibility(View.VISIBLE);
        } else {
            playlistControlBinding.getRoot().setVisibility(View.GONE);
        }

        disposables.clear();

        playlistControlBinding.playlistCtrlPlayAllButton.setOnClickListener(
                view -> NavigationHelper.playOnMainPlayer(activity, getPlayQueue()));
        playlistControlBinding.playlistCtrlPlayPopupButton.setOnClickListener(
                view -> NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(), false));
        playlistControlBinding.playlistCtrlPlayBgButton.setOnClickListener(
                view -> NavigationHelper.playOnBackgroundPlayer(activity, getPlayQueue(), false));

        playlistControlBinding.playlistCtrlPlayPopupButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPlayer(activity, getPlayQueue(), PlayerType.POPUP);
            return true;
        });

        playlistControlBinding.playlistCtrlPlayBgButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPlayer(activity, getPlayQueue(), PlayerType.AUDIO);
            return true;
        });
    }

    private PlayQueue getPlayQueue() {
        final List<StreamInfoItem> streamItems = infoListAdapter.getItemsList().stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList());

        return new ChannelPlayQueue(currentInfo.getServiceId(), currentInfo.getUrl(),
                currentInfo.getNextPage(), streamItems, 0);
    }
}
