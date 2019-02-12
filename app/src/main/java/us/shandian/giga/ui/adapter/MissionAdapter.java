package us.shandian.giga.ui.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.Log;
import android.util.SparseArray;
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

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.common.Deleter;
import us.shandian.giga.ui.common.ProgressDrawable;
import us.shandian.giga.util.Utility;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static us.shandian.giga.get.DownloadMission.ERROR_CONNECT_HOST;
import static us.shandian.giga.get.DownloadMission.ERROR_FILE_CREATION;
import static us.shandian.giga.get.DownloadMission.ERROR_HTTP_NO_CONTENT;
import static us.shandian.giga.get.DownloadMission.ERROR_HTTP_UNSUPPORTED_RANGE;
import static us.shandian.giga.get.DownloadMission.ERROR_NOTHING;
import static us.shandian.giga.get.DownloadMission.ERROR_PATH_CREATION;
import static us.shandian.giga.get.DownloadMission.ERROR_PERMISSION_DENIED;
import static us.shandian.giga.get.DownloadMission.ERROR_POSTPROCESSING;
import static us.shandian.giga.get.DownloadMission.ERROR_SSL_EXCEPTION;
import static us.shandian.giga.get.DownloadMission.ERROR_UNKNOWN_EXCEPTION;
import static us.shandian.giga.get.DownloadMission.ERROR_UNKNOWN_HOST;

public class MissionAdapter extends Adapter<ViewHolder> {
    private static final SparseArray<String> ALGORITHMS = new SparseArray<>();
    private static final String TAG = "MissionAdapter";
    private static final String UNDEFINED_PROGRESS = "--.-%";


    static {
        ALGORITHMS.put(R.id.md5, "MD5");
        ALGORITHMS.put(R.id.sha1, "SHA1");
    }

    private Context mContext;
    private LayoutInflater mInflater;
    private DownloadManager mDownloadManager;
    private Deleter mDeleter;
    private int mLayout;
    private DownloadManager.MissionIterator mIterator;
    private ArrayList<ViewHolderItem> mPendingDownloadsItems = new ArrayList<>();
    private Handler mHandler;
    private MenuItem mClear;
    private View mEmptyMessage;

