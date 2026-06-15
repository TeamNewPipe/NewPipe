/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentBlockedChannelsBinding
import org.schabi.newpipe.util.ThemeHelper

class BlockedChannelsFragment : Fragment() {
    private var _binding: FragmentBlockedChannelsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BlockedChannelsViewModel by viewModels()

    private lateinit var adapter: BlockedChannelsAdapter

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

        adapter = BlockedChannelsAdapter(viewModel::unblockChannel)
        binding.blockedChannelsList.layoutManager = LinearLayoutManager(requireContext())
        binding.blockedChannelsList.adapter = adapter

        viewModel.blockedChannelsLiveData.observe(viewLifecycleOwner) { blockedChannels ->
            adapter.submitList(blockedChannels)
            binding.emptyState.isVisible = blockedChannels.isEmpty()
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.blocked_channels))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
