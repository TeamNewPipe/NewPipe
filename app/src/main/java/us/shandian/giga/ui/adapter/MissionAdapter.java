package us.shandian.giga.ui.adapter;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DeleteDownloadManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.common.ProgressDrawable;
import us.shandian.giga.util.Utility;

import static android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class MissionAdapter extends RecyclerView.Adapter<MissionAdapter.ViewHolder> {
    private static final Map<Integer, String> ALGORITHMS = new HashMap<>();
    private static final String TAG = "MissionAdapter";

    static {
        ALGORITHMS.put(R.id.md5, "MD5");
        ALGORITHMS.put(R.id.sha1, "SHA1");
    }

    private Activity mContext;
    private LayoutInflater mInflater;
    private DownloadManager mDownloadManager;
    private DeleteDownloadManager mDeleteDownloadManager;
    private List<DownloadMission> mItemList;
    private DownloadManagerService.DMBinder mBinder;
    private int mLayout;

    public MissionAdapter(Activity context, DownloadManagerService.DMBinder binder, DownloadManager downloadManager, DeleteDownloadManager deleteDownloadManager, boolean isLinear) {
        mContext = context;
        mDownloadManager = downloadManager;
        mDeleteDownloadManager = deleteDownloadManager;
        mBinder = binder;

        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLayout = isLinear ? R.layout.mission_item_linear : R.layout.mission_item;

        mItemList = new ArrayList<>();
        updateItemList();
    }

    public void updateItemList() {
        mItemList.clear();

        for (int i = 0; i < mDownloadManager.getCount(); i++) {
            DownloadMission mission = mDownloadManager.getMission(i);
            if (!mDeleteDownloadManager.contains(mission)) {
                mItemList.add(mDownloadManager.getMission(i));
            }
        }
    }

    @Override
    public MissionAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final ViewHolder h = new ViewHolder(mInflater.inflate(mLayout, parent, false));

        h.menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buildPopup(h);
            }
        });

		/*h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
			public void onClick(View v) {
				showDetail(h);
			}
		});*/

        return h;
    }

    @Override
    public void onViewRecycled(MissionAdapter.ViewHolder h) {
        super.onViewRecycled(h);
        h.mission.removeListener(h.observer);
        h.mission = null;
        h.observer = null;
        h.progress = null;
        h.position = -1;
        h.lastTimeStamp = -1;
        h.lastDone = -1;
        h.colorId = 0;
    }

    @Override
    public void onBindViewHolder(MissionAdapter.ViewHolder h, int pos) {
        DownloadMission ms = mItemList.get(pos);
        h.mission = ms;
        h.position = pos;

        Utility.FileType type = Utility.getFileType(ms.name);

        h.icon.setImageResource(Utility.getIconForFileType(type));
        h.name.setText(ms.name);
        h.size.setText(Utility.formatBytes(ms.length));

        h.progress = new ProgressDrawable(mContext, Utility.getBackgroundForFileType(type), Utility.getForegroundForFileType(type));
        ViewCompat.setBackground(h.bkg, h.progress);

        h.observer = new MissionObserver(this, h);
        ms.addListener(h.observer);

        updateProgress(h);
    }

    @Override
    public int getItemCount() {
        return mItemList.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private void updateProgress(ViewHolder h) {
        updateProgress(h, false);
    }

    private void updateProgress(ViewHolder h, boolean finished) {
        if (h.mission == null) return;

        long now = System.currentTimeMillis();

        if (h.lastTimeStamp == -1) {
            h.lastTimeStamp = now;
        }

        if (h.lastDone == -1) {
            h.lastDone = h.mission.done;
        }

        long deltaTime = now - h.lastTimeStamp;
        long deltaDone = h.mission.done - h.lastDone;

        if (deltaTime == 0 || deltaTime > 1000 || finished) {
            if (h.mission.errCode > 0) {
                h.status.setText(R.string.msg_error);
            } else {
                float progress = (float) h.mission.done / h.mission.length;
                h.status.setText(String.format(Locale.US, "%.2f%%", progress * 100));
                h.progress.setProgress(progress);
            }
        }

        if (deltaTime > 1000 && deltaDone > 0) {
            float speed = (float) deltaDone / deltaTime;
            String speedStr = Utility.formatSpeed(speed * 1000);
            String sizeStr = Utility.formatBytes(h.mission.length);

            h.size.setText(sizeStr + " " + speedStr);

            h.lastTimeStamp = now;
            h.lastDone = h.mission.done;
        }
    }


    private void buildPopup(final ViewHolder h) {
        PopupMenu popup = new PopupMenu(mContext, h.menu);
        popup.inflate(R.menu.mission);

        Menu menu = popup.getMenu();
        MenuItem start = menu.findItem(R.id.start);
        MenuItem pause = menu.findItem(R.id.pause);
        MenuItem view = menu.findItem(R.id.view);
        MenuItem delete = menu.findItem(R.id.delete);
        MenuItem checksum = menu.findItem(R.id.checksum);

        // Set to false first
        start.setVisible(false);
        pause.setVisible(false);
        view.setVisible(false);
        delete.setVisible(false);
        checksum.setVisible(false);

        if (!h.mission.finished) {
            if (!h.mission.running) {
                if (h.mission.errCode == -1) {
                    start.setVisible(true);
                }

                delete.setVisible(true);
            } else {
                pause.setVisible(true);
            }
        } else {
            view.setVisible(true);
            delete.setVisible(true);
            checksum.setVisible(true);
        }

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                switch (id) {
                    case R.id.start:
                        mDownloadManager.resumeMission(h.position);
                        mBinder.onMissionAdded(mItemList.get(h.position));
                        return true;
                    case R.id.pause:
                        mDownloadManager.pauseMission(h.position);
                        mBinder.onMissionRemoved(mItemList.get(h.position));
                        h.lastTimeStamp = -1;
                        h.lastDone = -1;
                        return true;
                    case R.id.view:
                        File f = new File(h.mission.location, h.mission.name);
                        String ext = Utility.getFileExt(h.mission.name);

                        Log.d(TAG, "Viewing file: " + f.getAbsolutePath() + " ext: " + ext);

                        if (ext == null) {
                            Log.w(TAG, "Can't view file because it has no extension: " +
                                    h.mission.name);
                            return false;
                        }

                        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.substring(1));
                        Log.v(TAG, "Mime: " + mime + " package: " + mContext.getApplicationContext().getPackageName() + ".provider");
                        if (f.exists()) {
                            viewFileWithFileProvider(f, mime);
                        } else {
                            Log.w(TAG, "File doesn't exist");
                        }

                        return true;
                    case R.id.delete:
                        mDeleteDownloadManager.add(h.mission);
                        updateItemList();
                        notifyDataSetChanged();
                        return true;
                    case R.id.md5:
                    case R.id.sha1:
                        DownloadMission mission = mItemList.get(h.position);
                        new ChecksumTask(mContext).execute(mission.location + "/" + mission.name, ALGORITHMS.get(id));
                        return true;
                    default:
                        return false;
                }
            }
        });

        popup.show();
    }

    private void viewFileWithFileProvider(File file, String mimetype) {
        String ourPackage = mContext.getApplicationContext().getPackageName();
        Uri uri = FileProvider.getUriForFile(mContext, ourPackage + ".provider", file);
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimetype);
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(FLAG_GRANT_PREFIX_URI_PERMISSION);
        }
        //mContext.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Log.v(TAG, "Starting intent: " + intent);
        if (intent.resolveActivity(mContext.getPackageManager()) != null) {
            mContext.startActivity(intent);
        } else {
            Toast noPlayerToast = Toast.makeText(mContext, R.string.toast_no_player, Toast.LENGTH_LONG);
            noPlayerToast.show();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        public DownloadMission mission;
        public int position;

        public final TextView status;
        public final ImageView icon;
        public final TextView name;
        public final TextView size;
        public final View bkg;
        public final ImageView menu;
        public ProgressDrawable progress;
        public MissionObserver observer;

        public long lastTimeStamp = -1;
        public long lastDone = -1;
        public int colorId;

        public ViewHolder(View v) {
            super(v);

            status = v.findViewById(R.id.item_status);
            icon = v.findViewById(R.id.item_icon);
            name = v.findViewById(R.id.item_name);
            size = v.findViewById(R.id.item_size);
            bkg = v.findViewById(R.id.item_bkg);
            menu = v.findViewById(R.id.item_more);
        }
    }

    static class MissionObserver implements DownloadMission.MissionListener {
        private final MissionAdapter mAdapter;
        private final ViewHolder mHolder;

        public MissionObserver(MissionAdapter adapter, ViewHolder holder) {
            mAdapter = adapter;
            mHolder = holder;
        }

        @Override
        public void onProgressUpdate(DownloadMission downloadMission, long done, long total) {
            mAdapter.updateProgress(mHolder);
        }

        @Override
        public void onFinish(DownloadMission downloadMission) {
            //mAdapter.mManager.deleteMission(mHolder.position);
            // TODO Notification
            //mAdapter.notifyDataSetChanged();
            if (mHolder.mission != null) {
                mHolder.size.setText(Utility.formatBytes(mHolder.mission.length));
                mAdapter.updateProgress(mHolder, true);
            }
        }

        @Override
        public void onError(DownloadMission downloadMission, int errCode) {
            mAdapter.updateProgress(mHolder);
        }

    }

    private static class ChecksumTask extends AsyncTask<String, Void, String> {
        ProgressDialog prog;
        final WeakReference<Activity> weakReference;

        ChecksumTask(@NonNull Activity activity) {
            weakReference = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Activity activity = getActivity();
            if (activity != null) {
                // Create dialog
                prog = new ProgressDialog(activity);
                prog.setCancelable(false);
                prog.setMessage(activity.getString(R.string.msg_wait));
                prog.show();
            }
        }

        @Override
        protected String doInBackground(String... params) {
            return Utility.checksum(params[0], params[1]);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            if (prog != null) {
                Utility.copyToClipboard(prog.getContext(), result);
                if (getActivity() != null) {
                    prog.dismiss();
                }
            }
        }

        @Nullable
        private Activity getActivity() {
            Activity activity = weakReference.get();

            if (activity != null && activity.isFinishing()) {
                return null;
            } else {
                return activity;
            }
        }
    }
}
