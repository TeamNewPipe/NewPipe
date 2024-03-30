package org.schabi.newpipe.settings

import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import org.schabi.newpipe.R
import java.util.Objects
import java.util.function.Predicate

/**
 * A registry that contains information about SettingsFragments.
 * <br></br>
 * includes:
 *
 *  * Class of the SettingsFragment
 *  * XML-Resource
 *  * ...
 *
 *
 * E.g. used by the preference search.
 */
class SettingsResourceRegistry private constructor() {
    private val registeredEntries: MutableSet<SettingRegistryEntry?> = HashSet()

    init {
        add(MainSettingsFragment::class.java, R.xml.main_settings).setSearchable(false)
        add(AppearanceSettingsFragment::class.java, R.xml.appearance_settings)
        add(ContentSettingsFragment::class.java, R.xml.content_settings)
        add(DebugSettingsFragment::class.java, R.xml.debug_settings).setSearchable(false)
        add(DownloadSettingsFragment::class.java, R.xml.download_settings)
        add(HistorySettingsFragment::class.java, R.xml.history_settings)
        add(NotificationSettingsFragment::class.java, R.xml.notifications_settings)
        add(PlayerNotificationSettingsFragment::class.java, R.xml.player_notification_settings)
        add(UpdateSettingsFragment::class.java, R.xml.update_settings)
        add(VideoAudioSettingsFragment::class.java, R.xml.video_audio_settings)
        add(ExoPlayerSettingsFragment::class.java, R.xml.exoplayer_settings)
        add(BackupRestoreSettingsFragment::class.java, R.xml.backup_restore_settings)
    }

    private fun add(
            fragmentClass: Class<out Fragment?>,
            @XmlRes preferencesResId: Int
    ): SettingRegistryEntry {
        val entry: SettingRegistryEntry = SettingRegistryEntry(fragmentClass, preferencesResId)
        registeredEntries.add(entry)
        return entry
    }

    fun getEntryByFragmentClass(
            fragmentClass: Class<out Fragment?>?
    ): SettingRegistryEntry? {
        Objects.requireNonNull(fragmentClass)
        return registeredEntries.stream()
                .filter(Predicate({ e: SettingRegistryEntry? -> Objects.equals(e!!.getFragmentClass(), fragmentClass) }))
                .findFirst()
                .orElse(null)
    }

    fun getEntryByPreferencesResId(@XmlRes preferencesResId: Int): SettingRegistryEntry? {
        return registeredEntries.stream()
                .filter(Predicate({ e: SettingRegistryEntry? -> Objects.equals(e!!.getPreferencesResId(), preferencesResId) }))
                .findFirst()
                .orElse(null)
    }

    fun getPreferencesResId(fragmentClass: Class<out Fragment?>): Int {
        val entry: SettingRegistryEntry? = getEntryByFragmentClass(fragmentClass)
        if (entry == null) {
            return -1
        }
        return entry.getPreferencesResId()
    }

    fun getFragmentClass(@XmlRes preferencesResId: Int): Class<out Fragment>? {
        val entry: SettingRegistryEntry? = getEntryByPreferencesResId(preferencesResId)
        if (entry == null) {
            return null
        }
        return entry.getFragmentClass()
    }

    fun getAllEntries(): Set<SettingRegistryEntry?> {
        return HashSet(registeredEntries)
    }

    class SettingRegistryEntry(
            fragmentClass: Class<out Fragment?>,
            @field:XmlRes @param:XmlRes private val preferencesResId: Int
    ) {
        private val fragmentClass: Class<out Fragment>
        private var searchable: Boolean = true

        init {
            this.fragmentClass = Objects.requireNonNull(fragmentClass)
        }

        fun setSearchable(searchable: Boolean): SettingRegistryEntry {
            this.searchable = searchable
            return this
        }

        fun getFragmentClass(): Class<out Fragment> {
            return fragmentClass
        }

        fun getPreferencesResId(): Int {
            return preferencesResId
        }

        fun isSearchable(): Boolean {
            return searchable
        }

        public override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that: SettingRegistryEntry = o as SettingRegistryEntry
            return (getPreferencesResId() == that.getPreferencesResId()
                    && (getFragmentClass() == that.getFragmentClass()))
        }

        public override fun hashCode(): Int {
            return Objects.hash(getFragmentClass(), getPreferencesResId())
        }
    }

    companion object {
        private val INSTANCE: SettingsResourceRegistry = SettingsResourceRegistry()
        fun getInstance(): SettingsResourceRegistry {
            return INSTANCE
        }
    }
}
