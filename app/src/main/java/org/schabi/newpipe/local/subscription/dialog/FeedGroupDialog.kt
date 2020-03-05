package org.schabi.newpipe.local.subscription.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Section
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import icepick.Icepick
import icepick.State
import kotlinx.android.synthetic.main.dialog_feed_group_create.*
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialogViewModel.DialogEvent.*
import org.schabi.newpipe.local.subscription.item.EmptyPlaceholderItem
import org.schabi.newpipe.local.subscription.item.PickerIconItem
import org.schabi.newpipe.local.subscription.item.PickerSubscriptionItem
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.ThemeHelper
import java.io.Serializable

class FeedGroupDialog : DialogFragment() {
    private lateinit var viewModel: FeedGroupDialogViewModel
    private var groupId: Long = NO_GROUP_SELECTED
    private var groupIcon: FeedGroupIcon? = null
    private var groupSortOrder: Long = -1

    sealed class ScreenState : Serializable {
        object InitialScreen : ScreenState()
        object SubscriptionsPicker : ScreenState()
        object IconPickerList : ScreenState()
    }

    @State @JvmField var selectedIcon: FeedGroupIcon? = null
    @State @JvmField var selectedSubscriptions: HashSet<Long> = HashSet()
    @State @JvmField var currentScreen: ScreenState = ScreenState.InitialScreen

    @State @JvmField var subscriptionsListState: Parcelable? = null
    @State @JvmField var iconsListState: Parcelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Icepick.restoreInstanceState(this, savedInstanceState)

