package us.shandian.giga.ui.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.io.StoredFileHelper;
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
import static us.shandian.giga.get.DownloadMission.ERROR_INSUFFICIENT_STORAGE;
import static us.shandian.giga.get.DownloadMission.ERROR_NOTHING;
import static us.shandian.giga.get.DownloadMission.ERROR_PATH_CREATION;
import static us.shandian.giga.get.DownloadMission.ERROR_PERMISSION_DENIED;
import static us.shandian.giga.get.DownloadMission.ERROR_POSTPROCESSING;
import static us.shandian.giga.get.DownloadMission.ERROR_POSTPROCESSING_HOLD;
import static us.shandian.giga.get.DownloadMission.ERROR_POSTPROCESSING_STOPPED;
import static us.shandian.giga.get.DownloadMission.ERROR_PROGRESS_LOST;
import static us.shandian.giga.get.DownloadMission.ERROR_RESOURCE_GONE;
import static us.shandian.giga.get.DownloadMission.ERROR_SSL_EXCEPTION;
import static us.shandian.giga.get.DownloadMission.ERROR_TIMEOUT;
import static us.shandian.giga.get.DownloadMission.ERROR_UNKNOWN_EXCEPTION;
import static us.shandian.giga.get.DownloadMission.ERROR_UNKNOWN_HOST;

public class MissionAdapter extends Adapter<ViewHolder> implements Handler.Callback {
    private static final SparseArray<String> ALGORITHMS = new SparseArray<>();
    private static final String TAG = "MissionAdapter";
    private static final String UNDEFINED_PROGRESS = "--.-%";
    private static final String DEFAULT_MIME_TYPE = "*/*";
    private static final String UNDEFINED_ETA = "--:--";


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
    private MenuItem mStartButton;
    private MenuItem mPauseButton;
    private View mEmptyMessage;
    private RecoverHelper mRecover;
    private View mView;
    private ArrayList<Mission> mHidden;
    private Snackbar mSnackbar;

    private final Runnable rUpdater = this::updater;
    private final Runnable rDelete = this::deleteFinishedDownloads;

    public MissionAdapter(Context context, @NonNull DownloadManager downloadManager, View emptyMessage, View root) {
        mContext = context;
        mDownloadManager = downloadManager;

        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLayout = R.layout.mission_item;

        mHandler = new Handler(context.getMainLooper());

        mEmptyMessage = emptyMessage;

        mIterator = downloadManager.getIterator();

        mDeleter = new Deleter(root, mContext, this, mDownloadManager, mIterator, mHandler);

        mView = root;

        mHidden = new ArrayList<>();

        checkEmptyMessageVisibility();
        onResume();
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
            if (mPendingDownloadsItems.size() < 1) {
                checkMasterButtonsVisibility();
            }
        }

        h.popupMenu.dismiss();
        h.item = null;
        h.resetSpeedMeasure();
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

        Utility.FileType type = Utility.getFileType(item.mission.kind, item.mission.storage.getName());

        h.icon.setImageResource(Utility.getIconForFileType(type));
        h.name.setText(item.mission.storage.getName());

        h.progress.setColors(Utility.getBackgroundForFileType(mContext, type), Utility.getForegroundForFileType(mContext, type));

