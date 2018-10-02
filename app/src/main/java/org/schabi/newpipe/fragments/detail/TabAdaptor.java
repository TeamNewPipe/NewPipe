package org.schabi.newpipe.fragments.detail;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;

import java.util.ArrayList;
import java.util.List;

public class TabAdaptor extends FragmentPagerAdapter {

    private final List<Fragment> mFragmentList = new ArrayList<>();
    private final List<String> mFragmentTitleList = new ArrayList<>();
    int baseId = 0;

    public TabAdaptor(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        return mFragmentList.get(position);
    }

    @Override
    public int getCount() {
        return mFragmentList.size();
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return mFragmentTitleList.get(position);
    }

    @Override
    public long getItemId(int position) {
        // give an ID different from position when position has been changed
        return baseId + position;
    }

    public void addFragment(Fragment fragment, String title) {
        mFragmentList.add(fragment);
        mFragmentTitleList.add(title);
        notifyDataSetChanged();
    }

    public void clearAllItems() {
        mFragmentList.clear();
        mFragmentTitleList.clear();
        notifyDataSetChanged();
    }

    public void removeItem(int position){
        mFragmentList.remove(position == 0 ? 0 : position - 1);
        mFragmentTitleList.remove(position == 0 ? 0 : position - 1);
        notifyDataSetChanged();
    }

    public void updateItem(int position, Fragment fragment){
        mFragmentList.set(position, fragment);
        // shift the ID returned by getItemId outside the range of all previous fragments
        // https://stackoverflow.com/questions/10396321/remove-fragment-page-from-viewpager-in-android
        baseId += getCount() + 1;
        notifyDataSetChanged();
    }

    public void updateItem(String title, Fragment fragment){
        int index = mFragmentTitleList.indexOf(title);
        if(index != -1){
            updateItem(index, fragment);
        }
    }

    @Override
    public int getItemPosition(Object object) {
        if (mFragmentList.contains(object)) return mFragmentList.indexOf(object);
        else return POSITION_NONE;
    }
}
