package org.schabi.newpipe.fragments.list.videos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.RelatedStreamInfo;

import java.io.Serializable;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

public class RelatedVideosFragment extends BaseListInfoFragment<RelatedStreamInfo>
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String INFO_KEY = "related_info_key";
    private CompositeDisposable disposables = new CompositeDisposable();
    private RelatedStreamInfo relatedStreamInfo;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View headerRootLayout;
    private Switch autoplaySwitch;

    public static RelatedVideosFragment getInstance(final StreamInfo info) {
        RelatedVideosFragment instance = new RelatedVideosFragment();
        instance.setInitialData(info);
        return instance;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_related_streams, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) {
            disposables.clear();
        }
    }

    protected View getListHeader() {
        if (relatedStreamInfo != null && relatedStreamInfo.getRelatedItems() != null) {
            headerRootLayout = activity.getLayoutInflater()
                    .inflate(R.layout.related_streams_header, itemsList, false);
            autoplaySwitch = headerRootLayout.findViewById(R.id.autoplay_switch);

            final SharedPreferences pref = PreferenceManager
                    .getDefaultSharedPreferences(getContext());
            final boolean autoplay = pref.getBoolean(getString(R.string.auto_queue_key), false);
            autoplaySwitch.setChecked(autoplay);
            autoplaySwitch.setOnCheckedChangeListener((compoundButton, b) ->
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                            .putBoolean(getString(R.string.auto_queue_key), b).apply());
            return headerRootLayout;
        } else {
            return null;
        }
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage> loadMoreItemsLogic() {
        return Single.fromCallable(ListExtractor.InfoItemsPage::emptyPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<RelatedStreamInfo> loadResult(final boolean forceLoad) {
        return Single.fromCallable(() -> relatedStreamInfo);
    }

    @Override
    public void showLoading() {
        super.showLoading();
        if (headerRootLayout != null) {
            headerRootLayout.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void handleResult(@NonNull final RelatedStreamInfo result) {
        super.handleResult(result);

        if (headerRootLayout != null) {
            headerRootLayout.setVisibility(View.VISIBLE);
        }
        AnimationUtils.slideUp(getView(), 120, 96, 0.06f);

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(), UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(result.getServiceId()), result.getUrl(), 0);
        }

        if (disposables != null) {
            disposables.clear();
        }
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage result) {
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
    protected boolean onError(final Throwable exception) {
        if (super.onError(exception)) {
            return true;
        }

        hideLoading();
        showSnackBarError(exception, UserAction.REQUESTED_STREAM,
                NewPipe.getNameOfService(serviceId), url, R.string.general_error);
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final String title) {
        return;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        return;
    }

    private void setInitialData(final StreamInfo info) {
        super.setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        if (this.relatedStreamInfo == null) {
            this.relatedStreamInfo = RelatedStreamInfo.getInfo(info);
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(INFO_KEY, relatedStreamInfo);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        if (savedState != null) {
            Serializable serializable = savedState.getSerializable(INFO_KEY);
            if (serializable instanceof RelatedStreamInfo) {
                this.relatedStreamInfo = (RelatedStreamInfo) serializable;
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String s) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean autoplay = pref.getBoolean(getString(R.string.auto_queue_key), false);
        if (autoplaySwitch != null) {
            autoplaySwitch.setChecked(autoplay);
        }
    }

    @Override
    protected boolean isGridLayout() {
        return false;
    }
}