        if (h.item.mission instanceof DownloadMission) {
            DownloadMission mission = (DownloadMission) item.mission;
            String length = Utility.formatBytes(mission.getLength());
            if (mission.running && !mission.isPsRunning()) length += " --.- kB/s";

            h.size.setText(length);
            h.pause.setTitle(mission.unknownLength ? R.string.stop : R.string.pause);
            updateProgress(h);
            mPendingDownloadsItems.add(h);
        } else {
            h.progress.setMarquee(false);
            h.status.setText("100%");
            h.progress.setProgress(1.0f);
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

        DownloadMission mission = (DownloadMission) h.item.mission;
        double done = mission.done;
        long length = mission.getLength();
        long now = System.currentTimeMillis();
        boolean hasError = mission.errCode != ERROR_NOTHING;

        // hide on error
        // show if current resource length is not fetched
        // show if length is unknown
        h.progress.setMarquee(mission.isRecovering() || !hasError && (!mission.isInitialized() || mission.unknownLength));

        double progress;
        if (mission.unknownLength) {
            progress = Double.NaN;
            h.progress.setProgress(0.0f);
        } else {
            progress = done / length;
        }

        if (hasError) {
            h.progress.setProgress(isNotFinite(progress) ? 1d : progress);
            h.status.setText(R.string.msg_error);
        } else if (isNotFinite(progress)) {
            h.status.setText(UNDEFINED_PROGRESS);
        } else {
            h.status.setText(String.format("%.2f%%", progress * 100));
            h.progress.setProgress(progress);
        }

        @StringRes int state;
        String sizeStr = Utility.formatBytes(length).concat("  ");

        if (mission.isPsFailed() || mission.errCode == ERROR_POSTPROCESSING_HOLD) {
            h.size.setText(sizeStr);
            return;
        } else if (!mission.running) {
            state = mission.enqueued ? R.string.queued : R.string.paused;
        } else if (mission.isPsRunning()) {
            state = R.string.post_processing;
        } else if (mission.isRecovering()) {
            state = R.string.recovering;
        } else {
            state = 0;
        }

        if (state != 0) {
            // update state without download speed
            h.size.setText(sizeStr.concat("(").concat(mContext.getString(state)).concat(")"));
            h.resetSpeedMeasure();
            return;
        }

        if (h.lastTimestamp < 0) {
            h.size.setText(sizeStr);
            h.lastTimestamp = now;
            h.lastDone = done;
            return;
        }

        long deltaTime = now - h.lastTimestamp;
        double deltaDone = done - h.lastDone;

        if (h.lastDone > done) {
            h.lastDone = done;
            h.size.setText(sizeStr);
            return;
        }

        if (deltaDone > 0 && deltaTime > 0) {
            float speed = (float) ((deltaDone * 1000d) / deltaTime);
            float averageSpeed = speed;

            if (h.lastSpeedIdx < 0) {
                for (int i = 0; i < h.lastSpeed.length; i++) {
                    h.lastSpeed[i] = speed;
                }
                h.lastSpeedIdx = 0;
            } else {
                for (int i = 0; i < h.lastSpeed.length; i++) {
                    averageSpeed += h.lastSpeed[i];
                }
                averageSpeed /= h.lastSpeed.length + 1.0f;
            }

            String speedStr = Utility.formatSpeed(averageSpeed);
            String etaStr;

            if (mission.unknownLength) {
                etaStr = "";
            } else {
                long eta = (long) Math.ceil((length - done) / averageSpeed);
                etaStr = Utility.formatBytes((long) done) + "/" + Utility.stringifySeconds(eta) + "  ";
            }

            h.size.setText(sizeStr.concat(etaStr).concat(speedStr));

            h.lastTimestamp = now;
            h.lastDone = done;
            h.lastSpeed[h.lastSpeedIdx++] = speed;

            if (h.lastSpeedIdx >= h.lastSpeed.length) h.lastSpeedIdx = 0;
        }
    }

    private void viewWithFileProvider(Mission mission) {
        if (checkInvalidFile(mission)) return;

        String mimeType = resolveMimeType(mission);

        if (BuildConfig.DEBUG)
            Log.v(TAG, "Mime: " + mimeType + " package: " + BuildConfig.APPLICATION_ID + ".provider");

        Uri uri = resolveShareableUri(mission);

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(FLAG_GRANT_PREFIX_URI_PERMISSION);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        }

