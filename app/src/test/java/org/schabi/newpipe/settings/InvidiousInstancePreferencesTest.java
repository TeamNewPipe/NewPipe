package org.schabi.newpipe.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;

import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InvidiousInstancePreferencesTest {
    @Test
    public void switchingPersistsBothOriginsAndRestartsOnlyWhenTheActiveBackendChanges()
            throws Exception {
        final Context context = RuntimeEnvironment.getApplication();
        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        preferences.edit().clear().putString(context.getString(R.string.invidious_instance_key),
                "https://old.example").commit();
        final ContentSettingsFragment fragment = mock(ContentSettingsFragment.class,
                CALLS_REAL_METHODS);
        doReturn(context).when(fragment).requireContext();
        doReturn(context.getResources()).when(fragment).getResources();
        final FragmentActivity activity = mock(FragmentActivity.class);
        doReturn(activity).when(fragment).requireActivity();
        final PreferenceManager manager = new PreferenceManager(context);
        final PreferenceScreen screen = manager.createPreferenceScreen(context);
        final EditTextPreference active = new EditTextPreference(context);
        final EditTextPreference saved = new EditTextPreference(context);
        final ListPreference choices = new ListPreference(context);
        choices.setPersistent(false);
        bind(fragment, screen, active, R.string.invidious_instance_key, context);
        bind(fragment, screen, saved, R.string.invidious_instances_key, context);
        bind(fragment, screen, new EditTextPreference(context),
                R.string.invidious_media_origins_key, context);
        bind(fragment, screen, choices, R.string.invidious_switch_instance_key, context);
        bind(fragment, screen, new Preference(context), R.string.invidious_directory_key, context);
        bind(fragment, screen, new Preference(context), R.string.invidious_enabled_key, context);
        final Method setup = ContentSettingsFragment.class
                .getDeclaredMethod("setupInvidiousPreferences");
        setup.setAccessible(true);
        setup.invoke(fragment);

        try (MockedStatic<NavigationHelper> navigation = mockStatic(NavigationHelper.class)) {
            assertFalse(active.callChangeListener("HTTPS://NEW.EXAMPLE/"));
            assertEquals("https://new.example", preferences.getString(
                    context.getString(R.string.invidious_instance_key), ""));
            assertEquals("https://old.example\nhttps://new.example", saved.getText());
            assertEquals(2, choices.getEntries().length);
            navigation.verifyNoInteractions();

            preferences.edit().putBoolean(context.getString(R.string.invidious_enabled_key),
                    true).commit();
            assertFalse(choices.callChangeListener("https://old.example"));
            assertEquals("https://old.example", active.getText());
            assertEquals("https://old.example", choices.getValue());
            navigation.verify(() -> NavigationHelper.restartApp(activity));
        }
    }

    private static void bind(final ContentSettingsFragment fragment, final PreferenceScreen screen,
                             final Preference preference, final int key, final Context context) {
        preference.setKey(context.getString(key));
        screen.addPreference(preference);
        doReturn(preference).when(fragment).requirePreference(key);
    }
}
