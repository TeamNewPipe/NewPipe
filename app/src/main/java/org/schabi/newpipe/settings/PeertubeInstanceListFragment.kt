package org.schabi.newpipe.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.grack.nanojson.JsonStringWriter
import com.grack.nanojson.JsonWriter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.databinding.FragmentInstanceListBinding
import org.schabi.newpipe.databinding.ItemInstanceBinding
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance
import org.schabi.newpipe.util.PeertubeHelper
import org.schabi.newpipe.util.ThemeHelper
import java.util.Collections
import java.util.concurrent.Callable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

class PeertubeInstanceListFragment() : Fragment() {
    private var selectedInstance: PeertubeInstance? = null
    private var savedInstanceListKey: String? = null
    private var instanceListAdapter: InstanceListAdapter? = null
    private var binding: FragmentInstanceListBinding? = null
    private var sharedPreferences: SharedPreferences? = null
    private var disposables: CompositeDisposable? = CompositeDisposable()

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        savedInstanceListKey = getString(R.string.peertube_instance_list_key)
        selectedInstance = PeertubeHelper.getCurrentInstance()
        setHasOptionsMenu(true)
    }

    public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        binding = FragmentInstanceListBinding.inflate(inflater, container, false)
        return binding!!.getRoot()
    }

    public override fun onViewCreated(rootView: View,
                                      savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        binding!!.instanceHelpTV.setText(getString(R.string.peertube_instance_url_help,
                getString(R.string.peertube_instance_list_url)))
        binding!!.addInstanceButton.setOnClickListener(View.OnClickListener({ v: View? -> showAddItemDialog(requireContext()) }))
        binding!!.instances.setLayoutManager(LinearLayoutManager(requireContext()))
        val itemTouchHelper: ItemTouchHelper = ItemTouchHelper(getItemTouchCallback())
        itemTouchHelper.attachToRecyclerView(binding!!.instances)
        instanceListAdapter = InstanceListAdapter(requireContext(), itemTouchHelper)
        binding!!.instances.setAdapter(instanceListAdapter)
        instanceListAdapter!!.submitList(PeertubeHelper.getInstanceList(requireContext()))
    }

    public override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(getActivity(),
                getString(R.string.peertube_instance_url_title))
    }

    public override fun onPause() {
        super.onPause()
        saveChanges()
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (disposables != null) {
            disposables!!.clear()
        }
        disposables = null
    }

    public override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_chooser_fragment, menu)
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.menu_item_restore_default) {
            restoreDefaults()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun selectInstance(instance: PeertubeInstance?) {
        selectedInstance = PeertubeHelper.selectInstance(instance, requireContext())
        sharedPreferences!!.edit().putBoolean(KEY_MAIN_PAGE_CHANGE, true).apply()
    }

    private fun saveChanges() {
        val jsonWriter: JsonStringWriter = JsonWriter.string().`object`().array("instances")
        for (instance: PeertubeInstance? in instanceListAdapter!!.getCurrentList()) {
            jsonWriter.`object`()
            jsonWriter.value("name", instance!!.getName())
            jsonWriter.value("url", instance.getUrl())
            jsonWriter.end()
        }
        val jsonToSave: String = jsonWriter.end().end().done()
        sharedPreferences!!.edit().putString(savedInstanceListKey, jsonToSave).apply()
    }

    private fun restoreDefaults() {
        val context: Context = requireContext()
        AlertDialog.Builder(context)
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int ->
                    sharedPreferences!!.edit().remove(savedInstanceListKey).apply()
                    selectInstance(PeertubeInstance.DEFAULT_INSTANCE)
                    instanceListAdapter!!.submitList(PeertubeHelper.getInstanceList(context))
                }))
                .show()
    }

    private fun showAddItemDialog(c: Context) {
        val dialogBinding: DialogEditTextBinding = DialogEditTextBinding.inflate(getLayoutInflater())
        dialogBinding.dialogEditText.setInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        dialogBinding.dialogEditText.setHint(R.string.peertube_instance_add_help)
        AlertDialog.Builder(c)
                .setTitle(R.string.peertube_instance_add_title)
                .setIcon(R.drawable.ic_placeholder_peertube)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialog1: DialogInterface?, which: Int ->
                    val url: String = dialogBinding.dialogEditText.getText().toString()
                    addInstance(url)
                }))
                .show()
    }

    private fun addInstance(url: String) {
        val cleanUrl: String? = cleanUrl(url)
        if (cleanUrl == null) {
            return
        }
        binding!!.loadingProgressBar.setVisibility(View.VISIBLE)
        val disposable: Disposable = Single.fromCallable(Callable({
            val instance: PeertubeInstance = PeertubeInstance(cleanUrl)
            instance.fetchInstanceMetaData()
            instance
        })).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer({ instance: PeertubeInstance ->
                    binding!!.loadingProgressBar.setVisibility(View.GONE)
                    add(instance)
                }), Consumer({ e: Throwable? ->
                    binding!!.loadingProgressBar.setVisibility(View.GONE)
                    Toast.makeText(getActivity(), R.string.peertube_instance_add_fail,
                            Toast.LENGTH_SHORT).show()
                }))
        disposables!!.add(disposable)
    }

    private fun cleanUrl(url: String): String? {
        var cleanUrl: String = url.trim({ it <= ' ' })
        // if protocol not present, add https
        if (!cleanUrl.startsWith("http")) {
            cleanUrl = "https://" + cleanUrl
        }
        // remove trailing slash
        cleanUrl = cleanUrl.replace("/$".toRegex(), "")
        // only allow https
        if (!cleanUrl.startsWith("https://")) {
            Toast.makeText(getActivity(), R.string.peertube_instance_add_https_only,
                    Toast.LENGTH_SHORT).show()
            return null
        }
        // only allow if not already exists
        for (instance: PeertubeInstance? in instanceListAdapter!!.getCurrentList()) {
            if ((instance!!.getUrl() == cleanUrl)) {
                Toast.makeText(getActivity(), R.string.peertube_instance_add_exists,
                        Toast.LENGTH_SHORT).show()
                return null
            }
        }
        return cleanUrl
    }

    private fun add(instance: PeertubeInstance) {
        val list: ArrayList<PeertubeInstance?> = ArrayList(instanceListAdapter!!.getCurrentList())
        list.add(instance)
        instanceListAdapter!!.submitList(list)
    }

    private fun getItemTouchCallback(): ItemTouchHelper.SimpleCallback {
        return object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                ItemTouchHelper.START or ItemTouchHelper.END) {
            public override fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView,
                                                             viewSize: Int,
                                                             viewSizeOutOfBounds: Int,
                                                             totalSize: Int,
                                                             msSinceStartScroll: Long): Int {
                val standardSpeed: Int = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                        viewSizeOutOfBounds, totalSize, msSinceStartScroll)
                val minimumAbsVelocity: Int = max(12.0, abs(standardSpeed.toDouble())).toInt()
                return minimumAbsVelocity * sign(viewSizeOutOfBounds.toDouble()).toInt()
            }

            public override fun onMove(recyclerView: RecyclerView,
                                       source: RecyclerView.ViewHolder,
                                       target: RecyclerView.ViewHolder): Boolean {
                if ((source.getItemViewType() != target.getItemViewType()
                                || instanceListAdapter == null)) {
                    return false
                }
                val sourceIndex: Int = source.getBindingAdapterPosition()
                val targetIndex: Int = target.getBindingAdapterPosition()
                instanceListAdapter!!.swapItems(sourceIndex, targetIndex)
                return true
            }

            public override fun isLongPressDragEnabled(): Boolean {
                return false
            }

            public override fun isItemViewSwipeEnabled(): Boolean {
                return true
            }

            public override fun onSwiped(viewHolder: RecyclerView.ViewHolder,
                                         swipeDir: Int) {
                val position: Int = viewHolder.getBindingAdapterPosition()
                // do not allow swiping the selected instance
                if ((instanceListAdapter!!.getCurrentList().get(position)!!.getUrl()
                                == selectedInstance!!.getUrl())) {
                    instanceListAdapter!!.notifyItemChanged(position)
                    return
                }
                val list: ArrayList<PeertubeInstance?> = ArrayList(instanceListAdapter!!.getCurrentList())
                list.removeAt(position)
                if (list.isEmpty()) {
                    list.add(selectedInstance)
                }
                instanceListAdapter!!.submitList(list)
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    ////////////////////////////////////////////////////////////////////////// */
    private inner class InstanceListAdapter internal constructor(context: Context?, private val itemTouchHelper: ItemTouchHelper?) : ListAdapter<PeertubeInstance?, InstanceListAdapter.TabViewHolder>(PeertubeInstanceCallback()) {
        private val inflater: LayoutInflater
        private var lastChecked: RadioButton? = null

        init {
            inflater = LayoutInflater.from(context)
        }

        fun swapItems(fromPosition: Int, toPosition: Int) {
            val list: ArrayList<PeertubeInstance?> = ArrayList(getCurrentList())
            Collections.swap(list, fromPosition, toPosition)
            submitList(list)
        }

        public override fun onCreateViewHolder(parent: ViewGroup,
                                               viewType: Int): TabViewHolder {
            return TabViewHolder(ItemInstanceBinding.inflate(inflater,
                    parent, false))
        }

        public override fun onBindViewHolder(holder: TabViewHolder,
                                             position: Int) {
            holder.bind(position)
        }

        internal inner class TabViewHolder(private val itemBinding: ItemInstanceBinding) : RecyclerView.ViewHolder(binding!!.getRoot()) {
            @SuppressLint("ClickableViewAccessibility")
            fun bind(position: Int) {
                itemBinding.handle.setOnTouchListener(OnTouchListener({ view: View?, motionEvent: MotionEvent ->
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (itemTouchHelper != null && getItemCount() > 1) {
                            itemTouchHelper.startDrag(this)
                            return@setOnTouchListener true
                        }
                    }
                    false
                }))
                val instance: PeertubeInstance? = getItem(position)
                itemBinding.instanceName.setText(instance!!.getName())
                itemBinding.instanceUrl.setText(instance.getUrl())
                itemBinding.selectInstanceRB.setOnCheckedChangeListener(null)
                if ((selectedInstance!!.getUrl() == instance.getUrl())) {
                    if (lastChecked != null && lastChecked !== itemBinding.selectInstanceRB) {
                        lastChecked!!.setChecked(false)
                    }
                    itemBinding.selectInstanceRB.setChecked(true)
                    lastChecked = itemBinding.selectInstanceRB
                }
                itemBinding.selectInstanceRB.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener({ buttonView: CompoundButton?, isChecked: Boolean ->
                    if (isChecked) {
                        selectInstance(instance)
                        if (lastChecked != null && lastChecked !== itemBinding.selectInstanceRB) {
                            lastChecked!!.setChecked(false)
                        }
                        lastChecked = itemBinding.selectInstanceRB
                    }
                }))
                itemBinding.instanceIcon.setImageResource(R.drawable.ic_placeholder_peertube)
            }
        }
    }

    private class PeertubeInstanceCallback() : DiffUtil.ItemCallback<PeertubeInstance>() {
        public override fun areItemsTheSame(oldItem: PeertubeInstance,
                                            newItem: PeertubeInstance): Boolean {
            return (oldItem.getUrl() == newItem.getUrl())
        }

        public override fun areContentsTheSame(oldItem: PeertubeInstance,
                                               newItem: PeertubeInstance): Boolean {
            return ((oldItem.getName() == newItem.getName()) && (oldItem.getUrl() == newItem.getUrl()))
        }
    }
}
