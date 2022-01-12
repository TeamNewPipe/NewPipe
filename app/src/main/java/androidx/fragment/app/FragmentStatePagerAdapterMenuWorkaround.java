/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.fragment.app;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager.widget.PagerAdapter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

// TODO: Replace this deprecated class with its ViewPager2 counterpart

/**
 * This is a copy from {@link androidx.fragment.app.FragmentStatePagerAdapter}.
 * <p>
 * It includes a workaround to fix the menu visibility when the adapter is restored.
 * </p>
 * <p>
 * When restoring the state of this adapter, all the fragments' menu visibility were set to false,
 * effectively disabling the menu from the user until he switched pages or another event
 * that triggered the menu to be visible again happened.
 * </p>
 * <p>
 * <b>Check out the changes in:</b>
 * </p>
 * <ul>
 *     <li>{@link #saveState()}</li>
 *     <li>{@link #restoreState(Parcelable, ClassLoader)}</li>
 * </ul>
 *
 * @deprecated Switch to {@link androidx.viewpager2.widget.ViewPager2} and use
 * {@link androidx.viewpager2.adapter.FragmentStateAdapter} instead.
 */
@SuppressWarnings("deprecation")
@Deprecated
public abstract class FragmentStatePagerAdapterMenuWorkaround extends PagerAdapter {
    private static final String TAG = "FragmentStatePagerAdapt";
    private static final boolean DEBUG = false;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BEHAVIOR_SET_USER_VISIBLE_HINT, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT})
    private @interface Behavior { }

    /**
     * Indicates that {@link Fragment#setUserVisibleHint(boolean)} will be called when the current
     * fragment changes.
     *
     * @deprecated This behavior relies on the deprecated
     * {@link Fragment#setUserVisibleHint(boolean)} API. Use
     * {@link #BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT} to switch to its replacement,
     * {@link FragmentTransaction#setMaxLifecycle}.
     * @see #FragmentStatePagerAdapterMenuWorkaround(FragmentManager, int)
     */
    @Deprecated
    public static final int BEHAVIOR_SET_USER_VISIBLE_HINT = 0;

    /**
     * Indicates that only the current fragment will be in the {@link Lifecycle.State#RESUMED}
     * state. All other Fragments are capped at {@link Lifecycle.State#STARTED}.
     *
     * @see #FragmentStatePagerAdapterMenuWorkaround(FragmentManager, int)
     */
    public static final int BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT = 1;

    private final FragmentManager mFragmentManager;
    private final int mBehavior;
    private FragmentTransaction mCurTransaction = null;

    private final ArrayList<Fragment.SavedState> mSavedState = new ArrayList<>();
    private final ArrayList<Fragment> mFragments = new ArrayList<>();
    private Fragment mCurrentPrimaryItem = null;
    private boolean mExecutingFinishUpdate;

    /**
     * Constructor for {@link FragmentStatePagerAdapterMenuWorkaround}
     * that sets the fragment manager for the adapter. This is the equivalent of calling
     * {@link #FragmentStatePagerAdapterMenuWorkaround(FragmentManager, int)} and passing in
     * {@link #BEHAVIOR_SET_USER_VISIBLE_HINT}.
     *
     * <p>Fragments will have {@link Fragment#setUserVisibleHint(boolean)} called whenever the
     * current Fragment changes.</p>
     *
     * @param fm fragment manager that will interact with this adapter
     * @deprecated use {@link #FragmentStatePagerAdapterMenuWorkaround(FragmentManager, int)} with
     * {@link #BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT}
     */
    @Deprecated
    public FragmentStatePagerAdapterMenuWorkaround(@NonNull final FragmentManager fm) {
        this(fm, BEHAVIOR_SET_USER_VISIBLE_HINT);
    }

    /**
     * Constructor for {@link FragmentStatePagerAdapterMenuWorkaround}.
     *
     * If {@link #BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT} is passed in, then only the current
     * Fragment is in the {@link Lifecycle.State#RESUMED} state, while all other fragments are
     * capped at {@link Lifecycle.State#STARTED}. If {@link #BEHAVIOR_SET_USER_VISIBLE_HINT} is
     * passed, all fragments are in the {@link Lifecycle.State#RESUMED} state and there will be
     * callbacks to {@link Fragment#setUserVisibleHint(boolean)}.
     *
     * @param fm fragment manager that will interact with this adapter
     * @param behavior determines if only current fragments are in a resumed state
     */
    public FragmentStatePagerAdapterMenuWorkaround(@NonNull final FragmentManager fm,
                                                   @Behavior final int behavior) {
        mFragmentManager = fm;
        mBehavior = behavior;
    }

    /**
     * @param position the position of the item you want
     * @return the {@link Fragment} associated with a specified position
     */
    @NonNull
    public abstract Fragment getItem(int position);

    @Override
    public void startUpdate(@NonNull final ViewGroup container) {
        if (container.getId() == View.NO_ID) {
            throw new IllegalStateException("ViewPager with adapter " + this
                    + " requires a view id");
        }
    }

    @SuppressWarnings("deprecation")
    @NonNull
    @Override
    public Object instantiateItem(@NonNull final ViewGroup container, final int position) {
        // If we already have this item instantiated, there is nothing
        // to do.  This can happen when we are restoring the entire pager
        // from its saved state, where the fragment manager has already
        // taken care of restoring the fragments we previously had instantiated.
        if (mFragments.size() > position) {
            final Fragment f = mFragments.get(position);
            if (f != null) {
                return f;
            }
        }

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        final Fragment fragment = getItem(position);
        if (DEBUG) {
            Log.v(TAG, "Adding item #" + position + ": f=" + fragment);
        }
        if (mSavedState.size() > position) {
            final Fragment.SavedState fss = mSavedState.get(position);
            if (fss != null) {
                fragment.setInitialSavedState(fss);
            }
        }
        while (mFragments.size() <= position) {
            mFragments.add(null);
        }
        fragment.setMenuVisibility(false);
        if (mBehavior == BEHAVIOR_SET_USER_VISIBLE_HINT) {
            fragment.setUserVisibleHint(false);
        }

        mFragments.set(position, fragment);
        mCurTransaction.add(container.getId(), fragment);

        if (mBehavior == BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
            mCurTransaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED);
        }

        return fragment;
    }

    @Override
    public void destroyItem(@NonNull final ViewGroup container, final int position,
                            @NonNull final Object object) {
        final Fragment fragment = (Fragment) object;

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }
        if (DEBUG) {
            Log.v(TAG, "Removing item #" + position + ": f=" + object
                    + " v=" + ((Fragment) object).getView());
        }
        while (mSavedState.size() <= position) {
            mSavedState.add(null);
        }
        mSavedState.set(position, fragment.isAdded()
                ? mFragmentManager.saveFragmentInstanceState(fragment) : null);
        mFragments.set(position, null);

        mCurTransaction.remove(fragment);
        if (fragment.equals(mCurrentPrimaryItem)) {
            mCurrentPrimaryItem = null;
        }
    }

    @Override
    @SuppressWarnings({"ReferenceEquality", "deprecation"})
    public void setPrimaryItem(@NonNull final ViewGroup container, final int position,
                               @NonNull final Object object) {
        final Fragment fragment = (Fragment) object;
        if (fragment != mCurrentPrimaryItem) {
            if (mCurrentPrimaryItem != null) {
                mCurrentPrimaryItem.setMenuVisibility(false);
                if (mBehavior == BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
                    if (mCurTransaction == null) {
                        mCurTransaction = mFragmentManager.beginTransaction();
                    }
                    mCurTransaction.setMaxLifecycle(mCurrentPrimaryItem, Lifecycle.State.STARTED);
                } else {
                    mCurrentPrimaryItem.setUserVisibleHint(false);
                }
            }
            fragment.setMenuVisibility(true);
            if (mBehavior == BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
                if (mCurTransaction == null) {
                    mCurTransaction = mFragmentManager.beginTransaction();
                }
                mCurTransaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED);
            } else {
                fragment.setUserVisibleHint(true);
            }

            mCurrentPrimaryItem = fragment;
        }
    }

    @Override
    public void finishUpdate(@NonNull final ViewGroup container) {
        if (mCurTransaction != null) {
            // We drop any transactions that attempt to be committed
            // from a re-entrant call to finishUpdate(). We need to
            // do this as a workaround for Robolectric running measure/layout
            // calls inline rather than allowing them to be posted
            // as they would on a real device.
            if (!mExecutingFinishUpdate) {
                try {
                    mExecutingFinishUpdate = true;
                    mCurTransaction.commitNowAllowingStateLoss();
                } finally {
                    mExecutingFinishUpdate = false;
                }
            }
            mCurTransaction = null;
        }
    }

    @Override
    public boolean isViewFromObject(@NonNull final View view, @NonNull final Object object) {
        return ((Fragment) object).getView() == view;
    }

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private final String selectedFragment = "selected_fragment";
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

    @Override
    @Nullable
    public Parcelable saveState() {
        Bundle state = null;
        if (mSavedState.size() > 0) {
            state = new Bundle();
            final Fragment.SavedState[] fss = new Fragment.SavedState[mSavedState.size()];
            mSavedState.toArray(fss);
            state.putParcelableArray("states", fss);
        }
        for (int i = 0; i < mFragments.size(); i++) {
            final Fragment f = mFragments.get(i);
            if (f != null && f.isAdded()) {
                if (state == null) {
                    state = new Bundle();
                }
                final String key = "f" + i;
                mFragmentManager.putFragment(state, key, f);

                //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // Check if it's the same fragment instance
                if (f == mCurrentPrimaryItem) {
                    state.putString(selectedFragment, key);
                }
                //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            }
        }
        return state;
    }

    @Override
    public void restoreState(@Nullable final Parcelable state, @Nullable final ClassLoader loader) {
        if (state != null) {
            final Bundle bundle = (Bundle) state;
            bundle.setClassLoader(loader);
            final Parcelable[] fss = bundle.getParcelableArray("states");
            mSavedState.clear();
            mFragments.clear();
            if (fss != null) {
                for (final Parcelable parcelable : fss) {
                    mSavedState.add((Fragment.SavedState) parcelable);
                }
            }
            final Iterable<String> keys = bundle.keySet();
            for (final String key : keys) {
                if (key.startsWith("f")) {
                    final int index = Integer.parseInt(key.substring(1));
                    final Fragment f = mFragmentManager.getFragment(bundle, key);
                    if (f != null) {
                        while (mFragments.size() <= index) {
                            mFragments.add(null);
                        }
                        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                        final boolean wasSelected = bundle.getString(selectedFragment, "")
                                .equals(key);
                        f.setMenuVisibility(wasSelected);
                        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                        mFragments.set(index, f);
                    } else {
                        Log.w(TAG, "Bad fragment at key " + key);
                    }
                }
            }
        }
    }
}
