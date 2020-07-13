package org.schabi.newpipe.local.subscription.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.Section
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import icepick.Icepick
import icepick.State
import java.io.Serializable
import kotlin.collections.contains
import kotlinx.android.synthetic.main.dialog_feed_group_create.*
import kotlinx.android.synthetic.main.toolbar_search_layout.*
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.DeleteScreen
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.IconPickerScreen
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.InitialScreen
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.SubscriptionsPickerScreen
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialogViewModel.DialogEvent.ProcessingEvent
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialogViewModel.DialogEvent.SuccessEvent
import org.schabi.newpipe.local.subscription.item.EmptyPlaceholderItem
import org.schabi.newpipe.local.subscription.item.PickerIconItem
import org.schabi.newpipe.local.subscription.item.PickerSubscriptionItem
import org.schabi.newpipe.util.AndroidTvUtils
import org.schabi.newpipe.util.ThemeHelper

class FeedGroupDialog : DialogFragment(), BackPressable {
    private lateinit var viewModel: FeedGroupDialogViewModel
    private var groupId: Long = NO_GROUP_SELECTED
    private var groupIcon: FeedGroupIcon? = null
    private var groupSortOrder: Long = -1

    sealed class ScreenState : Serializable {
        object InitialScreen : ScreenState()
        object IconPickerScreen : ScreenState()
        object SubscriptionsPickerScreen : ScreenState()
        object DeleteScreen : ScreenState()
    }

    @State @JvmField var selectedIcon: FeedGroupIcon? = null
    @State @JvmField var selectedSubscriptions: HashSet<Long> = HashSet()
    @State @JvmField var wasSubscriptionSelectionChanged: Boolean = false
    @State @JvmField var currentScreen: ScreenState = InitialScreen

    @State @JvmField var subscriptionsListState: Parcelable? = null
    @State @JvmField var iconsListState: Parcelable? = null
    @State @JvmField var wasSearchSubscriptionsVisible = false
    @State @JvmField var subscriptionsCurrentSearchQuery = ""
    @State @JvmField var subscriptionsShowOnlyUngrouped = false

    private val subscriptionMainSection = Section()
    private val subscriptionEmptyFooter = Section()
    private lateinit var subscriptionGroupAdapter: GroupAdapter<GroupieViewHolder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Icepick.restoreInstanceState(this, savedInstanceState)

