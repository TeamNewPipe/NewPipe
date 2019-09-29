package org.schabi.newpipe.fragments.list.comments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.ExtractorHelper;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

public class CommentsFragment extends BaseListInfoFragment<CommentsInfo> {

    private CompositeDisposable disposables = new CompositeDisposable();
    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/



    private boolean mIsVisibleToUser = false;

    public static CommentsFragment getInstance(int serviceId, String url, String name) {
        CommentsFragment instance = new CommentsFragment();
        instance.setInitialData(serviceId, url, name);
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
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comments, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) disposables.clear();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ListExtractor.InfoItemsPage> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreCommentItems(serviceId, currentInfo, currentNextPageUrl);
    }

    @Override
    protected Single<CommentsInfo> loadResult(boolean forceLoad) {
        return ExtractorHelper.getCommentsInfo(serviceId, url, forceLoad);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
    }

    @Override
    public void handleResult(@NonNull CommentsInfo result) {
        super.handleResult(result);

        AnimationUtils.slideUp(getView(),120, 150, 0.06f);

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(), UserAction.REQUESTED_COMMENTS, NewPipe.getNameOfService(result.getServiceId()), result.getUrl(), 0);
        }

        if (disposables != null) disposables.clear();
    }

    @Override
    public void handleNextItems(ListExtractor.InfoItemsPage result) {
        super.handleNextItems(result);

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(),
                    UserAction.REQUESTED_COMMENTS,
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
        showSnackBarError(exception, UserAction.REQUESTED_COMMENTS, NewPipe.getNameOfService(serviceId), url, R.string.error_unable_to_load_comments);
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(String title) {
        return;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        return;
    }

    @Override
    protected boolean isGridLayout() {
        return false;
    }
}
