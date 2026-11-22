package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.PopupMenu;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/** Flat player choices avoid AppCompat's cascading-menu anchor cleanup in desktop windows. */
public final class PlayerPopupMenu {
    private ListPopupWindow popup;
    private AlertDialog dialog;

    static View.OnAttachStateChangeListener detachListener(final Runnable onDetach) {
        return new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(final View view) {
            }

            @Override
            public void onViewDetachedFromWindow(final View view) {
                onDetach.run();
            }
        };
    }

    void dismiss() {
        if (dialog != null) {
            final AlertDialog previous = dialog;
            dialog = null;
            previous.dismiss();
        }
        if (popup != null) {
            final ListPopupWindow previous = popup;
            popup = null;
            previous.dismiss();
        }
    }

    boolean show(final PopupMenu menu, final View anchor, final Runnable onDismiss) {
        if (menu == null || !anchor.isAttachedToWindow() || !anchor.isShown()
                || anchor.getWindowToken() == null) {
            dismiss();
            return false;
        }
        return show(menu, anchor, onDismiss,
                DeviceUtils.isDesktopMode(anchor.getContext()));
    }

    boolean show(final PopupMenu menu, final View anchor, final Runnable onDismiss,
                 final boolean useDialog) {
        dismiss();
        if (menu == null || !anchor.isAttachedToWindow() || !anchor.isShown()
                || anchor.getWindowToken() == null) {
            return false;
        }
        final List<MenuItem> items = new ArrayList<>();
        for (int index = 0; index < menu.getMenu().size(); index++) {
            final MenuItem item = menu.getMenu().getItem(index);
            if (item.isVisible()) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return false;
        }
        final MaterialAlertDialogBuilder dialogBuilder = useDialog
                ? new MaterialAlertDialogBuilder(dialogContext(anchor)) : null;
        final Context context = useDialog ? dialogBuilder.getContext()
                : new ContextThemeWrapper(anchor.getContext(), R.style.PlayerOverlayPopupMenu);
        final ArrayAdapter<MenuItem> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, items) {
            @Override
            public boolean areAllItemsEnabled() {
                return false;
            }

            @Override
            public boolean isEnabled(final int position) {
                return getItem(position).isEnabled();
            }

            @Override
            public View getView(final int position, final View recycled, final ViewGroup parent) {
                final TextView row = (TextView) super.getView(position, recycled, parent);
                row.setText(getItem(position).getTitle());
                row.setEnabled(isEnabled(position));
                org.schabi.newpipe.util.AppFonts.inflated(row);
                return row;
            }
        };
        if (useDialog) {
            final AlertDialog choices = dialogBuilder
                    .setAdapter(adapter, (selectedDialog, position) -> {
                        final MenuItem selected = items.get(position);
                        if (selected.isEnabled()) {
                            menu.getMenu().performIdentifierAction(selected.getItemId(),
                                    Menu.FLAG_PERFORM_NO_CLOSE);
                        }
                    })
                    .create();
            choices.setOnDismissListener(ignored -> {
                if (dialog == choices) {
                    dialog = null;
                }
                onDismiss.run();
            });
            dialog = choices;
            // Player views are inflated from the application context, including in DeX.
            // Attach the dialog to their actual window rather than requiring an Activity context.
            final Window window = choices.getWindow();
            if (window != null) {
                final WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
                attributes.token = anchor.getWindowToken();
                window.setAttributes(attributes);
            }
            try {
                choices.show();
                org.schabi.newpipe.util.AppFonts.observeDialog(choices);
                return true;
            } catch (final WindowManager.BadTokenException exception) {
                dismiss();
                return false;
            }
        }
        final ListPopupWindow choices = new ListPopupWindow(context);
        choices.setAnchorView(anchor);
        choices.setModal(true);
        choices.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        choices.setAdapter(adapter);
        final int available = anchor.getRootView().getWidth();
        final int minimum = (int) (180 * context.getResources().getDisplayMetrics().density);
        int width = minimum;
        for (int index = 0; index < adapter.getCount(); index++) {
            final View row = adapter.getView(index, null, new android.widget.FrameLayout(context));
            row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            width = Math.max(width, row.getMeasuredWidth());
        }
        choices.setContentWidth(available > 0 ? Math.min(width, available) : width);
        choices.setOnDismissListener(() -> {
            if (popup == choices) {
                popup = null;
            }
            onDismiss.run();
        });
        choices.setOnItemClickListener((parent, view, position, id) -> {
            final MenuItem selected = items.get(position);
            if (selected.isEnabled()) {
                choices.dismiss();
                menu.getMenu().performIdentifierAction(selected.getItemId(),
                        Menu.FLAG_PERFORM_NO_CLOSE);
            }
        });
        popup = choices;
        try {
            choices.show();
            return true;
        } catch (final WindowManager.BadTokenException exception) {
            // A desktop or popup-player window can disappear after the anchor check.
            dismiss();
            return false;
        }
    }

    public static Context dialogContext(final View anchor) {
        final Context source = anchor.getContext();
        final WindowManager manager = (WindowManager) source.getSystemService(
                Context.WINDOW_SERVICE);
        // Recreating the default display's Resources can discard legacy theme attributes.
        // Keep the anchor's Resources on that display; rebuild the user palette only when
        // the player actually belongs to another display.
        final boolean sameDisplay = anchor.getDisplay() == null || manager == null
                || manager.getDefaultDisplay().getDisplayId() == anchor.getDisplay().getDisplayId();
        final Context base = sameDisplay ? source
                : PlayerUiTheme.createContext(source.createDisplayContext(anchor.getDisplay()));
        final ContextThemeWrapper context = new ContextThemeWrapper(base, 0);
        context.getTheme().setTo(base.getTheme());
        // Display contexts and legacy service/view contexts can lack AppCompat attributes.
        // Fill missing attributes from a complete Material theme without replacing the palette.
        context.getTheme().applyStyle(ThemeHelper.getThemeForService(source, -1), false);
        return context;
    }
}