    public MissionAdapter(Context context, DownloadManager downloadManager, MenuItem clearButton, View emptyMessage) {
        mContext = context;
        mDownloadManager = downloadManager;
        mDeleter = null;

        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLayout = R.layout.mission_item;

        mHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case DownloadManagerService.MESSAGE_PROGRESS:
                    case DownloadManagerService.MESSAGE_ERROR:
                    case DownloadManagerService.MESSAGE_FINISHED:
                        onServiceMessage(msg);
                        break;
                }
            }
        };

        mClear = clearButton;
        mEmptyMessage = emptyMessage;

        mIterator = downloadManager.getIterator();

        checkEmptyMessageVisibility();
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case DownloadManager.SPECIAL_PENDING:
            case DownloadManager.SPECIAL_FINISHED:
                return new ViewHolderHeader(mInflater.inflate(R.layout.missions_header, parent, false));
        }

        return new ViewHolderItem(mInflater.inflate(mLayout, parent, false));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder view) {
        super.onViewRecycled(view);

        if (view instanceof ViewHolderHeader) return;
        ViewHolderItem h = (ViewHolderItem) view;

        if (h.item.mission instanceof DownloadMission) {
            mPendingDownloadsItems.remove(h);
            if (mPendingDownloadsItems.size() < 1) setAutoRefresh(false);
        }

        h.popupMenu.dismiss();
        h.item = null;
        h.lastTimeStamp = -1;
        h.lastDone = -1;
        h.lastCurrent = -1;
        h.state = 0;
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void onBindViewHolder(@NonNull ViewHolder view, @SuppressLint("RecyclerView") int pos) {
        DownloadManager.MissionItem item = mIterator.getItem(pos);

        if (view instanceof ViewHolderHeader) {
            if (item.special == DownloadManager.SPECIAL_NOTHING) return;
            int str;
            if (item.special == DownloadManager.SPECIAL_PENDING) {
                str = R.string.missions_header_pending;
            } else {
                str = R.string.missions_header_finished;
                if (mClear != null) mClear.setVisible(true);
            }

            ((ViewHolderHeader) view).header.setText(str);
            return;
        }

        ViewHolderItem h = (ViewHolderItem) view;
        h.item = item;

        Utility.FileType type = Utility.getFileType(item.mission.kind, item.mission.name);

        h.icon.setImageResource(Utility.getIconForFileType(type));
        h.name.setText(item.mission.name);

        h.progress.setColors(Utility.getBackgroundForFileType(mContext, type), Utility.getForegroundForFileType(mContext, type));

        if (h.item.mission instanceof DownloadMission) {
            DownloadMission mission = (DownloadMission) item.mission;
            String length = Utility.formatBytes(mission.getLength());
            if (mission.running && !mission.isPsRunning()) length += " --.- kB/s";

            h.size.setText(length);
            h.pause.setTitle(mission.unknownLength ? R.string.stop : R.string.pause);
            h.lastCurrent = mission.current;
            updateProgress(h);
            mPendingDownloadsItems.add(h);
        } else {
            h.progress.setMarquee(false);
            h.status.setText("100%");
            h.progress.setProgress(1f);
            h.size.setText(Utility.formatBytes(item.mission.length));
        }
    }

    @Override
    public int getItemCount() {
        return mIterator.getOldListSize();
    }

    @Override
    public int getItemViewType(int position) {
        return mIterator.getSpecialAtItem(position);
    }

    @SuppressLint("DefaultLocale")
    private void updateProgress(ViewHolderItem h) {
        if (h == null || h.item == null || h.item.mission instanceof FinishedMission) return;

        long now = System.currentTimeMillis();
        DownloadMission mission = (DownloadMission) h.item.mission;

        if (h.lastCurrent != mission.current) {
            h.lastCurrent = mission.current;
            h.lastTimeStamp = now;
            h.lastDone = 0;
        } else {
            if (h.lastTimeStamp == -1) h.lastTimeStamp = now;
            if (h.lastDone == -1) h.lastDone = mission.done;
        }

        long deltaTime = now - h.lastTimeStamp;
        long deltaDone = mission.done - h.lastDone;
        boolean hasError = mission.errCode != ERROR_NOTHING;

        // on error hide marquee or show if condition (mission.done < 1 || mission.unknownLength) is true
        h.progress.setMarquee(!hasError && (mission.done < 1 || mission.unknownLength));

        float progress;
        if (mission.unknownLength) {
            progress = Float.NaN;
            h.progress.setProgress(0f);
        } else {
            progress = (float) ((double) mission.done / mission.length);
            if (mission.urls.length > 1 && mission.current < mission.urls.length) {
                progress = (progress / mission.urls.length) + ((float) mission.current / mission.urls.length);
            }
        }

        if (hasError) {
            h.progress.setProgress(isNotFinite(progress) ? 1f : progress);
            h.status.setText(R.string.msg_error);
        } else if (isNotFinite(progress)) {
            h.status.setText(UNDEFINED_PROGRESS);
        } else {
            h.status.setText(String.format("%.2f%%", progress * 100));
            h.progress.setProgress(progress);
        }

        long length = mission.getLength();

        int state;
        if (mission.isPsFailed()) {
            state = 0;
        } else if (!mission.running) {
            state = mission.enqueued ? 1 : 2;
        } else if (mission.isPsRunning()) {
            state = 3;
        } else {
            state = 0;
        }

        if (state != 0) {
            // update state without download speed
            if (h.state != state) {
                String statusStr;
                h.state = state;

                switch (state) {
                    case 1:
                        statusStr = mContext.getString(R.string.queued);
                        break;
                    case 2:
                        statusStr = mContext.getString(R.string.paused);
                        break;
                    case 3:
                        statusStr = mContext.getString(R.string.post_processing);
                        break;
                    default:
                        statusStr = "?";
                        break;
                }

                h.size.setText(Utility.formatBytes(length).concat("  (").concat(statusStr).concat(")"));
            } else if (deltaDone > 0) {
                h.lastTimeStamp = now;
                h.lastDone = mission.done;
            }

            return;
        }

        if (deltaDone > 0 && deltaTime > 0) {
            float speed = (deltaDone * 1000f) / deltaTime;

            String speedStr = Utility.formatSpeed(speed);
            String sizeStr = Utility.formatBytes(length);

            h.size.setText(sizeStr.concat(" ").concat(speedStr));

            h.lastTimeStamp = now;
            h.lastDone = mission.done;
        }
    }

    private boolean viewWithFileProvider(@NonNull File file) {
        if (!file.exists()) return true;

        String ext = Utility.getFileExt(file.getName());
        if (ext == null) return false;

        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.substring(1));
        Log.v(TAG, "Mime: " + mimeType + " package: " + BuildConfig.APPLICATION_ID + ".provider");

        Uri uri = FileProvider.getUriForFile(mContext, BuildConfig.APPLICATION_ID + ".provider", file);

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(FLAG_GRANT_PREFIX_URI_PERMISSION);
        }
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        }
        //mContext.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Log.v(TAG, "Starting intent: " + intent);
        if (intent.resolveActivity(mContext.getPackageManager()) != null) {
            mContext.startActivity(intent);
        } else {
            Toast noPlayerToast = Toast.makeText(mContext, R.string.toast_no_player, Toast.LENGTH_LONG);
            noPlayerToast.show();
        }

        return true;
    }

    public Handler getMessenger() {
        return mHandler;
    }

    private void onServiceMessage(@NonNull Message msg) {
        switch (msg.what) {
            case DownloadManagerService.MESSAGE_PROGRESS:
                setAutoRefresh(true);
                return;
            case DownloadManagerService.MESSAGE_ERROR:
            case DownloadManagerService.MESSAGE_FINISHED:
                break;
            default:
                return;
        }

        for (int i = 0; i < mPendingDownloadsItems.size(); i++) {
            ViewHolderItem h = mPendingDownloadsItems.get(i);
            if (h.item.mission != msg.obj) continue;

            if (msg.what == DownloadManagerService.MESSAGE_FINISHED) {
                // DownloadManager should mark the download as finished
                applyChanges();
                return;
            }

            updateProgress(h);
            return;
        }
    }

    private void showError(@NonNull DownloadMission mission) {
        StringBuilder str = new StringBuilder();
        str.append(mContext.getString(R.string.label_code));
        str.append(": ");
        str.append(mission.errCode);
        str.append('\n');

        switch (mission.errCode) {
            case 416:
                str.append(mContext.getString(R.string.error_http_requested_range_not_satisfiable));
                break;
            case 404:
                str.append(mContext.getString(R.string.error_http_not_found));
                break;
            case ERROR_NOTHING:
                str.append("¿?");
                break;
            case ERROR_FILE_CREATION:
                str.append(mContext.getString(R.string.error_file_creation));
                break;
            case ERROR_HTTP_NO_CONTENT:
                str.append(mContext.getString(R.string.error_http_no_content));
                break;
            case ERROR_HTTP_UNSUPPORTED_RANGE:
                str.append(mContext.getString(R.string.error_http_unsupported_range));
                break;
            case ERROR_PATH_CREATION:
                str.append(mContext.getString(R.string.error_path_creation));
                break;
            case ERROR_PERMISSION_DENIED:
                str.append(mContext.getString(R.string.permission_denied));
                break;
            case ERROR_SSL_EXCEPTION:
                str.append(mContext.getString(R.string.error_ssl_exception));
                break;
            case ERROR_UNKNOWN_HOST:
                str.append(mContext.getString(R.string.error_unknown_host));
                break;
            case ERROR_CONNECT_HOST:
                str.append(mContext.getString(R.string.error_connect_host));
                break;
            case ERROR_POSTPROCESSING:
                str.append(mContext.getString(R.string.error_postprocessing_failed));
            case ERROR_UNKNOWN_EXCEPTION:
                break;
            default:
                if (mission.errCode >= 100 && mission.errCode < 600) {
                    str = new StringBuilder(8);
                    str.append("HTTP ");
                    str.append(mission.errCode);
                } else if (mission.errObject == null) {
                    str.append("(not_decelerated_error_code)");
                }
                break;
        }

        if (mission.errObject != null) {
            str.append("\n\n");
            str.append(mission.errObject.toString());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mission.name)
                .setMessage(str)
                .setNegativeButton(android.R.string.ok, (dialog, which) -> dialog.cancel())
                .create()
                .show();
    }

    public void clearFinishedDownloads() {
        mDownloadManager.forgetFinishedDownloads();
        applyChanges();
    }

    private boolean handlePopupItem(@NonNull ViewHolderItem h, @NonNull MenuItem option) {
        int id = option.getItemId();
        DownloadMission mission = h.item.mission instanceof DownloadMission ? (DownloadMission) h.item.mission : null;

        if (mission != null) {
            switch (id) {
                case R.id.start:
                    h.status.setText(UNDEFINED_PROGRESS);
                    h.state = -1;
                    h.size.setText(Utility.formatBytes(mission.getLength()));
                    mDownloadManager.resumeMission(mission);
                    return true;
                case R.id.pause:
                    h.state = -1;
                    mDownloadManager.pauseMission(mission);
                    updateProgress(h);
                    h.lastTimeStamp = -1;
                    h.lastDone = -1;
                    return true;
                case R.id.error_message_view:
                    showError(mission);
                    return true;
                case R.id.queue:
                    h.queue.setChecked(!h.queue.isChecked());
                    mission.enqueued = h.queue.isChecked();
                    updateProgress(h);
                    return true;
            }
        }

        switch (id) {
            case R.id.open:
                return viewWithFileProvider(h.item.mission.getDownloadedFile());
            case R.id.delete:
                if (mDeleter == null) {
                    mDownloadManager.deleteMission(h.item.mission);
                } else {
                    mDeleter.append(h.item.mission);
                }
                applyChanges();
                return true;
            case R.id.md5:
            case R.id.sha1:
                new ChecksumTask(mContext).execute(h.item.mission.getDownloadedFile().getAbsolutePath(), ALGORITHMS.get(id));
                return true;
            case R.id.source:
                /*Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(h.item.mission.source));
                mContext.startActivity(intent);*/
                try {
                    Intent intent = NavigationHelper.getIntentByLink(mContext, h.item.mission.source);
                    intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
                    mContext.startActivity(intent);
                } catch (Exception e) {
                    Log.w(TAG, "Selected item has a invalid source", e);
                }
                return true;
            default:
                return false;
        }
    }

    public void applyChanges() {
        mIterator.start();
        DiffUtil.calculateDiff(mIterator, true).dispatchUpdatesTo(this);
        mIterator.end();

        checkEmptyMessageVisibility();
        if (mClear != null) mClear.setVisible(mIterator.hasFinishedMissions());
    }

    public void forceUpdate() {
        mIterator.start();
        mIterator.end();

        for (ViewHolderItem item : mPendingDownloadsItems) {
            item.lastTimeStamp = -1;
        }

        notifyDataSetChanged();
    }

    public void setLinear(boolean isLinear) {
        mLayout = isLinear ? R.layout.mission_item_linear : R.layout.mission_item;
    }

    public void setClearButton(MenuItem clearButton) {
        if (mClear == null) clearButton.setVisible(mIterator.hasFinishedMissions());
        mClear = clearButton;
    }

    private void checkEmptyMessageVisibility() {
        int flag = mIterator.getOldListSize() > 0 ? View.GONE : View.VISIBLE;
        if (mEmptyMessage.getVisibility() != flag) mEmptyMessage.setVisibility(flag);
    }


    public void deleterDispose(Bundle bundle) {
        if (mDeleter != null) mDeleter.dispose(bundle);
    }

    public void deleterLoad(Bundle bundle, View view) {
        if (mDeleter == null)
            mDeleter = new Deleter(bundle, view, mContext, this, mDownloadManager, mIterator, mHandler);
    }

    public void deleterResume() {
        if (mDeleter != null) mDeleter.resume();
    }


    private boolean mUpdaterRunning = false;
    private final Runnable rUpdater = this::updater;

    public void onPaused() {
        setAutoRefresh(false);
    }

    private void setAutoRefresh(boolean enabled) {
        if (enabled && !mUpdaterRunning) {
            mUpdaterRunning = true;
            updater();
        } else if (!enabled && mUpdaterRunning) {
            mUpdaterRunning = false;
            mHandler.removeCallbacks(rUpdater);
        }
    }

    private void updater() {
        if (!mUpdaterRunning) return;

        boolean running = false;
        for (ViewHolderItem h : mPendingDownloadsItems) {
            // check if the mission is running first
            if (!((DownloadMission) h.item.mission).running) continue;

            updateProgress(h);
            running = true;
        }

        if (running) {
            mHandler.postDelayed(rUpdater, 1000);
        } else {
            mUpdaterRunning = false;
        }
    }

    private boolean isNotFinite(Float value) {
        return Float.isNaN(value) || Float.isInfinite(value);
    }


    class ViewHolderItem extends RecyclerView.ViewHolder {
        DownloadManager.MissionItem item;

        TextView status;
        ImageView icon;
        TextView name;
        TextView size;
        ProgressDrawable progress;

        PopupMenu popupMenu;
        MenuItem start;
        MenuItem pause;
        MenuItem open;
        MenuItem queue;
        MenuItem showError;
        MenuItem delete;
        MenuItem source;
        MenuItem checksum;

        long lastTimeStamp = -1;
        long lastDone = -1;
        int lastCurrent = -1;
        int state = 0;

        ViewHolderItem(View view) {
            super(view);

            progress = new ProgressDrawable();
            ViewCompat.setBackground(itemView.findViewById(R.id.item_bkg), progress);

            status = itemView.findViewById(R.id.item_status);
            name = itemView.findViewById(R.id.item_name);
            icon = itemView.findViewById(R.id.item_icon);
            size = itemView.findViewById(R.id.item_size);

            name.setSelected(true);

            ImageView button = itemView.findViewById(R.id.item_more);
            popupMenu = buildPopup(button);
            button.setOnClickListener(v -> showPopupMenu());

            Menu menu = popupMenu.getMenu();
            start = menu.findItem(R.id.start);
            pause = menu.findItem(R.id.pause);
            open = menu.findItem(R.id.open);
            queue = menu.findItem(R.id.queue);
            showError = menu.findItem(R.id.error_message_view);
            delete = menu.findItem(R.id.delete);
            source = menu.findItem(R.id.source);
            checksum = menu.findItem(R.id.checksum);

            itemView.setOnClickListener((v) -> {
                if (item.mission instanceof FinishedMission)
                    viewWithFileProvider(item.mission.getDownloadedFile());
            });
        }

        private void showPopupMenu() {
            start.setVisible(false);
            pause.setVisible(false);
            open.setVisible(false);
            queue.setVisible(false);
            showError.setVisible(false);
            delete.setVisible(false);
            source.setVisible(false);
            checksum.setVisible(false);

            DownloadMission mission = item.mission instanceof DownloadMission ? (DownloadMission) item.mission : null;

            if (mission != null) {
                if (!mission.isPsRunning()) {
                    if (mission.running) {
                        pause.setVisible(true);
                    } else {
                        if (mission.errCode != ERROR_NOTHING) {
                            showError.setVisible(true);
                        }

                        queue.setChecked(mission.enqueued);

                        delete.setVisible(true);

                        boolean flag = !mission.isPsFailed();
                        start.setVisible(flag);
                        queue.setVisible(flag);
                    }
                }
            } else {
                open.setVisible(true);
                delete.setVisible(true);
                checksum.setVisible(true);
            }

            if (item.mission.source != null && !item.mission.source.isEmpty()) {
                source.setVisible(true);
            }

            popupMenu.show();
        }

        private PopupMenu buildPopup(final View button) {
            PopupMenu popup = new PopupMenu(mContext, button);
            popup.inflate(R.menu.mission);
            popup.setOnMenuItemClickListener(option -> handlePopupItem(this, option));

            return popup;
        }
    }

    class ViewHolderHeader extends RecyclerView.ViewHolder {
        TextView header;

        ViewHolderHeader(View view) {
            super(view);
            header = itemView.findViewById(R.id.item_name);
        }
    }


    static class ChecksumTask extends AsyncTask<String, Void, String> {
        ProgressDialog progressDialog;
        WeakReference<Activity> weakReference;

        ChecksumTask(@NonNull Context context) {
            weakReference = new WeakReference<>((Activity) context);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Activity activity = getActivity();
            if (activity != null) {
                // Create dialog
                progressDialog = new ProgressDialog(activity);
                progressDialog.setCancelable(false);
                progressDialog.setMessage(activity.getString(R.string.msg_wait));
                progressDialog.show();
            }
        }

        @Override
        protected String doInBackground(String... params) {
            return Utility.checksum(params[0], params[1]);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            if (progressDialog != null) {
                Utility.copyToClipboard(progressDialog.getContext(), result);
                if (getActivity() != null) {
                    progressDialog.dismiss();
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
