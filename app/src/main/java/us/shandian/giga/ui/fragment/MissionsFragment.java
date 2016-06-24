package us.shandian.giga.ui.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import org.schabi.newpipe.R;
import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.adapter.MissionAdapter;
import us.shandian.giga.util.Utility;

public abstract class MissionsFragment extends Fragment
{
	private DownloadManager mManager;
	private DownloadManagerService.DMBinder mBinder;
	
	private SharedPreferences mPrefs;
	private boolean mLinear;
	private MenuItem mSwitch;
	
	private RecyclerView mList;
	private MissionAdapter mAdapter;
	private GridLayoutManager mGridManager;
	private LinearLayoutManager mLinearManager;
	private Activity mActivity;
	
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
		
		mPrefs = getActivity().getSharedPreferences("mode", Context.MODE_WORLD_READABLE);
		mLinear = mPrefs.getBoolean("linear", false);
		
		// Bind the service
		Intent i = new Intent();
		i.setClass(getActivity(), DownloadManagerService.class);
		getActivity().bindService(i, mConnection, Context.BIND_AUTO_CREATE);
		
		// Views
		mList = Utility.findViewById(v, R.id.mission_recycler);
		
		// Init
		mGridManager = new GridLayoutManager(getActivity(), 2);
		mLinearManager = new LinearLayoutManager(getActivity());
		mList.setLayoutManager(mGridManager);
		
		setHasOptionsMenu(true);
		
		return v;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mActivity = activity;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.switch_mode:
				mLinear = !mLinear;
				updateList();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
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
