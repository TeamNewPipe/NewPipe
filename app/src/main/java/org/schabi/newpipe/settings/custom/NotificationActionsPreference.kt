package org.schabi.newpipe.settings.custom

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.player.notification.NotificationConstants
import java.util.function.BiConsumer
import java.util.function.IntFunction
import java.util.stream.IntStream

class NotificationActionsPreference(context: Context?, attrs: AttributeSet?) : Preference((context)!!, attrs) {
    private var notificationSlots: Array<NotificationSlot>?
    private var compactSlots: MutableList<Int?>? = null

    init {
        setLayoutResource(R.layout.settings_notification)
    }

    ////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ////////////////////////////////////////////////////////////////////////////
    public override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (holder.itemView.findViewById<View>(R.id.summary) as TextView)
                    .setText(R.string.notification_actions_summary_android13)
        }
        holder.itemView.setClickable(false)
        setupActions(holder.itemView)
    }

    public override fun onDetached() {
        super.onDetached()
        saveChanges()
        // set package to this app's package to prevent the intent from being seen outside
        getContext().sendBroadcast(Intent(NotificationConstants.ACTION_RECREATE_NOTIFICATION)
                .setPackage(App.Companion.PACKAGE_NAME))
    }

    ////////////////////////////////////////////////////////////////////////////
    // Setup
    ////////////////////////////////////////////////////////////////////////////
    private fun setupActions(view: View) {
        compactSlots = ArrayList(NotificationConstants.getCompactSlotsFromPreferences(
                getContext(), getSharedPreferences()))
        notificationSlots = IntStream.range(0, 5)
                .mapToObj<NotificationSlot>(IntFunction<NotificationSlot>({ i: Int ->
                    NotificationSlot(getContext(), getSharedPreferences(), i, view,
                            compactSlots.contains(i), BiConsumer<Int, CheckBox>({ i: Int, checkBox: CheckBox -> onToggleCompactSlot(i, checkBox) }))
                }))
                .toArray<NotificationSlot>(IntFunction<Array<NotificationSlot>>({ _Dummy_.__Array__() }))
    }

    private fun onToggleCompactSlot(i: Int, checkBox: CheckBox) {
        if (checkBox.isChecked()) {
            compactSlots!!.remove(i as Int?)
        } else if (compactSlots!!.size < 3) {
            compactSlots!!.add(i)
        } else {
            Toast.makeText(getContext(),
                    R.string.notification_actions_at_most_three,
                    Toast.LENGTH_SHORT).show()
            return
        }
        checkBox.toggle()
    }

    ////////////////////////////////////////////////////////////////////////////
    // Saving
    ////////////////////////////////////////////////////////////////////////////
    private fun saveChanges() {
        if (compactSlots != null && notificationSlots != null) {
            val editor: SharedPreferences.Editor = getSharedPreferences()!!.edit()
            for (i in 0..2) {
                editor.putInt(getContext().getString(
                        NotificationConstants.SLOT_COMPACT_PREF_KEYS.get(i)),
                        ((if (i < compactSlots!!.size) compactSlots!!.get(i) else -1)!!))
            }
            for (i in 0..4) {
                editor.putInt(getContext().getString(NotificationConstants.SLOT_PREF_KEYS.get(i)),
                        notificationSlots!!.get(i).getSelectedAction())
            }
            editor.apply()
        }
    }
}
