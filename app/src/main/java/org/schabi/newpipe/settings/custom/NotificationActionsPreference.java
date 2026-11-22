package org.schabi.newpipe.settings.custom;

import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_RECREATE_NOTIFICATION;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.notification.NotificationConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class NotificationActionsPreference extends Preference {

    public NotificationActionsPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.settings_notification);
    }


    private NotificationSlot[] notificationSlots;
    private List<Integer> compactSlots;
    private NotificationActionPreview getViewHolderPreview;


    ////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ((TextView) holder.itemView.findViewById(R.id.summary))
                    .setText(R.string.notification_actions_summary_android13);
        }

        holder.itemView.setClickable(false);
        getViewHolderPreview = holder.itemView.findViewById(R.id.notification_actions_preview);
        setupActions(holder.itemView);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        saveChanges();
        getViewHolderPreview = null;
        // set package to this app's package to prevent the intent from being seen outside
        getContext().sendBroadcast(new Intent(ACTION_RECREATE_NOTIFICATION)
                .setPackage(App.PACKAGE_NAME));
    }


    ////////////////////////////////////////////////////////////////////////////
    // Setup
    ////////////////////////////////////////////////////////////////////////////

    private void setupActions(@NonNull final View view) {
        compactSlots = new ArrayList<>(NotificationConstants.getCompactSlotsFromPreferences(
                getContext(), getSharedPreferences()));
        notificationSlots = IntStream.range(0, 5)
                .mapToObj(i -> new NotificationSlot(getContext(), getSharedPreferences(), i, view,
                        compactSlots.contains(i), this::onToggleCompactSlot,
                        this::refreshPreview))
                .toArray(NotificationSlot[]::new);
        refreshPreview();
    }

    private void refreshPreview() {
        if (notificationSlots == null || getViewHolderPreview == null) {
            return;
        }
        final java.util.List<Integer> actions = new java.util.ArrayList<>();
        for (final NotificationSlot slot : notificationSlots) {
            actions.add(slot.getSelectedAction());
        }
        getViewHolderPreview.update(actions, compactSlots);
    }

    private void onToggleCompactSlot(final int i, final CheckBox checkBox) {
        if (checkBox.isChecked()) {
            compactSlots.remove((Integer) i);
        } else if (compactSlots.size() < 3) {
            compactSlots.add(i);
        } else {
            Toast.makeText(getContext(),
                    R.string.notification_actions_at_most_three,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        checkBox.toggle();
        refreshPreview();
    }


    ////////////////////////////////////////////////////////////////////////////
    // Saving
    ////////////////////////////////////////////////////////////////////////////

    private void saveChanges() {
        if (compactSlots != null && notificationSlots != null) {
            final SharedPreferences.Editor editor = getSharedPreferences().edit();

            for (int i = 0; i < 3; i++) {
                editor.putInt(getContext().getString(
                        NotificationConstants.SLOT_COMPACT_PREF_KEYS[i]),
                        (i < compactSlots.size() ? compactSlots.get(i) : -1));
            }

            for (int i = 0; i < 5; i++) {
                editor.putInt(getContext().getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                        notificationSlots[i].getSelectedAction());
            }

            editor.apply();
        }
    }
}
