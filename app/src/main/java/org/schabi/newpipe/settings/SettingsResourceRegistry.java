package org.schabi.newpipe.settings;

import androidx.annotation.NonNull;
import androidx.annotation.XmlRes;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A registry that contains information about SettingsFragments.
 * <br/>
 * includes:
 * <ul>
 *     <li>Class of the SettingsFragment</li>
 *     <li>XML-Resource</li>
 *     <li>...</li>
 * </ul>
 *
 * E.g. used by the preference search.
 */
public final class SettingsResourceRegistry {

    private static final SettingsResourceRegistry INSTANCE = new SettingsResourceRegistry();

    private final Set<SettingRegistryEntry> registeredEntries = new HashSet<>();

    private SettingsResourceRegistry() {
        add(MainSettingsFragment.class, R.xml.main_settings).setSearchable(false);

        add(AppearanceSettingsFragment.class, R.xml.appearance_settings);
        add(ContentSettingsFragment.class, R.xml.content_settings);
        add(DebugSettingsFragment.class, R.xml.debug_settings).setSearchable(false);
        add(DownloadSettingsFragment.class, R.xml.download_settings);
        add(HistorySettingsFragment.class, R.xml.history_settings);
        add(NotificationSettingsFragment.class, R.xml.notifications_settings);
        add(PlayerNotificationSettingsFragment.class, R.xml.player_notification_settings);
        add(UpdateSettingsFragment.class, R.xml.update_settings);
        add(VideoAudioSettingsFragment.class, R.xml.video_audio_settings);
        add(ExoPlayerSettingsFragment.class, R.xml.exoplayer_settings);
    }

    private SettingRegistryEntry add(
            @NonNull final Class<? extends Fragment> fragmentClass,
            @XmlRes final int preferencesResId
    ) {
        final SettingRegistryEntry entry =
                new SettingRegistryEntry(fragmentClass, preferencesResId);
        this.registeredEntries.add(entry);
        return entry;
    }

    public SettingRegistryEntry getEntryByFragmentClass(
            final Class<? extends Fragment> fragmentClass
    ) {
        Objects.requireNonNull(fragmentClass);
        return registeredEntries.stream()
                .filter(e -> Objects.equals(e.getFragmentClass(), fragmentClass))
                .findFirst()
                .orElse(null);
    }

    public SettingRegistryEntry getEntryByPreferencesResId(@XmlRes final int preferencesResId) {
        return registeredEntries.stream()
                .filter(e -> Objects.equals(e.getPreferencesResId(), preferencesResId))
                .findFirst()
                .orElse(null);
    }

    public int getPreferencesResId(@NonNull final Class<? extends Fragment> fragmentClass) {
        final SettingRegistryEntry entry = getEntryByFragmentClass(fragmentClass);
        if (entry == null) {
            return -1;
        }
        return entry.getPreferencesResId();
    }

    public Class<? extends Fragment> getFragmentClass(@XmlRes final int preferencesResId) {
        final SettingRegistryEntry entry = getEntryByPreferencesResId(preferencesResId);
        if (entry == null) {
            return null;
        }
        return entry.getFragmentClass();
    }

    public Set<SettingRegistryEntry> getAllEntries() {
        return new HashSet<>(registeredEntries);
    }

    public static SettingsResourceRegistry getInstance() {
        return INSTANCE;
    }


    public static class SettingRegistryEntry {
        @NonNull
        private final Class<? extends Fragment> fragmentClass;
        @XmlRes
        private final int preferencesResId;

        private boolean searchable = true;

        public SettingRegistryEntry(
                @NonNull final Class<? extends Fragment> fragmentClass,
                @XmlRes final int preferencesResId
        ) {
            this.fragmentClass = Objects.requireNonNull(fragmentClass);
            this.preferencesResId = preferencesResId;
        }

        @SuppressWarnings("HiddenField")
        public SettingRegistryEntry setSearchable(final boolean searchable) {
            this.searchable = searchable;
            return this;
        }

        @NonNull
        public Class<? extends Fragment> getFragmentClass() {
            return fragmentClass;
        }

        public int getPreferencesResId() {
            return preferencesResId;
        }

        public boolean isSearchable() {
            return searchable;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SettingRegistryEntry that = (SettingRegistryEntry) o;
            return getPreferencesResId() == that.getPreferencesResId()
                    && getFragmentClass().equals(that.getFragmentClass());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getFragmentClass(), getPreferencesResId());
        }
    }
}
