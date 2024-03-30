package org.schabi.newpipe.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.openActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.util.DeviceUtils
import java.util.Collections

/**
 * In order to add a migration, follow these steps, given P is the previous version:<br></br>
 * - in the class body add a new `MIGRATION_P_P+1 = new Migration(P, P+1) { ... }` and put in
 * the `migrate()` method the code that need to be run when migrating from P to P+1<br></br>
 * - add `MIGRATION_P_P+1` at the end of [SettingMigrations.SETTING_MIGRATIONS]<br></br>
 * - increment [SettingMigrations.VERSION]'s value by 1 (so it should become P+1)
 */
object SettingMigrations {
    private val TAG: String = SettingMigrations::class.java.toString()
    private var sp: SharedPreferences? = null
    private val MIGRATION_0_1: Migration = object : Migration(0, 1) {
        public override fun migrate(context: Context) {
            // We changed the content of the dialog which opens when sharing a link to NewPipe
            // by removing the "open detail page" option.
            // Therefore, show the dialog once again to ensure users need to choose again and are
            // aware of the changed dialog.
            val editor: SharedPreferences.Editor = sp!!.edit()
            editor.putString(context.getString(R.string.preferred_open_action_key),
                    context.getString(R.string.always_ask_open_action_key))
            editor.apply()
        }
    }
    private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        protected override fun migrate(context: Context) {
            // The new application workflow introduced in #2907 allows minimizing videos
            // while playing to do other stuff within the app.
            // For an even better workflow, we minimize a stream when switching the app to play in
            // background.
            // Therefore, set default value to background, if it has not been changed yet.
            val minimizeOnExitKey: String = context.getString(R.string.minimize_on_exit_key)
            if ((sp!!.getString(minimizeOnExitKey, "")
                            == context.getString(R.string.minimize_on_exit_none_key))) {
                val editor: SharedPreferences.Editor = sp!!.edit()
                editor.putString(minimizeOnExitKey,
                        context.getString(R.string.minimize_on_exit_background_key))
                editor.apply()
            }
        }
    }
    private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        protected override fun migrate(context: Context) {
            // Storage Access Framework implementation was improved in #5415, allowing the modern
            // and standard way to access folders and files to be used consistently everywhere.
            // We reset the setting to its default value, i.e. "use SAF", since now there are no
            // more issues with SAF and users should use that one instead of the old
            // NoNonsenseFilePicker. Also, there's a bug on FireOS in which SAF open/close
            // dialogs cannot be confirmed with a remote (see #6455).
            sp!!.edit().putBoolean(
                    context.getString(R.string.storage_use_saf),
                    !DeviceUtils.isFireTv()
            ).apply()
        }
    }
    private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        protected override fun migrate(context: Context) {
            // Pull request #3546 added support for choosing the type of search suggestions to
            // show, replacing the on-off switch used before, so migrate the previous user choice
            val showSearchSuggestionsKey: String = context.getString(R.string.show_search_suggestions_key)
            var addAllSearchSuggestionTypes: Boolean
            try {
                addAllSearchSuggestionTypes = sp!!.getBoolean(showSearchSuggestionsKey, true)
            } catch (e: ClassCastException) {
                // just in case it was not a boolean for some reason, let's consider it a "true"
                addAllSearchSuggestionTypes = true
            }
            val showSearchSuggestionsValueList: Set<String> = HashSet()
            if (addAllSearchSuggestionTypes) {
                // if the preference was true, all suggestions will be shown, otherwise none
                Collections.addAll(showSearchSuggestionsValueList, *context.getResources()
                        .getStringArray(R.array.show_search_suggestions_value_list))
            }
            sp!!.edit().putStringSet(
                    showSearchSuggestionsKey, showSearchSuggestionsValueList).apply()
        }
    }
    private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        protected override fun migrate(context: Context) {
            val brightness: Boolean = sp!!.getBoolean("brightness_gesture_control", true)
            val volume: Boolean = sp!!.getBoolean("volume_gesture_control", true)
            val editor: SharedPreferences.Editor = sp!!.edit()
            editor.putString(context.getString(R.string.right_gesture_control_key),
                    context.getString(if (volume) R.string.volume_control_key else R.string.none_control_key))
            editor.putString(context.getString(R.string.left_gesture_control_key),
                    context.getString(if (brightness) R.string.brightness_control_key else R.string.none_control_key))
            editor.apply()
        }
    }
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        protected override fun migrate(context: Context) {
            val loadImages: Boolean = sp!!.getBoolean("download_thumbnail_key", true)
            sp!!.edit()
                    .putString(context.getString(R.string.image_quality_key),
                            context.getString(if (loadImages) R.string.image_quality_default else R.string.image_quality_none_key))
                    .apply()
        }
    }

    /**
     * List of all implemented migrations.
     *
     *
     * **Append new migrations to the end of the list** to keep it sorted ascending.
     * If not sorted correctly, migrations which depend on each other, may fail.
     */
    private val SETTING_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_0_1,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6)

    /**
     * Version number for preferences. Must be incremented every time a migration is necessary.
     */
    private val VERSION: Int = 6
    fun runMigrationsIfNeeded(context: Context) {
        // setup migrations and check if there is something to do
        sp = PreferenceManager.getDefaultSharedPreferences(context)
        val lastPrefVersionKey: String = context.getString(R.string.last_used_preferences_version)
        val lastPrefVersion: Int = sp.getInt(lastPrefVersionKey, 0)

        // no migration to run, already up to date
        if (App.Companion.getApp().isFirstRun()) {
            sp.edit().putInt(lastPrefVersionKey, VERSION).apply()
            return
        } else if (lastPrefVersion == VERSION) {
            return
        }

        // run migrations
        var currentVersion: Int = lastPrefVersion
        for (currentMigration: Migration in SETTING_MIGRATIONS) {
            try {
                if (currentMigration.shouldMigrate(currentVersion)) {
                    if (MainActivity.Companion.DEBUG) {
                        Log.d(TAG, ("Migrating preferences from version "
                                + currentVersion + " to " + currentMigration.newVersion))
                    }
                    currentMigration.migrate(context)
                    currentVersion = currentMigration.newVersion
                }
            } catch (e: Exception) {
                // save the version with the last successful migration and report the error
                sp.edit().putInt(lastPrefVersionKey, currentVersion).apply()
                openActivity(context, ErrorInfo(
                        e,
                        UserAction.PREFERENCES_MIGRATION,
                        ("Migrating preferences from version " + lastPrefVersion + " to "
                                + VERSION + ". "
                                + "Error at " + currentVersion + " => " + ++currentVersion)
                ))
                return
            }
        }

        // store the current preferences version
        sp.edit().putInt(lastPrefVersionKey, currentVersion).apply()
    }

    abstract class Migration protected constructor(val oldVersion: Int, val newVersion: Int) {
        /**
         * @param currentVersion current settings version
         * @return Returns whether this migration should be run.
         * A migration is necessary if the old version of this migration is lower than or equal to
         * the current settings version.
         */
        fun shouldMigrate(currentVersion: Int): Boolean {
            return oldVersion >= currentVersion
        }

        abstract fun migrate(context: Context)
    }
}
