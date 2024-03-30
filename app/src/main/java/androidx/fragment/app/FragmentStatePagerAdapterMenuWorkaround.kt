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
package androidx.fragment.app

import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.core.os.BundleCompat
import androidx.lifecycle.Lifecycle
import androidx.viewpager.widget.PagerAdapter

// TODO: Replace this deprecated class with its ViewPager2 counterpart
/**
 * This is a copy from [androidx.fragment.app.FragmentStatePagerAdapter].
 *
 *
 * It includes a workaround to fix the menu visibility when the adapter is restored.
 *
 *
 *
 * When restoring the state of this adapter, all the fragments' menu visibility were set to false,
 * effectively disabling the menu from the user until he switched pages or another event
 * that triggered the menu to be visible again happened.
 *
 *
 *
 * **Check out the changes in:**
 *
 *
 *  * [.saveState]
 *  * [.restoreState]
 *
 *
 */
@Suppress("deprecation")
@Deprecated("""Switch to {@link androidx.viewpager2.widget.ViewPager2} and use
  {@link androidx.viewpager2.adapter.FragmentStateAdapter} instead.""")
abstract class FragmentStatePagerAdapterMenuWorkaround
/**
 * Constructor for [FragmentStatePagerAdapterMenuWorkaround].
 *
 * If [.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT] is passed in, then only the current
 * Fragment is in the [Lifecycle.State.RESUMED] state, while all other fragments are
 * capped at [Lifecycle.State.STARTED]. If [.BEHAVIOR_SET_USER_VISIBLE_HINT] is
 * passed, all fragments are in the [Lifecycle.State.RESUMED] state and there will be
 * callbacks to [Fragment.setUserVisibleHint].
 *
 * @param fm fragment manager that will interact with this adapter
 * @param behavior determines if only current fragments are in a resumed state
 */(private val mFragmentManager: FragmentManager,
    @param:Behavior private val mBehavior: Int) : PagerAdapter() {
    @Retention(AnnotationRetention.SOURCE)
    @IntDef([BEHAVIOR_SET_USER_VISIBLE_HINT, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT])
    private annotation class Behavior

    private var mCurTransaction: FragmentTransaction? = null
    private val mSavedState = ArrayList<Fragment.SavedState?>()
    private val mFragments = ArrayList<Fragment>()
    private var mCurrentPrimaryItem: Fragment? = null
    private var mExecutingFinishUpdate = false

    /**
     * Constructor for [FragmentStatePagerAdapterMenuWorkaround]
     * that sets the fragment manager for the adapter. This is the equivalent of calling
     * [.FragmentStatePagerAdapterMenuWorkaround] and passing in
     * [.BEHAVIOR_SET_USER_VISIBLE_HINT].
     *
     *
     * Fragments will have [Fragment.setUserVisibleHint] called whenever the
     * current Fragment changes.
     *
     * @param fm fragment manager that will interact with this adapter
     */
    @Deprecated("""use {@link #FragmentStatePagerAdapterMenuWorkaround(FragmentManager, int)} with
      {@link #BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT}""")
    constructor(fm: FragmentManager) : this(fm, BEHAVIOR_SET_USER_VISIBLE_HINT)

    /**
     * @param position the position of the item you want
     * @return the [Fragment] associated with a specified position
     */
    abstract fun getItem(position: Int): Fragment
    override fun startUpdate(container: ViewGroup) {
        check(container.id != View.NO_ID) {
            ("ViewPager with adapter " + this
                    + " requires a view id")
        }
    }

    @Suppress("deprecation")
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        // If we already have this item instantiated, there is nothing
        // to do.  This can happen when we are restoring the entire pager
        // from its saved state, where the fragment manager has already
        // taken care of restoring the fragments we previously had instantiated.
        if (mFragments.size > position) {
            val f = mFragments[position]
            if (f != null) {
                return f
            }
        }
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction()
        }
        val fragment = getItem(position)
        if (DEBUG) {
            Log.v(TAG, "Adding item #$position: f=$fragment")
        }
        if (mSavedState.size > position) {
            val fss = mSavedState[position]
            if (fss != null) {
                fragment.setInitialSavedState(fss)
            }
        }
        while (mFragments.size <= position) {
            mFragments.add(null)
        }
        fragment.setMenuVisibility(false)
        if (mBehavior == BEHAVIOR_SET_USER_VISIBLE_HINT) {
            fragment.setUserVisibleHint(false)
        }
        mFragments[position] = fragment
        mCurTransaction!!.add(container.id, fragment)
        if (mBehavior == BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
            mCurTransaction!!.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
        }
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int,
                             `object`: Any) {
        val fragment = `object` as Fragment
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction()
        }
        if (DEBUG) {
            Log.v(TAG, "Removing item #" + position + ": f=" + `object`
                    + " v=" + `object`.view)
        }
        while (mSavedState.size <= position) {
            mSavedState.add(null)
        }
        mSavedState[position] = if (fragment.isAdded) mFragmentManager.saveFragmentInstanceState(fragment) else null
        mFragments.set(position, null)
        mCurTransaction!!.remove(fragment)
        if (fragment == mCurrentPrimaryItem) {
            mCurrentPrimaryItem = null
        }
    }

    @Suppress("deprecation")
    override fun setPrimaryItem(container: ViewGroup, position: Int,
                                `object`: Any) {
        val fragment = `object` as Fragment
        if (fragment !== mCurrentPrimaryItem) {
            if (mCurrentPrimaryItem != null) {
                mCurrentPrimaryItem!!.setMenuVisibility(false)
                if (mBehavior == BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
                    if (mCurTransaction == null) {
                        mCurTransaction = mFragmentManager.beginTransaction()
                    }
                    mCurTransaction!!.setMaxLifecycle(mCurrentPrimaryItem!!, Lifecycle.State.STARTED)
                } else {
                    mCurrentPrimaryItem!!.setUserVisibleHint(false)
                }
            }
            fragment.setMenuVisibility(true)
            if (mBehavior == BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
                if (mCurTransaction == null) {
                    mCurTransaction = mFragmentManager.beginTransaction()
                }
                mCurTransaction!!.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            } else {
                fragment.setUserVisibleHint(true)
            }
            mCurrentPrimaryItem = fragment
        }
    }

    override fun finishUpdate(container: ViewGroup) {
        if (mCurTransaction != null) {
            // We drop any transactions that attempt to be committed
            // from a re-entrant call to finishUpdate(). We need to
            // do this as a workaround for Robolectric running measure/layout
            // calls inline rather than allowing them to be posted
            // as they would on a real device.
            if (!mExecutingFinishUpdate) {
                try {
                    mExecutingFinishUpdate = true
                    mCurTransaction!!.commitNowAllowingStateLoss()
                } finally {
                    mExecutingFinishUpdate = false
                }
            }
            mCurTransaction = null
        }
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return (`object` as Fragment).view === view
    }

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private val selectedFragment = "selected_fragment"

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    override fun saveState(): Parcelable? {
        var state: Bundle? = null
        if (!mSavedState.isEmpty()) {
            state = Bundle()
            state.putParcelableArrayList("states", mSavedState)
        }
        for (i in mFragments.indices) {
            val f = mFragments[i]
            if (f != null && f.isAdded) {
                if (state == null) {
                    state = Bundle()
                }
                val key = "f$i"
                mFragmentManager.putFragment(state, key, f)

                //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // Check if it's the same fragment instance
                if (f === mCurrentPrimaryItem) {
                    state.putString(selectedFragment, key)
                }
                //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            }
        }
        return state
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        if (state != null) {
            val bundle = state as Bundle
            bundle.classLoader = loader
            val states = BundleCompat.getParcelableArrayList(bundle, "states",
                    Fragment.SavedState::class.java)
            mSavedState.clear()
            mFragments.clear()
            if (states != null) {
                mSavedState.addAll(states)
            }
            val keys: Iterable<String> = bundle.keySet()
            for (key in keys) {
                if (key.startsWith("f")) {
                    val index = key.substring(1).toInt()
                    val f = mFragmentManager.getFragment(bundle, key)
                    if (f != null) {
                        while (mFragments.size <= index) {
                            mFragments.add(null)
                        }
                        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                        val wasSelected = (bundle.getString(selectedFragment, "")
                                == key)
                        f.setMenuVisibility(wasSelected)
                        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                        mFragments[index] = f
                    } else {
                        Log.w(TAG, "Bad fragment at key $key")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "FragmentStatePagerAdapt"
        private const val DEBUG = false

        /**
         * Indicates that [Fragment.setUserVisibleHint] will be called when the current
         * fragment changes.
         *
         * @see .FragmentStatePagerAdapterMenuWorkaround
         */
        @Deprecated("""This behavior relies on the deprecated
      {@link Fragment#setUserVisibleHint(boolean)} API. Use
      {@link #BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT} to switch to its replacement,
      {@link FragmentTransaction#setMaxLifecycle}.
      """)
        val BEHAVIOR_SET_USER_VISIBLE_HINT = 0

        /**
         * Indicates that only the current fragment will be in the [Lifecycle.State.RESUMED]
         * state. All other Fragments are capped at [Lifecycle.State.STARTED].
         *
         * @see .FragmentStatePagerAdapterMenuWorkaround
         */
        const val BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT = 1
    }
}
