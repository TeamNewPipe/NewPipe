package org.schabi.newpipe.settings.migration;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Consumer;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * MigrationManager is responsible for running migrations and showing the user information about
 * the migrations that were applied.
 */
public final class MigrationManager {

    private static final String TAG = MigrationManager.class.getSimpleName();
    /**
     * List of UI actions that are performed after the UI is initialized (e.g. showing alert
     * dialogs) to inform the user about changes that were applied by migrations.
     */
    private static final List<Consumer<Context>> MIGRATION_INFO = new ArrayList<>();

    private MigrationManager() {
        // MigrationManager is a utility class that is completely static
    }

    /**
     * Run all migrations that are needed for the current version of NewPipe.
     * This method should be called at the start of the application, before any other operations
     * that depend on the settings.
     *
     * @param context Context that can be used to run migrations
     */
    public static void runMigrationsIfNeeded(@NonNull final Context context) {
        SettingMigrations.runMigrationsIfNeeded(context);
    }

    /**
     * Perform UI actions informing about migrations that took place if they are present.
     * @param context Context that can be used to show dialogs/snackbars/toasts
     */
    public static void showUserInfoIfPresent(@NonNull final Context context) {
        if (MIGRATION_INFO.isEmpty()) {
            return;
        }

        try {
            MIGRATION_INFO.get(0).accept(context);
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(context, "Showing migration info to the user", e);
            // Remove the migration that caused the error and continue with the next one
            MIGRATION_INFO.remove(0);
            showUserInfoIfPresent(context);
        }
    }

    /**
     * Add a migration info action that will be executed after the UI is initialized.
     * This can be used to show dialogs/snackbars/toasts to inform the user about changes that
     * were applied by migrations.
     *
     * @param info the action to be executed
     */
    public static void addMigrationInfo(final Consumer<Context> info) {
        MIGRATION_INFO.add(info);
    }

    /**
     * This method should be called when the user dismisses the migration info
     * to check if there are any more migration info actions to be shown.
     * @param context Context that can be used to show dialogs/snackbars/toasts
     */
    public static void onMigrationInfoDismissed(@NonNull final Context context) {
        MIGRATION_INFO.remove(0);
        showUserInfoIfPresent(context);
    }

    /**
     * Creates a dialog to inform the user about the migration.
     * @param uiContext Context that can be used to show dialogs/snackbars/toasts
     * @param title the title of the dialog
     * @param message the message of the dialog
     * @return the dialog that can be shown to the user with a custom dismiss listener
     */
    static AlertDialog createMigrationInfoDialog(@NonNull final Context uiContext,
                                                 @NonNull final String title,
                                                 @NonNull final String message) {
        return new AlertDialog.Builder(uiContext)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener(dialog ->
                        MigrationManager.onMigrationInfoDismissed(uiContext))
                .setCancelable(false) // prevents the dialog from being dismissed accidentally
                .create();
    }

}