        setStyle(STYLE_NO_TITLE, ThemeHelper.getMinWidthDialogTheme(requireContext()))
        groupId = arguments?.getLong(KEY_GROUP_ID, NO_GROUP_SELECTED) ?: NO_GROUP_SELECTED
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_feed_group_create, container)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                if (currentScreen !is ScreenState.InitialScreen) {
                    showInitialScreen()
                } else {
                    super.onBackPressed()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        iconsListState = icon_selector.layoutManager?.onSaveInstanceState()
        subscriptionsListState = subscriptions_selector_list.layoutManager?.onSaveInstanceState()

        Icepick.saveInstanceState(this, outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProviders.of(this, FeedGroupDialogViewModel.Factory(requireContext(), groupId))
                .get(FeedGroupDialogViewModel::class.java)

        viewModel.groupLiveData.observe(viewLifecycleOwner, Observer(::handleGroup))
        viewModel.subscriptionsLiveData.observe(viewLifecycleOwner, Observer { setupSubscriptionPicker(it.first, it.second) })
        viewModel.dialogEventLiveData.observe(viewLifecycleOwner, Observer {
            when (it) {
                ProcessingEvent -> disableInput()
                SuccessEvent -> dismiss()
            }
        })

        setupIconPicker()

        delete_button.setOnClickListener { viewModel.deleteGroup() }

        cancel_button.setOnClickListener {
            if (currentScreen !is ScreenState.InitialScreen) {
                showInitialScreen()
            } else {
                dismiss()
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

        confirm_button.setOnClickListener {
            if (currentScreen is ScreenState.InitialScreen) {
                val name = group_name_input.text.toString().trim()
                val icon = selectedIcon ?: groupIcon ?: FeedGroupIcon.ALL

                if (name.isBlank()) {
                    group_name_input_container.error = getString(R.string.feed_group_dialog_empty_name)
                    group_name_input.text = null
                    group_name_input.requestFocus()
                    return@setOnClickListener
                } else {
                    group_name_input_container.error = null
                }

                if (selectedSubscriptions.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.feed_group_dialog_empty_selection), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                when (groupId) {
                    NO_GROUP_SELECTED -> viewModel.createGroup(name, icon, selectedSubscriptions)
                    else -> viewModel.updateGroup(name, icon, selectedSubscriptions, groupSortOrder)
                }
            } else {
                showInitialScreen()
            }
        }

        when (currentScreen) {
            is ScreenState.InitialScreen -> showInitialScreen()
            is ScreenState.IconPickerList -> showIconPicker()
            is ScreenState.SubscriptionsPicker -> showSubscriptionsPicker()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Setup
    ///////////////////////////////////////////////////////////////////////////

    private fun handleGroup(feedGroupEntity: FeedGroupEntity? = null) {
        val icon = feedGroupEntity?.icon ?: FeedGroupIcon.ALL
        val name = feedGroupEntity?.name ?: ""
        groupIcon = feedGroupEntity?.icon
        groupSortOrder = feedGroupEntity?.sortOrder ?: -1

        icon_preview.setImageResource((if (selectedIcon == null) icon else selectedIcon!!).getDrawableRes(requireContext()))

        if (group_name_input.text.isNullOrBlank()) {
            group_name_input.setText(name)
        }
    }

    private fun setupSubscriptionPicker(subscriptions: List<SubscriptionEntity>, selectedSubscriptions: Set<Long>) {
        this.selectedSubscriptions.addAll(selectedSubscriptions)
        val useGridLayout = subscriptions.isNotEmpty()

        val groupAdapter = GroupAdapter<GroupieViewHolder>()
        groupAdapter.spanCount = if (useGridLayout) 4 else 1

        val selectedCountText = getString(R.string.feed_group_dialog_selection_count, this.selectedSubscriptions.size)
        selected_subscription_count_view.text = selectedCountText
        subscriptions_selector_header_info.text = selectedCountText

        Section().apply {
            addAll(subscriptions.map {
                val isSelected = this@FeedGroupDialog.selectedSubscriptions.contains(it.uid)
                PickerSubscriptionItem(it, isSelected)
            })
            setPlaceholder(EmptyPlaceholderItem())

            groupAdapter.add(this)
        }

        subscriptions_selector_list.apply {
            layoutManager = if (useGridLayout) {
                GridLayoutManager(requireContext(), groupAdapter.spanCount, RecyclerView.VERTICAL, false)
            } else {
                LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            }

            adapter = groupAdapter

            if (subscriptionsListState != null) {
                layoutManager?.onRestoreInstanceState(subscriptionsListState)
                subscriptionsListState = null
            }
        }

        groupAdapter.setOnItemClickListener { item, _ ->
            when (item) {
                is PickerSubscriptionItem -> {
                    val subscriptionId = item.subscriptionEntity.uid

                    val isSelected = if (this.selectedSubscriptions.contains(subscriptionId)) {
                        this.selectedSubscriptions.remove(subscriptionId)
                        false
                    } else {
                        this.selectedSubscriptions.add(subscriptionId)
                        true
                    }

                    item.isSelected = isSelected
                    item.notifyChanged(PickerSubscriptionItem.UPDATE_SELECTED)

                    val updateSelectedCountText = getString(R.string.feed_group_dialog_selection_count, this.selectedSubscriptions.size)
                    selected_subscription_count_view.text = updateSelectedCountText
                    subscriptions_selector_header_info.text = updateSelectedCountText
                }
            }
        }

        select_channel_button.setOnClickListener {
            subscriptions_selector_list.scrollToPosition(0)
            showSubscriptionsPicker()
        }
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

                    showInitialScreen()
                }
            }
        }
        icon_preview.setOnClickListener {
            icon_selector.scrollToPosition(0)
            showIconPicker()
        }

        if (groupId == NO_GROUP_SELECTED) {
            val icon = selectedIcon ?: FeedGroupIcon.ALL
            icon_preview.setImageResource(icon.getDrawableRes(requireContext()))
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Screen Selector
    ///////////////////////////////////////////////////////////////////////////

    private fun showInitialScreen() {
        currentScreen = ScreenState.InitialScreen
        animateView(icon_selector, false, 0)
        animateView(subscriptions_selector, false, 0)
        animateView(options_root, true, 250)

        separator.visibility = View.GONE
        confirm_button.setText(if (groupId == NO_GROUP_SELECTED) R.string.create else android.R.string.ok)
        delete_button.visibility = if (groupId == NO_GROUP_SELECTED) View.GONE else View.VISIBLE
        cancel_button.visibility = View.VISIBLE
    }

    private fun showIconPicker() {
        currentScreen = ScreenState.IconPickerList
        animateView(icon_selector, true, 250)
        animateView(subscriptions_selector, false, 0)
        animateView(options_root, false, 0)

        separator.visibility = View.VISIBLE
        confirm_button.setText(android.R.string.ok)
        delete_button.visibility = View.GONE
        cancel_button.visibility = View.GONE

        hideKeyboard()
    }

    private fun showSubscriptionsPicker() {
        currentScreen = ScreenState.SubscriptionsPicker
        animateView(icon_selector, false, 0)
        animateView(subscriptions_selector, true, 250)
        animateView(options_root, false, 0)

        separator.visibility = View.VISIBLE
        confirm_button.setText(android.R.string.ok)
        delete_button.visibility = View.GONE
        cancel_button.visibility = View.GONE

        hideKeyboard()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun hideKeyboard() {
        val inputMethodManager = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(group_name_input.windowToken, InputMethodManager.RESULT_UNCHANGED_SHOWN)
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