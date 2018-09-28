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
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DeleteDownloadManager;

import io.reactivex.disposables.Disposable;
import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.adapter.MissionAdapter;

public abstract class MissionsFragment extends Fragment {
    private DownloadManager mDownloadManager;
    private DownloadManagerService.DMBinder mBinder;

    private SharedPreferences mPrefs;
    private boolean mLinear;
    private MenuItem mSwitch;

    private RecyclerView mList;
    private MissionAdapter mAdapter;
    private GridLayoutManager mGridManager;
    private LinearLayoutManager mLinearManager;
    private Context mActivity;
    private DeleteDownloadManager mDeleteDownloadManager;
    private Disposable mDeleteDisposable;

    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mBinder = (DownloadManagerService.DMBinder) binder;
            mDownloadManager = setupDownloadManager(mBinder);
            if (mDeleteDownloadManager != null) {
                mDeleteDownloadManager.setDownloadManager(mDownloadManager);
                updateList();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // What to do?
        }


    };

    public void setDeleteManager(@NonNull DeleteDownloadManager deleteDownloadManager) {
        mDeleteDownloadManager = deleteDownloadManager;
        if (mDownloadManager != null) {
            mDeleteDownloadManager.setDownloadManager(mDownloadManager);
            updateList();
        }
    }

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
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mDeleteDownloadManager != null) {
            mDeleteDisposable = mDeleteDownloadManager.getUndoObservable().subscribe(mission -> {
                if (mAdapter != null) {
                    mAdapter.updateItemList();
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().unbindService(mConnection);
        if (mDeleteDisposable != null) {
            mDeleteDisposable.dispose();
        }
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
        mAdapter = new MissionAdapter((Activity) mActivity, mBinder, mDownloadManager, mDeleteDownloadManager, mLinear);

        if (mLinear) {
            mList.setLayoutManager(mLinearManager);
        } else {
            mList.setLayoutManager(mGridManager);
        }

        mList.setAdapter(mAdapter);

        if (mSwitch != null) {
            mSwitch.setIcon(mLinear ? R.drawable.grid : R.drawable.list);
        }

        mPrefs.edit().putBoolean("linear", mLinear).apply();
    }

    protected abstract DownloadManager setupDownloadManager(DownloadManagerService.DMBinder binder);
}
