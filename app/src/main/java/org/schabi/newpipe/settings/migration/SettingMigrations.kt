package org.schabi.newpipe.settings.migration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import java.util.*
import org.schabi.newpipe.App
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ServiceList.SoundCloud
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.util.DeviceUtils

/**
 * This class contains the code to migrate the settings from one version to another.
 * Migrations are run automatically when the app is started and the settings version changed.
 */
object SettingMigrations {

    private val TAG = SettingMigrations::class.java.toString()
    private lateinit var sp: SharedPreferences

    private val MIGRATION_0_1 = object : Migration(0, 1) {
        override fun migrate(context: Context) {
            val editor = sp.edit()
            editor.putString(
                context.getString(R.string.preferred_open_action_key),
                context.getString(R.string.always_ask_open_action_key)
            )
            editor.apply()
        }
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(context: Context) {
            val minimizeOnExitKey = context.getString(R.string.minimize_on_exit_key)
            if (sp.getString(minimizeOnExitKey, "") ==
                context.getString(R.string.minimize_on_exit_none_key)
            ) {
                val editor = sp.edit()
                editor.putString(
                    minimizeOnExitKey,
                    context.getString(R.string.minimize_on_exit_background_key)
                )
                editor.apply()
            }
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(context: Context) {
            sp.edit().putBoolean(
                context.getString(R.string.storage_use_saf),
                !DeviceUtils.isFireTv()
            ).apply()
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(context: Context) {
            val showSearchSuggestionsKey = context.getString(R.string.show_search_suggestions_key)

            val addAllSearchSuggestionTypes = try {
                sp.getBoolean(showSearchSuggestionsKey, true)
            } catch (e: ClassCastException) {
                true
            }

            val showSearchSuggestionsValueList = HashSet<String>()
            if (addAllSearchSuggestionTypes) {
                Collections.addAll(
                    showSearchSuggestionsValueList,
                    *context.resources.getStringArray(R.array.show_search_suggestions_value_list)
                )
            }

            sp.edit().putStringSet(showSearchSuggestionsKey, showSearchSuggestionsValueList).apply()
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(context: Context) {
            val brightness = sp.getBoolean("brightness_gesture_control", true)
            val volume = sp.getBoolean("volume_gesture_control", true)

            val editor = sp.edit()
            editor.putString(
                context.getString(R.string.right_gesture_control_key),
                context.getString(if (volume) R.string.volume_control_key else R.string.none_control_key)
            )
            editor.putString(
                context.getString(R.string.left_gesture_control_key),
                context.getString(if (brightness) R.string.brightness_control_key else R.string.none_control_key)
            )
            editor.apply()
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(context: Context) {
            val loadImages = sp.getBoolean("download_thumbnail_key", true)
            sp.edit().putString(
                context.getString(R.string.image_quality_key),
                context.getString(
                    if (loadImages) R.string.image_quality_default else R.string.image_quality_none_key
                )
            ).apply()
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(context: Context) {
            val tabsManager = TabsManager.getManager(context)
            val tabs = tabsManager.tabs
            val cleanedTabs = tabs.filter { tab ->
                !(
                    tab is Tab.KioskTab &&
                        tab.kioskServiceId == SoundCloud.getServiceId() &&
                        tab.kioskId == "Top 50"
                    )
            }
            if (tabs.size != cleanedTabs.size) {
                tabsManager.saveTabs(cleanedTabs)
                MigrationManager.addMigrationInfo { uiContext ->
                    MigrationManager.createMigrationInfoDialog(
                        uiContext,
                        uiContext.getString(R.string.migration_info_6_7_title),
                        uiContext.getString(R.string.migration_info_6_7_message)
                    ).show()
                }
            }
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(context: Context) {
            val tabsManager = TabsManager.getManager(context)
            val tabs = tabsManager.tabs
            val cleanedTabs = tabs.filter { tab ->
                !(
                    tab is Tab.KioskTab &&
                        tab.kioskServiceId == YouTube.getServiceId() &&
                        tab.kioskId == "Trending"
                    )
            }
            if (tabs.size != cleanedTabs.size) {
                tabsManager.saveTabs(cleanedTabs)
            }

            val hasDefaultTrendingTab = tabs.any { it is Tab.DefaultKioskTab }

            if (tabs.size != cleanedTabs.size || hasDefaultTrendingTab) {
                MigrationManager.addMigrationInfo { uiContext ->
                    MigrationManager.createMigrationInfoDialog(
                        uiContext,
                        uiContext.getString(R.string.migration_info_7_8_title),
                        uiContext.getString(R.string.migration_info_7_8_message)
                    ).show()
                }
            }
        }
    }

    private val SETTING_MIGRATIONS = arrayOf(
        MIGRATION_0_1,
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8
    )

    private const val VERSION = 8

    @JvmStatic
    fun runMigrationsIfNeeded(context: Context) {
        sp = PreferenceManager.getDefaultSharedPreferences(context)
        val lastPrefVersionKey = context.getString(R.string.last_used_preferences_version)
        val lastPrefVersion = sp.getInt(lastPrefVersionKey, 0)

        if (App.instance.isFirstRun) {
            sp.edit().putInt(lastPrefVersionKey, VERSION).apply()
            return
        } else if (lastPrefVersion == VERSION) {
            return
        }

        var currentVersion = lastPrefVersion
        for (currentMigration in SETTING_MIGRATIONS) {
            try {
                if (currentMigration.shouldMigrate(currentVersion)) {
                    if (DEBUG) {
                        Log.d(TAG, "Migrating preferences from version $currentVersion to ${currentMigration.newVersion}")
                    }
                    currentMigration.migrate(context)
                    currentVersion = currentMigration.newVersion
                }
            } catch (e: Exception) {
                sp.edit().putInt(lastPrefVersionKey, currentVersion).apply()
                ErrorUtil.openActivity(
                    context,
                    ErrorInfo(
                        e,
                        UserAction.PREFERENCES_MIGRATION,
                        "Migrating preferences from version $lastPrefVersion to $VERSION. Error at $currentVersion => ${++currentVersion}"
                    )
                )
                return
            }
        }
        sp.edit().putInt(lastPrefVersionKey, currentVersion).apply()
    }

    abstract class Migration(val oldVersion: Int, val newVersion: Int) {
        fun shouldMigrate(currentVersion: Int): Boolean = oldVersion >= currentVersion
        abstract fun migrate(context: Context)
    }
}