        setStyle(STYLE_NO_TITLE, ThemeHelper.getMinWidthDialogTheme(requireContext()))
        groupId = arguments?.getLong(KEY_GROUP_ID, NO_GROUP_SELECTED) ?: NO_GROUP_SELECTED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_feed_group_create, container)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                if (!this@FeedGroupDialog.onBackPressed()) {
                    super.onBackPressed()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        wasSearchSubscriptionsVisible = isSearchVisible()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        iconsListState = icon_selector.layoutManager?.onSaveInstanceState()
        subscriptionsListState = subscriptions_selector_list.layoutManager?.onSaveInstanceState()

        Icepick.saveInstanceState(this, outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this,
            FeedGroupDialogViewModel.Factory(requireContext(),
                groupId, subscriptionsCurrentSearchQuery, subscriptionsShowOnlyUngrouped)
        ).get(FeedGroupDialogViewModel::class.java)

        viewModel.groupLiveData.observe(viewLifecycleOwner, Observer(::handleGroup))
        viewModel.subscriptionsLiveData.observe(viewLifecycleOwner, Observer {
            setupSubscriptionPicker(it.first, it.second)
        })
        viewModel.dialogEventLiveData.observe(viewLifecycleOwner, Observer {
            when (it) {
                ProcessingEvent -> disableInput()
                SuccessEvent -> dismiss()
            }
        })

        subscriptionGroupAdapter = GroupAdapter<GroupieViewHolder>().apply {
            add(subscriptionMainSection)
            add(subscriptionEmptyFooter)
            spanCount = 4
        }
        subscriptions_selector_list.apply {
            // Disable animations, too distracting.
            itemAnimator = null
            adapter = subscriptionGroupAdapter
            layoutManager = GridLayoutManager(requireContext(), subscriptionGroupAdapter.spanCount,
                RecyclerView.VERTICAL, false).apply {
                spanSizeLookup = subscriptionGroupAdapter.spanSizeLookup
            }
        }

        setupIconPicker()
        setupListeners()

        showScreen(currentScreen)

        if (currentScreen == SubscriptionsPickerScreen && wasSearchSubscriptionsVisible) {
            showSearch()
        } else if (currentScreen == InitialScreen && groupId == NO_GROUP_SELECTED) {
            showKeyboard()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        subscriptions_selector_list?.adapter = null
        icon_selector?.adapter = null
    }

    /*/​//////////////////////////////////////////////////////////////////////////
    // Setup
    //​//////////////////////////////////////////////////////////////////////// */

    override fun onBackPressed(): Boolean {
        if (currentScreen is SubscriptionsPickerScreen && isSearchVisible()) {
            hideSearch()
            return true
        } else if (currentScreen !is InitialScreen) {
            showScreen(InitialScreen)
            return true
        }

        return false
    }

    private fun setupListeners() {
        delete_button.setOnClickListener { showScreen(DeleteScreen) }

        cancel_button.setOnClickListener {
            when (currentScreen) {
                InitialScreen -> dismiss()
                else -> showScreen(InitialScreen)
            }
        }

        group_name_input_container.error = null
        group_name_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (group_name_input_container.isErrorEnabled && !s.isNullOrBlank()) {
                    group_name_input_container.error = null
                }
            }
        })

        confirm_button.setOnClickListener { handlePositiveButton() }

        select_channel_button.setOnClickListener {
            subscriptions_selector_list.scrollToPosition(0)
            showScreen(SubscriptionsPickerScreen)
        }

        val headerMenu = subscriptions_header_toolbar.menu
        requireActivity().menuInflater.inflate(R.menu.menu_feed_group_dialog, headerMenu)

        headerMenu.findItem(R.id.action_search).setOnMenuItemClickListener {
            showSearch()
            true
        }

        headerMenu.findItem(R.id.feed_group_toggle_show_only_ungrouped_subscriptions).apply {
            isChecked = subscriptionsShowOnlyUngrouped
            setOnMenuItemClickListener {
                subscriptionsShowOnlyUngrouped = !subscriptionsShowOnlyUngrouped
                it.isChecked = subscriptionsShowOnlyUngrouped
                viewModel.toggleShowOnlyUngrouped(subscriptionsShowOnlyUngrouped)
                true
            }
        }

        toolbar_search_clear.setOnClickListener {
            if (TextUtils.isEmpty(toolbar_search_edit_text.text)) {
                hideSearch()
                return@setOnClickListener
            }
            resetSearch()
            showKeyboardSearch()
        }

        toolbar_search_edit_text.setOnClickListener {
            if (AndroidTvUtils.isTv(context)) {
                showKeyboardSearch()
            }
        }

        toolbar_search_edit_text.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val newQuery: String = toolbar_search_edit_text.text.toString()
                subscriptionsCurrentSearchQuery = newQuery
                viewModel.filterSubscriptionsBy(newQuery)
            }
        })

        subscriptionGroupAdapter?.setOnItemClickListener(subscriptionPickerItemListener)
    }

    private fun handlePositiveButton() = when {
        currentScreen is InitialScreen -> handlePositiveButtonInitialScreen()
        currentScreen is DeleteScreen -> viewModel.deleteGroup()
        currentScreen is SubscriptionsPickerScreen && isSearchVisible() -> hideSearch()
        else -> showScreen(InitialScreen)
    }

    private fun handlePositiveButtonInitialScreen() {
        val name = group_name_input.text.toString().trim()
        val icon = selectedIcon ?: groupIcon ?: FeedGroupIcon.ALL

        if (name.isBlank()) {
            group_name_input_container.error = getString(R.string.feed_group_dialog_empty_name)
            group_name_input.text = null
            group_name_input.requestFocus()
            return
        } else {
            group_name_input_container.error = null
        }

        if (selectedSubscriptions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.feed_group_dialog_empty_selection), Toast.LENGTH_SHORT).show()
            return
        }

        when (groupId) {
            NO_GROUP_SELECTED -> viewModel.createGroup(name, icon, selectedSubscriptions)
            else -> viewModel.updateGroup(name, icon, selectedSubscriptions, groupSortOrder)
        }
    }

    private fun handleGroup(feedGroupEntity: FeedGroupEntity? = null) {
        val icon = feedGroupEntity?.icon ?: FeedGroupIcon.ALL
        val name = feedGroupEntity?.name ?: ""
        groupIcon = feedGroupEntity?.icon
        groupSortOrder = feedGroupEntity?.sortOrder ?: -1

        val feedGroupIcon = if (selectedIcon == null) icon else selectedIcon!!
        icon_preview.setImageResource(feedGroupIcon.getDrawableRes(requireContext()))

        if (group_name_input.text.isNullOrBlank()) {
            group_name_input.setText(name)
        }
    }

    private val subscriptionPickerItemListener = OnItemClickListener { item, view ->
        if (item is PickerSubscriptionItem) {
            val subscriptionId = item.subscriptionEntity.uid
            wasSubscriptionSelectionChanged = true

            val isSelected = if (this.selectedSubscriptions.contains(subscriptionId)) {
                this.selectedSubscriptions.remove(subscriptionId)
                false
            } else {
                this.selectedSubscriptions.add(subscriptionId)
                true
            }

            item.updateSelected(view, isSelected)
            updateSubscriptionSelectedCount()
        }
    }

    private fun setupSubscriptionPicker(
        subscriptions: List<PickerSubscriptionItem>,
        selectedSubscriptions: Set<Long>
    ) {
        if (!wasSubscriptionSelectionChanged) {
            this.selectedSubscriptions.addAll(selectedSubscriptions)
        }

        updateSubscriptionSelectedCount()

        if (subscriptions.isEmpty()) {
            subscriptionEmptyFooter.clear()
            subscriptionEmptyFooter.add(EmptyPlaceholderItem())
        } else {
            subscriptionEmptyFooter.clear()
        }

        subscriptions.forEach {
            it.isSelected = this@FeedGroupDialog.selectedSubscriptions
                .contains(it.subscriptionEntity.uid)
        }

        subscriptionMainSection.update(subscriptions, false)

        if (subscriptionsListState != null) {
            subscriptions_selector_list.layoutManager?.onRestoreInstanceState(subscriptionsListState)
            subscriptionsListState = null
        } else {
            subscriptions_selector_list.scrollToPosition(0)
        }
    }

    private fun updateSubscriptionSelectedCount() {
        val selectedCount = this.selectedSubscriptions.size
        val selectedCountText = resources.getQuantityString(
            R.plurals.feed_group_dialog_selection_count,
            selectedCount, selectedCount)
        selected_subscription_count_view.text = selectedCountText
        subscriptions_header_info.text = selectedCountText
    }

    private fun setupIconPicker() {
        val groupAdapter = GroupAdapter<GroupieViewHolder>()
        groupAdapter.addAll(FeedGroupIcon.values().map { PickerIconItem(requireContext(), it) })

        icon_selector.apply {
            layoutManager = GridLayoutManager(requireContext(), 7, RecyclerView.VERTICAL, false)
            adapter = groupAdapter

            if (iconsListState != null) {
                layoutManager?.onRestoreInstanceState(iconsListState)
                iconsListState = null
            }
        }

        groupAdapter.setOnItemClickListener { item, _ ->
            when (item) {
                is PickerIconItem -> {
                    selectedIcon = item.icon
                    icon_preview.setImageResource(item.iconRes)

                    showScreen(InitialScreen)
                }
            }
        }
        icon_preview.setOnClickListener {
            icon_selector.scrollToPosition(0)
            showScreen(IconPickerScreen)
        }

        if (groupId == NO_GROUP_SELECTED) {
            val icon = selectedIcon ?: FeedGroupIcon.ALL
            icon_preview.setImageResource(icon.getDrawableRes(requireContext()))
        }
    }

    /*/​//////////////////////////////////////////////////////////////////////////
    // Screen Selector
    //​//////////////////////////////////////////////////////////////////////// */

    private fun showScreen(screen: ScreenState) {
        currentScreen = screen

        options_root.onlyVisibleIn(InitialScreen)
        icon_selector.onlyVisibleIn(IconPickerScreen)
        subscriptions_selector.onlyVisibleIn(SubscriptionsPickerScreen)
        delete_screen_message.onlyVisibleIn(DeleteScreen)

        separator.onlyVisibleIn(SubscriptionsPickerScreen, IconPickerScreen)
        cancel_button.onlyVisibleIn(InitialScreen, DeleteScreen)

        confirm_button.setText(when {
            currentScreen == InitialScreen && groupId == NO_GROUP_SELECTED -> R.string.create
            else -> android.R.string.ok
        })

        delete_button.visibility = when {
            currentScreen != InitialScreen -> View.GONE
            groupId == NO_GROUP_SELECTED -> View.GONE
            else -> View.VISIBLE
        }

        hideKeyboard()
        hideSearch()
    }

    private fun View.onlyVisibleIn(vararg screens: ScreenState) {
        visibility = when (currentScreen) {
            in screens -> View.VISIBLE
            else -> View.GONE
        }
    }

    /*/​//////////////////////////////////////////////////////////////////////////
    // Utils
    //​//////////////////////////////////////////////////////////////////////// */

    private fun isSearchVisible() = subscriptions_header_search_container?.visibility == View.VISIBLE

    private fun resetSearch() {
        toolbar_search_edit_text.setText("")
        subscriptionsCurrentSearchQuery = ""
        viewModel.clearSubscriptionsFilter()
    }

    private fun hideSearch() {
        resetSearch()
        subscriptions_header_search_container.visibility = View.GONE
        subscriptions_header_info_container.visibility = View.VISIBLE
        subscriptions_header_toolbar.menu.findItem(R.id.action_search).isVisible = true
        hideKeyboardSearch()
    }

    private fun showSearch() {
        subscriptions_header_search_container.visibility = View.VISIBLE
        subscriptions_header_info_container.visibility = View.GONE
        subscriptions_header_toolbar.menu.findItem(R.id.action_search).isVisible = false
        showKeyboardSearch()
    }

    private val inputMethodManager by lazy {
        requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    private fun showKeyboardSearch() {
        if (toolbar_search_edit_text.requestFocus()) {
            inputMethodManager.showSoftInput(toolbar_search_edit_text, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboardSearch() {
        inputMethodManager.hideSoftInputFromWindow(toolbar_search_edit_text.windowToken,
            InputMethodManager.RESULT_UNCHANGED_SHOWN)
        toolbar_search_edit_text.clearFocus()
    }

    private fun showKeyboard() {
        if (group_name_input.requestFocus()) {
            inputMethodManager.showSoftInput(group_name_input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(group_name_input.windowToken,
            InputMethodManager.RESULT_UNCHANGED_SHOWN)
        group_name_input.clearFocus()
    }

    private fun disableInput() {
        delete_button?.isEnabled = false
        confirm_button?.isEnabled = false
        cancel_button?.isEnabled = false
        isCancelable = false

        hideKeyboard()
    }

    companion object {
        private const val KEY_GROUP_ID = "KEY_GROUP_ID"
        private const val NO_GROUP_SELECTED = -1L

        fun newInstance(groupId: Long = NO_GROUP_SELECTED): FeedGroupDialog {
            val dialog = FeedGroupDialog()

            dialog.arguments = Bundle().apply {
                putLong(KEY_GROUP_ID, groupId)
            }

            return dialog
        }
    }
}
