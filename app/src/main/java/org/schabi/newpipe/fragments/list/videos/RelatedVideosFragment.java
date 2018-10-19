package org.schabi.newpipe.fragments.list.videos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.RelatedStreamInfo;

import java.io.Serializable;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

public class RelatedVideosFragment extends BaseListInfoFragment<RelatedStreamInfo> implements SharedPreferences.OnSharedPreferenceChangeListener{

    private CompositeDisposable disposables = new CompositeDisposable();
    private RelatedStreamInfo relatedStreamInfo;
    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/
    private View headerRootLayout;
    private Switch aSwitch;

    private boolean mIsVisibleToUser = false;

    public static RelatedVideosFragment getInstance(StreamInfo info) {
        RelatedVideosFragment instance = new RelatedVideosFragment();
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
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_related_streams, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) disposables.clear();
    }

    protected View getListHeader(){
        if(relatedStreamInfo != null && relatedStreamInfo.getNextStream() != null){
            headerRootLayout = activity.getLayoutInflater().inflate(R.layout.related_streams_header, itemsList, false);
            aSwitch = headerRootLayout.findViewById(R.id.autoplay_switch);

            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
            Boolean autoplay = pref.getBoolean(getString(R.string.auto_queue_key), false);
            aSwitch.setChecked(autoplay);
            aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
                    prefEdit.putBoolean(getString(R.string.auto_queue_key), b);
                    prefEdit.apply();
                }
            });
            return headerRootLayout;
        }else{
            return null;
        }
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage> loadMoreItemsLogic() {
        return Single.fromCallable(() -> ListExtractor.InfoItemsPage.emptyPage());
    }

    @Override
    protected Single<RelatedStreamInfo> loadResult(boolean forceLoad) {
        return Single.fromCallable(() -> relatedStreamInfo);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
    }

    @Override
    public void handleResult(@NonNull RelatedStreamInfo result) {
        super.handleResult(result);

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

        showSnackBarError(exception, UserAction.REQUESTED_STREAM, NewPipe.getNameOfService(serviceId), url, R.string.general_error);
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

    private void setInitialData(StreamInfo info) {
        super.setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        if(this.relatedStreamInfo == null) this.relatedStreamInfo = RelatedStreamInfo.getInfo(info);
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
        if (savedState != null) {
            Serializable serializable = savedState.getSerializable(INFO_KEY);
            if(serializable instanceof RelatedStreamInfo){
                this.relatedStreamInfo = (RelatedStreamInfo) serializable;
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
        Boolean autoplay = pref.getBoolean(getString(R.string.auto_queue_key), false);
        aSwitch.setChecked(autoplay);
    }
}
