package org.schabi.newpipe.downloadmanager.ui.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;

import org.schabi.newpipe.downloadmanager.get.DownloadManager;
import org.schabi.newpipe.downloadmanager.service.DownloadManagerService;
import org.schabi.newpipe.downloadmanager.ui.adapter.MissionAdapter;

/**
 * Copyright (C) 2014 Peter Cai
 * Changes by Christian Schabesberger (C) 2018
 *
 * org.schabi.newpipe.downloadmanager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * org.schabi.newpipe.downloadmanager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with org.schabi.newpipe.downloadmanager.  If not, see <http://www.gnu.org/licenses/>.
 */

public abstract class MissionsFragment extends Fragment {
    private DownloadManager mManager;
    private DownloadManagerService.DMBinder mBinder;

    private SharedPreferences mPrefs;
    private boolean mLinear;
    private MenuItem mSwitch;

    private RecyclerView mList;
    private MissionAdapter mAdapter;
    private GridLayoutManager mGridManager;
    private LinearLayoutManager mLinearManager;
    private Context mActivity;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mBinder = (DownloadManagerService.DMBinder) binder;
            mManager = setupDownloadManager(mBinder);
            updateList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // What to do?
        }


    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.missions, container, false);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mLinear = mPrefs.getBoolean("linear", false);

        // Bind the service
        Intent i = new Intent();
        i.setClass(getActivity(), DownloadManagerService.class);
        getActivity().bindService(i, mConnection, Context.BIND_AUTO_CREATE);

        // Views
        mList = v.findViewById(R.id.mission_recycler);

        // Init
        mGridManager = new GridLayoutManager(getActivity(), 2);
        mLinearManager = new LinearLayoutManager(getActivity());
        mList.setLayoutManager(mGridManager);

        setHasOptionsMenu(true);

        return v;
    }

    /**
     * Added in API level 23.
     */
    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);

        // Bug: in api< 23 this is never called
        // so mActivity=null
        // so app crashes with nullpointer exception
        mActivity = activity;
    }

    /**
     * deprecated in API level 23,
     * but must remain to allow compatibility with api<23
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        mActivity = activity;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().unbindService(mConnection);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);

		/*switch (item.getItemId()) {
            case R.id.switch_mode:
				mLinear = !mLinear;
				updateList();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}*/
    }

    public void notifyChange() {
        mAdapter.notifyDataSetChanged();
    }

    private void updateList() {
        mAdapter = new MissionAdapter(mActivity, mBinder, mManager, mLinear);

        if (mLinear) {
            mList.setLayoutManager(mLinearManager);
        } else {
            mList.setLayoutManager(mGridManager);
        }

        mList.setAdapter(mAdapter);

        if (mSwitch != null) {
            mSwitch.setIcon(mLinear ? R.drawable.grid : R.drawable.list);
        }

        mPrefs.edit().putBoolean("linear", mLinear).commit();
    }

    protected abstract DownloadManager setupDownloadManager(DownloadManagerService.DMBinder binder);
}
