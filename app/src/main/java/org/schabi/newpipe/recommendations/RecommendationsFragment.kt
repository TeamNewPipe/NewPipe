package org.schabi.newpipe.recommendations

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.function.Supplier
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.fragments.list.BaseListFragment
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ContentBlockingHelper
import org.schabi.newpipe.util.ServiceHelper

/** A home tab backed exclusively by the local candidate pool. */
class RecommendationsFragment : BaseListFragment<List<StreamInfoItem>, Void?>() {
    private var sourceItems: List<StreamInfoItem> = emptyList()
    private var selectedInterest: String? = null
    private var contentFilter = ContentFilter.ALL
    private var interestGroup: ChipGroup? = null
    private var interestLabel: TextView? = null
    private var interestScroll: HorizontalScrollView? = null
    private var filterEmpty: TextView? = null
    private var headerEnabled = true
    private var displayUpdatePosted = false

    override fun getContentBlockingTarget(): ContentBlockingHelper.Target = ContentBlockingHelper.Target.KIOSKS

    override fun showRecommendationReason(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedInterest = savedInstanceState?.getString(STATE_INTEREST)
        contentFilter = savedInstanceState?.getString(STATE_FILTER)
            ?.let { runCatching { ContentFilter.valueOf(it) }.getOrNull() }
            ?: ContentFilter.ALL
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_kiosk, container, false)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_INTEREST, selectedInterest)
        outState.putString(STATE_FILTER, contentFilter.name)
    }

    override fun onResume() {
        super.onResume()
        startLoading(false)
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        super.onSharedPreferenceChanged(preferences, key)
        if (view != null &&
            (IncognitoMode.KEY == key || ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY == key)
        ) {
            selectedInterest = null
            startLoading(false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.add(R.string.local_recommendations_title).setOnMenuItemClickListener {
            RecommendationControls.show(requireContext()) { startLoading(false) }
            true
        }
    }

    override fun getListHeaderSupplier(): Supplier<View> = Supplier {
        layoutInflater.inflate(R.layout.recommendations_header, itemsList, false).also(::bindHeader)
    }

    override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        handleResult(
            LocalRecommendations.feed(
                requireContext(),
                ServiceHelper.getSelectedServiceId(requireContext())
            )
        )
    }

    override fun handleResult(result: List<StreamInfoItem>) {
        super.handleResult(result)
        isLoading.set(false)
        setTitle(getString(R.string.local_recommendations_feed))
        sourceItems = result
        setHeaderEnabled(result.isNotEmpty())
        updateInterests()
        displayItems()
        showListFooter(false)
        if (result.isEmpty()) {
            showEmptyState()
            setEmptyStateMessage(R.string.local_recommendations_empty)
        } else {
            hideLoading()
        }
    }

    override fun loadMoreItems() = Unit

    override fun hasMoreItems(): Boolean = false

    override fun handleNextItems(result: Void?) = Unit

    private fun bindHeader(header: View) {
        interestGroup = header.findViewById(R.id.recommendation_interest_chips)
        interestLabel = header.findViewById(R.id.recommendation_interest_label)
        interestScroll = header.findViewById(R.id.recommendation_interest_scroll)
        filterEmpty = header.findViewById(R.id.recommendation_filter_empty)
        val filters = mapOf(
            R.id.recommendation_filter_all to ContentFilter.ALL,
            R.id.recommendation_filter_videos to ContentFilter.VIDEOS,
            R.id.recommendation_filter_shorts to ContentFilter.SHORTS,
            R.id.recommendation_filter_audio to ContentFilter.AUDIO
        )
        filters.forEach { (id, filter) ->
            header.findViewById<Chip>(id).apply {
                isChecked = contentFilter == filter
                setOnClickListener {
                    contentFilter = filter
                    displayItems()
                }
            }
        }
        updateInterests()
        updateFilterEmptyState(filteredItems())
    }

    private fun setHeaderEnabled(enabled: Boolean) {
        if (headerEnabled == enabled) return
        headerEnabled = enabled
        if (!enabled) {
            interestGroup = null
            interestLabel = null
            interestScroll = null
            filterEmpty = null
        }
        infoListAdapter.setHeaderSupplier(if (enabled) getListHeaderSupplier() else null)
    }

    private fun updateInterests() {
        val group = interestGroup ?: return
        val interests = LocalRecommendations.tasteSummary(requireContext()).interests
        if (selectedInterest !in interests) selectedInterest = null
        group.removeAllViews()
        interests.forEach { interest ->
            val chip = layoutInflater.inflate(
                R.layout.item_recommendation_interest_chip,
                group,
                false
            ) as Chip
            chip.text = interest
            chip.isChecked = selectedInterest == interest
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedInterest = interest
                } else if (selectedInterest == interest) {
                    selectedInterest = null
                }
                displayItems()
            }
            group.addView(chip)
        }
        val visible = interests.isNotEmpty()
        interestLabel?.isVisible = visible
        interestScroll?.isVisible = visible
    }

    private fun displayItems() {
        if (infoListAdapter == null) return
        if (itemsList.isComputingLayout) {
            if (!displayUpdatePosted) {
                displayUpdatePosted = true
                itemsList.post {
                    displayUpdatePosted = false
                    if (view != null) displayItems()
                }
            }
            return
        }

        val shown = filteredItems()
        infoListAdapter.clearStreamItemList()
        infoListAdapter.addInfoItemList(shown)
        updateFilterEmptyState(shown)
    }

    private fun filteredItems(): List<StreamInfoItem> = sourceItems.filter { item ->
        (
            selectedInterest == null ||
                LocalRecommendations.matchesInterest(item, requireNotNull(selectedInterest))
            ) &&
            contentFilter.matches(item)
    }

    private fun updateFilterEmptyState(shown: List<StreamInfoItem>) {
        filterEmpty?.isVisible = sourceItems.isNotEmpty() && shown.isEmpty()
    }

    private enum class ContentFilter {
        ALL,
        VIDEOS,
        SHORTS,
        AUDIO;

        fun matches(item: StreamInfoItem): Boolean = when (this) {
            ALL -> true
            VIDEOS -> item.streamType == StreamType.VIDEO_STREAM && !item.isShortFormContent
            SHORTS -> item.isShortFormContent
            AUDIO -> item.streamType == StreamType.AUDIO_STREAM
        }
    }

    private companion object {
        const val STATE_INTEREST = "recommendation_interest"
        const val STATE_FILTER = "recommendation_filter"
    }
}
