package org.schabi.newpipe.cast.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastDeviceType
import org.schabi.newpipe.cast.model.CastPlayerState
import org.schabi.newpipe.cast.session.CastSession
import org.schabi.newpipe.cast.session.CastSessionManager
import org.schabi.newpipe.cast.session.CastStateListener
import org.schabi.newpipe.util.ThemeHelper

class CastDeviceBottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int {
        return ThemeHelper.getBottomSheetDialogTheme(requireContext())
    }

    private lateinit var castManager: CastSessionManager
    private val devices = mutableListOf<CastDevice>()
    private lateinit var adapter: DevicesAdapter

    private var activeSessionContainer: View? = null
    private var connectedDeviceName: TextView? = null
    private var disconnectButton: Button? = null
    private var volumeSeekBar: SeekBar? = null
    private var progressBar: ProgressBar? = null
    private var emptyView: TextView? = null

    private val discoveryListener: (List<CastDevice>) -> Unit = { list ->
        activity?.runOnUiThread {
            devices.clear()
            devices.addAll(list)
            adapter.notifyDataSetChanged()
            updateViews()
        }
    }

    private val sessionListener = object : CastStateListener {
        override fun onSessionStarted(session: CastSession) {
            activity?.runOnUiThread { updateViews() }
        }

        override fun onSessionEnded(session: CastSession, error: Throwable?) {
            activity?.runOnUiThread { updateViews() }
        }

        override fun onPlaybackStateChanged(state: CastPlayerState) {
            // No-op: bottom sheet only displays connection state
        }

        override fun onProgressUpdated(positionMs: Long, durationMs: Long) {
            // No-op: bottom sheet does not track stream progress
        }

        override fun onMediaCompleted() {
            // No-op: stream completion handled in PlayerUi
        }
        override fun onVolumeChanged(volume: Float) {
            activity?.runOnUiThread {
                volumeSeekBar?.progress = (volume * 100).toInt()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.cast_device_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        castManager = CastSessionManager.getInstance(requireContext())

        activeSessionContainer = view.findViewById(R.id.activeSessionContainer)
        connectedDeviceName = view.findViewById(R.id.connectedDeviceName)
        disconnectButton = view.findViewById(R.id.disconnectButton)
        volumeSeekBar = view.findViewById(R.id.volumeSeekBar)
        progressBar = view.findViewById(R.id.discoveryProgressBar)
        emptyView = view.findViewById(R.id.emptyDevicesText)

        val recyclerView = view.findViewById<RecyclerView>(R.id.devicesRecyclerView)
        adapter = DevicesAdapter(devices) { device ->
            castManager.connect(device)
            dismiss()
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        disconnectButton?.setOnClickListener {
            castManager.disconnect()
            updateViews()
        }

        volumeSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    castManager.currentSession?.setVolume(progress / 100f)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // No-op: volume update triggered on progress change
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // No-op: volume update triggered on progress change
            }
        })

        view.findViewById<View>(R.id.linkWithTvCodeButton).setOnClickListener {
            LinkWithTvCodeDialog.show(requireContext()) {
                dismiss()
            }
        }

        castManager.addListener(sessionListener)
        castManager.addDiscoveryListener(discoveryListener)
        castManager.startDiscovery()
        updateViews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        castManager.removeListener(sessionListener)
        castManager.removeDiscoveryListener(discoveryListener)
        castManager.stopDiscovery()
    }

    private fun updateViews() {
        val currentSession = castManager.currentSession
        if (currentSession != null && currentSession.isConnected()) {
            activeSessionContainer?.visibility = View.VISIBLE
            connectedDeviceName?.text = getString(R.string.casting_to, currentSession.device.name)
        } else {
            activeSessionContainer?.visibility = View.GONE
        }

        if (devices.isEmpty()) {
            emptyView?.visibility = View.VISIBLE
        } else {
            emptyView?.visibility = View.GONE
        }
    }

    private class DevicesAdapter(
        private val items: List<CastDevice>,
        private val onItemClick: (CastDevice) -> Unit
    ) : RecyclerView.Adapter<DevicesAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.deviceIcon)
            val name: TextView = v.findViewById(R.id.deviceName)
            val type: TextView = v.findViewById(R.id.deviceType)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cast_device, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.type.text = when (item.type) {
                CastDeviceType.CHROMECAST -> "Google Cast"
                CastDeviceType.YOUTUBE_LOUNGE -> "Smart TV"
            }
            holder.icon.setImageResource(
                if (item.type == CastDeviceType.CHROMECAST) R.drawable.ic_cast else R.drawable.ic_tv
            )
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
