package org.schabi.newpipe.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.NotificationConstants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.FocusOverlayView;

import java.util.List;

public class NotificationSettingsFragment extends Fragment {

    private Switch scaleSwitch;
    private NotificationSlot[] notificationSlots;

    private SharedPreferences pref;
    private List<Integer> compactSlots;
    private String scaleKey;

    ////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = PreferenceManager.getDefaultSharedPreferences(requireContext());
        scaleKey = getString(R.string.scale_to_square_image_in_notifications_key);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings_notification, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        setupScaleSwitch(rootView);
        setupActions(rootView);
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(),
                getString(R.string.settings_category_notification_title));
    }

    @Override
    public void onPause() {
        super.onPause();
        saveChanges();
        requireContext().sendBroadcast(new Intent(MainPlayer.ACTION_RECREATE_NOTIFICATION));
    }


    ////////////////////////////////////////////////////////////////////////////
    // Setup
    ////////////////////////////////////////////////////////////////////////////

    private void setupScaleSwitch(@NonNull final View view) {
        scaleSwitch = view.findViewById(R.id.notificationScaleSwitch);
        scaleSwitch.setChecked(pref.getBoolean(scaleKey, false));

        view.findViewById(R.id.notificationScaleSwitchClickableArea)
                .setOnClickListener(v -> scaleSwitch.toggle());
    }

    private void setupActions(@NonNull final View view) {
        compactSlots =
                NotificationConstants.getCompactSlotsFromPreferences(requireContext(), pref, 5);
        notificationSlots = new NotificationSlot[5];
        for (int i = 0; i < 5; i++) {
            notificationSlots[i] = new NotificationSlot(i, view);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    // Saving
    ////////////////////////////////////////////////////////////////////////////

    private void saveChanges() {
        final SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(scaleKey, scaleSwitch.isChecked());

        for (int i = 0; i < 3; i++) {
            editor.putInt(getString(NotificationConstants.SLOT_COMPACT_PREF_KEYS[i]),
                    (i < compactSlots.size() ? compactSlots.get(i) : -1));
        }

        for (int i = 0; i < 5; i++) {
            editor.putInt(getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                    notificationSlots[i].selectedAction);
        }

        editor.apply();
    }


    ////////////////////////////////////////////////////////////////////////////
    // Notification action
    ////////////////////////////////////////////////////////////////////////////

    private static final int[] SLOT_ITEMS = {
            R.id.notificationAction0,
            R.id.notificationAction1,
            R.id.notificationAction2,
            R.id.notificationAction3,
            R.id.notificationAction4,
    };

    private static final int[] SLOT_TITLES = {
            R.string.notification_action_0_title,
            R.string.notification_action_1_title,
            R.string.notification_action_2_title,
            R.string.notification_action_3_title,
            R.string.notification_action_4_title,
    };

    private class NotificationSlot {

        final int i;
        @NotificationConstants.Action int selectedAction;

        ImageView icon;
        TextView summary;

        NotificationSlot(final int actionIndex, final View parentView) {
            this.i = actionIndex;

            final View view = parentView.findViewById(SLOT_ITEMS[i]);
            setupSelectedAction(view);
            setupTitle(view);
            setupCheckbox(view);
        }

        void setupTitle(final View view) {
            ((TextView) view.findViewById(R.id.notificationActionTitle))
                    .setText(SLOT_TITLES[i]);
            view.findViewById(R.id.notificationActionClickableArea).setOnClickListener(
                    v -> openActionChooserDialog());
        }

        void setupCheckbox(final View view) {
            final CheckBox compactSlotCheckBox = view.findViewById(R.id.notificationActionCheckBox);
            compactSlotCheckBox.setChecked(compactSlots.contains(i));
            view.findViewById(R.id.notificationActionCheckBoxClickableArea).setOnClickListener(
                    v -> {
                        if (compactSlotCheckBox.isChecked()) {
                            compactSlots.remove((Integer) i);
                        } else if (compactSlots.size() < 3) {
                            compactSlots.add(i);
                        } else {
                            Toast.makeText(requireContext(),
                                    R.string.notification_actions_at_most_three,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        compactSlotCheckBox.toggle();
                    });
        }

        void setupSelectedAction(final View view) {
            icon = view.findViewById(R.id.notificationActionIcon);
            summary = view.findViewById(R.id.notificationActionSummary);
            selectedAction = pref.getInt(getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                    NotificationConstants.SLOT_DEFAULTS[i]);
            updateInfo();
        }

        void updateInfo() {
            if (NotificationConstants.ACTION_ICONS[selectedAction] == 0) {
                icon.setImageDrawable(null);
            } else {
                icon.setImageDrawable(AppCompatResources.getDrawable(requireContext(),
                        NotificationConstants.ACTION_ICONS[selectedAction]));
            }

            summary.setText(NotificationConstants.ACTION_SUMMARIES[selectedAction]);
        }

        void openActionChooserDialog() {
            final LayoutInflater inflater = LayoutInflater.from(requireContext());
            final LinearLayout rootLayout = (LinearLayout) inflater.inflate(
                    R.layout.single_choice_dialog_view, null, false);
            final RadioGroup radioGroup = rootLayout.findViewById(android.R.id.list);

            final AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
                    .setTitle(SLOT_TITLES[i])
                    .setView(radioGroup)
                    .setCancelable(true)
                    .create();

            final View.OnClickListener radioButtonsClickListener = v -> {
                final int id = ((RadioButton) v).getId();
                selectedAction = NotificationConstants.SLOT_ALLOWED_ACTIONS[i][id];
                updateInfo();
                alertDialog.dismiss();
            };

            for (int id = 0; id < NotificationConstants.SLOT_ALLOWED_ACTIONS[i].length; ++id) {
                final int action = NotificationConstants.SLOT_ALLOWED_ACTIONS[i][id];
                final RadioButton radioButton
                        = (RadioButton) inflater.inflate(R.layout.list_radio_icon_item, null);

                // if present set action icon with correct color
                if (NotificationConstants.ACTION_ICONS[action] != 0) {
                    final Drawable drawable = AppCompatResources.getDrawable(requireContext(),
                            NotificationConstants.ACTION_ICONS[action]);
                    if (drawable != null) {
                        final int color = ThemeHelper.resolveColorFromAttr(requireContext(),
                                android.R.attr.textColorPrimary);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            drawable.setTint(color);
                        } else {
                            drawable.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                        }
                        radioButton.setCompoundDrawablesWithIntrinsicBounds(
                                null, null, drawable, null);
                    }
                }

                radioButton.setText(NotificationConstants.ACTION_SUMMARIES[action]);
                radioButton.setChecked(action == selectedAction);
                radioButton.setId(id);
                radioButton.setLayoutParams(new RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                radioButton.setOnClickListener(radioButtonsClickListener);
                radioGroup.addView(radioButton);
            }
            alertDialog.show();

            if (DeviceUtils.isTv(requireContext())) {
                FocusOverlayView.setupFocusObserver(alertDialog);
            }
        }
    }
}
