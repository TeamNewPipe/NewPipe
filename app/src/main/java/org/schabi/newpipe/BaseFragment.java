package org.schabi.newpipe;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;

import icepick.Icepick;

public abstract class BaseFragment extends Fragment {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected boolean DEBUG = MainActivity.DEBUG;

    protected AppCompatActivity activity;
    public static final ImageLoader imageLoader = ImageLoader.getInstance();

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (AppCompatActivity) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        super.onCreate(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);
        if (savedInstanceState != null) onRestoreInstanceState(savedInstanceState);
    }


    @Override
    public void onViewCreated(View rootView, Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        if (DEBUG) {
            Log.d(TAG, "onViewCreated() called with: rootView = [" + rootView + "], savedInstanceState = [" + savedInstanceState + "]");
        }
        initViews(rootView, savedInstanceState);
        initListeners();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    protected void initViews(View rootView, Bundle savedInstanceState) {
    }

    protected void initListeners() {
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected final int resolveResourceIdFromAttr(@AttrRes int attr) {
        TypedArray a = activity.getTheme().obtainStyledAttributes(new int[]{attr});
        int attributeResourceId = a.getResourceId(0, 0);
        a.recycle();
        return attributeResourceId;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // DisplayImageOptions default configurations
    //////////////////////////////////////////////////////////////////////////*/

    public static final DisplayImageOptions BASE_OPTIONS =
            new DisplayImageOptions.Builder().cacheInMemory(true).build();

    public static final DisplayImageOptions DISPLAY_AVATAR_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_OPTIONS)
                    .showImageOnLoading(R.drawable.buddy)
                    .showImageForEmptyUri(R.drawable.buddy)
                    .showImageOnFail(R.drawable.buddy)
                    .build();

    public static final DisplayImageOptions DISPLAY_THUMBNAIL_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_OPTIONS)
                    .displayer(new FadeInBitmapDisplayer(250))
                    .showImageForEmptyUri(R.drawable.dummy_thumbnail)
                    .showImageOnFail(R.drawable.dummy_thumbnail)
                    .build();

    public static final DisplayImageOptions DISPLAY_BANNER_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cloneFrom(BASE_OPTIONS)
                    .showImageOnLoading(R.drawable.channel_banner)
                    .showImageForEmptyUri(R.drawable.channel_banner)
                    .showImageOnFail(R.drawable.channel_banner)
                    .build();
}
