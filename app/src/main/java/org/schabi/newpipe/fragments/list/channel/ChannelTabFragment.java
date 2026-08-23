package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.Queue;

import io.reactivex.rxjava3.core.Single;

public class ChannelTabFragment extends BaseListInfoFragment<InfoItem, ChannelTabInfo> {

    private static final String KEY_SERVICE_ID = "serviceId";
    private static final String KEY_TAB_HANDLER = "tabHandler";
    private static final String KEY_CHANNEL_NAME = "channelName";

    protected int serviceId = Constants.NO_SERVICE_ID;

    protected ListLinkHandler tabHandler;

    protected String channelName;

    public static ChannelTabFragment getInstance(final int serviceId,
                                                 final ListLinkHandler tabHandler,
                                                 final String channelName) {
        final ChannelTabFragment instance = new ChannelTabFragment();
        final Bundle arguments = new Bundle();
        arguments.putInt(KEY_SERVICE_ID, serviceId);
        arguments.putSerializable(KEY_TAB_HANDLER, tabHandler);
        arguments.putString(KEY_CHANNEL_NAME, channelName);
        instance.setArguments(arguments);
        instance.serviceId = serviceId;
        instance.tabHandler = tabHandler;
        instance.channelName = channelName;
        return instance;
    }

    public ChannelTabFragment() {
        super(UserAction.REQUESTED_CHANNEL);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SERVICE_ID, serviceId);
        outState.putSerializable(KEY_TAB_HANDLER, tabHandler);
        outState.putString(KEY_CHANNEL_NAME, channelName);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceId = savedInstanceState.getInt(KEY_SERVICE_ID, Constants.NO_SERVICE_ID);
        if (savedInstanceState.containsKey(KEY_TAB_HANDLER)) {
            tabHandler = (ListLinkHandler) savedInstanceState.getSerializable(KEY_TAB_HANDLER);
        }
        channelName = savedInstanceState.getString(KEY_CHANNEL_NAME);
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(tabHandler);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        tabHandler = (ListLinkHandler) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreFromArguments();
        setHasOptionsMenu(false);
    }

    void restoreFromArguments() {
        if (tabHandler == null && getArguments() != null) {
            serviceId = getArguments().getInt(KEY_SERVICE_ID, Constants.NO_SERVICE_ID);
            tabHandler = (ListLinkHandler) getArguments().getSerializable(KEY_TAB_HANDLER);
            channelName = getArguments().getString(KEY_CHANNEL_NAME);
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_tab, container, false);
    }

    @Override
    protected Single<ChannelTabInfo> loadResult(final boolean forceLoad) {
        if (tabHandler == null) {
            return Single.error(new IllegalStateException(
                    "The channel tab link handler could not be restored"));
        }
        return ExtractorHelper.getChannelTab(serviceId, tabHandler, forceLoad);
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        if (tabHandler == null) {
            return Single.error(new IllegalStateException(
                    "The channel tab link handler could not be restored"));
        }
        return ExtractorHelper.getMoreChannelTabItems(serviceId, tabHandler, currentNextPage);
    }

    @Override
    public void setTitle(final String title) {
        super.setTitle(channelName);
    }
}
