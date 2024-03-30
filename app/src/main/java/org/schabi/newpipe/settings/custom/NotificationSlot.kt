package org.schabi.newpipe.settings.custom

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.TextViewCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ListRadioIconItemBinding
import org.schabi.newpipe.databinding.SingleChoiceDialogViewBinding
import org.schabi.newpipe.player.notification.NotificationConstants
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.FocusOverlayView
import java.util.Objects
import java.util.function.BiConsumer

internal class NotificationSlot(private val context: Context,
                                prefs: SharedPreferences?,
                                private val i: Int,
                                parentView: View,
                                isCompactSlotChecked: Boolean,
                                private val onToggleCompactSlot: BiConsumer<Int, CheckBox>) {
    @NotificationConstants.Action
    private var selectedAction: Int
    private var icon: ImageView? = null
    private var summary: TextView? = null

    init {
        selectedAction = Objects.requireNonNull(prefs).getInt(
                context.getString(NotificationConstants.SLOT_PREF_KEYS.get(i)),
                NotificationConstants.SLOT_DEFAULTS.get(i))
        val view: View = parentView.findViewById(SLOT_ITEMS.get(i))

        // only show the last two notification slots on Android 13+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || i >= 3) {
            setupSelectedAction(view)
            setupTitle(view)
            setupCheckbox(view, isCompactSlotChecked)
        } else {
            view.setVisibility(View.GONE)
        }
    }

    fun setupTitle(view: View) {
        (view.findViewById<View>(R.id.notificationActionTitle) as TextView)
                .setText(SLOT_TITLES.get(i))
        view.findViewById<View>(R.id.notificationActionClickableArea).setOnClickListener(
                View.OnClickListener({ v: View? -> openActionChooserDialog() }))
    }

    fun setupCheckbox(view: View, isCompactSlotChecked: Boolean) {
        val compactSlotCheckBox: CheckBox = view.findViewById(R.id.notificationActionCheckBox)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // there are no compact slots to customize on Android 13+
            compactSlotCheckBox.setVisibility(View.GONE)
            view.findViewById<View>(R.id.notificationActionCheckBoxClickableArea)
                    .setVisibility(View.GONE)
            return
        }
        compactSlotCheckBox.setChecked(isCompactSlotChecked)
        view.findViewById<View>(R.id.notificationActionCheckBoxClickableArea).setOnClickListener(
                View.OnClickListener({ v: View? -> onToggleCompactSlot.accept(i, compactSlotCheckBox) }))
    }

    fun setupSelectedAction(view: View) {
        icon = view.findViewById(R.id.notificationActionIcon)
        summary = view.findViewById(R.id.notificationActionSummary)
        updateInfo()
    }

    fun updateInfo() {
        if (NotificationConstants.ACTION_ICONS.get(selectedAction) == 0) {
            icon!!.setImageDrawable(null)
        } else {
            icon!!.setImageDrawable(AppCompatResources.getDrawable(context,
                    NotificationConstants.ACTION_ICONS.get(selectedAction)))
        }
        summary!!.setText(NotificationConstants.getActionName(context, selectedAction))
    }

    fun openActionChooserDialog() {
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val binding: SingleChoiceDialogViewBinding = SingleChoiceDialogViewBinding.inflate(inflater)
        val alertDialog: AlertDialog = AlertDialog.Builder(context)
                .setTitle(SLOT_TITLES.get(i))
                .setView(binding.getRoot())
                .setCancelable(true)
                .create()
        val radioButtonsClickListener: View.OnClickListener = View.OnClickListener({ v: View ->
            selectedAction = NotificationConstants.ALL_ACTIONS.get(v.getId())
            updateInfo()
            alertDialog.dismiss()
        })
        for (id in NotificationConstants.ALL_ACTIONS.indices) {
            val action: Int = NotificationConstants.ALL_ACTIONS.get(id)
            val radioButton: RadioButton = ListRadioIconItemBinding.inflate(inflater)
                    .getRoot()

            // if present set action icon with correct color
            val iconId: Int = NotificationConstants.ACTION_ICONS.get(action)
            if (iconId != 0) {
                radioButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, iconId, 0)
                val color: ColorStateList = ColorStateList.valueOf(ThemeHelper.resolveColorFromAttr(context, android.R.attr.textColorPrimary))
                TextViewCompat.setCompoundDrawableTintList(radioButton, color)
            }
            radioButton.setText(NotificationConstants.getActionName(context, action))
            radioButton.setChecked(action == selectedAction)
            radioButton.setId(id)
            radioButton.setLayoutParams(RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            radioButton.setOnClickListener(radioButtonsClickListener)
            binding.list.addView(radioButton)
        }
        alertDialog.show()
        if (DeviceUtils.isTv(context)) {
            FocusOverlayView.Companion.setupFocusObserver(alertDialog)
        }
    }

    @NotificationConstants.Action
    fun getSelectedAction(): Int {
        return selectedAction
    }

    companion object {
        private val SLOT_ITEMS: IntArray = intArrayOf(
                R.id.notificationAction0,
                R.id.notificationAction1,
                R.id.notificationAction2,
                R.id.notificationAction3,
                R.id.notificationAction4)
        private val SLOT_TITLES: IntArray = intArrayOf(
                R.string.notification_action_0_title,
                R.string.notification_action_1_title,
                R.string.notification_action_2_title,
                R.string.notification_action_3_title,
                R.string.notification_action_4_title)
    }
}
