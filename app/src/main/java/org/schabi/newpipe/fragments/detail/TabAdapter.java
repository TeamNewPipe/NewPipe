package org.schabi.newpipe.fragments.detail;

import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.ArrayList;
import java.util.List;

public class TabAdapter extends FragmentPagerAdapter {
    private final List<Fragment> mFragmentList = new ArrayList<>();
    private final List<String> mFragmentTitleList = new ArrayList<>();
    private final FragmentManager fragmentManager;

    public TabAdapter(final FragmentManager fm) {
        // if changed to BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT => crash if enqueueing stream in
        // the background and then clicking on it to open VideoDetailFragment:
        // "Cannot setMaxLifecycle for Fragment not attached to FragmentManager"
        super(fm, BEHAVIOR_SET_USER_VISIBLE_HINT);
        this.fragmentManager = fm;
    }

    @NonNull
    @Override
    public Fragment getItem(final int position) {
        return mFragmentList.get(position);
    }

    @Override
    public int getCount() {
        return mFragmentList.size();
    }

    @Override
    public long getItemId(final int position) {
        return mFragmentTitleList.get(position).hashCode();
    }

    public void addFragment(final Fragment fragment, final String title) {
        if(fragmentManager.isStateSaved()){
            Log.d("TabAdapter", "This should not happen: addFragment() called after onSaveInstanceState()");
            return ;
        }
        mFragmentList.add(fragment);
        mFragmentTitleList.add(title);
        notifyDataSetChanged();
    }

    public void clearAllItems() {
        if(fragmentManager.isStateSaved()){
            Log.d("TabAdapter", "This should not happen: clearAllItems() called after onSaveInstanceState()");
            return ;
        }
        mFragmentList.clear();
        mFragmentTitleList.clear();
        notifyDataSetChanged();
    }

    public void removeItem(final int position) {
        if(fragmentManager.isStateSaved()){
            Log.d("TabAdapter", "This should not happen: removeItem() called after onSaveInstanceState()");
            return ;
        }
        mFragmentList.remove(position);
        mFragmentTitleList.remove(position);
        notifyDataSetChanged();
    }

    public void updateItem(final int position, final Fragment fragment) {
        if(fragmentManager.isStateSaved()){
            Log.d("TabAdapter", "This should not happen: updateItem() called after onSaveInstanceState()");
            return ;
        }
        mFragmentList.set(position, fragment);
        notifyDataSetChanged();
    }

    public void updateItem(final String title, final Fragment fragment) {
        final int index = mFragmentTitleList.indexOf(title);
        if (index != -1) {
            updateItem(index, fragment);
        }
    }

    @Override
    public int getItemPosition(@NonNull final Object object) {
        if (mFragmentList.contains(object)) {
            return mFragmentList.indexOf(object);
        } else {
            return POSITION_NONE;
        }
    }

    public int getItemPositionByTitle(final String title) {
        return mFragmentTitleList.indexOf(title);
    }

    @Nullable
    public String getItemTitle(final int position) {
        if (position < 0 || position >= mFragmentTitleList.size()) {
            return null;
        }
        return mFragmentTitleList.get(position);
    }

    public void notifyDataSetUpdate() {
        notifyDataSetChanged();
    }

    @Override
    public void destroyItem(@NonNull final ViewGroup container,
                            final int position,
                            @NonNull final Object object) {
        fragmentManager.beginTransaction().remove((Fragment) object).commitNowAllowingStateLoss();
    }

    @Override
    public void notifyDataSetChanged() {
        if (!fragmentManager.isStateSaved()) {
            try {
                super.notifyDataSetChanged();
            } catch (final IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }
}
