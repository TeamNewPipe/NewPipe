package us.shandian.giga.ui.fragment

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nononsenseapps.filepicker.Utils
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.FilePickerActivityHelper
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder
import us.shandian.giga.ui.adapter.MissionAdapter
import us.shandian.giga.ui.adapter.MissionAdapter.RecoverHelper
import java.io.File
import java.io.IOException

class MissionsFragment() : Fragment() {
    private var mPrefs: SharedPreferences? = null
    private var mLinear: Boolean = false
    private var mSwitch: MenuItem? = null
    private var mClear: MenuItem? = null
    private var mStart: MenuItem? = null
    private var mPause: MenuItem? = null
    private var mList: RecyclerView? = null
    private var mEmpty: View? = null
    private var mAdapter: MissionAdapter? = null
    private var mGridManager: GridLayoutManager? = null
    private var mLinearManager: LinearLayoutManager? = null
    private var mContext: Context? = null
    private var mBinder: DownloadManagerBinder? = null
    private var mForceUpdate: Boolean = false
    private var unsafeMissionTarget: DownloadMission? = null
    private val requestDownloadSaveAsLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestDownloadSaveAsResult(result) }))
    private val mConnection: ServiceConnection = object : ServiceConnection {
        public override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            mBinder = binder as DownloadManagerBinder?
            mBinder!!.clearDownloadNotifications()
            mAdapter = MissionAdapter(mContext, (mBinder!!.getDownloadManager())!!, mEmpty, getView())
            mAdapter!!.setRecover(RecoverHelper({ mission: DownloadMission -> recoverMission(mission) }))
            setAdapterButtons()
            mBinder!!.addMissionEventListener(mAdapter!!)
            mBinder!!.enableNotifications(false)
            updateList()
        }

        public override fun onServiceDisconnected(name: ComponentName) {
            // What to do?
        }
    }

    public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v: View = inflater.inflate(R.layout.missions, container, false)
        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        mLinear = mPrefs.getBoolean("linear", false)

        // Bind the service
        mContext!!.bindService(Intent(mContext, DownloadManagerService::class.java), mConnection, Context.BIND_AUTO_CREATE)

        // Views
        mEmpty = v.findViewById(R.id.list_empty_view)
        mList = v.findViewById(R.id.mission_recycler)

        // Init layouts managers
        mGridManager = GridLayoutManager(getActivity(), SPAN_SIZE)
        mGridManager!!.setSpanSizeLookup(object : SpanSizeLookup() {
            public override fun getSpanSize(position: Int): Int {
                when (mAdapter!!.getItemViewType(position)) {
                    DownloadManager.Companion.SPECIAL_PENDING, DownloadManager.Companion.SPECIAL_FINISHED -> return SPAN_SIZE
                    else -> return 1
                }
            }
        })
        mLinearManager = LinearLayoutManager(getActivity())
        setHasOptionsMenu(true)
        return v
    }

    /**
     * Added in API level 23.
     */
    public override fun onAttach(context: Context) {
        super.onAttach(context)

        // Bug: in api< 23 this is never called
        // so mActivity=null
        // so app crashes with null-pointer exception
        mContext = context
    }

    /**
     * deprecated in API level 23,
     * but must remain to allow compatibility with api<23
     */
    @Suppress("deprecation")
    public override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        mContext = activity
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (mBinder == null || mAdapter == null) return
        mBinder!!.removeMissionEventListener(mAdapter!!)
        mBinder!!.enableNotifications(true)
        mContext!!.unbindService(mConnection)
        mAdapter!!.onDestroy()
        mBinder = null
        mAdapter = null
    }

    public override fun onPrepareOptionsMenu(menu: Menu) {
        mSwitch = menu.findItem(R.id.switch_mode)
        mClear = menu.findItem(R.id.clear_list)
        mStart = menu.findItem(R.id.start_downloads)
        mPause = menu.findItem(R.id.pause_downloads)
        if (mAdapter != null) setAdapterButtons()
        super.onPrepareOptionsMenu(menu)
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.switch_mode -> {
                mLinear = !mLinear
                updateList()
                return true
            }

            R.id.clear_list -> {
                showClearDownloadHistoryPrompt()
                return true
            }

            R.id.start_downloads -> {
                mBinder!!.getDownloadManager()!!.startAllMissions()
                return true
            }

            R.id.pause_downloads -> {
                mBinder!!.getDownloadManager()!!.pauseAllMissions(false)
                mAdapter!!.refreshMissionItems() // update items view
                return super.onOptionsItemSelected(item)
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun showClearDownloadHistoryPrompt() {
        // ask the user whether he wants to just clear history or instead delete files on disk
        AlertDialog.Builder((mContext)!!)
                .setTitle(R.string.clear_download_history)
                .setMessage(R.string.confirm_prompt) // Intentionally misusing buttons' purpose in order to achieve good order
                .setNegativeButton(R.string.clear_download_history, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> mAdapter!!.clearFinishedDownloads(false) }))
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_downloaded_files, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> showDeleteDownloadedFilesConfirmationPrompt() }))
                .show()
    }

    fun showDeleteDownloadedFilesConfirmationPrompt() {
        // make sure the user confirms once more before deleting files on disk
        AlertDialog.Builder((mContext)!!)
                .setTitle(R.string.delete_downloaded_files_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> mAdapter!!.clearFinishedDownloads(true) }))
                .show()
    }

    private fun updateList() {
        if (mLinear) {
            mList!!.setLayoutManager(mLinearManager)
        } else {
            mList!!.setLayoutManager(mGridManager)
        }

        // destroy all created views in the recycler
        mList!!.setAdapter(null)
        mAdapter.notifyDataSetChanged()

        // re-attach the adapter in grid/lineal mode
        mAdapter!!.setLinear(mLinear)
        mList!!.setAdapter(mAdapter)
        if (mSwitch != null) {
            mSwitch!!.setIcon(if (mLinear) R.drawable.ic_apps else R.drawable.ic_list)
            mSwitch!!.setTitle(if (mLinear) R.string.grid else R.string.list)
            mPrefs!!.edit().putBoolean("linear", mLinear).apply()
        }
    }

    private fun setAdapterButtons() {
        if ((mClear == null) || (mStart == null) || (mPause == null)) return
        mAdapter!!.setClearButton(mClear!!)
        mAdapter!!.setMasterButtons(mStart, mPause)
    }

    private fun recoverMission(mission: DownloadMission) {
        unsafeMissionTarget = mission
        val initialPath: Uri?
        if (NewPipeSettings.useStorageAccessFramework(mContext)) {
            initialPath = null
        } else {
            val initialSavePath: File
            if ((DownloadManager.Companion.TAG_AUDIO == mission.storage!!.getType())) {
                initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC)
            } else {
                initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES)
            }
            initialPath = Uri.parse(initialSavePath.getAbsolutePath())
        }
        NoFileManagerSafeGuard.launchSafe<Intent>(
                requestDownloadSaveAsLauncher,
                StoredFileHelper.Companion.getNewPicker((mContext)!!, mission.storage!!.getName(),
                        (mission.storage!!.getType())!!, initialPath),
                TAG,
                mContext
        )
    }

    public override fun onResume() {
        super.onResume()
        if (mAdapter != null) {
            mAdapter!!.onResume()
            if (mForceUpdate) {
                mForceUpdate = false
                mAdapter!!.forceUpdate()
            }
            mBinder!!.addMissionEventListener(mAdapter!!)
            mAdapter!!.checkMasterButtonsVisibility()
        }
        if (mBinder != null) mBinder!!.enableNotifications(false)
    }

    public override fun onPause() {
        super.onPause()
        if (mAdapter != null) {
            mForceUpdate = true
            mBinder!!.removeMissionEventListener(mAdapter!!)
            mAdapter!!.onPaused()
        }
        if (mBinder != null) mBinder!!.enableNotifications(true)
    }

    private fun requestDownloadSaveAsResult(result: ActivityResult) {
        if (result.getResultCode() != Activity.RESULT_OK) {
            return
        }
        if (unsafeMissionTarget == null || result.getData() == null) {
            return
        }
        try {
            var fileUri: Uri? = result.getData()!!.getData()
            if (fileUri!!.getAuthority() != null && FilePickerActivityHelper.Companion.isOwnFileUri((mContext)!!, (fileUri))) {
                fileUri = Uri.fromFile(Utils.getFileForUri((fileUri)))
            }
            val tag: String? = unsafeMissionTarget!!.storage!!.getTag()
            unsafeMissionTarget!!.storage = StoredFileHelper(mContext, null, (fileUri)!!, tag)
            mAdapter!!.recoverMission(unsafeMissionTarget)
        } catch (e: IOException) {
            Toast.makeText(mContext, R.string.general_error, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private val TAG: String = "MissionsFragment"
        private val SPAN_SIZE: Int = 2
    }
}
