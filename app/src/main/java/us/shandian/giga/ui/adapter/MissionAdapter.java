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
import java.util.Locale;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.common.Deleter;
import us.shandian.giga.ui.common.ProgressDrawable;
import us.shandian.giga.util.Utility;

import static android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class MissionAdapter extends RecyclerView.Adapter<ViewHolder> {
    private static final SparseArray<String> ALGORITHMS = new SparseArray<>();
    private static final String TAG = "MissionAdapter";

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

        if (h.item.mission instanceof DownloadMission) mPendingDownloadsItems.remove(h);

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
                mClear.setVisible(true);
            }

            ((ViewHolderHeader) view).header.setText(str);
            return;
        }

        ViewHolderItem h = (ViewHolderItem) view;
        h.item = item;

        Utility.FileType type = Utility.getFileType(item.mission.kind, item.mission.name);

        h.icon.setImageResource(Utility.getIconForFileType(type));
        h.name.setText(item.mission.name);
        h.size.setText(Utility.formatBytes(item.mission.length));

        h.progress.setColors(Utility.getBackgroundForFileType(mContext, type), Utility.getForegroundForFileType(mContext, type));

        if (h.item.mission instanceof DownloadMission) {
            DownloadMission mission = (DownloadMission) item.mission;
            h.progress.setMarquee(mission.done < 1);
            updateProgress(h);
            h.pause.setTitle(mission.unknownLength ? R.string.stop : R.string.pause);
            mPendingDownloadsItems.add(h);
        } else {
            h.progress.setMarquee(false);
            h.status.setText("100%");
            h.progress.setProgress(1f);
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

    private void updateProgress(ViewHolderItem h) {
        if (h == null || h.item == null || h.item.mission instanceof FinishedMission) return;

        DownloadMission mission = (DownloadMission) h.item.mission;

        long now = System.currentTimeMillis();

        if (h.lastTimeStamp == -1) {
            h.lastTimeStamp = now;
        }

        if (h.lastDone == -1) {
            h.lastDone = mission.done;
        }
        if (h.lastCurrent != mission.current) {
            h.lastCurrent = mission.current;
            h.lastDone = 0;
            h.lastTimeStamp = now;
        }

        long deltaTime = now - h.lastTimeStamp;
        long deltaDone = mission.done - h.lastDone;
        boolean hasError = mission.errCode != DownloadMission.ERROR_NOTHING;

        if (hasError || deltaTime == 0 || deltaTime > 1000) {
            // on error hide marquee or show if condition (mission.done < 1 || mission.unknownLength) is true
            h.progress.setMarquee(!hasError && (mission.done < 1 || mission.unknownLength));

            float progress;
            if (mission.unknownLength) {
                progress = Float.NaN;
                h.progress.setProgress(0f);
            } else {
                progress = (float) mission.done / mission.length;
                if (mission.urls.length > 1 && mission.current < mission.urls.length) {
                    progress = (progress / mission.urls.length) + ((float) mission.current / mission.urls.length);
                }
            }

            if (hasError) {
                if (Float.isNaN(progress) || Float.isInfinite(progress)) h.progress.setProgress(1f);
                h.status.setText(R.string.msg_error);
            } else if (Float.isNaN(progress) || Float.isInfinite(progress)) {
                h.status.setText("--.-%");
            } else {
                h.status.setText(String.format("%.2f%%", progress * 100));
                h.progress.setProgress(progress);
            }
        }

        long length = mission.offsets[mission.current < mission.offsets.length ? mission.current : (mission.offsets.length - 1)];
        length += mission.length;

        int state = 0;
        if (!mission.isFinished()) {
            if (!mission.running) {
                state = mission.enqueued ? 1 : 2;
            } else if (mission.postprocessingRunning) {
                state = 3;
            }
        }

        if (state != 0) {
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
            } else if (deltaTime > 1000 && deltaDone > 0) {
                h.lastTimeStamp = now;
                h.lastDone = mission.done;
            }

            return;
        }

      
        if (deltaTime > 1000 && deltaDone > 0) {
            float speed = (float) deltaDone / deltaTime;
            String speedStr = Utility.formatSpeed(speed * 1000);
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

                mPendingDownloadsItems.remove(i);
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
            case DownloadMission.ERROR_NOTHING:
                str.append("¿?");
                break;
            case DownloadMission.ERROR_FILE_CREATION:
                str.append(mContext.getString(R.string.error_file_creation));
                break;
            case DownloadMission.ERROR_HTTP_NO_CONTENT:
                str.append(mContext.getString(R.string.error_http_no_content));
                break;
            case DownloadMission.ERROR_HTTP_UNSUPPORTED_RANGE:
                str.append(mContext.getString(R.string.error_http_unsupported_range));
                break;
            case DownloadMission.ERROR_PATH_CREATION:
                str.append(mContext.getString(R.string.error_path_creation));
                break;
            case DownloadMission.ERROR_PERMISSION_DENIED:
                str.append(mContext.getString(R.string.permission_denied));
                break;
            case DownloadMission.ERROR_SSL_EXCEPTION:
                str.append(mContext.getString(R.string.error_ssl_exception));
                break;
            case DownloadMission.ERROR_UNKNOWN_HOST:
                str.append(mContext.getString(R.string.error_unknown_host));
                break;
            case DownloadMission.ERROR_CONNECT_HOST:
                str.append(mContext.getString(R.string.error_connect_host));
                break;
            case DownloadMission.ERROR_POSTPROCESSING_FAILED:
                str.append(R.string.error_postprocessing_failed);
            case DownloadMission.ERROR_UNKNOWN_EXCEPTION:
                break;
            default:
                if (mission.errCode >= 100 && mission.errCode < 600) {
                    str.append("HTTP");
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
        mClear.setVisible(false);
    }

    private boolean handlePopupItem(@NonNull ViewHolderItem h, @NonNull MenuItem option) {
        int id = option.getItemId();
        DownloadMission mission = h.item.mission instanceof DownloadMission ? (DownloadMission) h.item.mission : null;

        if (mission != null) {
            switch (id) {
                case R.id.start:
                    h.state = -1;
                    h.size.setText(Utility.formatBytes(mission.length));
                    mDownloadManager.resumeMission(mission);
                    return true;
                case R.id.pause:
                    h.state = -1;
                    mDownloadManager.pauseMission(mission);
                    notifyItemChanged(h.getAdapterPosition());
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
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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

        if (mIterator.getOldListSize() > 0) {
            int lastItemType = mIterator.getSpecialAtItem(mIterator.getOldListSize() - 1);
            mClear.setVisible(lastItemType == DownloadManager.SPECIAL_FINISHED);
        }
    }

    public void forceUpdate() {
        mIterator.start();
        mIterator.end();

        notifyDataSetChanged();
    }

    public void setLinear(boolean isLinear) {
        mLayout = isLinear ? R.layout.mission_item_linear : R.layout.mission_item;
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
                  if(h.mission.finished) viewWithFileProvider(h);
            });

            //h.itemView.setOnClickListener(v -> showDetail(h));
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
                if (!mission.postprocessingRunning) {
                    if (mission.running) {
                        pause.setVisible(true);
                    } else {
                        if (mission.errCode != DownloadMission.ERROR_NOTHING) {
                            showError.setVisible(true);
                        }

                        queue.setChecked(mission.enqueued);

                        start.setVisible(mission.errCode != DownloadMission.ERROR_POSTPROCESSING_FAILED);
                        delete.setVisible(true);
                        queue.setVisible(true);
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
