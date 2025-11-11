package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentBlockedChannelsBinding
import org.schabi.newpipe.util.BlockedChannelsManager

/**
 * Fragment to display and manage blocked channels
 */
class BlockedChannelsFragment : Fragment() {
    private var _binding: FragmentBlockedChannelsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BlockedChannelsAdapter
    private val blockedChannels = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlockedChannelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadBlockedChannels()
    }

    private fun setupRecyclerView() {
        adapter = BlockedChannelsAdapter(blockedChannels) { channelUrl ->
            showUnblockDialog(channelUrl)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun loadBlockedChannels() {
        blockedChannels.clear()
        blockedChannels.addAll(BlockedChannelsManager.getBlockedChannelsList(requireContext()))
        adapter.notifyDataSetChanged()

        updateEmptyView()
    }

    private fun updateEmptyView() {
        if (blockedChannels.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showUnblockDialog(channelUrl: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.unblock_channel)
            .setMessage(R.string.unblock_channel_confirmation)
            .setPositiveButton(R.string.unblock_channel) { _, _ ->
                unblockChannel(channelUrl)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun unblockChannel(channelUrl: String) {
        BlockedChannelsManager.unblockChannel(requireContext(), channelUrl)
        loadBlockedChannels()

        Snackbar.make(
            binding.root,
            R.string.channel_unblocked,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * RecyclerView adapter for blocked channels
     */
    private class BlockedChannelsAdapter(
        private val channels: List<String>,
        private val onUnblockClick: (String) -> Unit
    ) : RecyclerView.Adapter<BlockedChannelsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_channel, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val channelUrl = channels[position]
            holder.bind(channelUrl, onUnblockClick)
        }

        override fun getItemCount(): Int = channels.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val channelUrlText: android.widget.TextView = itemView.findViewById(R.id.channel_url)
            private val unblockButton: android.widget.Button = itemView.findViewById(R.id.unblock_button)

            fun bind(channelUrl: String, onUnblockClick: (String) -> Unit) {
                channelUrlText.text = channelUrl
                unblockButton.setOnClickListener {
                    onUnblockClick(channelUrl)
                }
            }
        }
    }
}