        //mContext.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (intent.resolveActivity(mContext.getPackageManager()) != null) {
            mContext.startActivity(intent);
        } else {
            Toast.makeText(mContext, R.string.toast_no_player, Toast.LENGTH_LONG).show();
        }
    }

    private void shareFile(Mission mission) {
        if (checkInvalidFile(mission)) return;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(resolveMimeType(mission));
        intent.putExtra(Intent.EXTRA_STREAM, resolveShareableUri(mission));
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);

        mContext.startActivity(Intent.createChooser(intent, null));
    }

    /**
     * Returns an Uri which can be shared to other applications.
     *
     * @see <a href="https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed">
     * https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed</a>
     */
    private Uri resolveShareableUri(Mission mission) {
        if (mission.storage.isDirect()) {
            return FileProvider.getUriForFile(
                mContext,
                BuildConfig.APPLICATION_ID + ".provider",
                new File(URI.create(mission.storage.getUri().toString()))
            );
        } else {
            return mission.storage.getUri();
        }
    }

    private static String resolveMimeType(@NonNull Mission mission) {
        String mimeType;

        if (!mission.storage.isInvalid()) {
            mimeType = mission.storage.getType();
            if (mimeType != null && mimeType.length() > 0 && !mimeType.equals(StoredFileHelper.DEFAULT_MIME))
                return mimeType;
        }

        String ext = Utility.getFileExt(mission.storage.getName());
        if (ext == null) return DEFAULT_MIME_TYPE;

        mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.substring(1));

        return mimeType == null ? DEFAULT_MIME_TYPE : mimeType;
    }

    private boolean checkInvalidFile(@NonNull Mission mission) {
        if (mission.storage.existsAsFile()) return false;

        Toast.makeText(mContext, R.string.missing_file, Toast.LENGTH_SHORT).show();
        return true;
    }

    private ViewHolderItem getViewHolder(Object mission) {
        for (ViewHolderItem h : mPendingDownloadsItems) {
            if (h.item.mission == mission) return h;
        }
        return null;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (mStartButton != null && mPauseButton != null) {
            checkMasterButtonsVisibility();
        }

        switch (msg.what) {
            case DownloadManagerService.MESSAGE_ERROR:
            case DownloadManagerService.MESSAGE_FINISHED:
            case DownloadManagerService.MESSAGE_DELETED:
            case DownloadManagerService.MESSAGE_PAUSED:
                break;
            default:
                return false;
        }

        ViewHolderItem h = getViewHolder(msg.obj);
        if (h == null) return false;

        switch (msg.what) {
            case DownloadManagerService.MESSAGE_FINISHED:
            case DownloadManagerService.MESSAGE_DELETED:
                // DownloadManager should mark the download as finished
                applyChanges();
                return true;
        }

        updateProgress(h);
        return true;
    }

    private void showError(@NonNull DownloadMission mission) {
        @StringRes int msg = R.string.general_error;
        String msgEx = null;

        switch (mission.errCode) {
            case 416:
                msg = R.string.error_http_unsupported_range;
                break;
            case 404:
                msg = R.string.error_http_not_found;
                break;
            case ERROR_NOTHING:
                return;// this never should happen
            case ERROR_FILE_CREATION:
                msg = R.string.error_file_creation;
                break;
            case ERROR_HTTP_NO_CONTENT:
                msg = R.string.error_http_no_content;
                break;
            case ERROR_PATH_CREATION:
                msg = R.string.error_path_creation;
                break;
            case ERROR_PERMISSION_DENIED:
                msg = R.string.permission_denied;
                break;
            case ERROR_SSL_EXCEPTION:
                msg = R.string.error_ssl_exception;
                break;
            case ERROR_UNKNOWN_HOST:
                msg = R.string.error_unknown_host;
                break;
            case ERROR_CONNECT_HOST:
                msg = R.string.error_connect_host;
                break;
            case ERROR_POSTPROCESSING_STOPPED:
                msg = R.string.error_postprocessing_stopped;
                break;
            case ERROR_POSTPROCESSING:
            case ERROR_POSTPROCESSING_HOLD:
                showError(mission, UserAction.DOWNLOAD_POSTPROCESSING, R.string.error_postprocessing_failed);
                return;
            case ERROR_INSUFFICIENT_STORAGE:
                msg = R.string.error_insufficient_storage;
                break;
            case ERROR_UNKNOWN_EXCEPTION:
                if (mission.errObject != null) {
                    showError(mission, UserAction.DOWNLOAD_FAILED, R.string.general_error);
                    return;
                } else {
                    msg = R.string.msg_error;
                    break;
                }
            case ERROR_PROGRESS_LOST:
                msg = R.string.error_progress_lost;
                break;
            case ERROR_TIMEOUT:
                msg = R.string.error_timeout;
                break;
            case ERROR_RESOURCE_GONE:
                msg = R.string.error_download_resource_gone;
                break;
            default:
                if (mission.errCode >= 100 && mission.errCode < 600) {
                    msgEx = "HTTP " + mission.errCode;
                } else if (mission.errObject == null) {
                    msgEx = "(not_decelerated_error_code)";
                } else {
                    showError(mission, UserAction.DOWNLOAD_FAILED, msg);
                    return;
                }
                break;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        if (msgEx != null)
            builder.setMessage(msgEx);
        else
            builder.setMessage(msg);

        // add report button for non-HTTP errors (range 100-599)
        if (mission.errObject != null && (mission.errCode < 100 || mission.errCode >= 600)) {
            @StringRes final int mMsg = msg;
            builder.setPositiveButton(R.string.error_report_title, (dialog, which) ->
                    showError(mission, UserAction.DOWNLOAD_FAILED, mMsg)
            );
        }

        builder.setNegativeButton(R.string.finish, (dialog, which) -> dialog.cancel())
                .setTitle(mission.storage.getName())
                .create()
                .show();
    }

    private void showError(DownloadMission mission, UserAction action, @StringRes int reason) {
        StringBuilder request = new StringBuilder(256);
        request.append(mission.source);

        request.append(" [");
        if (mission.recoveryInfo != null) {
            for (MissionRecoveryInfo recovery : mission.recoveryInfo)
                request.append(' ')
                        .append(recovery.toString())
                        .append(' ');
        }
        request.append("]");

        String service;
        try {
            service = NewPipe.getServiceByUrl(mission.source).getServiceInfo().getName();
        } catch (Exception e) {
            service = "-";
        }

        ErrorActivity.reportError(
                mContext,
                mission.errObject,
                null,
                null,
                ErrorActivity.ErrorInfo.make(action, service, request.toString(), reason)
        );
    }

    public void clearFinishedDownloads(boolean delete) {
        if (delete && mIterator.hasFinishedMissions() && mHidden.isEmpty()) {
            for (int i = 0; i < mIterator.getOldListSize(); i++) {
                FinishedMission mission = mIterator.getItem(i).mission instanceof FinishedMission ? (FinishedMission) mIterator.getItem(i).mission : null;
                if (mission != null) {
                    mIterator.hide(mission);
                    mHidden.add(mission);
                }
            }
            applyChanges();

            String msg = String.format(mContext.getString(R.string.deleted_downloads), mHidden.size());
            mSnackbar = Snackbar.make(mView, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.setAction(R.string.undo, s -> {
                Iterator<Mission> i = mHidden.iterator();
                while (i.hasNext()) {
                    mIterator.unHide(i.next());
                    i.remove();
                }
                applyChanges();
                mHandler.removeCallbacks(rDelete);
            });
            mSnackbar.setActionTextColor(Color.YELLOW);
            mSnackbar.show();

            mHandler.postDelayed(rDelete, 5000);
        } else if (!delete) {
            mDownloadManager.forgetFinishedDownloads();
            applyChanges();
        }
    }

    private void deleteFinishedDownloads() {
        if (mSnackbar != null) mSnackbar.dismiss();

        Iterator<Mission> i = mHidden.iterator();
        while (i.hasNext()) {
            Mission mission = i.next();
            if (mission != null) {
                mDownloadManager.deleteMission(mission);
                mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mission.storage.getUri()));
            }
            i.remove();
        }
    }

    private boolean handlePopupItem(@NonNull ViewHolderItem h, @NonNull MenuItem option) {
        if (h.item == null) return true;

        int id = option.getItemId();
        DownloadMission mission = h.item.mission instanceof DownloadMission ? (DownloadMission) h.item.mission : null;

        if (mission != null) {
            switch (id) {
                case R.id.start:
                    h.status.setText(UNDEFINED_PROGRESS);
                    mDownloadManager.resumeMission(mission);
                    return true;
                case R.id.pause:
                    mDownloadManager.pauseMission(mission);
                    return true;
                case R.id.error_message_view:
                    showError(mission);
                    return true;
                case R.id.queue:
                    boolean flag = !h.queue.isChecked();
                    h.queue.setChecked(flag);
                    mission.setEnqueued(flag);
                    updateProgress(h);
                    return true;
                case R.id.retry:
                    if (mission.isPsRunning()) {
                        mission.psContinue(true);
                    } else {
                        mDownloadManager.tryRecover(mission);
                        if (mission.storage.isInvalid())
                            mRecover.tryRecover(mission);
                        else
                            recoverMission(mission);
                    }
                    return true;
                case R.id.cancel:
                    mission.psContinue(false);
                    return false;
            }
        }

        switch (id) {
            case R.id.menu_item_share:
                shareFile(h.item.mission);
                return true;
            case R.id.delete:
                mDeleter.append(h.item.mission);
                applyChanges();
                checkMasterButtonsVisibility();
                return true;
            case R.id.md5:
            case R.id.sha1:
                new ChecksumTask(mContext).execute(h.item.mission.storage, ALGORITHMS.get(id));
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
            item.resetSpeedMeasure();
        }

        notifyDataSetChanged();
    }

    public void setLinear(boolean isLinear) {
        mLayout = isLinear ? R.layout.mission_item_linear : R.layout.mission_item;
    }

    public void setClearButton(MenuItem clearButton) {
        if (mClear == null)
            clearButton.setVisible(mIterator.hasFinishedMissions());

        mClear = clearButton;
    }

    public void setMasterButtons(MenuItem startButton, MenuItem pauseButton) {
        boolean init = mStartButton == null || mPauseButton == null;

        mStartButton = startButton;
        mPauseButton = pauseButton;

        if (init) checkMasterButtonsVisibility();
    }

    private void checkEmptyMessageVisibility() {
        int flag = mIterator.getOldListSize() > 0 ? View.GONE : View.VISIBLE;
        if (mEmptyMessage.getVisibility() != flag) mEmptyMessage.setVisibility(flag);
    }

    public void checkMasterButtonsVisibility() {
        boolean[] state = mIterator.hasValidPendingMissions();
        Log.d(TAG, "checkMasterButtonsVisibility() running=" + state[0] + " paused=" + state[1]);
        setButtonVisible(mPauseButton, state[0]);
        setButtonVisible(mStartButton, state[1]);
    }

    private static void setButtonVisible(MenuItem button, boolean visible) {
        if (button.isVisible() != visible)
            button.setVisible(visible);
    }

    public void refreshMissionItems() {
        for (ViewHolderItem h : mPendingDownloadsItems) {
            if (((DownloadMission) h.item.mission).running) continue;
            updateProgress(h);
            h.resetSpeedMeasure();
        }
    }


    public void onDestroy() {
        mDeleter.dispose();
    }

    public void onResume() {
        mDeleter.resume();
        mHandler.post(rUpdater);
    }

    public void onPaused() {
        mDeleter.pause();
        mHandler.removeCallbacks(rUpdater);
    }


    public void recoverMission(DownloadMission mission) {
        ViewHolderItem h = getViewHolder(mission);
        if (h == null) return;

        mission.errObject = null;
        mission.resetState(true, false, DownloadMission.ERROR_NOTHING);

        h.status.setText(UNDEFINED_PROGRESS);
        h.size.setText(Utility.formatBytes(mission.getLength()));
        h.progress.setMarquee(true);

        mDownloadManager.resumeMission(mission);
    }

    private void updater() {
        for (ViewHolderItem h : mPendingDownloadsItems) {
            // check if the mission is running first
            if (!((DownloadMission) h.item.mission).running) continue;

            updateProgress(h);
        }

        mHandler.postDelayed(rUpdater, 1000);
    }

    private boolean isNotFinite(double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }

    public void setRecover(@NonNull RecoverHelper callback) {
        mRecover = callback;
    }


    class ViewHolderItem extends RecyclerView.ViewHolder {
        DownloadManager.MissionItem item;

        TextView status;
        ImageView icon;
        TextView name;
        TextView size;
        ProgressDrawable progress;

        PopupMenu popupMenu;
        MenuItem retry;
        MenuItem cancel;
        MenuItem start;
        MenuItem pause;
        MenuItem open;
        MenuItem queue;
        MenuItem showError;
        MenuItem delete;
        MenuItem source;
        MenuItem checksum;

        long lastTimestamp = -1;
        double lastDone;
        int lastSpeedIdx;
        float[] lastSpeed = new float[3];
        String estimatedTimeArrival = UNDEFINED_ETA;

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
            retry = menu.findItem(R.id.retry);
            cancel = menu.findItem(R.id.cancel);
            start = menu.findItem(R.id.start);
            pause = menu.findItem(R.id.pause);
            open = menu.findItem(R.id.menu_item_share);
            queue = menu.findItem(R.id.queue);
            showError = menu.findItem(R.id.error_message_view);
            delete = menu.findItem(R.id.delete);
            source = menu.findItem(R.id.source);
            checksum = menu.findItem(R.id.checksum);

            itemView.setHapticFeedbackEnabled(true);

            itemView.setOnClickListener(v -> {
                if (item.mission instanceof FinishedMission)
                    viewWithFileProvider(item.mission);
            });

            itemView.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showPopupMenu();
                return true;
            });
        }

        private void showPopupMenu() {
            retry.setVisible(false);
            cancel.setVisible(false);
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
                if (mission.hasInvalidStorage()) {
                    retry.setVisible(true);
                    delete.setVisible(true);
                    showError.setVisible(true);
                } else if (mission.isPsRunning()) {
                    switch (mission.errCode) {
                        case ERROR_INSUFFICIENT_STORAGE:
                        case ERROR_POSTPROCESSING_HOLD:
                            retry.setVisible(true);
                            cancel.setVisible(true);
                            showError.setVisible(true);
                            break;
                    }
                } else {
                    if (mission.running) {
                        pause.setVisible(true);
                    } else {
                        if (mission.errCode != ERROR_NOTHING) {
                            showError.setVisible(true);
                        }

                        queue.setChecked(mission.enqueued);

                        delete.setVisible(true);

                        boolean flag = !mission.isPsFailed() && mission.urls.length > 0;
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

        private void resetSpeedMeasure() {
            estimatedTimeArrival = UNDEFINED_ETA;
            lastTimestamp = -1;
            lastSpeedIdx = -1;
        }
    }

    class ViewHolderHeader extends RecyclerView.ViewHolder {
        TextView header;

        ViewHolderHeader(View view) {
            super(view);
            header = itemView.findViewById(R.id.item_name);
        }
    }


    static class ChecksumTask extends AsyncTask<Object, Void, String> {
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
        protected String doInBackground(Object... params) {
            return Utility.checksum((StoredFileHelper) params[0], (String) params[1]);
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

    public interface RecoverHelper {
        void tryRecover(DownloadMission mission);
    }

}
