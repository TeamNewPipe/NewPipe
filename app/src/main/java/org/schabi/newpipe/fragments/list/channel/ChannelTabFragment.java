package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

import static org.schabi.newpipe.util.ExtractorHelper.getMoreChannelTabItems;

public class ChannelTabFragment extends BaseListInfoFragment<ChannelTabInfo> {

    private CompositeDisposable disposables = new CompositeDisposable();
    private ChannelTabInfo channelTabInfo;

    private View playlistCtrl;
    private LinearLayout headerPlayAllButton;
    private LinearLayout headerPopupButton;
    private LinearLayout headerBackgroundButton;


    public static ChannelTabFragment getInstance(ChannelTabInfo info) {
        ChannelTabFragment instance = new ChannelTabFragment();
        instance.setInitialData(info);
        return instance;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        mIsVisibleToUser = isVisibleToUser;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_tab, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (disposables != null) disposables.clear();
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage> loadMoreItemsLogic() {
        return getMoreChannelTabItems(channelTabInfo, currentNextPageUrl);
    }

    @Override
    protected Single<ChannelTabInfo> loadResult(boolean forceLoad) {
        return Single.fromCallable(() -> channelTabInfo.loadTab());
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        playlistCtrl = rootView.findViewById(R.id.playlist_control);
        playlistCtrl.setVisibility(View.GONE);

        headerPlayAllButton = playlistCtrl.findViewById(R.id.playlist_ctrl_play_all_button);
        headerPopupButton = playlistCtrl.findViewById(R.id.playlist_ctrl_play_popup_button);
        headerBackgroundButton = playlistCtrl.findViewById(R.id.playlist_ctrl_play_bg_button);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull ChannelTabInfo result) {
        super.handleResult(result);

        AnimationUtils.slideUp(getView(),120, 96, 0.06f);

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(), UserAction.REQUESTED_CHANNEL, NewPipe.getNameOfService(result.getServiceId()), result.getUrl(), 0);
        }

        if (disposables != null) disposables.clear();

        switch (result.getName()) {
            case "Videos":
            case "Tracks":
            case "Popular tracks":
            case "Reposts":
            case "Events":
                playlistCtrl.setVisibility(View.VISIBLE);

                headerPlayAllButton.setOnClickListener(
                        view -> NavigationHelper.playOnMainPlayer(activity, getPlayQueue(0), false));
                headerPopupButton.setOnClickListener(
                        view -> NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(0), false));
                headerBackgroundButton.setOnClickListener(
                        view -> NavigationHelper.playOnBackgroundPlayer(activity, getPlayQueue(0), false));
        }

        if (channelTabInfo.isUsedForFeed()) {
            new SubscriptionManager(activity).updateChannelTabInfo(currentInfo);
        }
    }

    private PlayQueue getPlayQueue(final int index) {
        final List<StreamInfoItem> streamItems = new ArrayList<>();
        for (InfoItem i : infoListAdapter.getItemsList()) {
            if (i instanceof StreamInfoItem) {
                streamItems.add((StreamInfoItem) i);
            }
        }
        return new ChannelPlayQueue(
                currentInfo,
                streamItems,
                index
        );
    }

    @Override
    public void handleNextItems(ListExtractor.InfoItemsPage result) {
        super.handleNextItems(result);

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(),
                    UserAction.REQUESTED_CHANNEL,
                    NewPipe.getNameOfService(serviceId),
                    "Get next page of: " + url,
                    R.string.general_error);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnError
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        hideLoading();
        showSnackBarError(exception, UserAction.REQUESTED_CHANNEL, NewPipe.getNameOfService(serviceId), url, R.string.general_error);

        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(String title) {}

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {}

    private void setInitialData(ChannelTabInfo info) {
        super.setInitialData(info.getServiceId(), info.getUrl(), info.getName());

        if (this.channelTabInfo == null) this.channelTabInfo = info;
    }

    private static final String INFO_KEY = "related_info_key";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(INFO_KEY, channelTabInfo);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedState) {
        super.onRestoreInstanceState(savedState);

        Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof ChannelTabInfo) {
            this.channelTabInfo = (ChannelTabInfo) serializable;
        }
    }

    @Override
    protected boolean isGridLayout() {
        return false;
    }
}
