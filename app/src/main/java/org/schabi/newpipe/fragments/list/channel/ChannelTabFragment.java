package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AnimationUtils;

import java.io.Serializable;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

import static org.schabi.newpipe.util.ExtractorHelper.getMoreChannelTabItems;

public class ChannelTabFragment extends BaseListInfoFragment<ChannelTabInfo> {

    private CompositeDisposable disposables = new CompositeDisposable();
    private ChannelTabInfo relatedStreamInfo;

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
        return getMoreChannelTabItems(relatedStreamInfo, currentNextPageUrl);
    }

    @Override
    protected Single<ChannelTabInfo> loadResult(boolean forceLoad) {
        return Single.fromCallable(() -> relatedStreamInfo);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull ChannelTabInfo result) {
        super.handleResult(result);

        AnimationUtils.slideUp(getView(),120, 96, 0.06f);

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(), UserAction.REQUESTED_STREAM, NewPipe.getNameOfService(result.getServiceId()), result.getUrl(), 0);
        }

        if (disposables != null) disposables.clear();
    }

    @Override
    public void handleNextItems(ListExtractor.InfoItemsPage result) {
        super.handleNextItems(result);

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(),
                    UserAction.REQUESTED_STREAM,
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

        if(this.relatedStreamInfo == null) this.relatedStreamInfo = info;
    }

    private static final String INFO_KEY = "related_info_key";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(INFO_KEY, relatedStreamInfo);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedState) {
        super.onRestoreInstanceState(savedState);

        Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof ChannelTabInfo) {
            this.relatedStreamInfo = (ChannelTabInfo) serializable;
        }
    }

    @Override
    protected boolean isGridLayout() {
        return false;
    }
}
